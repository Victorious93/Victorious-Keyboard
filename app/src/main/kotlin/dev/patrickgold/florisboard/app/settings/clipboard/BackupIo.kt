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

package dev.patrickgold.florisboard.app.settings.clipboard

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import dev.patrickgold.florisboard.ime.clipboard.backup.BackupException
import dev.patrickgold.florisboard.ime.clipboard.backup.BackupLimits
import dev.patrickgold.florisboard.ime.clipboard.vault.BackupLibrary
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * File access for backups, exclusively through the Storage Access Framework: the user picks every file; the app
 * holds no broad storage permission. All reads are size-bounded.
 */
object BackupIo {
    data class FileMeta(val displayName: String, val sizeBytes: Long?)

    @Throws(IOException::class, BackupException::class)
    fun readBytes(resolver: ContentResolver, uri: Uri, maxBytes: Int = BackupLimits().maxFileBytes): ByteArray {
        val input = resolver.openInputStream(uri) ?: throw IOException("The file could not be opened.")
        input.use { stream ->
            val out = ByteArrayOutputStream()
            val buf = ByteArray(64 * 1024)
            var total = 0L
            while (true) {
                val n = stream.read(buf)
                if (n < 0) break
                total += n
                // Reject while reading: the provider's reported size is not trusted.
                if (total > maxBytes) throw BackupException.TooLarge("the file is larger than the allowed size")
                out.write(buf, 0, n)
            }
            return out.toByteArray()
        }
    }

    /**
     * Writes [bytes] to [uri] (truncating) and **reads it back** to verify. A backup that silently failed to write
     * completely is worse than no backup, so a mismatch throws.
     */
    @Throws(IOException::class)
    fun writeAndVerify(resolver: ContentResolver, uri: Uri, bytes: ByteArray) {
        val out = resolver.openOutputStream(uri, "wt") ?: throw IOException("The destination could not be opened.")
        out.use { it.write(bytes); it.flush() }
        val back = readBytes(resolver, uri, maxBytes = bytes.size + 1)
        if (!back.contentEquals(bytes)) throw IOException("The written file did not match; the backup is not valid.")
    }

    fun queryMeta(resolver: ContentResolver, uri: Uri): FileMeta {
        var name = uri.lastPathSegment ?: "backup"
        var size: Long? = null
        runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    c.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 }?.let { name = c.getString(it) ?: name }
                    c.getColumnIndex(OpenableColumns.SIZE).takeIf { it >= 0 && !c.isNull(it) }?.let { size = c.getLong(it) }
                }
            }
        }
        return FileMeta(name, size)
    }

    /** Asks Android to keep the grant across restarts. Not all providers allow it, so failure is not an error. */
    fun takePersistable(resolver: ContentResolver, uri: Uri, write: Boolean): Boolean = runCatching {
        var flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
        if (write) flags = flags or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        resolver.takePersistableUriPermission(uri, flags)
    }.isSuccess

    fun release(resolver: ContentResolver, uri: Uri) {
        runCatching {
            resolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }
    }

    /** True only if the file can be opened right now. A past grant is never assumed to still be valid. */
    fun isReadable(resolver: ContentResolver, uri: Uri): Boolean =
        runCatching { resolver.openInputStream(uri)?.use { true } ?: false }.getOrDefault(false)

    /** Deletes the document if its provider supports it. Returns false if unsupported or it failed. */
    fun delete(resolver: ContentResolver, uri: Uri): Boolean =
        runCatching { DocumentsContract.deleteDocument(resolver, uri) }.getOrDefault(false)
}

/** Persists the [BackupLibrary] (metadata only) in `noBackupFilesDir`, so Android auto-backup never carries it. */
class BackupLibraryStore(context: Context) {
    private val file = File(context.noBackupFilesDir, "clipboard_backup_library.json")

    @Synchronized
    fun load(): BackupLibrary = BackupLibrary.fromJson(runCatching { file.readText() }.getOrNull())

    @Synchronized
    fun save(library: BackupLibrary) {
        val tmp = File(file.path + ".tmp")
        tmp.writeText(library.toJson())
        Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    }
}
