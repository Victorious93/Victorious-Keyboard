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

package dev.patrickgold.florisboard.ime.terminal

/**
 * Android `KeyEvent` constants duplicated here so the terminal core has no `android.*` imports and runs in plain JVM
 * tests. `AndroidKeyConstantsTest` asserts every value against `android.view.KeyEvent`, so a typo cannot go unnoticed.
 */
internal object AndroidKeys {
    const val META_SHIFT_ON = 0x1
    const val META_ALT_ON = 0x2
    const val META_SHIFT_LEFT_ON = 0x40
    const val META_ALT_LEFT_ON = 0x10
    const val META_CTRL_ON = 0x1000
    const val META_CTRL_LEFT_ON = 0x2000

    const val KEYCODE_0 = 7
    const val KEYCODE_DPAD_UP = 19
    const val KEYCODE_DPAD_DOWN = 20
    const val KEYCODE_DPAD_LEFT = 21
    const val KEYCODE_DPAD_RIGHT = 22
    const val KEYCODE_A = 29
    const val KEYCODE_COMMA = 55
    const val KEYCODE_PERIOD = 56
    const val KEYCODE_TAB = 61
    const val KEYCODE_SPACE = 62
    const val KEYCODE_ENTER = 66
    const val KEYCODE_DEL = 67
    const val KEYCODE_GRAVE = 68
    const val KEYCODE_MINUS = 69
    const val KEYCODE_EQUALS = 70
    const val KEYCODE_LEFT_BRACKET = 71
    const val KEYCODE_RIGHT_BRACKET = 72
    const val KEYCODE_BACKSLASH = 73
    const val KEYCODE_SEMICOLON = 74
    const val KEYCODE_APOSTROPHE = 75
    const val KEYCODE_SLASH = 76
    const val KEYCODE_PAGE_UP = 92
    const val KEYCODE_PAGE_DOWN = 93
    const val KEYCODE_ESCAPE = 111
    const val KEYCODE_FORWARD_DEL = 112
    const val KEYCODE_MOVE_HOME = 122
    const val KEYCODE_MOVE_END = 123
    const val KEYCODE_INSERT = 124
    const val KEYCODE_F1 = 131
}
