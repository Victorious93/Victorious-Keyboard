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

import android.content.Context
import android.net.Uri
import androidx.room.RoomDatabase
import com.victorious.keyboard.ime.dictionary.DictionaryManager
import com.victorious.keyboard.ime.dictionary.UserDictionaryDao
import com.victorious.keyboard.ime.dictionary.UserDictionaryEntry
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed interface PackLoad {
    data class Loaded(val pack: DictionaryPack) : PackLoad
    data class Rejected(val error: PackError) : PackLoad
}

sealed interface InstallResult {
    data class Done(val changed: Int, val unchanged: Int) : InstallResult
    /** The internal user dictionary is switched off or could not be opened. */
    data object Unavailable : InstallResult
    data object Failed : InstallResult
}

/**
 * Android shell for dictionary packs: reads the bundled zips (`assets/ime/dictionary-packs`) or a zip the user picks, and
 * adds or removes the words in the internal user dictionary. All work runs on `Dispatchers.IO`, inside one database
 * transaction, and nothing is logged. What is added and removed is decided by the pure [PackPlanner].
 */
object DictionaryPackInstaller {
    private const val ASSET_DIR = "ime/dictionary-packs"

    suspend fun bundled(context: Context): List<DictionaryPack> = withContext(Dispatchers.IO) {
        val names = try { context.assets.list(ASSET_DIR).orEmpty().filter { it.endsWith(".zip") }.sorted() } catch (_: Exception) { emptyList() }
        names.mapNotNull { name ->
            try {
                context.assets.open("$ASSET_DIR/$name").use { readBounded(it) }
                    ?.let { (DictionaryPackReader.read(it) as? PackReadResult.Ok)?.pack }
            } catch (_: Exception) {
                null // a damaged bundled pack is skipped, never fatal
            }
        }
    }

    suspend fun fromUri(context: Context, uri: Uri): PackLoad = withContext(Dispatchers.IO) {
        try {
            val bytes = context.contentResolver.openInputStream(uri)?.use { readBounded(it) }
                ?: return@withContext PackLoad.Rejected(PackError.TOO_LARGE)
            when (val r = DictionaryPackReader.read(bytes)) {
                is PackReadResult.Ok -> PackLoad.Loaded(r.pack)
                is PackReadResult.Failed -> PackLoad.Rejected(r.error)
            }
        } catch (_: Exception) {
            PackLoad.Rejected(PackError.NOT_A_ZIP)
        }
    }

    /** Reads at most [PackLimits.MAX_ZIP_BYTES] bytes; returns null if the stream is longer. */
    private fun readBounded(input: java.io.InputStream): ByteArray? {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(8192)
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            if (out.size() + n > PackLimits.MAX_ZIP_BYTES) return null
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }

    private fun openDao(): Pair<RoomDatabase, UserDictionaryDao>? {
        val manager = DictionaryManager.default()
        manager.loadUserDictionariesIfNecessary()
        val db = manager.florisUserDictionaryDatabase() as? RoomDatabase ?: return null
        val dao = manager.florisUserDictionaryDao() ?: return null
        return db to dao
    }

    private fun UserDictionaryDao.existing(): List<ExistingWord> =
        queryAll().map { ExistingWord(it.id, it.word, it.freq, it.locale, it.shortcut) }

    /** How many of the pack's words are already in the dictionary exactly as the pack has them; null if unavailable. */
    suspend fun presentCount(pack: DictionaryPack): Int? = withContext(Dispatchers.IO) {
        try {
            val (_, dao) = openDao() ?: return@withContext null
            val plan = PackPlanner.planInstall(dao.existing(), pack)
            pack.entries.size - plan.toInsert.size
        } catch (_: Exception) {
            null
        }
    }

    suspend fun install(pack: DictionaryPack): InstallResult = withContext(Dispatchers.IO) {
        try {
            val (db, dao) = openDao() ?: return@withContext InstallResult.Unavailable
            var added = 0
            var present = 0
            db.runInTransaction {
                val plan = PackPlanner.planInstall(dao.existing(), pack)
                for (e in plan.toInsert) dao.insert(UserDictionaryEntry(0, e.word, e.freq, null, null))
                added = plan.toInsert.size
                present = plan.alreadyPresent
            }
            InstallResult.Done(added, present)
        } catch (_: Exception) {
            InstallResult.Failed
        }
    }

    /** Removes only words identical to the pack's (see [PackPlanner.planRemove]); anything the user changed stays. */
    suspend fun remove(pack: DictionaryPack): InstallResult = withContext(Dispatchers.IO) {
        try {
            val (db, dao) = openDao() ?: return@withContext InstallResult.Unavailable
            var removed = 0
            db.runInTransaction {
                val current = dao.queryAll()
                val ids = PackPlanner.planRemove(current.map { ExistingWord(it.id, it.word, it.freq, it.locale, it.shortcut) }, pack).toSet()
                for (entry in current) if (entry.id in ids) { dao.delete(entry); removed++ }
            }
            InstallResult.Done(removed, pack.entries.size - removed)
        } catch (_: Exception) {
            InstallResult.Failed
        }
    }
}
