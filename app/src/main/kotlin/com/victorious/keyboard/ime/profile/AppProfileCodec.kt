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

import com.victorious.keyboard.ime.mode.TerminalKeyRow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Saved per-app profiles. A broken file becomes an empty list and is not logged. */
object AppProfileCodec {
    const val FORMAT = "victorious-app-profiles"
    const val MAX_CHARS = 64 * 1024
    const val MAX_PROFILES = 40
    private val json = Json { ignoreUnknownKeys = true }
    private val packageName = Regex("""[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z][A-Za-z0-9_]*)+""")

    @Serializable
    private data class File(
        val format: String = "",
        val version: Int = 0,
        val profiles: List<Row> = emptyList(),
    )

    @Serializable
    private data class Row(
        val packageName: String = "",
        val layoutSubtypeId: Long? = null,
        val themeId: String? = null,
        val terminalRow: String? = null,
        val autocorrectOff: Boolean? = null,
        val suggestions: Boolean? = null,
    )

    fun encode(profiles: List<AppProfile>): String {
        val file = File(
            format = FORMAT,
            version = 1,
            profiles = profiles.take(MAX_PROFILES).map { profile ->
                Row(
                    packageName = profile.packageName,
                    layoutSubtypeId = profile.layoutSubtypeId,
                    themeId = profile.themeId,
                    terminalRow = profile.terminalRow?.name,
                    autocorrectOff = profile.autocorrectOff,
                    suggestions = profile.suggestions,
                )
            },
        )
        return json.encodeToString(File.serializer(), file)
    }

    fun decode(text: String?): List<AppProfile> {
        if (text.isNullOrBlank() || text.length > MAX_CHARS) return emptyList()
        return try {
            val file = json.decodeFromString(File.serializer(), text)
            if (file.format != FORMAT || file.version != 1) return emptyList()
            file.profiles.mapNotNull { row -> normalize(row) }.take(MAX_PROFILES)
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun normalize(row: Row): AppProfile? {
        val name = row.packageName.trim()
        if (name.length !in 3..200 || !packageName.matches(name)) return null
        val theme = row.themeId?.trim()?.take(120)?.ifEmpty { null }
        val layout = row.layoutSubtypeId?.takeIf { it > 0L }
        val rowPolicy = row.terminalRow?.let { raw ->
            TerminalKeyRow.entries.firstOrNull { it.name == raw }
        }
        return AppProfile(
            packageName = name,
            layoutSubtypeId = layout,
            themeId = theme,
            terminalRow = rowPolicy,
            autocorrectOff = row.autocorrectOff,
            suggestions = row.suggestions,
        )
    }
}
