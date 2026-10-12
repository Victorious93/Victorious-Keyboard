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

package dev.patrickgold.florisboard.ime.mode

/**
 * Parses the user's own app-to-mode mappings for automatic mode selection. One mapping per line:
 * `com.example.app=TERMUX` (or a space instead of `=`); `#` starts a comment. Untrusted text: at most [MAX_ENTRIES]
 * entries, package names must look like Android package names, and the mode must exist **and be selectable**.
 * Rejected lines are returned, never guessed at.
 */
object ModeOverrides {
    const val MAX_ENTRIES = 50

    private val PACKAGE = Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+")

    data class Parsed(val overrides: Map<String, OperatingMode>, val rejected: List<String>)

    fun parse(text: String): Parsed {
        val map = LinkedHashMap<String, OperatingMode>()
        val rejected = mutableListOf<String>()
        for (raw in text.lineSequence()) {
            val line = raw.substringBefore('#').trim()
            if (line.isEmpty()) continue
            val parts = line.split('=', ' ', '\t').filter { it.isNotEmpty() }
            val mode = parts.getOrNull(1)?.let { name ->
                OperatingMode.entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
            }
            when {
                parts.size != 2 || !PACKAGE.matches(parts[0]) || parts[0].length > 255 -> rejected += line
                mode == null || !mode.isSelectable -> rejected += line
                map.size >= MAX_ENTRIES && parts[0] !in map -> rejected += line
                else -> map[parts[0]] = mode
            }
        }
        return Parsed(map, rejected)
    }
}
