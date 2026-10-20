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

import com.victorious.keyboard.ime.terminal.AndroidKeys as K

enum class SpecialKey {
    ESCAPE, TAB, ENTER, BACKSPACE, DELETE,
    UP, DOWN, LEFT, RIGHT, HOME, END, PAGE_UP, PAGE_DOWN, INSERT,
    F1, F2, F3, F4, F5, F6, F7, F8, F9, F10, F11, F12,
}

/** What the input layer must do for one key press. */
sealed interface TerminalAction {
    /** Send a real down+up `KeyEvent` with this key code and meta state. Never literal text such as "Ctrl+C". */
    data class Key(val keyCode: Int, val metaState: Int) : TerminalAction

    /** Commit this text through the normal text path (no modifier applies). */
    data class Text(val text: String) : TerminalAction
}

/**
 * Pure mapping from "key + modifiers" to the event a terminal app can understand.
 *
 * Scope and honest limits:
 * * This produces *what is sent*. How Termux/SSH clients interpret `Ctrl+C` etc. is up to the receiving app and has
 *   **not** been verified on a device.
 * * The character table assumes a US QWERTY key map (which is what Android's virtual keyboard map uses): `@` is
 *   `Shift+2`, `{` is `Shift+[`, and so on. Characters without an entry (non-ASCII, emoji, …) are committed as plain
 *   text and the modifier does not apply to them.
 */
object TerminalKeyMapper {

    /** A typed character, optionally under Ctrl/Alt. Without Ctrl/Alt the text path is used unchanged. */
    fun mapChar(text: String, mods: Modifiers): TerminalAction {
        if (!mods.any) return TerminalAction.Text(text)
        if (text.isEmpty() || text.codePointCount(0, text.length) != 1) return TerminalAction.Text(text)
        val entry = charKey(text.codePointAt(0)) ?: return TerminalAction.Text(text)
        return TerminalAction.Key(entry.keyCode, meta(mods.ctrl, mods.alt, mods.shift || entry.shift))
    }

    fun mapSpecial(key: SpecialKey, mods: Modifiers): TerminalAction =
        TerminalAction.Key(keyCodeOf(key), meta(mods.ctrl, mods.alt, mods.shift))

    /** Same layout as the editor's `meta()` helper so both paths send identical meta flags. */
    fun meta(ctrl: Boolean, alt: Boolean, shift: Boolean): Int {
        var m = 0
        if (ctrl) m = m or K.META_CTRL_ON or K.META_CTRL_LEFT_ON
        if (alt) m = m or K.META_ALT_ON or K.META_ALT_LEFT_ON
        if (shift) m = m or K.META_SHIFT_ON or K.META_SHIFT_LEFT_ON
        return m
    }

    fun keyCodeOf(key: SpecialKey): Int = when (key) {
        SpecialKey.ESCAPE -> K.KEYCODE_ESCAPE
        SpecialKey.TAB -> K.KEYCODE_TAB
        SpecialKey.ENTER -> K.KEYCODE_ENTER
        SpecialKey.BACKSPACE -> K.KEYCODE_DEL
        SpecialKey.DELETE -> K.KEYCODE_FORWARD_DEL
        SpecialKey.UP -> K.KEYCODE_DPAD_UP
        SpecialKey.DOWN -> K.KEYCODE_DPAD_DOWN
        SpecialKey.LEFT -> K.KEYCODE_DPAD_LEFT
        SpecialKey.RIGHT -> K.KEYCODE_DPAD_RIGHT
        SpecialKey.HOME -> K.KEYCODE_MOVE_HOME
        SpecialKey.END -> K.KEYCODE_MOVE_END
        SpecialKey.PAGE_UP -> K.KEYCODE_PAGE_UP
        SpecialKey.PAGE_DOWN -> K.KEYCODE_PAGE_DOWN
        SpecialKey.INSERT -> K.KEYCODE_INSERT
        else -> K.KEYCODE_F1 + (key.ordinal - SpecialKey.F1.ordinal) // F1..F12 are consecutive in Android
    }

    private class CharKey(val keyCode: Int, val shift: Boolean)

    private fun charKey(cp: Int): CharKey? {
        val c = if (cp in 0..0x7F) cp.toChar() else return null
        return when (c) {
            in 'a'..'z' -> CharKey(K.KEYCODE_A + (c - 'a'), false)
            in 'A'..'Z' -> CharKey(K.KEYCODE_A + (c - 'A'), true)
            in '0'..'9' -> CharKey(K.KEYCODE_0 + (c - '0'), false)
            ' ' -> CharKey(K.KEYCODE_SPACE, false)
            else -> PUNCTUATION[c]
        }
    }

    private val PUNCTUATION: Map<Char, CharKey> = buildMap {
        fun plain(c: Char, code: Int) = put(c, CharKey(code, false))
        fun shifted(c: Char, code: Int) = put(c, CharKey(code, true))
        plain('`', K.KEYCODE_GRAVE); shifted('~', K.KEYCODE_GRAVE)
        plain('-', K.KEYCODE_MINUS); shifted('_', K.KEYCODE_MINUS)
        plain('=', K.KEYCODE_EQUALS); shifted('+', K.KEYCODE_EQUALS)
        plain('[', K.KEYCODE_LEFT_BRACKET); shifted('{', K.KEYCODE_LEFT_BRACKET)
        plain(']', K.KEYCODE_RIGHT_BRACKET); shifted('}', K.KEYCODE_RIGHT_BRACKET)
        plain('\\', K.KEYCODE_BACKSLASH); shifted('|', K.KEYCODE_BACKSLASH)
        plain(';', K.KEYCODE_SEMICOLON); shifted(':', K.KEYCODE_SEMICOLON)
        plain('\'', K.KEYCODE_APOSTROPHE); shifted('"', K.KEYCODE_APOSTROPHE)
        plain(',', K.KEYCODE_COMMA); shifted('<', K.KEYCODE_COMMA)
        plain('.', K.KEYCODE_PERIOD); shifted('>', K.KEYCODE_PERIOD)
        plain('/', K.KEYCODE_SLASH); shifted('?', K.KEYCODE_SLASH)
        // Shifted digits (US layout).
        "!@#$%^&*()".forEachIndexed { i, c -> shifted(c, K.KEYCODE_0 + ((i + 1) % 10)) }
    }
}
