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

/** How willing space is to replace the word just typed. A known word is never replaced at any level. */
enum class AutocorrectLevel {
    OFF,
    MILD,
    NORMAL,
}

/**
 * QWERTY neighbors. Used so a far key (h versus r) does not outrank a key that actually sits next
 * to the one the user pressed, and so Mild only replaces that kind of slip.
 */
object KeyProximity {
    private val neighbors: Map<Char, String> = mapOf(
        'q' to "wa",
        'w' to "qeas",
        'e' to "wrds",
        'r' to "etfd",
        't' to "rygf",
        'y' to "tuhg",
        'u' to "yijh",
        'i' to "uokj",
        'o' to "iplk",
        'p' to "ol",
        'a' to "qwsz",
        's' to "weadzx",
        'd' to "ersfxc",
        'f' to "rtdgcv",
        'g' to "tyfhvb",
        'h' to "yugjbn",
        'j' to "uihknm",
        'k' to "iojlm",
        'l' to "opk",
        'z' to "asx",
        'x' to "zsdc",
        'c' to "xdfv",
        'v' to "cfgb",
        'b' to "vghn",
        'n' to "bhjm",
        'm' to "njk",
    )

    fun adjacent(a: Char, b: Char): Boolean {
        if (a == b) return true
        val left = a.lowercaseChar()
        val right = b.lowercaseChar()
        return neighbors[left]?.contains(right) == true
    }

    /** Same length, exactly one changed letter, and that letter is a neighbor key. */
    fun isAdjacentSubstitution(typed: String, other: String): Boolean {
        if (typed.length != other.length || typed.isEmpty()) return false
        var diffs = 0
        for (i in typed.indices) {
            if (typed[i] == other[i]) continue
            if (!adjacent(typed[i], other[i])) return false
            diffs++
            if (diffs > 1) return false
        }
        return diffs == 1
    }

    /**
     * Lower is a closer slip. An adjacent substitution ranks ahead of a far substitution and ahead
     * of an insert or delete when the spelling distance is the same.
     */
    fun substitutionRank(typed: String, other: String): Int {
        if (typed.length == other.length) {
            var cost = 0
            var diffs = 0
            for (i in typed.indices) {
                if (typed[i] == other[i]) continue
                diffs++
                cost += if (adjacent(typed[i], other[i])) 1 else 4
            }
            if (diffs > 0) return cost
        }
        return 2 + kotlin.math.abs(typed.length - other.length)
    }
}

object Autocorrect {
    /**
     * [editDistance] is the spelling distance already computed. Known words never reach this:
     * the predictor returns before offering a replacement.
     */
    fun allowsAutoCommit(
        typed: String,
        correction: String,
        editDistance: Int,
        level: AutocorrectLevel,
    ): Boolean {
        if (level == AutocorrectLevel.OFF) return false
        if (editDistance != 1 || typed.length < 4) return false
        if (level == AutocorrectLevel.MILD) {
            return KeyProximity.isAdjacentSubstitution(typed.lowercase(), correction.lowercase())
        }
        return true
    }
}

/** The word the user typed, and the word space wrote in its place. */
data class AutocorrectReplacement(
    val typed: String,
    val committed: String,
    val withSpace: Boolean,
)

data class AutocorrectUndoStep(val deleteCount: Int, val restore: String)

object AutocorrectUndo {
    /**
     * One backspace undoes the replacement, including the space that triggered it, and puts the
     * typed word back. A mismatch means the field changed; the caller deletes one character instead.
     */
    fun plan(textBeforeCursor: String, replacement: AutocorrectReplacement): AutocorrectUndoStep? {
        if (replacement.typed.isEmpty() || replacement.committed.isEmpty()) return null
        if (replacement.typed == replacement.committed) return null
        val suffix = buildString {
            append(replacement.committed)
            if (replacement.withSpace) append(' ')
        }
        if (suffix.isEmpty() || !textBeforeCursor.endsWith(suffix)) return null
        return AutocorrectUndoStep(deleteCount = suffix.length, restore = replacement.typed)
    }
}

