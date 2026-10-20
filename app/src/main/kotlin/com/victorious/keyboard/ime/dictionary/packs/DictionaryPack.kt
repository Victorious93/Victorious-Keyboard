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

package com.victorious.keyboard.ime.dictionary.packs

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipException
import java.util.zip.ZipInputStream
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * A dictionary pack is a small **flat** zip with `pack.json` (manifest) and `words.txt` (the user dictionary's own
 * "combined list" text format, one `w=word;f=frequency;l=all` line per word) plus optional text files that are ignored.
 * Packs are untrusted input (CLAUDE.md invariant 5): sizes are bounded by counting the bytes actually decompressed (never
 * by the sizes the zip claims), names must be plain file names, nothing is ever extracted to disk, and nothing in a pack
 * is executed or interpreted beyond being a list of words. Pure Kotlin; no logging.
 */
object PackLimits {
    const val MAX_ZIP_BYTES = 1 * 1024 * 1024
    const val MAX_ZIP_ENTRIES = 8
    const val MAX_ENTRY_BYTES = 512 * 1024
    const val MAX_TOTAL_BYTES = 1024 * 1024
    const val MAX_WORDS = 5_000
    const val MAX_WORD_LENGTH = 64
    const val MAX_LINE_LENGTH = 256
    const val MAX_TITLE = 80
    const val MAX_DESCRIPTION = 400
    const val FORMAT = "victorious-dictionary-pack"
    const val VERSION = 1
    const val MANIFEST_NAME = "pack.json"
    const val WORDS_NAME = "words.txt"
}

@Serializable
data class PackManifest(
    val format: String = "",
    val version: Int = 0,
    val id: String = "",
    val title: String = "",
    val description: String = "",
)

data class PackEntry(val word: String, val freq: Int)

data class DictionaryPack(
    val manifest: PackManifest,
    val entries: List<PackEntry>,
    /** Lines of words.txt that were left out because they were invalid (never repaired). */
    val rejectedLines: Int,
)

enum class PackError {
    NOT_A_ZIP, TOO_LARGE, TOO_MANY_FILES, UNSAFE_NAME, DUPLICATE_FILE, MISSING_MANIFEST, MISSING_WORDS, BAD_MANIFEST,
    UNSUPPORTED_VERSION, TOO_MANY_WORDS, NO_VALID_WORDS,
}

sealed interface PackReadResult {
    data class Ok(val pack: DictionaryPack) : PackReadResult
    data class Failed(val error: PackError) : PackReadResult
}

object DictionaryPackReader {
    private val json = Json { ignoreUnknownKeys = true; isLenient = false }
    private val ID = Regex("[a-z0-9][a-z0-9-]{0,39}")
    private val SAFE_NAME = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,63}")

    fun read(bytes: ByteArray): PackReadResult =
        if (bytes.size > PackLimits.MAX_ZIP_BYTES) PackReadResult.Failed(PackError.TOO_LARGE) else read(bytes.inputStream())

    fun read(input: InputStream): PackReadResult {
        val files = HashMap<String, ByteArray>()
        var total = 0
        var count = 0
        try {
            ZipInputStream(input).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (++count > PackLimits.MAX_ZIP_ENTRIES) return PackReadResult.Failed(PackError.TOO_MANY_FILES)
                    val name = entry.name
                    if (entry.isDirectory || !SAFE_NAME.matches(name) || name.contains("..")) {
                        return PackReadResult.Failed(PackError.UNSAFE_NAME)
                    }
                    if (files.containsKey(name)) return PackReadResult.Failed(PackError.DUPLICATE_FILE)
                    val out = ByteArrayOutputStream()
                    val buf = ByteArray(8192)
                    while (true) {
                        val n = zip.read(buf)
                        if (n < 0) break
                        total += n
                        if (out.size() + n > PackLimits.MAX_ENTRY_BYTES || total > PackLimits.MAX_TOTAL_BYTES) {
                            return PackReadResult.Failed(PackError.TOO_LARGE)
                        }
                        out.write(buf, 0, n)
                    }
                    // Only the two named files are kept; anything else (README, licence) is read for the size limit and dropped.
                    if (name == PackLimits.MANIFEST_NAME || name == PackLimits.WORDS_NAME) files[name] = out.toByteArray()
                }
            }
        } catch (_: ZipException) {
            return PackReadResult.Failed(PackError.NOT_A_ZIP)
        } catch (_: IOException) {
            return PackReadResult.Failed(PackError.NOT_A_ZIP)
        }
        if (count == 0) return PackReadResult.Failed(PackError.NOT_A_ZIP)
        val manifestBytes = files[PackLimits.MANIFEST_NAME] ?: return PackReadResult.Failed(PackError.MISSING_MANIFEST)
        val wordsBytes = files[PackLimits.WORDS_NAME] ?: return PackReadResult.Failed(PackError.MISSING_WORDS)
        val manifest = try {
            json.decodeFromString<PackManifest>(manifestBytes.toString(Charsets.UTF_8))
        } catch (_: SerializationException) {
            return PackReadResult.Failed(PackError.BAD_MANIFEST)
        } catch (_: IllegalArgumentException) {
            return PackReadResult.Failed(PackError.BAD_MANIFEST)
        }
        if (manifest.format != PackLimits.FORMAT || !ID.matches(manifest.id) || manifest.title.isBlank() ||
            manifest.title.length > PackLimits.MAX_TITLE || manifest.title.any { it.isISOControl() } ||
            manifest.description.length > PackLimits.MAX_DESCRIPTION || manifest.description.any { it.isISOControl() && it != '\n' }
        ) return PackReadResult.Failed(PackError.BAD_MANIFEST)
        if (manifest.version != PackLimits.VERSION) return PackReadResult.Failed(PackError.UNSUPPORTED_VERSION)
        val parsed = PackWordList.parse(wordsBytes.toString(Charsets.UTF_8))
        if (parsed.entries.size > PackLimits.MAX_WORDS) return PackReadResult.Failed(PackError.TOO_MANY_WORDS)
        if (parsed.entries.isEmpty()) return PackReadResult.Failed(PackError.NO_VALID_WORDS)
        return PackReadResult.Ok(DictionaryPack(manifest, parsed.entries, parsed.rejected))
    }
}

