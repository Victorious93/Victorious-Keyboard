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

package dev.patrickgold.florisboard.ime.developer

import dev.patrickgold.florisboard.ime.terminal.SpecialKey
import dev.patrickgold.florisboard.ime.terminal.TerminalModifier
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class DeveloperRowsTest : FunSpec({
    test("the terminal row has the keys a terminal needs, in a stable order") {
        val actions = DeveloperRows.terminalRow.map { it.action }
        actions shouldContain DevKeyAction.Special(SpecialKey.ESCAPE)
        actions shouldContain DevKeyAction.Special(SpecialKey.TAB)
        actions shouldContain DevKeyAction.Modifier(TerminalModifier.CTRL)
        actions shouldContain DevKeyAction.Modifier(TerminalModifier.ALT)
        for (k in listOf(SpecialKey.UP, SpecialKey.DOWN, SpecialKey.LEFT, SpecialKey.RIGHT)) {
            actions shouldContain DevKeyAction.Special(k)
        }
        DeveloperRows.terminalRow.map { it.label }.take(4) shouldBe listOf("Esc", "Tab", "Ctrl", "Alt")
    }

    test("every profile has a non-empty symbol row with usable keys") {
        for (profile in LanguageProfile.entries) {
            val row = DeveloperRows.symbolRow(profile)
            row.shouldNotBeEmpty()
            for (key in row) {
                key.label.isNotBlank() shouldBe true
                key.description.isNotBlank() shouldBe true
                val a = key.action
                if (a is DevKeyAction.Insert) {
                    a.text.isNotEmpty() shouldBe true
                    (a.cursorBack in 0..a.text.length) shouldBe true
                }
            }
        }
    }

    test("no duplicate labels within one row") {
        for (profile in LanguageProfile.entries) {
            val labels = DeveloperRows.symbolRow(profile).map { it.label }
            labels.distinct().size shouldBe labels.size
        }
        DeveloperRows.terminalRow.map { it.label }.distinct().size shouldBe DeveloperRows.terminalRow.size
    }

    test("pair keys put the cursor between the two halves") {
        val braces = DeveloperRows.symbolRow(LanguageProfile.JS_TS).first { it.label == "{}" }
        braces.action shouldBe DevKeyAction.Insert("{}", 1)
        val quotes = DeveloperRows.symbolRow(LanguageProfile.PYTHON).first { it.label == "\"\"" }
        quotes.action shouldBe DevKeyAction.Insert("\"\"", 1)
    }

    test("markdown code block leaves the cursor on the empty line between the fences") {
        val block = DeveloperRows.symbolRow(LanguageProfile.MARKDOWN).first { it.label.startsWith("```") }
        val a = block.action as DevKeyAction.Insert
        a.text.substring(0, a.text.length - a.cursorBack) shouldBe "```\n"
    }

    test("profile ids are unique and round-trip") {
        LanguageProfile.entries.map { it.id }.distinct().size shouldBe LanguageProfile.entries.size
        for (p in LanguageProfile.entries) LanguageProfile.fromId(p.id) shouldBe p
        LanguageProfile.fromId("nope") shouldBe null
        LanguageProfile.fromId(null) shouldBe null
        LanguageProfile.fromId("shell") shouldNotBe null
    }

    test("the shown row is the terminal keys followed by the profile symbols, or just the symbols") {
        for (profile in LanguageProfile.entries) {
            val withTerminal = DeveloperRows.row(profile, includeTerminalKeys = true)
            withTerminal shouldBe DeveloperRows.terminalRow + DeveloperRows.symbolRow(profile)
            DeveloperRows.row(profile, includeTerminalKeys = false) shouldBe DeveloperRows.symbolRow(profile)
        }
    }
})
