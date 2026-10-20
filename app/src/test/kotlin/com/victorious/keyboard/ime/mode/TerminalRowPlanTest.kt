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

package com.victorious.keyboard.ime.mode

import com.victorious.keyboard.ime.developer.CustomSymbols
import com.victorious.keyboard.ime.developer.CustomSymbolsMode
import com.victorious.keyboard.ime.developer.DevKey
import com.victorious.keyboard.ime.developer.DeveloperRows
import com.victorious.keyboard.ime.developer.LanguageProfile
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldNotContainAnyOf
import io.kotest.matchers.shouldBe

private const val CHROME = "com.android.chrome"

private fun prefs(
    profile: LanguageProfile = LanguageProfile.SHELL,
    terminal: Boolean = true,
    navigation: Boolean = true,
    symbols: Boolean = true,
    enabled: Boolean = false,
) = DeveloperRowPrefs(enabled, profile, terminal, navigation, symbols)

private fun plan(
    policy: TerminalKeyRow = TerminalKeyRow.AUTO,
    mode: OperatingMode = OperatingMode.STANDARD,
    prefs: DeveloperRowPrefs = prefs(),
    custom: List<DevKey> = emptyList(),
    customMode: CustomSymbolsMode = CustomSymbolsMode.APPEND,
    packageName: String? = CHROME,
    typeNull: Boolean = false,
) = TerminalRowPolicy.plan(policy, mode, prefs, custom, customMode, packageName, typeNull)

class TerminalRowPlanTest : FunSpec({
    test("Auto hides the row in an ordinary app in Standard mode") {
        plan().visible shouldBe false
        plan().keys shouldBe emptyList()
    }

    test("Auto shows the row in a terminal app and in a TYPE_NULL field") {
        plan(packageName = "com.termux").visible shouldBe true
        plan(typeNull = true).visible shouldBe true
    }

    test("Auto shows the row in Developer and Termux mode in any app") {
        plan(mode = OperatingMode.DEVELOPER).visible shouldBe true
        plan(mode = OperatingMode.TERMUX).visible shouldBe true
    }

    test("planned modes never show the row on their own") {
        for (m in OperatingMode.entries.filterNot { it.isSelectable }) {
            plan(mode = m).visible shouldBe false
        }
    }

    test("Never wins over the app, the field type and the mode") {
        plan(TerminalKeyRow.NEVER, OperatingMode.TERMUX, packageName = "com.termux", typeNull = true).visible shouldBe false
    }

    test("Always shows the row everywhere") {
        plan(TerminalKeyRow.ALWAYS).visible shouldBe true
        plan(TerminalKeyRow.ALWAYS, packageName = null).visible shouldBe true
    }

    test("the user's switches decide the content outside Termux mode") {
        val row = plan(TerminalKeyRow.ALWAYS, prefs = prefs(navigation = false))
        row.keys shouldContainAll DeveloperRows.modifierRow
        row.keys shouldNotContainAnyOf DeveloperRows.navigationRow

        val noTerminal = plan(TerminalKeyRow.ALWAYS, prefs = prefs(terminal = false))
        noTerminal.keys shouldNotContainAnyOf DeveloperRows.modifierRow
        noTerminal.keys shouldContainAll DeveloperRows.navigationRow
    }

    test("the drawn options match the keys, so the row and its reserved height agree") {
        val row = plan(TerminalKeyRow.ALWAYS, prefs = prefs(navigation = false, profile = LanguageProfile.JSON))
        DeveloperRows.row(row.profile, row.options) shouldBe row.keys
    }

    test("Termux mode forces terminal keys, navigation and Shell symbols") {
        val row = plan(
            mode = OperatingMode.TERMUX,
            prefs = prefs(profile = LanguageProfile.JSON, terminal = false, navigation = false, symbols = false),
        )
        row.profile shouldBe LanguageProfile.SHELL
        row.keys shouldBe DeveloperRows.row(LanguageProfile.SHELL, row.options)
        row.keys shouldContainAll DeveloperRows.modifierRow + DeveloperRows.navigationRow
    }

    test("Developer mode keeps the user's profile") {
        plan(mode = OperatingMode.DEVELOPER, prefs = prefs(profile = LanguageProfile.PYTHON)).profile shouldBe
            LanguageProfile.PYTHON
    }

    test("the old developer-row switch no longer hides the row") {
        plan(TerminalKeyRow.ALWAYS, prefs = prefs(enabled = false)).visible shouldBe true
    }

    test("a row with every switch off and no custom keys is hidden, not drawn empty") {
        val row = plan(TerminalKeyRow.ALWAYS, prefs = prefs(terminal = false, navigation = false, symbols = false))
        row.visible shouldBe false
    }

    test("custom keys alone keep the row visible; replace mode uses them instead of the profile symbols") {
        val custom = CustomSymbols.parse("=> ::").keys
        val alone = plan(
            TerminalKeyRow.ALWAYS,
            prefs = prefs(terminal = false, navigation = false, symbols = false),
            custom = custom,
        )
        alone.visible shouldBe true
        alone.keys shouldBe custom

        val replaced = plan(TerminalKeyRow.ALWAYS, prefs = prefs(terminal = false, navigation = false), custom = custom,
            customMode = CustomSymbolsMode.REPLACE)
        replaced.keys shouldBe custom
    }
})
