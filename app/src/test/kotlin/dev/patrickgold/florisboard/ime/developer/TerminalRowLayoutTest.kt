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

import android.view.KeyEvent
import dev.patrickgold.florisboard.ime.developer.CustomSymbolsMode
import dev.patrickgold.florisboard.ime.mode.DeveloperRowPrefs
import dev.patrickgold.florisboard.ime.mode.OperatingMode
import dev.patrickgold.florisboard.ime.mode.TerminalKeyRow
import dev.patrickgold.florisboard.ime.mode.TerminalRowPolicy
import dev.patrickgold.florisboard.ime.terminal.Modifiers
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe

class TerminalRowLayoutTest : FunSpec({
    test("a blank order keeps today's row and the default ids match that row's start") {
        val base = DeveloperRows.row(
            LanguageProfile.SHELL,
            RowOptions(terminalKeys = true, navigationKeys = true, symbols = true),
        )
        TerminalRowLayout.arrange(base, "", "") shouldBe base
        TerminalRowLayout.keysFor(TerminalRowLayout.defaultIds.joinToString(",")) shouldBe
            DeveloperRows.modifierRow + DeveloperRows.navigationRow
    }

    test("a saved order chooses and reorders keys, and drops unknown ids") {
        val keys = TerminalRowLayout.keysFor("tab, no-such, esc, pipe, tab")
        keys.map { it.label } shouldBe listOf("Tab", "Esc", "|")
        val plan = TerminalRowPolicy.plan(
            policy = TerminalKeyRow.ALWAYS,
            mode = OperatingMode.STANDARD,
            prefs = DeveloperRowPrefs(true, LanguageProfile.SHELL, true, true, true),
            custom = emptyList(),
            customMode = CustomSymbolsMode.APPEND,
            packageName = "com.example.app",
            typeNull = false,
            keyOrder = "slash,tilde",
            macros = TerminalRowLayout.renderMacros(listOf(TerminalRowLayout.ctrlMacro('c')!!)),
        )
        plan.keys.map { it.label } shouldBe listOf("/", "~", "Ctrl+C")
    }

    test("a command macro is text plus Enter, and Ctrl+C is a key event") {
        val command = TerminalRowLayout.commandMacro("ls -l")!!
        command.steps shouldBe listOf(MacroStep.Text("ls -l"), MacroStep.Enter)
        val stored = TerminalRowLayout.addMacro("", command)
        TerminalRowLayout.parseMacros(stored).single().label shouldBe "ls -l"
        val resolved = DeveloperKeyResolver.resolve(DevKeyAction.Play(command.steps), Modifiers())
        resolved.consumesModifiers shouldBe false
        resolved.steps shouldBe listOf(
            DevStep.Text("ls -l"),
            DevStep.Key(KeyEvent.KEYCODE_ENTER, 0),
        )
        val ctrl = DeveloperKeyResolver.resolve(
            DevKeyAction.Play(listOf(MacroStep.Ctrl('C'))),
            Modifiers(),
        )
        ctrl.steps shouldBe listOf(
            DevStep.Key(
                KeyEvent.KEYCODE_C,
                KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON,
            ),
        )
        val full = (1..TerminalRowLayout.MAX_MACROS).fold("") { raw, n ->
            TerminalRowLayout.addMacro(raw, TerminalRowLayout.ctrlMacro(('a'.code + n - 1).toChar())!!)
        }
        TerminalRowLayout.parseMacros(full) shouldHaveSize TerminalRowLayout.MAX_MACROS
        TerminalRowLayout.parseMacros(TerminalRowLayout.addMacro(full, TerminalRowLayout.ctrlMacro('z')!!)) shouldHaveSize
            TerminalRowLayout.MAX_MACROS
    }
})
