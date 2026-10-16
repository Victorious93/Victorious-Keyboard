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

import dev.patrickgold.florisboard.ime.mode.OperatingMode

/**
 * How snippet expansion behaves in one operating mode. Applied on top of the master switch and the Enter/Tab opt-ins.
 *
 * **The enum constant names are persisted in preferences. Never rename or reorder them**; only add.
 */
enum class ModeExpansion {
    /** The keys the user chose: Space, plus Enter and Tab when those switches are on. */
    FOLLOW,

    /** Space only, whatever the Enter and Tab switches say. In a terminal, Enter runs the line, so this is the safe on. */
    SPACE_ONLY,

    /** No expansion while this mode is active. */
    OFF,
}

/**
 * One [ModeExpansion] per selectable operating mode. The defaults keep the behaviour from before per-mode rules existed:
 * Standard and Developer follow the switches, Termux is off.
 */
data class ModeExpansionRules(
    val standard: ModeExpansion = ModeExpansion.FOLLOW,
    val developer: ModeExpansion = ModeExpansion.FOLLOW,
    val termux: ModeExpansion = ModeExpansion.OFF,
) {
    /** The rule for [mode]. Planned and unknown modes use Standard's, as `OperatingMode.sanitize` does everywhere else. */
    fun of(mode: OperatingMode?): ModeExpansion = when (OperatingMode.sanitize(mode)) {
        OperatingMode.DEVELOPER -> developer
        OperatingMode.TERMUX -> termux
        else -> standard
    }
}

object ModeExpansionPolicy {
    /**
     * The keys that may complete an abbreviation while [mode] is active, or null when expansion is off there. The master
     * switch always wins: a mode rule can narrow expansion but never turn it on.
     */
    fun triggers(
        mode: OperatingMode?,
        masterEnabled: Boolean,
        global: ExpansionTriggers,
        rules: ModeExpansionRules,
    ): ExpansionTriggers? {
        if (!masterEnabled) return null
        val effective = when (rules.of(mode)) {
            ModeExpansion.FOLLOW -> global
            ModeExpansion.SPACE_ONLY -> ExpansionTriggers(space = global.space, enter = false, tab = false)
            ModeExpansion.OFF -> return null
        }
        return if (effective.boundaryChars().isEmpty()) null else effective
    }

    /**
     * The Termux rule for a stored value of the retired "Expand snippets in Termux mode" switch
     * (`snippets__expand_in_terminal_mode`). On meant "Enter and Tab follow the same switches as other modes", which is
     * [ModeExpansion.FOLLOW]; off, or anything that is not a boolean, is [ModeExpansion.OFF]. Nobody's behaviour changes.
     */
    fun termuxFromLegacy(rawValue: String?): ModeExpansion =
        if (rawValue?.trim()?.lowercase() == "true") ModeExpansion.FOLLOW else ModeExpansion.OFF
}
