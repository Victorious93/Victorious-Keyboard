/*
 * Copyright (C) 2026 Victorious Keyboard Contributors
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

package com.victorious.keyboard.ime.nlp.prediction

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * Words and following-word counts learned on this device. Empty unless the user turned learning on.
 * Nothing here is logged.
 */
data class LearnedLexicon(
    val words: Map<String, Int> = emptyMap(),
    val bigrams: Map<String, Map<String, Int>> = emptyMap(),
) {
    fun record(word: String, previous: String?): LearnedLexicon {
        val nextWord = LearnedWords.normalize(word) ?: return this
        val prev = previous?.let(LearnedWords::normalize)
        val updatedWords = LinkedHashMap(words)
        updatedWords[nextWord] = minOf(255, (updatedWords[nextWord] ?: 24) + 16)
        while (updatedWords.size > LearnedWords.MAX_WORDS) {
            val victim = updatedWords.minByOrNull { it.value }?.key ?: break
            updatedWords.remove(victim)
        }
        if (prev == null) return copy(words = updatedWords)
        val updatedBigrams = LinkedHashMap<String, Map<String, Int>>()
        bigrams.forEach { (k, v) -> updatedBigrams[k] = v }
        val row = LinkedHashMap(updatedBigrams[prev].orEmpty())
        row[nextWord] = minOf(255, (row[nextWord] ?: 0) + 16)
        updatedBigrams[prev] = row
        var pairs = updatedBigrams.values.sumOf { it.size }
        while (pairs > LearnedWords.MAX_BIGRAMS) {
            val victimKey = updatedBigrams.entries
                .flatMap { (left, rowMap) -> rowMap.entries.map { right -> Triple(left, right.key, right.value) } }
                .minByOrNull { it.third } ?: break
            val rowMap = LinkedHashMap(updatedBigrams[victimKey.first].orEmpty())
            rowMap.remove(victimKey.second)
            if (rowMap.isEmpty()) updatedBigrams.remove(victimKey.first) else updatedBigrams[victimKey.first] = rowMap
            pairs--
        }
        return copy(words = updatedWords, bigrams = updatedBigrams)
    }
}

object LearnedWords {
    const val FORMAT = "victorious-learned-words"
    const val VERSION = 1
    const val MAX_WORDS = 2_000
    const val MAX_BIGRAMS = 4_000
    const val MAX_FILE_BYTES = 1024 * 1024
    private val TOKEN = Regex("""[a-z0-9_./+\-]{2,64}""")

    fun normalize(raw: String): String? {
        val word = raw.trim().lowercase()
        return word.takeIf { TOKEN.matches(it) }
    }
}

object LearnedLexiconCodec {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Serializable
    private data class FileModel(
        val format: String = "",
        val version: Int = 0,
        val words: Map<String, Int> = emptyMap(),
        val bigrams: Map<String, Map<String, Int>> = emptyMap(),
    )

    fun encode(lexicon: LearnedLexicon): ByteArray {
        val model = FileModel(
            format = LearnedWords.FORMAT,
            version = LearnedWords.VERSION,
            words = lexicon.words,
            bigrams = lexicon.bigrams,
        )
        return json.encodeToString(model).encodeToByteArray()
    }

    /** Untrusted input. A wrong, oversized, or corrupt file becomes an empty lexicon and is not repaired in place. */
    fun decode(bytes: ByteArray): LearnedLexicon {
        if (bytes.size > LearnedWords.MAX_FILE_BYTES) return LearnedLexicon()
        val model = try {
            json.decodeFromString<FileModel>(bytes.toString(Charsets.UTF_8))
        } catch (_: SerializationException) {
            return LearnedLexicon()
        } catch (_: IllegalArgumentException) {
            return LearnedLexicon()
        }
        if (model.format != LearnedWords.FORMAT || model.version != LearnedWords.VERSION) return LearnedLexicon()
        val words = LinkedHashMap<String, Int>()
        for ((word, freq) in model.words) {
            val key = LearnedWords.normalize(word) ?: continue
            if (freq !in 1..255) continue
            words[key] = freq
            if (words.size >= LearnedWords.MAX_WORDS) break
        }
        val bigrams = LinkedHashMap<String, Map<String, Int>>()
        var pairs = 0
        for ((left, row) in model.bigrams) {
            val prev = LearnedWords.normalize(left) ?: continue
            val clean = LinkedHashMap<String, Int>()
            for ((right, count) in row) {
                val next = LearnedWords.normalize(right) ?: continue
                if (count !in 1..255) continue
                clean[next] = count
                pairs++
                if (pairs >= LearnedWords.MAX_BIGRAMS) break
            }
            if (clean.isNotEmpty()) bigrams[prev] = clean
            if (pairs >= LearnedWords.MAX_BIGRAMS) break
        }
        return LearnedLexicon(words, bigrams)
    }
}

/**
 * The on-device file for [LearnedLexicon]. The caller must pass a directory under `noBackupFilesDir`
 * and must not call [load] or [save] when learning is off.
 */
class LearnedWordFile(private val dir: File) {
    private val target = File(dir, "learned-words.json")

    fun load(): LearnedLexicon {
        if (!target.exists()) return LearnedLexicon()
        if (target.length() > LearnedWords.MAX_FILE_BYTES) return LearnedLexicon()
        val bytes = try {
            target.readBytes()
        } catch (_: IOException) {
            return LearnedLexicon()
        }
        return LearnedLexiconCodec.decode(bytes)
    }

    fun save(lexicon: LearnedLexicon) {
        dir.mkdirs()
        val tmp = File(dir, "learned-words.json.tmp")
        FileOutputStream(tmp).use { out ->
            out.write(LearnedLexiconCodec.encode(lexicon))
            out.flush()
            out.fd.sync()
        }
        try {
            Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    fun clear() {
        target.delete()
        File(dir, "learned-words.json.tmp").delete()
    }
}
