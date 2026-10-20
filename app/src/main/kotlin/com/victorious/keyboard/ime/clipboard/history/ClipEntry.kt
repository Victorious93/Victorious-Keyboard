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

package com.victorious.keyboard.ime.clipboard.history

import java.security.MessageDigest

/**
 * Local, rule-based category of a clipboard entry. Computed on-device by [ClipClassifier]; never sent anywhere.
 * The user can always override it.
 */
enum class ClipCategory {
    GENERAL,
    URL,
    CODE,
    SHELL,
    AI_PROMPT,
    JSON,
    MARKDOWN,

    /** Reserved for the permanent snippet collection (kept apart from the rolling history). */
    SNIPPET;

    companion object {
        /** Forward-compatible parse: unknown names (e.g. from a newer backup) become [GENERAL]. */
        fun parse(name: String?): ClipCategory = entries.firstOrNull { it.name == name } ?: GENERAL
    }
}

/**
 * A text clipboard entry in the platform-independent domain model.
 *
 * This type has no Android dependencies so that retention, search, backup and import logic can be unit-tested on
 * the JVM. Images and other URI-backed clips are intentionally not part of this model (see CLIPBOARD.md).
 *
 * @property id stable identifier (UUID string); survives export/import and is used as the AEAD context at rest.
 * @property pinned exempt from automatic eviction; counts toward the history cap.
 */
data class ClipEntry(
    val id: String,
    val text: String,
    val createdAtMs: Long,
    val pinned: Boolean = false,
    val favorite: Boolean = false,
    val tags: Set<String> = emptySet(),
    val category: ClipCategory = ClipCategory.GENERAL,
    val sensitive: Boolean = false,
    val editedAtMs: Long? = null,
) {
    val charCount: Int get() = text.length

    val lastModifiedMs: Long get() = editedAtMs ?: createdAtMs

    /** SHA-256 (hex) of the UTF-8 text. Used for duplicate detection and backup integrity checks. */
    val contentHash: String by lazy(LazyThreadSafetyMode.NONE) { ClipHash.sha256Hex(text) }

    /** Intentionally does not print [text]: entries must never end up in logs. */
    override fun toString(): String = "ClipEntry(id=$id, chars=$charCount, pinned=$pinned)"
}

object ClipHash {
    fun sha256Hex(text: String): String = sha256Hex(text.toByteArray(Charsets.UTF_8))

    fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        val sb = StringBuilder(digest.size * 2)
        for (b in digest) {
            sb.append(HEX[(b.toInt() shr 4) and 0xF]).append(HEX[b.toInt() and 0xF])
        }
        return sb.toString()
    }

    private val HEX = "0123456789abcdef".toCharArray()
}

/** Normalisation rules for user-entered tags so that search and export are predictable. */
object ClipTags {
    const val MAX_TAGS_PER_ENTRY = 16
    const val MAX_TAG_LENGTH = 32

    private val allowed = Regex("[^\\p{L}\\p{N}_.-]")

    /** Returns the normalised tag or null if nothing usable remains. */
    fun normalize(raw: String): String? {
        val cleaned = raw.trim().lowercase()
            .replace(Regex("\\s+"), "-")
            .replace(allowed, "")
            .trim('-', '.', '_')
            .take(MAX_TAG_LENGTH)
        return cleaned.ifEmpty { null }
    }

    fun normalizeAll(raw: Collection<String>): Set<String> {
        val out = LinkedHashSet<String>()
        for (r in raw) {
            val n = normalize(r) ?: continue
            out.add(n)
            if (out.size >= MAX_TAGS_PER_ENTRY) break
        }
        return out
    }
}
