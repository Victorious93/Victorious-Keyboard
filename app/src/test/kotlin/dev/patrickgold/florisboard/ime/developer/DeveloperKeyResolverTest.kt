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
import dev.patrickgold.florisboard.ime.terminal.Modifiers
import dev.patrickgold.florisboard.ime.terminal.SpecialKey
import dev.patrickgold.florisboard.ime.terminal.TerminalModifier
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

private const val CTRL_META = KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON
private val CTRL = Modifiers(ctrl = true)
private val NONE = Modifiers()

class DeveloperKeyResolverTest : FunSpec({
    test("a modifier key is not resolved to input") {
        DeveloperKeyResolver.resolve(DevKeyAction.Modifier(TerminalModifier.CTRL), CTRL) shouldBe
            DevResolution(emptyList(), false)
    }

    test("special keys always send a key event with the active modifiers") {
        DeveloperKeyResolver.resolve(DevKeyAction.Special(SpecialKey.ESCAPE), NONE) shouldBe
            DevResolution(listOf(DevStep.Key(KeyEvent.KEYCODE_ESCAPE, 0)), true)
        DeveloperKeyResolver.resolve(DevKeyAction.Special(SpecialKey.LEFT), CTRL) shouldBe
            DevResolution(listOf(DevStep.Key(KeyEvent.KEYCODE_DPAD_LEFT, CTRL_META)), true)
    }

    test("a single-character insert is text without modifiers") {
        DeveloperKeyResolver.resolve(DevKeyAction.Insert("|"), NONE) shouldBe
            DevResolution(listOf(DevStep.Text("|")), false)
    }

    test("a single-character insert becomes Ctrl+key while Ctrl is active (Ctrl+backslash)") {
        DeveloperKeyResolver.resolve(DevKeyAction.Insert("\\"), CTRL) shouldBe
            DevResolution(listOf(DevStep.Key(KeyEvent.KEYCODE_BACKSLASH, CTRL_META)), true)
    }

    test("a single-character insert without a key falls back to text and does not spend the modifier") {
        DeveloperKeyResolver.resolve(DevKeyAction.Insert("é"), CTRL) shouldBe
            DevResolution(listOf(DevStep.Text("é")), false)
    }

    test("pairs commit both halves and then move the cursor between them") {
        DeveloperKeyResolver.resolve(DevKeyAction.Insert("()", 1), NONE) shouldBe
            DevResolution(listOf(DevStep.Text("()"), DevStep.CursorLeft(1)), false)
        DeveloperKeyResolver.resolve(DevKeyAction.Insert("()", 1), CTRL) shouldBe
            DevResolution(listOf(DevStep.Text("()"), DevStep.CursorLeft(1)), false)
    }

    test("multi-character inserts without a cursor offset have no cursor step") {
        DeveloperKeyResolver.resolve(DevKeyAction.Insert("=>"), NONE) shouldBe
            DevResolution(listOf(DevStep.Text("=>")), false)
    }

    test("every key of every profile resolves without throwing, with and without modifiers") {
        val all = DeveloperRows.terminalRow + LanguageProfile.entries.flatMap { DeveloperRows.symbolRow(it) }
        for (mods in listOf(NONE, CTRL, Modifiers(alt = true), Modifiers(ctrl = true, alt = true, shift = true))) {
            for (key in all) {
                val r = DeveloperKeyResolver.resolve(key.action, mods)
                if (key.action !is DevKeyAction.Modifier) (r.steps.isNotEmpty()) shouldBe true
            }
        }
    }
})
