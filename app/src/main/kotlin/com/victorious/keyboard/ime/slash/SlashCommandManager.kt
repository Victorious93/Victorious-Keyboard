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

package com.victorious.keyboard.ime.slash

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Custom slash commands in `noBackupFilesDir/slash`. Command text is never logged. */
class SlashCommandFile(private val dir: File) {
    private val target = File(dir, "commands.json")

    fun load(): List<CustomSlashCommand> {
        if (!target.isFile) return emptyList()
        if (target.length() > SlashCommandRules.MAX_FILE_BYTES) return emptyList()
        val raw = try {
            target.readText()
        } catch (_: IOException) {
            return emptyList()
        }
        return when (val doc = SlashCommandCodec.decode(raw)) {
            is SlashDocument.Ok -> doc.items
            SlashDocument.Invalid -> emptyList()
        }
    }

    fun save(items: List<CustomSlashCommand>) {
        dir.mkdirs()
        val tmp = File(dir, "commands.json.tmp")
        FileOutputStream(tmp).use { out ->
            out.write(SlashCommandCodec.encode(items).encodeToByteArray())
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

class SlashCommandManager(context: Context) {
    private val file = SlashCommandFile(File(context.noBackupFilesDir, "slash"))
    private val lock = Any()
    private val _items = MutableStateFlow<List<CustomSlashCommand>>(emptyList())
    val items: StateFlow<List<CustomSlashCommand>> = _items

    init {
        _items.value = file.load()
    }

    fun snapshot(): List<CustomSlashCommand> = _items.value

    fun add(name: String, description: String, template: String): Boolean =
        mutate { SlashCommandList.add(it, name, description, template) }

    fun update(id: String, name: String, description: String, template: String, enabled: Boolean): Boolean =
        mutate { SlashCommandList.update(it, id, name, description, template, enabled) }

    fun delete(id: String): Boolean = mutate { SlashCommandList.delete(it, id) }

    fun move(id: String, direction: Int): Boolean = mutate { SlashCommandList.move(it, id, direction) }

    private fun mutate(block: (List<CustomSlashCommand>) -> List<CustomSlashCommand>?): Boolean {
        synchronized(lock) {
            val next = block(_items.value) ?: return false
            file.save(next)
            _items.value = next
            return true
        }
    }
}
