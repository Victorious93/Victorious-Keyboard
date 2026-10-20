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

package com.victorious.keyboard.ime.terminal

import android.view.KeyEvent
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

private val CTRL = Modifiers(ctrl = true)
private val ALT = Modifiers(alt = true)
private const val CTRL_META = KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON
private const val ALT_META = KeyEvent.META_ALT_ON or KeyEvent.META_ALT_LEFT_ON
private const val SHIFT_META = KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON

class TerminalKeyMapperTest : FunSpec({
    test("Ctrl+C is a key event with the Ctrl meta state, never the text 'Ctrl+C'") {
        TerminalKeyMapper.mapChar("c", CTRL) shouldBe TerminalAction.Key(KeyEvent.KEYCODE_C, CTRL_META)
    }

    test("Ctrl+A..Z map to KEYCODE_A..Z") {
        for (i in 0 until 26) {
            val letter = ('a' + i).toString()
            TerminalKeyMapper.mapChar(letter, CTRL) shouldBe TerminalAction.Key(KeyEvent.KEYCODE_A + i, CTRL_META)
        }
    }

    test("the documented terminal chords") {
        // Ctrl+C D L Z A E R W U K are what the manual checklist exercises on Termux.
        val expected = mapOf(
            "c" to KeyEvent.KEYCODE_C, "d" to KeyEvent.KEYCODE_D, "l" to KeyEvent.KEYCODE_L, "z" to KeyEvent.KEYCODE_Z,
            "a" to KeyEvent.KEYCODE_A, "e" to KeyEvent.KEYCODE_E, "r" to KeyEvent.KEYCODE_R, "w" to KeyEvent.KEYCODE_W,
            "u" to KeyEvent.KEYCODE_U, "k" to KeyEvent.KEYCODE_K,
        )
        for ((ch, code) in expected) TerminalKeyMapper.mapChar(ch, CTRL) shouldBe TerminalAction.Key(code, CTRL_META)
    }

    test("Alt+key") {
        TerminalKeyMapper.mapChar("x", ALT) shouldBe TerminalAction.Key(KeyEvent.KEYCODE_X, ALT_META)
        TerminalKeyMapper.mapChar("b", Modifiers(ctrl = true, alt = true)) shouldBe
            TerminalAction.Key(KeyEvent.KEYCODE_B, CTRL_META or ALT_META)
    }

    test("an uppercase letter under Ctrl adds Shift; so does an explicit shift") {
        TerminalKeyMapper.mapChar("C", CTRL) shouldBe TerminalAction.Key(KeyEvent.KEYCODE_C, CTRL_META or SHIFT_META)
        TerminalKeyMapper.mapChar("c", Modifiers(ctrl = true, shift = true)) shouldBe
            TerminalAction.Key(KeyEvent.KEYCODE_C, CTRL_META or SHIFT_META)
    }

    test("digits and punctuation") {
        TerminalKeyMapper.mapChar("5", CTRL) shouldBe TerminalAction.Key(KeyEvent.KEYCODE_5, CTRL_META)
        TerminalKeyMapper.mapChar("[", CTRL) shouldBe TerminalAction.Key(KeyEvent.KEYCODE_LEFT_BRACKET, CTRL_META)
        TerminalKeyMapper.mapChar("\\", CTRL) shouldBe TerminalAction.Key(KeyEvent.KEYCODE_BACKSLASH, CTRL_META)
        TerminalKeyMapper.mapChar("]", CTRL) shouldBe TerminalAction.Key(KeyEvent.KEYCODE_RIGHT_BRACKET, CTRL_META)
        TerminalKeyMapper.mapChar("/", CTRL) shouldBe TerminalAction.Key(KeyEvent.KEYCODE_SLASH, CTRL_META)
        TerminalKeyMapper.mapChar(" ", CTRL) shouldBe TerminalAction.Key(KeyEvent.KEYCODE_SPACE, CTRL_META)
        // US-layout shifted symbols
        TerminalKeyMapper.mapChar("@", CTRL) shouldBe TerminalAction.Key(KeyEvent.KEYCODE_2, CTRL_META or SHIFT_META)
        TerminalKeyMapper.mapChar("^", CTRL) shouldBe TerminalAction.Key(KeyEvent.KEYCODE_6, CTRL_META or SHIFT_META)
        TerminalKeyMapper.mapChar("_", CTRL) shouldBe TerminalAction.Key(KeyEvent.KEYCODE_MINUS, CTRL_META or SHIFT_META)
        TerminalKeyMapper.mapChar(")", ALT) shouldBe TerminalAction.Key(KeyEvent.KEYCODE_0, ALT_META or SHIFT_META)
        TerminalKeyMapper.mapChar("!", ALT) shouldBe TerminalAction.Key(KeyEvent.KEYCODE_1, ALT_META or SHIFT_META)
    }

    test("every printable ASCII character has a key under Ctrl (nothing silently degrades to text)") {
        for (cp in 0x20..0x7E) {
            TerminalKeyMapper.mapChar(cp.toChar().toString(), CTRL).shouldBeInstanceOf<TerminalAction.Key>()
        }
    }

    test("without Ctrl/Alt the text path is used unchanged, even for shift") {
        TerminalKeyMapper.mapChar("c", Modifiers()) shouldBe TerminalAction.Text("c")
        TerminalKeyMapper.mapChar("C", Modifiers(shift = true)) shouldBe TerminalAction.Text("C")
        TerminalKeyMapper.mapChar("é", Modifiers()) shouldBe TerminalAction.Text("é")
    }

    test("characters without a key (non-ASCII, emoji, multi-code-point, empty) fall back to plain text") {
        for (text in listOf("é", "ß", "日", "🔐", "ab", "", "é")) {
            TerminalKeyMapper.mapChar(text, CTRL) shouldBe TerminalAction.Text(text)
        }
    }

    test("control characters are not turned into keys") {
        TerminalKeyMapper.mapChar("\u0003", CTRL) shouldBe TerminalAction.Text("\u0003")
        TerminalKeyMapper.mapChar("\u007f", CTRL) shouldBe TerminalAction.Text("\u007f")
    }

    test("special keys carry the meta state") {
        TerminalKeyMapper.mapSpecial(SpecialKey.ESCAPE, Modifiers()) shouldBe
            TerminalAction.Key(KeyEvent.KEYCODE_ESCAPE, 0)
        TerminalKeyMapper.mapSpecial(SpecialKey.TAB, Modifiers()) shouldBe TerminalAction.Key(KeyEvent.KEYCODE_TAB, 0)
        TerminalKeyMapper.mapSpecial(SpecialKey.TAB, Modifiers(shift = true)) shouldBe
            TerminalAction.Key(KeyEvent.KEYCODE_TAB, SHIFT_META)
        TerminalKeyMapper.mapSpecial(SpecialKey.LEFT, CTRL) shouldBe
            TerminalAction.Key(KeyEvent.KEYCODE_DPAD_LEFT, CTRL_META)
        TerminalKeyMapper.mapSpecial(SpecialKey.UP, Modifiers(ctrl = true, alt = true, shift = true)) shouldBe
            TerminalAction.Key(KeyEvent.KEYCODE_DPAD_UP, CTRL_META or ALT_META or SHIFT_META)
    }

    test("all special keys map to the intended platform codes") {
        val expected = mapOf(
            SpecialKey.ENTER to KeyEvent.KEYCODE_ENTER, SpecialKey.BACKSPACE to KeyEvent.KEYCODE_DEL,
            SpecialKey.DELETE to KeyEvent.KEYCODE_FORWARD_DEL, SpecialKey.DOWN to KeyEvent.KEYCODE_DPAD_DOWN,
            SpecialKey.RIGHT to KeyEvent.KEYCODE_DPAD_RIGHT, SpecialKey.HOME to KeyEvent.KEYCODE_MOVE_HOME,
            SpecialKey.END to KeyEvent.KEYCODE_MOVE_END, SpecialKey.PAGE_UP to KeyEvent.KEYCODE_PAGE_UP,
            SpecialKey.PAGE_DOWN to KeyEvent.KEYCODE_PAGE_DOWN, SpecialKey.INSERT to KeyEvent.KEYCODE_INSERT,
        )
        for ((key, code) in expected) TerminalKeyMapper.keyCodeOf(key) shouldBe code
    }

    test("F1..F12 are consecutive platform codes") {
        for (n in 1..12) {
            val key = SpecialKey.valueOf("F$n")
            TerminalKeyMapper.keyCodeOf(key) shouldBe KeyEvent.KEYCODE_F1 + (n - 1)
        }
        TerminalKeyMapper.keyCodeOf(SpecialKey.F12) shouldBe KeyEvent.KEYCODE_F12
    }

    test("meta() matches the layout of the editor's own helper") {
        TerminalKeyMapper.meta(false, false, false) shouldBe 0
        TerminalKeyMapper.meta(true, false, false) shouldBe CTRL_META
        TerminalKeyMapper.meta(false, true, false) shouldBe ALT_META
        TerminalKeyMapper.meta(false, false, true) shouldBe SHIFT_META
    }
})
