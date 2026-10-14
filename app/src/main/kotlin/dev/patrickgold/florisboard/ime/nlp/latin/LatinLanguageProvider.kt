/*
 * Copyright (C) 2022-2025 The FlorisBoard Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.patrickgold.florisboard.ime.nlp.latin

import android.content.Context
import dev.patrickgold.florisboard.appContext
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.editorInstance
import dev.patrickgold.florisboard.ime.core.Subtype
import dev.patrickgold.florisboard.ime.editor.EditorContent
import dev.patrickgold.florisboard.ime.editor.isPasswordLike
import dev.patrickgold.florisboard.ime.nlp.SpellingProvider
import dev.patrickgold.florisboard.ime.nlp.SpellingResult
import dev.patrickgold.florisboard.ime.nlp.SuggestionCandidate
import dev.patrickgold.florisboard.ime.nlp.SuggestionProvider
import dev.patrickgold.florisboard.ime.nlp.WordSuggestionCandidate
import dev.patrickgold.florisboard.ime.dictionary.DictionaryManager
import dev.patrickgold.florisboard.ime.nlp.prediction.BundledLexicon
import dev.patrickgold.florisboard.ime.nlp.prediction.LearnedLexicon
import dev.patrickgold.florisboard.ime.nlp.prediction.LearnedWordFile
import dev.patrickgold.florisboard.ime.nlp.prediction.LearnedWords
import dev.patrickgold.florisboard.ime.nlp.prediction.LearningGate
import dev.patrickgold.florisboard.ime.nlp.prediction.RevertCountStore
import dev.patrickgold.florisboard.ime.nlp.prediction.TypingLearner
import dev.patrickgold.florisboard.ime.nlp.prediction.WordPredictor
import dev.patrickgold.florisboard.keyboardManager
import java.io.File
import dev.patrickgold.florisboard.lib.devtools.flogDebug
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import org.florisboard.lib.android.readText
import org.florisboard.lib.kotlin.guardedByLock

class LatinLanguageProvider(context: Context) : SpellingProvider, SuggestionProvider, TypingLearner {
    companion object {
        // Default user ID used for all subtypes, unless otherwise specified.
        // See `ime/core/Subtype.kt` Line 210 and 211 for the default usage
        const val ProviderId = "org.florisboard.nlp.providers.latin"
    }

    private val appContext by context.appContext()
    private val prefs by FlorisPreferenceStore
    private val editorInstance by context.editorInstance()
    private val keyboardManager by context.keyboardManager()
    private val learnedFile = LearnedWordFile(File(appContext.noBackupFilesDir, "prediction"))
    private var learnedCache = LearnedLexicon()
    private var learnedLoaded = false
    @Volatile private var lexicon: Map<String, Int> = emptyMap()

    private val wordData = guardedByLock { mutableMapOf<String, Int>() }
    private val wordDataSerializer = MapSerializer(String.serializer(), Int.serializer())

    override val providerId = ProviderId

    override suspend fun create() {
        // Here we initialize our provider, set up all things which are not language dependent.
    }

    override suspend fun preload(subtype: Subtype) = withContext(Dispatchers.IO) {
        // Here we have the chance to preload dictionaries and prepare a neural network for a specific language.
        // Is kept in sync with the active keyboard subtype of the user, however a new preload does not necessary mean
        // the previous language is not needed anymore (e.g. if the user constantly switches between two subtypes)

        // To read a file from the APK assets the following methods can be used:
        // appContext.assets.open()
        // appContext.assets.reader()
        // appContext.assets.bufferedReader()
        // appContext.assets.readText()
        // To copy an APK file/dir to the file system cache (appContext.cacheDir), the following methods are available:
        // appContext.assets.copy()
        // appContext.assets.copyRecursively()

        // The subtype we get here contains a lot of data, however we are only interested in subtype.primaryLocale and
        // subtype.secondaryLocales.

        ensureEnglishWords()
        if (lexicon.isEmpty()) {
            val packs = appContext.assets.list("ime/dictionary-packs").orEmpty()
                .filter { it.endsWith(".zip") }
                .map { name -> appContext.assets.open("ime/dictionary-packs/$name").use { it.readBytes() } }
            lexicon = BundledLexicon.fromPackZips(packs)
        }
        RevertCountStore.ensure(File(appContext.noBackupFilesDir, "prediction"))
    }

    override suspend fun spell(
        subtype: Subtype,
        word: String,
        precedingWords: List<String>,
        followingWords: List<String>,
        maxSuggestionCount: Int,
        allowPossiblyOffensive: Boolean,
        isPrivateSession: Boolean,
    ): SpellingResult {
        return when (word.lowercase()) {
            // Use typo for typing errors
            "typo" -> SpellingResult.typo(arrayOf("typo1", "typo2", "typo3"))
            // Use grammar error if the algorithm can detect this. On Android 11 and lower grammar errors are visually
            // marked as typos due to a lack of support
            "gerror" -> SpellingResult.grammarError(arrayOf("grammar1", "grammar2", "grammar3"))
            // Use valid word for valid input
            else -> SpellingResult.validWord()
        }
    }

    override suspend fun suggest(
        subtype: Subtype,
        content: EditorContent,
        maxCandidateCount: Int,
        allowPossiblyOffensive: Boolean,
        isPrivateSession: Boolean,
    ): List<SuggestionCandidate> {
        val field = currentField()
        if (!LearningGate.allowsPrediction(prefs.suggestion.predictionEnabled.get(), field)) return emptyList()
        val learned = if (LearningGate.allowsLearning(prefs.suggestion.learnFromTyping.get(), field)) {
            learnedSnapshot()
        } else {
            null
        }
        val (_, current) = WordPredictor.contextTokens(content.textBeforeSelection)
        return WordPredictor.predict(
            textBeforeCursor = content.textBeforeSelection,
            lexicon = lexicon,
            learned = learned,
            maxCandidates = maxCandidateCount,
            knownWords = knownWords(current),
            level = prefs.suggestion.autocorrectLevel.get(),
        ).map { prediction ->
            WordSuggestionCandidate(
                text = prediction.text,
                confidence = prediction.confidence,
                isEligibleForAutoCommit = prediction.autoCommit,
                isEligibleForUserRemoval = false,
                sourceProvider = this,
            )
        }
    }

    override suspend fun recordCommitted(word: String, previous: String?) {
        val field = currentField()
        if (!LearningGate.allowsLearning(prefs.suggestion.learnFromTyping.get(), field)) return
        val next = learnedSnapshot().record(word, previous)
        learnedCache = next
        try {
            learnedFile.save(next)
        } catch (_: java.io.IOException) {
            // A failed write must not crash the keyboard. The in-memory copy is kept for this session.
        }
    }

    override suspend fun clearLearned() {
        learnedCache = LearnedLexicon()
        learnedLoaded = true
        learnedFile.clear()
    }

    private fun currentField(): LearningGate.Field {
        val info = editorInstance.activeInfo
        return LearningGate.Field(
            passwordLike = info.inputAttributes.isPasswordLike(),
            incognito = keyboardManager.activeState.isIncognitoMode,
            noPersonalizedLearning = info.imeOptions.flagNoPersonalizedLearning,
            noSuggestions = info.inputAttributes.flagTextNoSuggestions,
            vaultHandoff = keyboardManager.vaultLearningHold,
        )
    }

    /**
     * Words that must not be replaced: the English frequency list, the user dictionary, and words
     * the user has undone twice. The check is membership of the token being typed, not a copy of
     * the whole list. A lookup failure is treated as "not known" and is not logged.
     */
    private suspend fun ensureEnglishWords() {
        wordData.withLock { data ->
            if (data.isEmpty()) {
                val rawData = appContext.assets.readText("ime/dict/data.json")
                val jsonData = Json.decodeFromString(wordDataSerializer, rawData)
                data.putAll(jsonData)
            }
        }
    }

    private suspend fun knownWords(current: String?): Set<String> {
        val key = current?.let(LearnedWords::normalize) ?: return emptySet()
        val out = HashSet<String>(3)
        val inEnglish = try {
            ensureEnglishWords()
            wordData.withLock { data -> key in data }
        } catch (_: Exception) {
            false
        }
        if (inEnglish) out += key
        if (inUserDictionary(current, key)) out += key
        RevertCountStore.ensure(File(appContext.noBackupFilesDir, "prediction"))
        if (key in RevertCountStore.protectedWords()) out += key
        return out
    }

    private fun inUserDictionary(typed: String, key: String): Boolean {
        val manager = try {
            DictionaryManager.default()
        } catch (_: UninitializedPropertyAccessException) {
            return false
        }
        return try {
            manager.loadUserDictionariesIfNecessary()
            val daos = listOfNotNull(manager.florisUserDictionaryDao(), manager.systemUserDictionaryDao())
            daos.any { dao -> dao.queryExact(typed).isNotEmpty() || dao.queryExact(key).isNotEmpty() }
        } catch (_: Exception) {
            false
        }
    }

    private fun learnedSnapshot(): LearnedLexicon {
        if (!learnedLoaded) {
            learnedCache = try {
                learnedFile.load()
            } catch (_: java.io.IOException) {
                LearnedLexicon()
            }
            learnedLoaded = true
        }
        return learnedCache
    }

    override suspend fun notifySuggestionAccepted(subtype: Subtype, candidate: SuggestionCandidate) {
        // Do not log the word. Typed and suggested text stays off logcat, including debug.
        flogDebug { "suggestion accepted" }
    }

    override suspend fun notifySuggestionReverted(subtype: Subtype, candidate: SuggestionCandidate) {
        flogDebug { "suggestion reverted" }
    }

    override suspend fun removeSuggestion(subtype: Subtype, candidate: SuggestionCandidate): Boolean {
        flogDebug { "suggestion remove requested" }
        return false
    }

    override suspend fun getListOfWords(subtype: Subtype): List<String> {
        return wordData.withLock { it.keys.toList() }
    }

    override suspend fun getFrequencyForWord(subtype: Subtype, word: String): Double {
        return wordData.withLock { it.getOrDefault(word, 0) / 255.0 }
    }

    override suspend fun destroy() {
        // Here we have the chance to de-allocate memory and finish our work. However this might never be called if
        // the app process is killed (which will most likely always be the case).
    }
}
