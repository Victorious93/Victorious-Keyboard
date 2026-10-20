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

/**
 * Ids for tools that are not built-in tiles: a user slash command (`slash:<id>`) or a fleet action
 * (`fleet:<package>:<action>`). The strings are stored in the toolbar file. They carry no command text.
 */
object ToolRef {
    private val slashPattern = Regex("""slash:[A-Za-z0-9_-]{1,64}""")
    private val fleetPattern = Regex("""fleet:[A-Za-z0-9_.]{1,120}:[A-Za-z0-9_.-]{1,40}""")

    fun slash(id: String): String = "slash:$id"

    fun fleet(packageName: String, action: String): String = "fleet:$packageName:$action"

    fun isSlash(raw: String): Boolean = slashPattern.matches(raw)

    fun isFleet(raw: String): Boolean = fleetPattern.matches(raw)

    fun isExtra(raw: String): Boolean = isSlash(raw) || isFleet(raw)

    fun slashId(raw: String): String? = if (isSlash(raw)) raw.removePrefix("slash:") else null

    /** Package and action. The package is everything between the first and last colon. */
    fun fleetParts(raw: String): Pair<String, String>? {
        if (!isFleet(raw)) return null
        val body = raw.removePrefix("fleet:")
        val split = body.lastIndexOf(':')
        if (split <= 0 || split == body.lastIndex) return null
        return body.substring(0, split) to body.substring(split + 1)
    }
}
