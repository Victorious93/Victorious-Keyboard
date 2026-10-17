/*
 * Copyright (C) 2021-2025 The FlorisBoard Contributors
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

package dev.patrickgold.florisboard.ime.nlp

import android.content.Context
import android.os.SystemClock
import android.util.LruCache
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.clipboardManager
import dev.patrickgold.florisboard.editorInstance
import dev.patrickgold.florisboard.ime.ai.AiPrivacy
import dev.patrickgold.florisboard.ime.clipboard.lanes.ClipVisibility
import dev.patrickgold.florisboard.ime.clipboard.provider.ClipboardItem
import dev.patrickgold.florisboard.ime.clipboard.provider.ItemType
import dev.patrickgold.florisboard.ime.core.Subtype
import dev.patrickgold.florisboard.ime.editor.EditorContent
import dev.patrickgold.florisboard.ime.editor.EditorRange
import dev.patrickgold.florisboard.ime.media.emoji.EmojiSuggestionProvider
import dev.patrickgold.florisboard.ime.nlp.han.HanShapeBasedLanguageProvider
import dev.patrickgold.florisboard.ime.editor.isPasswordLike
import dev.patrickgold.florisboard.ime.nlp.latin.LatinLanguageProvider
import dev.patrickgold.florisboard.ime.nlp.prediction.LearningGate
import dev.patrickgold.florisboard.ime.nlp.prediction.TypingLearner
import dev.patrickgold.florisboard.ime.nlp.prediction.WordPredictor
import dev.patrickgold.florisboard.customSuggestions
import dev.patrickgold.florisboard.fleetHub
import dev.patrickgold.florisboard.ime.slash.SlashCatalog
import dev.patrickgold.florisboard.ime.smartbar.StripPlan
import dev.patrickgold.florisboard.ime.smartbar.StripPlanner
import dev.patrickgold.florisboard.keyboardManager
import dev.patrickgold.florisboard.slashCommands
import dev.patrickgold.florisboard.localAiManager
import dev.patrickgold.florisboard.lib.util.NetworkUtils
import dev.patrickgold.florisboard.subtypeManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.florisboard.lib.kotlin.guardedByLock
import org.florisboard.lib.kotlin.collectLatestIn
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.properties.Delegates

private const val BLANK_STR_PATTERN = "^\\s*$"

class NlpManager(context: Context) {
    private val blankStrRegex = Regex(BLANK_STR_PATTERN)

    private val prefs by FlorisPreferenceStore
    private val clipboardManager by context.clipboardManager()
    private val customSuggestions by context.customSuggestions()
    private val slashCommands by context.slashCommands()
    private val fleet by context.fleetHub()
    private val editorInstance by context.editorInstance()
    private val keyboardManager by context.keyboardManager()
    private val localAi by context.localAiManager()
    private val subtypeManager by context.subtypeManager()

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val clipboardSuggestionProvider = ClipboardSuggestionProvider(context)
    private val emojiSuggestionProvider = EmojiSuggestionProvider(context)
    private val providers = guardedByLock {
        mapOf(
            LatinLanguageProvider.ProviderId to ProviderInstanceWrapper(LatinLanguageProvider(context)),
            HanShapeBasedLanguageProvider.ProviderId to ProviderInstanceWrapper(HanShapeBasedLanguageProvider(context)),
        )
    }
    // lock unnecessary because values constant
    private val providersForceSuggestionOn = mutableMapOf<String, Boolean>()

    private val internalSuggestionsGuard = Mutex()
    private var internalSuggestions by Delegates.observable(SystemClock.uptimeMillis() to listOf<SuggestionCandidate>()) { _, _, _ ->
        scope.launch { assembleCandidates() }
    }

    private val _activeCandidatesFlow = MutableStateFlow(listOf<SuggestionCandidate>())
    val activeCandidatesFlow = _activeCandidatesFlow.asStateFlow()
    inline var activeCandidates
        get() = activeCandidatesFlow.value
        private set(v) {
            _activeCandidatesFlow.value = v
        }

    val debugOverlaySuggestionsInfos = LruCache<Long, Pair<String, SpellingResult>>(10)
    var debugOverlayVersion = MutableStateFlow(0)

    init {
        clipboardManager.primaryClipFlow.collectLatestIn(scope) {
            assembleCandidates()
        }
        prefs.suggestion.enabled.asFlow().collectLatestIn(scope) {
            assembleCandidates()
        }
        prefs.suggestion.predictionEnabled.asFlow().collectLatestIn(scope) {
            assembleCandidates()
        }
        prefs.suggestion.learnFromTyping.asFlow().collectLatestIn(scope) {
            assembleCandidates()
        }
        prefs.clipboard.suggestionEnabled.asFlow().collectLatestIn(scope) {
            assembleCandidates()
        }
        prefs.emoji.suggestionEnabled.asFlow().collectLatestIn(scope) {
            assembleCandidates()
        }
        prefs.slash.enabled.asFlow().collectLatestIn(scope) {
            assembleCandidates()
        }
        customSuggestions.items.collectLatestIn(scope) {
            assembleCandidates()
        }
        slashCommands.items.collectLatestIn(scope) {
            assembleCandidates()
        }
        fleet.snapshot.collectLatestIn(scope) {
            assembleCandidates()
        }
        subtypeManager.activeSubtypeFlow.collectLatestIn(scope) { subtype ->
            preload(subtype)
        }
    }

    /**
     * Gets the punctuation rule from the currently active subtype and returns it. Falls back to a default one if the
     * subtype does not exist or defines an invalid punctuation rule.
     *
     * @return The punctuation rule or a fallback.
     */
    fun getActivePunctuationRule(): PunctuationRule {
        return getPunctuationRule(subtypeManager.activeSubtype)
    }

    /**
     * Gets the punctuation rule from the given subtype and returns it. Falls back to a default one if the subtype does
     * not exist or defines an invalid punctuation rule.
     *
     * @return The punctuation rule or a fallback.
     */
    fun getPunctuationRule(subtype: Subtype): PunctuationRule {
        return keyboardManager.resources.punctuationRules.value[subtype.punctuationRule] ?: PunctuationRule.Fallback
    }

    private suspend fun getSpellingProvider(subtype: Subtype): SpellingProvider {
        return providers.withLock { it[subtype.nlpProviders.spelling] }?.provider as? SpellingProvider
            ?: FallbackNlpProvider
    }

    private suspend fun getSuggestionProvider(subtype: Subtype): SuggestionProvider {
        return providers.withLock { it[subtype.nlpProviders.suggestion] }?.provider as? SuggestionProvider
            ?: FallbackNlpProvider
    }

    fun preload(subtype: Subtype) {
        scope.launch {
            emojiSuggestionProvider.preload(subtype)
            providers.withLock { providers ->
                subtype.nlpProviders.forEach { _, providerId ->
                    providers[providerId]?.let { provider ->
                        provider.createIfNecessary()
                        provider.preload(subtype)
                    }
                }
            }
        }
    }

    /**
     * Spell wrapper helper which calls the spelling provider and returns the result. Coroutine management must be done
     * by the source spell checker service.
     */
    suspend fun spell(
        subtype: Subtype,
        word: String,
        precedingWords: List<String>,
        followingWords: List<String>,
        maxSuggestionCount: Int,
    ): SpellingResult {
        return getSpellingProvider(subtype).spell(
            subtype = subtype,
            word = word,
            precedingWords = precedingWords,
            followingWords = followingWords,
            maxSuggestionCount = maxSuggestionCount,
            allowPossiblyOffensive = !prefs.suggestion.blockPossiblyOffensive.get(),
            isPrivateSession = keyboardManager.activeState.isIncognitoMode,
        )
    }

    suspend fun determineLocalComposing(
        textBeforeSelection: CharSequence, breakIterators: BreakIteratorGroup, localLastCommitPosition: Int
    ): EditorRange {
        return getSuggestionProvider(subtypeManager.activeSubtype).determineLocalComposing(
            subtypeManager.activeSubtype, textBeforeSelection, breakIterators, localLastCommitPosition
        )
    }

    fun providerForcesSuggestionOn(subtype: Subtype): Boolean {
        // Using a cache because I have no idea how fast the runBlocking is
        return providersForceSuggestionOn.getOrPut(subtype.nlpProviders.suggestion) {
            runBlocking {
                getSuggestionProvider(subtype).forcesSuggestionOn
            }
        }
    }

    fun isSuggestionOn(): Boolean =
        prefs.suggestion.enabled.get()
            || prefs.suggestion.predictionEnabled.get()
            || prefs.emoji.suggestionEnabled.get()
            || providerForcesSuggestionOn(subtypeManager.activeSubtype)

    fun mayLearnFromCurrentField(): Boolean {
        val info = editorInstance.activeInfo
        return LearningGate.allowsLearning(
            learningEnabled = prefs.suggestion.learnFromTyping.get(),
            field = currentLearningField(),
        )
    }

    private fun currentLearningField(): LearningGate.Field {
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
     * Optional on-device continuation. Off unless Local AI suggestions and learning are both on.
     * The prefix is read only after that check, and the model call itself is not awaited.
     */
    private fun localSuggestion(content: EditorContent): WordSuggestionCandidate? {
        val config = localAi.config.value
        val allow = AiPrivacy.maySuggest(
            suggestionsEnabled = config.suggestionsEnabled,
            modelSelected = config.selectedModelId != null,
            learningEnabled = prefs.suggestion.learnFromTyping.get(),
            predictionEnabled = prefs.suggestion.predictionEnabled.get(),
            field = currentLearningField(),
        )
        if (!allow) {
            localAi.offerSuggestion(null)
            return null
        }
        val prefix = WordPredictor.contextTokens(content.textBeforeSelection).second
        return localAi.offerSuggestion(prefix)
    }

    /**
     * Stores [committed] (or the token being typed, when null). Returns before reading the editor
     * when learning is not allowed, so a password field is not captured.
     */
    fun noteTypedOrCommitted(committed: String?) {
        if (!mayLearnFromCurrentField()) return
        val (previous, current) = WordPredictor.contextTokens(editorInstance.activeContent.textBeforeSelection)
        val word = committed ?: current ?: return
        scope.launch {
            val provider = getSuggestionProvider(subtypeManager.activeSubtype)
            (provider as? TypingLearner)?.recordCommitted(word, previous)
        }
    }

    fun clearLearnedWords() {
        scope.launch {
            val provider = getSuggestionProvider(subtypeManager.activeSubtype)
            (provider as? TypingLearner)?.clearLearned()
        }
    }

    fun suggest(subtype: Subtype, content: EditorContent) {
        val reqTime = SystemClock.uptimeMillis()
        scope.launch {
            val emojiSuggestions = when {
                prefs.emoji.suggestionEnabled.get() -> {
                    emojiSuggestionProvider.suggest(
                        subtype = subtype,
                        content = content,
                        maxCandidateCount = prefs.emoji.suggestionCandidateMaxCount.get(),
                        allowPossiblyOffensive = !prefs.suggestion.blockPossiblyOffensive.get(),
                        isPrivateSession = keyboardManager.activeState.isIncognitoMode,
                    )
                }
                else -> emptyList()
            }
            val suggestions = when {
                emojiSuggestions.isNotEmpty() && prefs.emoji.suggestionType.get().prefix.isNotEmpty() -> {
                    emptyList()
                }
                else -> {
                    getSuggestionProvider(subtype).suggest(
                        subtype = subtype,
                        content = content,
                        maxCandidateCount = 8,
                        allowPossiblyOffensive = !prefs.suggestion.blockPossiblyOffensive.get(),
                        isPrivateSession = keyboardManager.activeState.isIncognitoMode,
                    )
                }
            }
            val aiSuggestion = localSuggestion(content)
            internalSuggestionsGuard.withLock {
                if (internalSuggestions.first < reqTime) {
                    internalSuggestions = reqTime to buildList {
                        addAll(emojiSuggestions)
                        addAll(suggestions)
                        if (aiSuggestion != null) add(aiSuggestion)
                    }
                }
            }
        }
    }

    fun suggestDirectly(suggestions: List<SuggestionCandidate>) {
        val reqTime = SystemClock.uptimeMillis()
        runBlocking {
            internalSuggestions = reqTime to suggestions
        }
    }

    fun refreshSuggestions() {
        suggest(subtypeManager.activeSubtype, editorInstance.activeContent)
    }

    fun clearSuggestions() {
        val reqTime = SystemClock.uptimeMillis()
        runBlocking {
            internalSuggestions = reqTime to emptyList()
        }
    }

    fun getAutoCommitCandidate(): SuggestionCandidate? {
        return activeCandidates.firstOrNull { it.isEligibleForAutoCommit }
    }

    /** Saves a phrase the user long-pressed. Password fields are refused before the text is stored. */
    fun addCustomSuggestion(text: String, trigger: String = ""): Boolean {
        if (editorInstance.activeInfo.inputAttributes.isPasswordLike()) return false
        return customSuggestions.add(text, trigger)
    }

    fun removeCustomSuggestion(id: String): Boolean = customSuggestions.delete(id)

    fun removeSuggestion(subtype: Subtype, candidate: SuggestionCandidate): Boolean {
        return runBlocking { candidate.sourceProvider?.removeSuggestion(subtype, candidate) == true }.also { result ->
            if (result) {
                scope.launch {
                    // Need to re-trigger the suggestions algorithm
                    if (candidate is ClipboardSuggestionCandidate) {
                        assembleCandidates()
                    } else {
                        suggest(subtypeManager.activeSubtype, editorInstance.activeContent)
                    }
                }
            }
        }
    }

    fun getListOfWords(subtype: Subtype): List<String> {
        return runBlocking { getSuggestionProvider(subtype).getListOfWords(subtype) }
    }

    fun getFrequencyForWord(subtype: Subtype, word: String): Double {
        return runBlocking { getSuggestionProvider(subtype).getFrequencyForWord(subtype, word) }
    }

    private fun assembleCandidates() {
        runBlocking {
            val content = editorInstance.activeContent
            val passwordLike = editorInstance.activeInfo.inputAttributes.isPasswordLike()
            val suggestionsOn = isSuggestionOn()
            val clipboard = if (suggestionsOn && !passwordLike) {
                clipboardSuggestionProvider.suggest(
                    subtype = Subtype.DEFAULT,
                    content = content,
                    maxCandidateCount = 8,
                    allowPossiblyOffensive = !prefs.suggestion.blockPossiblyOffensive.get(),
                    isPrivateSession = keyboardManager.activeState.isIncognitoMode,
                )
            } else {
                emptyList()
            }
            val dictionary = if (suggestionsOn) {
                internalSuggestionsGuard.withLock { internalSuggestions.second }
            } else {
                emptyList()
            }
            val savedCommands = if (passwordLike) emptyList() else slashCommands.snapshot()
            val reserved = buildSet {
                addAll(SlashCatalog.builtins.map { it.name })
                addAll(savedCommands.map { it.name.lowercase() })
            }
            val fleetCommands = if (passwordLike) emptyList() else fleet.snapshot.value.commands(reserved)
            val plan = StripPlanner.plan(
                passwordLike = passwordLike,
                slashEnabled = prefs.slash.enabled.get(),
                textBeforeCursor = content.textBeforeSelection,
                selection = content.selectedText,
                custom = if (passwordLike) emptyList() else customSuggestions.snapshot(),
                commands = savedCommands,
                clipboardAvailable = clipboard.isNotEmpty(),
                fleetCommands = fleetCommands,
            )
            activeCandidates = when (plan) {
                is StripPlan.Slash -> plan.offers.map { offer ->
                    SlashSuggestionCandidate(
                        commandName = offer.name,
                        builtinName = offer.builtin?.name,
                        customId = offer.customId,
                        argument = plan.argument,
                        replaceLength = plan.replaceLength,
                        fleet = offer.fleet,
                        fleetPackage = offer.fleetPackage,
                        fleetRawName = offer.fleetRawName,
                    )
                }
                is StripPlan.Words -> buildList {
                    plan.selection?.let { add(AddToSuggestionsCandidate(it)) }
                    for (item in plan.custom) {
                        add(
                            CustomSuggestionCandidate(
                                text = item.text,
                                suggestionId = item.id,
                            ),
                        )
                    }
                    if (plan.showClipboard) addAll(clipboard)
                    if (plan.showDictionary) addAll(dictionary)
                }
            }
            autoExpandCollapseSmartbarActions(activeCandidates, NlpInlineAutofill.suggestions.value)
        }
    }

    fun autoExpandCollapseSmartbarActions(list1: List<*>?, list2: List<*>?) {
        // The strip stays on suggestions. The tools panel is a separate button, so an empty
        // candidate list must not expand the old page-tab toolbar over the strip.
        if (!prefs.smartbar.enabled.get()) return
        if (!prefs.smartbar.sharedActionsExpanded.get()) return
        scope.launch {
            prefs.smartbar.sharedActionsExpanded.set(false)
        }
    }

    fun addToDebugOverlay(word: String, info: SpellingResult) {
        debugOverlaySuggestionsInfos.put(System.currentTimeMillis(), word to info)
        debugOverlayVersion.update { it + 1 }
    }

    fun clearDebugOverlay() {
        debugOverlaySuggestionsInfos.evictAll()
        debugOverlayVersion.update { it + 1 }
    }

    private class ProviderInstanceWrapper(val provider: NlpProvider) {
        private var isInstanceAlive = AtomicBoolean(false)

        suspend fun createIfNecessary() {
            if (!isInstanceAlive.getAndSet(true)) provider.create()
        }

        suspend fun preload(subtype: Subtype) {
            provider.preload(subtype)
        }

        suspend fun destroyIfNecessary() {
            if (isInstanceAlive.getAndSet(true)) provider.destroy()
        }
    }

    inner class ClipboardSuggestionProvider internal constructor(private val context: Context) : SuggestionProvider {
        private var lastClipboardItemId: Long = -1

        override val providerId = "org.florisboard.nlp.providers.clipboard"

        override suspend fun create() {
            // Do nothing
        }

        override suspend fun preload(subtype: Subtype) {
            // Do nothing
        }

        override suspend fun suggest(
            subtype: Subtype,
            content: EditorContent,
            maxCandidateCount: Int,
            allowPossiblyOffensive: Boolean,
            isPrivateSession: Boolean,
        ): List<SuggestionCandidate> {
            // Check if enabled
            if (!prefs.clipboard.suggestionEnabled.get()) return emptyList()

            val currentItem = validateClipboardItem(clipboardManager.primaryClip, lastClipboardItemId, content.text)
                ?: return emptyList()
            if (!ClipVisibility.showAsSuggestion(ClipVisibility.laneOfSensitiveClip(currentItem.isSensitive))) {
                return emptyList()
            }

            return buildList {
                val now = System.currentTimeMillis()
                if ((now - currentItem.creationTimestampMs) < prefs.clipboard.suggestionTimeout.get() * 1000) {
                    add(ClipboardSuggestionCandidate(currentItem, sourceProvider = this@ClipboardSuggestionProvider, context = context))
                    if (currentItem.type == ItemType.TEXT) {
                        val text = currentItem.stringRepresentation()
                        val matches = buildList {
                            addAll(NetworkUtils.getEmailAddresses(text))
                            addAll(NetworkUtils.getUrls(text))
                            addAll(NetworkUtils.getPhoneNumbers(text))
                        }
                        matches.forEachIndexed { i, match ->
                            val isUniqueMatch = matches.subList(0, i).all { prevMatch ->
                                prevMatch.value != match.value && prevMatch.range.intersect(match.range).isEmpty()
                            }
                            if (match.value != text && isUniqueMatch) {
                                add(ClipboardSuggestionCandidate(
                                    clipboardItem = currentItem.copy(
                                        // TODO: adjust regex of phone number so we don't need to manually strip the
                                        //  parentheses from the match results
                                        text = if (match.value.startsWith("(") && match.value.endsWith(")")) {
                                            match.value.substring(1, match.value.length - 1)
                                        } else {
                                            match.value
                                        }
                                    ),
                                    sourceProvider = this@ClipboardSuggestionProvider,
                                    context = context,
                                ))
                            }
                        }
                    }
                }
            }
        }

        override suspend fun notifySuggestionAccepted(subtype: Subtype, candidate: SuggestionCandidate) {
            if (candidate is ClipboardSuggestionCandidate) {
                lastClipboardItemId = candidate.clipboardItem.id
            }
        }

        override suspend fun notifySuggestionReverted(subtype: Subtype, candidate: SuggestionCandidate) {
            // Do nothing
        }

        override suspend fun removeSuggestion(subtype: Subtype, candidate: SuggestionCandidate): Boolean {
            if (candidate is ClipboardSuggestionCandidate) {
                lastClipboardItemId = candidate.clipboardItem.id
                return true
            }
            return false
        }

        override suspend fun getListOfWords(subtype: Subtype): List<String> {
            return emptyList()
        }

        override suspend fun getFrequencyForWord(subtype: Subtype, word: String): Double {
            return 0.0
        }

        override suspend fun destroy() {
            // Do nothing
        }

        private fun validateClipboardItem(currentItem: ClipboardItem?, lastItemId: Long, contentText: String) =
            currentItem?.takeIf {
                // Check if already used
                it.id != lastItemId
                    // Check if content is empty
                    && contentText.isBlank()
                    // Check if clipboard content has any valid characters
                    && !currentItem.text.isNullOrBlank()
                    && !blankStrRegex.matches(currentItem.text)
            }
    }
}
