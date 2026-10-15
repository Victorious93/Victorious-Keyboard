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
 * When the extra Esc/Tab/Ctrl/Alt/arrow row is drawn. The enum constant names are persisted.
 * Never rename or reorder them; only add.
 */
enum class TerminalKeyRow {
    AUTO,
    ALWAYS,
    NEVER,
}

/**
 * The extra key row is hidden in ordinary apps. [TerminalKeyRow.AUTO] shows it for known terminal
 * packages and for `TYPE_NULL` fields (terminals that do not declare a text class).
 * This does not change [ModePresets], which still describes the older mode presets.
 */
object TerminalRowPolicy {
    private val extraPackages = setOf(
        "jackpal.androidterm",
        "io.neoterm",
    )

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
}
