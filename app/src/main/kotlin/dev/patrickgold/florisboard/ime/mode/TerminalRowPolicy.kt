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

import dev.patrickgold.florisboard.ime.developer.CustomSymbolsMode
import dev.patrickgold.florisboard.ime.developer.DevKey
import dev.patrickgold.florisboard.ime.developer.DeveloperRows
import dev.patrickgold.florisboard.ime.developer.LanguageProfile
import dev.patrickgold.florisboard.ime.developer.RowOptions
import dev.patrickgold.florisboard.ime.developer.TerminalRowLayout

/**
 * When the extra Esc/Tab/Ctrl/Alt/arrow row is drawn. The enum constant names are persisted.
 * Never rename or reorder them; only add.
 */
enum class TerminalKeyRow {
    AUTO,
    ALWAYS,
    NEVER,
}

/**
 * What the terminal key row draws right now. [keys] is empty when the row is hidden, so [visible] cannot disagree with
 * what is drawn.
 */
data class TerminalRowPlan(
    val profile: LanguageProfile,
    val options: RowOptions,
    val keys: List<DevKey>,
) {
    val visible: Boolean get() = keys.isNotEmpty()

    companion object {
        val Hidden = TerminalRowPlan(LanguageProfile.SHELL, RowOptions(), emptyList())
    }
}

/**
 * The extra key row is hidden in ordinary apps. [TerminalKeyRow.AUTO] shows it for known terminal
 * packages, for `TYPE_NULL` fields (terminals that do not declare a text class), and while the
 * Developer or Termux operating mode is active.
 */
object TerminalRowPolicy {
    private val extraPackages = setOf(
        "jackpal.androidterm",
        "io.neoterm",
    )

    /** Modes whose whole point is the row; in [TerminalKeyRow.AUTO] they show it in any app. */
    private val rowModes = setOf(OperatingMode.DEVELOPER, OperatingMode.TERMUX)

    fun isTerminalPackage(packageName: String?): Boolean {
        if (packageName.isNullOrEmpty()) return false
        if (packageName.startsWith("com.termux.")) return true
        if (packageName in extraPackages) return true
        return ModeSuggester.builtIn[packageName] == OperatingMode.TERMUX
    }

    fun shouldShow(policy: TerminalKeyRow, packageName: String?, typeNull: Boolean): Boolean = when (policy) {
        TerminalKeyRow.NEVER -> false
        TerminalKeyRow.ALWAYS -> true
        TerminalKeyRow.AUTO -> typeNull || isTerminalPackage(packageName)
    }

    /**
     * The single decision used both to draw the row and to reserve its height for panels that replace the keys.
     * Two separate decisions used to disagree (the row drawn in a terminal while no height was reserved for it).
     *
     * * [policy] decides whether the row may appear; [TerminalKeyRow.AUTO] also shows it in Developer and Termux mode.
     * * The mode decides the content: Termux forces terminal keys, navigation keys and Shell symbols; every other
     *   mode uses the user's switches and profile. `prefs.enabled` is no longer read (superseded by [policy]).
     * * A row with no keys (every switch off and no custom keys) is hidden rather than drawn empty.
     */
    fun plan(
        policy: TerminalKeyRow,
        mode: OperatingMode,
        prefs: DeveloperRowPrefs,
        custom: List<DevKey>,
        customMode: CustomSymbolsMode,
        packageName: String?,
        typeNull: Boolean,
        keyOrder: String = "",
        macros: String = "",
    ): TerminalRowPlan {
        val active = OperatingMode.sanitize(mode)
        val allowed = shouldShow(policy, packageName, typeNull) ||
            (policy == TerminalKeyRow.AUTO && active in rowModes)
        if (!allowed) return TerminalRowPlan.Hidden
        val preset = ModePresets.developerRow(active, prefs.copy(enabled = true))
        val options = RowOptions(
            terminalKeys = preset.terminalKeys,
            navigationKeys = preset.navigationKeys,
            symbols = preset.symbols,
            custom = custom,
            customMode = customMode,
        )
        val keys = TerminalRowLayout.arrange(DeveloperRows.row(preset.profile, options), keyOrder, macros)
        return if (keys.isEmpty()) TerminalRowPlan.Hidden else TerminalRowPlan(preset.profile, options, keys)
    }
}
