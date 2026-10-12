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

package dev.patrickgold.florisboard.ime.snippets

import android.content.Context
import androidx.core.os.UserManagerCompat
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Android shell around [SnippetLibrary] and [SnippetFileStore]. The file lives in `noBackupFilesDir/snippets`, so
 * Android auto-backup never carries it (CLAUDE.md invariant 7). All disk access runs on `Dispatchers.IO`; the keyboard's
 * hot path only calls [snapshot], which reads memory. Nothing here logs snippet content.
 */
class SnippetManager(context: Context) {
    data class State(
        val library: SnippetLibrary = SnippetLibrary.EMPTY,
        val loaded: Boolean = false,
        /** The stored file exists but could not be read (it was left untouched; the next save keeps a `.corrupt` copy). */
        val unreadable: Boolean = false,
        /** Entries in the stored file that failed validation and were left out of memory (a `.bak` keeps the old file). */
        val invalidOnLoad: Int = 0,
    )

    private val store = SnippetFileStore(File(context.noBackupFilesDir, "snippets"))
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Mutex()

    val state: StateFlow<State>
        field = MutableStateFlow(State())

    private val appContext = context.applicationContext

    init {
        ensureLoaded()
    }

    /**
     * Loads the snippet file once the device is unlocked. Before the first unlock (Direct Boot) the credential-encrypted
     * storage is unavailable and a missing file would look like "no snippets"; loading then would latch an empty library
     * and a later save would replace the user's real file. So nothing is loaded and nothing can be saved until a real
     * load has happened. Safe to call repeatedly and from any thread.
     */
    fun ensureLoaded() {
        if (!UserManagerCompat.isUserUnlocked(appContext)) return
        scope.launch {
            lock.withLock {
                if (state.value.loaded) return@withLock
                state.value = when (val r = store.load()) {
                    is LoadResult.Loaded -> {
                        // Entries are already validated individually; a library built by adding them keeps the invariants.
                        var lib = SnippetLibrary.EMPTY
                        var dropped = r.invalid.size
                        for (s in r.snippets) {
                            when (val added = lib.add(s)) {
                                is LibraryResult.Ok -> lib = added.library
                                is LibraryResult.Rejected -> dropped++
                            }
                        }
                        State(lib, loaded = true, invalidOnLoad = dropped)
                    }
                    is LoadResult.Unreadable -> State(loaded = true, unreadable = true)
                }
            }
        }
    }

    /** Memory only; safe on the main thread. Empty until the first load completes. */
    fun snapshot(): SnippetLibrary = state.value.library

    suspend fun add(s: Snippet): LibraryResult = change { it.add(s) }

    suspend fun update(s: Snippet): LibraryResult = change { it.update(s) }

    suspend fun remove(id: String): LibraryResult = change { it.remove(id) }

    /** Returns null if the merged library could not be saved (memory and disk are then unchanged). */
    suspend fun importMerge(incoming: List<Snippet>, mode: ImportMode): ImportOutcome? = lock.withLock {
        withContext(Dispatchers.IO) {
            if (!state.value.loaded) return@withContext null
            val outcome = SnippetImporter.merge(state.value.library, incoming, mode)
            if (outcome.added + outcome.updated == 0) return@withContext outcome
            if (store.save(outcome.library.snippets) is SaveResult.Saved) {
                state.value = state.value.copy(library = outcome.library)
                outcome
            } else {
                null
            }
        }
    }

    /** The bytes of a plain-text export file (see [SnippetBackup]). */
    fun exportBytes(): ByteArray = SnippetBackup.encode(snapshot().snippets)

    private suspend fun change(op: (SnippetLibrary) -> LibraryResult): LibraryResult = lock.withLock {
        withContext(Dispatchers.IO) {
            // Never save before a real load: the empty in-memory library would replace the stored file.
            if (!state.value.loaded) return@withContext LibraryResult.Rejected(LibraryError.SAVE_FAILED)
            when (val r = op(state.value.library)) {
                is LibraryResult.Rejected -> r
                is LibraryResult.Ok -> if (store.save(r.library.snippets) is SaveResult.Saved) {
                    state.value = state.value.copy(library = r.library)
                    r
                } else {
                    LibraryResult.Rejected(LibraryError.SAVE_FAILED)
                }
            }
        }
    }
}
