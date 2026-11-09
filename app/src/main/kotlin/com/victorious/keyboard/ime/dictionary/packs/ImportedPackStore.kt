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

import com.victorious.keyboard.ime.packs.PackHash
import java.io.File
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Why a pack file was not stored. */
enum class ImportRefusal { RESERVED_BUNDLED_ID, LIMIT_REACHED }

sealed interface ImportResult {
    /** [replaced] is true when a pack with the same id was already stored. [sha256] is of the file as picked. */
    data class Saved(val pack: DictionaryPack, val replaced: Boolean, val sha256: String) : ImportResult
    data class Invalid(val error: PackError) : ImportResult
    data class Refused(val reason: ImportRefusal) : ImportResult
    data object Failed : ImportResult
}

/**
 * Dictionary packs the user imported from a file, kept as the original zip so they stay in the list after the screen is
 * closed and can be removed later. Lives in one directory ([dir], `noBackupFilesDir/dictionary-packs` in the app, so
 * Android auto-backup never carries it; CLAUDE.md invariant 7).
 *
 * Imports are untrusted input (invariant 5):
 * * every file goes through [DictionaryPackReader] (size, entry, name and content limits) before anything is written;
 * * the file name is built only from an id that matches the reader's id pattern, so no path can be chosen by the zip;
 * * a pack may not take the id of a bundled pack (it would silently stand in for it in the list);
 * * at most [MAX_IMPORTED] packs are kept;
 * * a write goes to a temporary file in the same directory and is moved into place, so a failed write never leaves a
 *   half-written pack under a real name;
 * * nothing is extracted, executed or logged.
 *
 * Plain `java.io`/`java.nio`; no Android types, so it is tested on the JVM.
 */
class ImportedPackStore(private val dir: File) {
    companion object {
        const val MAX_IMPORTED = 20
        private const val SUFFIX = ".zip"
        private const val TEMP_SUFFIX = ".tmp"
        private val ID = Regex("[a-z0-9][a-z0-9-]{0,39}")

        /** The stored file name for [id], or null when [id] could not be a pack id (so it can never be a path). */
        fun fileNameFor(id: String): String? = if (ID.matches(id)) id + SUFFIX else null

        private fun idOf(name: String): String? =
            if (name.endsWith(SUFFIX)) name.removeSuffix(SUFFIX).takeIf { ID.matches(it) } else null
    }

    /** Ids of the stored files, from their names only. Does not open them. */
    private fun storedIds(): List<String> =
        dir.listFiles { f -> f.isFile && !Files.isSymbolicLink(f.toPath()) }.orEmpty()
            .mapNotNull { idOf(it.name) }.sorted()

    /** The stored packs, sorted by id. A file that no longer passes the reader is skipped, never fatal and never deleted. */
    fun list(): List<DictionaryPack> = storedIds().mapNotNull { id ->
        try {
            val file = File(dir, id + SUFFIX)
            if (file.length() > PackLimits.MAX_ZIP_BYTES) return@mapNotNull null
            val pack = (DictionaryPackReader.read(file.readBytes()) as? PackReadResult.Ok)?.pack
            pack?.takeIf { it.manifest.id == id }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Reads [bytes] as a pack and stores it. [bundledIds] are the ids of the packs that ship in the app. Nothing is
     * written unless the zip is valid and the id is allowed.
     */
    fun save(bytes: ByteArray, bundledIds: Set<String>): ImportResult {
        val pack = when (val r = DictionaryPackReader.read(bytes)) {
            is PackReadResult.Failed -> return ImportResult.Invalid(r.error)
            is PackReadResult.Ok -> r.pack
        }
        val id = pack.manifest.id
        val name = fileNameFor(id) ?: return ImportResult.Invalid(PackError.BAD_MANIFEST)
        if (id in bundledIds) return ImportResult.Refused(ImportRefusal.RESERVED_BUNDLED_ID)
        val existing = storedIds()
        val replaced = id in existing
        if (!replaced && existing.size >= MAX_IMPORTED) return ImportResult.Refused(ImportRefusal.LIMIT_REACHED)
        return try {
            if (!dir.isDirectory && !dir.mkdirs() && !dir.isDirectory) return ImportResult.Failed
            val target = File(dir, name)
            val temp = File(dir, name + TEMP_SUFFIX)
            try {
                temp.writeBytes(bytes)
                try {
                    Files.move(temp.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
                } catch (_: AtomicMoveNotSupportedException) {
                    Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
                }
            } finally {
                temp.delete() // gone after a successful move; removes a partial file after a failure
            }
            ImportResult.Saved(pack, replaced, PackHash.sha256Hex(bytes))
        } catch (_: IOException) {
            ImportResult.Failed
        } catch (_: SecurityException) {
            ImportResult.Failed
        }
    }

    /** Deletes the stored file of pack [id]. False when there is no such stored pack. Words already added stay. */
    fun delete(id: String): Boolean {
        val name = fileNameFor(id) ?: return false
        val file = File(dir, name)
        return try {
            file.isFile && file.delete()
        } catch (_: SecurityException) {
            false
        }
    }
}
