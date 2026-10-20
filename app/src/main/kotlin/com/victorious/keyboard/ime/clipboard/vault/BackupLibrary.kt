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

package com.victorious.keyboard.ime.clipboard.vault

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * What the app remembers about a backup file the user picked or created through the system file picker.
 *
 * Deliberately minimal: where the file is (a URI the user granted), what it was called, and facts that were true
 * when it was last inspected. **No clipboard content, no passwords, no keys.** Whether the URI is still readable is
 * checked live by the Android layer — the app never assumes a past grant is still valid.
 */
@Serializable
data class BackupLibraryEntry(
    val uri: String,
    val displayName: String,
    val addedAtMs: Long,
    val sizeBytes: Long? = null,
    /** "encrypted" | "zip" | "json" — as detected the last time the file was read. */
    val kind: String? = null,
    val itemCount: Int? = null,
    val formatVersion: Int? = null,
    val createdAtMs: Long? = null,
)

@Serializable
private data class LibraryFile(val version: Int = 1, val entries: List<BackupLibraryEntry> = emptyList())

/** Immutable list of [BackupLibraryEntry] with bounded size and safe (de)serialisation. */
class BackupLibrary private constructor(val entries: List<BackupLibraryEntry>) {

    fun add(entry: BackupLibraryEntry): BackupLibrary {
        val without = entries.filter { it.uri != entry.uri }
        // Newest first; the oldest are dropped from the *list* (never from the user's storage) beyond the cap.
        return BackupLibrary((listOf(entry) + without).take(MAX_ENTRIES))
    }

    fun update(uri: String, transform: (BackupLibraryEntry) -> BackupLibraryEntry): BackupLibrary =
        BackupLibrary(entries.map { if (it.uri == uri) transform(it) else it })

    fun remove(uri: String): BackupLibrary = BackupLibrary(entries.filter { it.uri != uri })

    fun find(uri: String): BackupLibraryEntry? = entries.firstOrNull { it.uri == uri }

    fun toJson(): String = json.encodeToString(LibraryFile.serializer(), LibraryFile(entries = entries))

    companion object {
        const val MAX_ENTRIES = 50
        private const val MAX_FILE_CHARS = 1_000_000

        private val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

        val EMPTY = BackupLibrary(emptyList())

        /** Never throws: a damaged or hostile library file yields an empty library. */
        fun fromJson(text: String?): BackupLibrary {
            if (text.isNullOrBlank() || text.length > MAX_FILE_CHARS) return EMPTY
            return try {
                val file = json.decodeFromString(LibraryFile.serializer(), text)
                BackupLibrary(
                    file.entries
                        .filter { it.uri.isNotBlank() && it.uri.length <= 2048 }
                        .map { it.copy(displayName = it.displayName.take(255)) }
                        .distinctBy { it.uri }
                        .take(MAX_ENTRIES)
                )
            } catch (_: SerializationException) {
                EMPTY
            } catch (_: IllegalArgumentException) {
                EMPTY
            }
        }
    }
}
