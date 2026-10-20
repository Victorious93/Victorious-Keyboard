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

package com.victorious.keyboard.ime.nlp.custom

import android.content.Context
import com.victorious.keyboard.ime.clipboard.backup.ImportMode
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * User phrases in `noBackupFilesDir/suggestions`. Android auto-backup does not carry that directory.
 * Phrase text is never logged.
 */
class CustomSuggestionFile(private val dir: File) {
    private val target = File(dir, "custom.json")

    fun load(): List<CustomSuggestion> {
        if (!target.isFile) return emptyList()
        if (target.length() > CustomSuggestionRules.MAX_FILE_BYTES) return emptyList()
        val raw = try {
            target.readText()
        } catch (_: IOException) {
            return emptyList()
        }
        return when (val doc = CustomSuggestionCodec.decode(raw)) {
            is SuggestionDocument.Ok -> doc.items
            SuggestionDocument.Invalid -> emptyList()
        }
    }

    fun save(items: List<CustomSuggestion>) {
        dir.mkdirs()
        val tmp = File(dir, "custom.json.tmp")
        FileOutputStream(tmp).use { out ->
            out.write(CustomSuggestionCodec.encode(items).encodeToByteArray())
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

class CustomSuggestionManager(context: Context) {
    private val file = CustomSuggestionFile(File(context.noBackupFilesDir, "suggestions"))
    private val lock = Any()
    private val _items = MutableStateFlow<List<CustomSuggestion>>(emptyList())
    val items: StateFlow<List<CustomSuggestion>> = _items

    init {
        _items.value = file.load()
    }

    fun snapshot(): List<CustomSuggestion> = _items.value

    fun add(text: String, trigger: String): Boolean = mutate { CustomSuggestionList.add(it, text, trigger, System.currentTimeMillis()) }

    fun update(id: String, text: String, trigger: String): Boolean =
        mutate { CustomSuggestionList.update(it, id, text, trigger) }

    fun delete(id: String): Boolean = mutate { CustomSuggestionList.delete(it, id) }

    /** JSON for the clipboard ZIP, or null when there is nothing to export. */
    fun exportDocumentOrNull(): String? {
        val current = snapshot()
        if (current.isEmpty()) return null
        return CustomSuggestionCodec.encode(current)
    }

    /**
     * Applies a `suggestions.json` member. An invalid document changes nothing, including in replace mode.
     * A valid empty list replaces only when [mode] is [ImportMode.REPLACE].
     */
    fun importDocument(document: String, mode: ImportMode) {
        val decoded = CustomSuggestionCodec.decode(document)
        if (decoded is SuggestionDocument.Invalid) return
        val incoming = (decoded as SuggestionDocument.Ok).items
        synchronized(lock) {
            val current = _items.value
            val next = when (mode) {
                ImportMode.REPLACE -> incoming
                ImportMode.MERGE -> CustomSuggestionList.merge(current, incoming, replace = false)
                ImportMode.SKIP_DUPLICATES -> {
                    val known = current.map { it.id }.toSet()
                    CustomSuggestionList.merge(current, incoming.filter { it.id !in known }, replace = false)
                }
            }
            file.save(next)
            _items.value = next
        }
    }

    private fun mutate(block: (List<CustomSuggestion>) -> List<CustomSuggestion>?): Boolean {
        synchronized(lock) {
            val next = block(_items.value) ?: return false
            file.save(next)
            _items.value = next
            return true
        }
    }
}
