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

package com.victorious.keyboard.ime.snippets

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

sealed interface LoadResult {
    /** A missing file is a normal first run: an empty list. */
    data class Loaded(val snippets: List<Snippet>, val invalid: List<InvalidEntry>) : LoadResult

    /** The file exists but cannot be used. It has **not** been modified or deleted. */
    data class Unreadable(val error: SnippetFileError?) : LoadResult
}

sealed interface SaveResult {
    data object Saved : SaveResult
    data class Failed(val cause: IOException) : SaveResult
}

/**
 * Stores snippets as one [SnippetBackup] JSON file in [dir]. The Android shell must pass a directory under
 * `noBackupFilesDir` (CLAUDE.md invariant 7): snippets are personal text and must not ride along in auto-backup.
 *
 * Safety: a save writes a temp file, syncs it and renames it over the target, so a crash leaves either the old or the
 * new file. Before replacing, the current file is kept as `snippets.json.bak` (if it was readable) or
 * `snippets.json.corrupt` (if it was not), so a bad save or a hand-edited file never silently destroys the last good copy.
 */
class SnippetFileStore(private val dir: File) {
    private val target get() = File(dir, FILE)

    fun load(): LoadResult {
        val f = target
        if (!f.exists()) return LoadResult.Loaded(emptyList(), emptyList())
        if (f.length() > SnippetLimits.MAX_FILE_BYTES) return LoadResult.Unreadable(SnippetFileError.TOO_LARGE)
        val bytes = try { f.readBytes() } catch (_: IOException) { return LoadResult.Unreadable(null) }
        return when (val d = SnippetBackup.decode(bytes)) {
            is DecodeResult.Ok -> LoadResult.Loaded(d.snippets, d.invalid)
            is DecodeResult.Failed -> LoadResult.Unreadable(d.error)
        }
    }

    fun save(snippets: List<Snippet>): SaveResult = try {
        dir.mkdirs()
        val bytes = SnippetBackup.encode(snippets)
        val tmp = File(dir, "$FILE.tmp")
        FileOutputStream(tmp).use { out ->
            out.write(bytes)
            out.flush()
            out.fd.sync()
        }
        keepPrevious()
        try {
            Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
        SaveResult.Saved
    } catch (e: IOException) {
        SaveResult.Failed(e)
    }

    private fun keepPrevious() {
        val f = target
        if (!f.exists()) return
        val readable = f.length() <= SnippetLimits.MAX_FILE_BYTES &&
            (try { SnippetBackup.decode(f.readBytes()) is DecodeResult.Ok } catch (_: IOException) { false })
        val keep = File(dir, if (readable) "$FILE.bak" else "$FILE.corrupt")
        Files.copy(f.toPath(), keep.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }

    companion object {
        const val FILE = "snippets.json"
    }
}
