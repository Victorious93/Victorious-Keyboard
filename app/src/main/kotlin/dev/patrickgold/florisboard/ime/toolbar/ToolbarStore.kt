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

package dev.patrickgold.florisboard.ime.toolbar

import android.content.Context
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Toolbar preferences and the opt-in shell history. The file is under `noBackupFilesDir`, so
 * Android auto-backup does not carry command text. Nothing in here is written to logcat.
 */
object ToolbarRuntime {
    private val mutable = MutableStateFlow(ToolbarConfig())
    val config: StateFlow<ToolbarConfig> = mutable
    private var loaded = false

    fun ensure(context: Context) {
        synchronized(this) {
            if (loaded) return
            loaded = true
            mutable.value = ToolbarConfigCodec.decode(read(store(context)))
        }
    }

    fun update(context: Context, transform: (ToolbarConfig) -> ToolbarConfig) {
        ensure(context)
        val next = transform(mutable.value)
        mutable.value = next
        write(store(context), ToolbarConfigCodec.encode(next))
    }

    fun remember(context: Context, command: String) {
        update(context) { current ->
            current.copy(recentCommands = ShellHistoryPolicy.remember(current.recentCommands, command))
        }
    }

    private fun store(context: Context): File {
        val dir = File(context.applicationContext.noBackupFilesDir, "toolbar")
        return File(dir, "config.json")
    }

    private fun read(file: File): String? = try {
        if (!file.isFile || file.length() > ToolbarConfigCodec.MAX_CHARS) null else file.readText()
    } catch (_: Exception) {
        null
    }

    private fun write(file: File, text: String) {
        try {
            file.parentFile?.mkdirs()
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(text)
            if (!tmp.renameTo(file)) {
                file.writeText(text)
                tmp.delete()
            }
        } catch (_: Exception) {
            // A failed write must not crash the keyboard. The in-memory copy remains for this session.
        }
    }
}