/** How many times the user has undone an autocorrect of each word. Not logged. */
data class RevertCounts(val counts: Map<String, Int> = emptyMap()) {
    fun note(typed: String): Pair<RevertCounts, Boolean> {
        val key = LearnedWords.normalize(typed) ?: return this to false
        val nextCount = ((counts[key] ?: 0) + 1).coerceAtMost(100)
        val updated = LinkedHashMap(counts)
        updated.remove(key)
        updated[key] = nextCount
        while (updated.size > RevertCountCodec.MAX_WORDS) {
            val victim = updated.entries.firstOrNull { it.value < 2 }?.key ?: updated.keys.firstOrNull() ?: break
            updated.remove(victim)
        }
        return RevertCounts(updated) to (nextCount >= 2)
    }

    /** Words undone at least twice. Treat them as dictionary words so they are not replaced again. */
    fun protectedWords(): Set<String> = counts.filterValues { it >= 2 }.keys
}

object RevertPrivacy {
    /** Undoing a correction in a sensitive field restores the letters and stores nothing. */
    fun mayRemember(field: LearningGate.Field): Boolean =
        !field.passwordLike &&
            !field.incognito &&
            !field.noPersonalizedLearning &&
            !field.noSuggestions &&
            !field.vaultHandoff
}

object RevertCountCodec {
    const val FORMAT = "victorious-revert-counts"
    const val VERSION = 1
    const val MAX_WORDS = 500
    const val MAX_FILE_BYTES = 256 * 1024

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(counts: RevertCounts): ByteArray {
        val model = RevertCountModel(
            format = FORMAT,
            version = VERSION,
            counts = counts.counts,
        )
        return json.encodeToString(RevertCountModel.serializer(), model).encodeToByteArray()
    }

    fun decode(bytes: ByteArray): RevertCounts {
        if (bytes.size > MAX_FILE_BYTES) return RevertCounts()
        val model = try {
            json.decodeFromString(RevertCountModel.serializer(), bytes.decodeToString())
        } catch (_: SerializationException) {
            return RevertCounts()
        } catch (_: IllegalArgumentException) {
            return RevertCounts()
        }
        if (model.format != FORMAT || model.version != VERSION) return RevertCounts()
        val kept = LinkedHashMap<String, Int>()
        for ((word, count) in model.counts) {
            val key = LearnedWords.normalize(word) ?: continue
            if (count !in 1..100) continue
            kept[key] = count
            if (kept.size >= MAX_WORDS) break
        }
        return RevertCounts(kept)
    }
}

@Serializable
private data class RevertCountModel(
    val format: String,
    val version: Int,
    val counts: Map<String, Int> = emptyMap(),
)

class RevertCountFile(private val dir: File) {
    private val target = File(dir, "revert-counts.json")

    fun load(): RevertCounts {
        if (!target.exists()) return RevertCounts()
        if (target.length() > RevertCountCodec.MAX_FILE_BYTES) return RevertCounts()
        val bytes = try {
            target.readBytes()
        } catch (_: IOException) {
            return RevertCounts()
        }
        return RevertCountCodec.decode(bytes)
    }

    fun save(counts: RevertCounts) {
        dir.mkdirs()
        val tmp = File(dir, "revert-counts.json.tmp")
        FileOutputStream(tmp).use { out ->
            out.write(RevertCountCodec.encode(counts))
            out.flush()
            out.fd.sync()
        }
        try {
            Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }
}

/**
 * In-memory copy of [RevertCountFile], shared by the suggestion provider and the backspace path.
 * The file lives under `noBackupFilesDir` and is never logged.
 */
object RevertCountStore {
    private val lock = Any()
    private var file: RevertCountFile? = null
    private var counts = RevertCounts()

    fun ensure(dir: File) {
        synchronized(lock) {
            if (file != null) return
            val opened = RevertCountFile(dir)
            file = opened
            counts = opened.load()
        }
    }

    fun protectedWords(): Set<String> = synchronized(lock) { counts.protectedWords() }

    /** Returns true when [typed] has been undone at least twice and should join the user dictionary. */
    fun note(dir: File, typed: String): Boolean {
        ensure(dir)
        synchronized(lock) {
            val (next, add) = counts.note(typed)
            counts = next
            try {
                file?.save(next)
            } catch (_: IOException) {
                // The in-memory count still protects the word for this session.
            }
            return add
        }
    }
}