/** The "combined list" text format of the user dictionary: `w=word;f=1..255;l=all` per line. */
object PackWordList {
    const val FREQ_MIN = 1
    const val FREQ_MAX = 255

    data class Parsed(val entries: List<PackEntry>, val rejected: Int)

    /** A word is one token without whitespace, controls, ';' ',' or '=' (the characters the list format itself uses). */
    fun isValidWord(w: String): Boolean =
        w.isNotEmpty() && w.length <= PackLimits.MAX_WORD_LENGTH &&
            w.none { it.isWhitespace() || it.isISOControl() || it == ';' || it == ',' || it == '=' }

    fun parse(text: String): Parsed {
        val seen = HashSet<String>()
        val out = ArrayList<PackEntry>()
        var rejected = 0
        for (raw in text.lineSequence()) {
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("dictionary=")) continue
            val entry = parseLine(line)
            // Duplicates (same word) keep the first line; they are not an error and not counted as rejected.
            if (entry == null) rejected++ else if (seen.add(entry.word)) out += entry
            if (out.size > PackLimits.MAX_WORDS) break // the caller rejects the pack as too large
        }
        return Parsed(out, rejected)
    }

    private fun parseLine(line: String): PackEntry? {
        if (line.length > PackLimits.MAX_LINE_LENGTH) return null
        var word: String? = null
        var freq: Int? = null
        for (property in line.split(';')) {
            val kv = property.split('=')
            if (kv.size != 2) return null
            val value = kv[1].trim()
            when (kv[0].trim().lowercase()) {
                "w", "word" -> word = value
                "f", "freq" -> freq = value.toIntOrNull(10)
                // Packs apply to every language. A pack line that names a language or a shortcut is not understood: reject it.
                "l", "locale" -> if (value != "all" && value != "null" && value.isNotEmpty()) return null
                else -> return null
            }
        }
        val w = word ?: return null
        val f = freq ?: return null
        if (!isValidWord(w) || f !in FREQ_MIN..FREQ_MAX) return null
        return PackEntry(w, f)
    }
}

/** What is already in the user dictionary, as far as the planners need to know. */
data class ExistingWord(val id: Long, val word: String, val freq: Int, val locale: String?, val shortcut: String?)

data class InstallPlan(val toInsert: List<PackEntry>, val alreadyPresent: Int)

object PackPlanner {
    /**
     * Words are added for all languages (locale `null`). A word the user already has for all languages is left exactly
     * as it is (their frequency and shortcut win); nothing is ever updated or removed by installing.
     */
    fun planInstall(existing: Collection<ExistingWord>, pack: DictionaryPack): InstallPlan {
        val have = existing.filter { it.locale == null }.mapTo(HashSet()) { it.word }
        val (present, missing) = pack.entries.partition { it.word in have }
        return InstallPlan(missing, present.size)
    }

    /**
     * Ids of the entries a pack removal would delete: only those that are **identical** to a pack word (same word and
     * frequency, all languages, no shortcut). An entry the user changed or created differently is never touched.
     */
    fun planRemove(existing: Collection<ExistingWord>, pack: DictionaryPack): List<Long> {
        val byWord = pack.entries.associate { it.word to it.freq }
        return existing.filter { it.locale == null && it.shortcut == null && byWord[it.word] == it.freq }.map { it.id }
    }
}
