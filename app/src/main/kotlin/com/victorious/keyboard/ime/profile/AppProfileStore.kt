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

package com.victorious.keyboard.ime.profile

import android.content.Context
import java.io.File

/**
 * Per-app profiles under `noBackupFilesDir`. The file holds package names and setting choices, not typed text.
 * Android auto-backup does not carry it.
 */
object AppProfileStore {
    private var loaded = false
    private var profiles: List<AppProfile> = emptyList()

    fun ensure(context: Context): List<AppProfile> {
        synchronized(this) {
            if (loaded) return profiles
            loaded = true
            profiles = AppProfileCodec.decode(read(store(context)))
            return profiles
        }
    }

    fun replace(context: Context, next: List<AppProfile>) {
        synchronized(this) {
            loaded = true
            profiles = next.take(AppProfileCodec.MAX_PROFILES)
            write(store(context), AppProfileCodec.encode(profiles))
        }
    }

    private fun store(context: Context): File {
        val dir = File(context.applicationContext.noBackupFilesDir, "profiles")
        return File(dir, "profiles.json")
    }

    private fun read(file: File): String? = try {
        if (!file.isFile || file.length() > AppProfileCodec.MAX_CHARS) null else file.readText()
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
            // A failed write must not crash the keyboard.
        }
    }
}
