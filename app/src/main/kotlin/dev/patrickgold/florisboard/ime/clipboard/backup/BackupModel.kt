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

package dev.patrickgold.florisboard.ime.clipboard.backup

import dev.patrickgold.florisboard.ime.clipboard.history.ClipEntry
import kotlinx.serialization.Serializable

/** See BACKUP_FORMAT.md. Bump only with a documented migration in [BackupMigrations]. */
const val BACKUP_FORMAT_NAME = "victorious-keyboard-clipboard"
const val BACKUP_FORMAT_VERSION = 1

@Serializable
data class AttachmentRef(
    val path: String,
    val sha256: String,
    val mime: String,
    val entryId: String,
)

@Serializable
data class BackupManifest(
    val format: String = BACKUP_FORMAT_NAME,
    val formatVersion: Int = BACKUP_FORMAT_VERSION,
    val createdAtMs: Long,
    val appVersion: String,
    val itemCount: Int,
    val supportedEntryTypes: List<String> = listOf(ENTRY_TYPE_TEXT),
    /** SHA-256 (hex) of `entries.json`; present in ZIP/encrypted backups, absent in the single-file JSON variant. */
    val entriesSha256: String? = null,
    val skippedUnsupported: Int = 0,
    val skippedSensitive: Int = 0,
    /** Reserved: image/URI attachments are not exported in format v1, so this is always empty. */
    val attachments: List<AttachmentRef> = emptyList(),
)

@Serializable
data class BackupEntry(
    val id: String,
    val type: String = ENTRY_TYPE_TEXT,
    val text: String,
    val createdAtMs: Long,
    val editedAtMs: Long? = null,
    val pinned: Boolean = false,
    val favorite: Boolean = false,
    val tags: List<String> = emptyList(),
    val category: String = "GENERAL",
    /** SHA-256 (hex) of the UTF-8 [text]; verified on import. */
    val contentHash: String,
)

@Serializable
data class BackupEntries(val entries: List<BackupEntry>)

/** Single-file JSON variant. */
@Serializable
data class BackupDocument(val manifest: BackupManifest, val entries: List<BackupEntry>)

const val ENTRY_TYPE_TEXT = "text"

/** What the user is told before anything is applied. */
data class ParsedBackup(
    val manifest: BackupManifest,
    val entries: List<ClipEntry>,
    /** Entries of types this version cannot restore (counted, not imported). */
    val unsupportedEntries: Int,
)

/** Descriptive container kind, detected from the first bytes. */
enum class BackupKind { ENCRYPTED, ZIP, JSON, UNKNOWN }

/** Hard bounds applied to every file we parse. Parsing hostile input must not exhaust memory or CPU. */
data class BackupLimits(
    val maxFileBytes: Int = 96 * 1024 * 1024,
    val maxUncompressedBytes: Int = 96 * 1024 * 1024,
    val maxZipEntries: Int = 16,
    val maxEntries: Int = 5_000,
    val maxTextChars: Int = 1_000_000,
    val maxTags: Int = 64,
    val minKdfIterations: Int = 100_000,
    val maxKdfIterations: Int = 5_000_000,
)

/** Every way reading a backup can fail, as a closed set the UI can present. Messages never contain clip text. */
sealed class BackupException(message: String) : Exception(message) {
    class NotABackup(msg: String = "This file is not a Victorious Keyboard backup.") : BackupException(msg)
    class UnsupportedVersion(val found: Int) :
        BackupException("This backup was created by a newer version (format $found). Update the app to read it.")
    class Malformed(val reason: String) : BackupException("The backup is malformed: $reason")
    class Corrupt(val reason: String) : BackupException("The backup failed its integrity check: $reason")
    class TooLarge(val reason: String) : BackupException("The backup exceeds safety limits: $reason")
    class PasswordRequired : BackupException("This backup is encrypted. Enter its password.")
    class WrongPasswordOrTampered :
        BackupException("Wrong password, or the file was modified after it was created.")
    class WeakPassword(val minLength: Int) : BackupException("Use a password of at least $minLength characters.")
}
