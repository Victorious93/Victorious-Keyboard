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

import android.view.KeyEvent
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/** The duplicated constants must equal the platform's; this is the only thing keeping them honest. */
class AndroidKeyConstantsTest : FunSpec({
    test("constants equal android.view.KeyEvent") {
        val pairs = listOf(
            AndroidKeys.META_SHIFT_ON to KeyEvent.META_SHIFT_ON,
            AndroidKeys.META_ALT_ON to KeyEvent.META_ALT_ON,
            AndroidKeys.META_SHIFT_LEFT_ON to KeyEvent.META_SHIFT_LEFT_ON,
            AndroidKeys.META_ALT_LEFT_ON to KeyEvent.META_ALT_LEFT_ON,
            AndroidKeys.META_CTRL_ON to KeyEvent.META_CTRL_ON,
            AndroidKeys.META_CTRL_LEFT_ON to KeyEvent.META_CTRL_LEFT_ON,
            AndroidKeys.KEYCODE_0 to KeyEvent.KEYCODE_0,
            AndroidKeys.KEYCODE_DPAD_UP to KeyEvent.KEYCODE_DPAD_UP,
            AndroidKeys.KEYCODE_DPAD_DOWN to KeyEvent.KEYCODE_DPAD_DOWN,
            AndroidKeys.KEYCODE_DPAD_LEFT to KeyEvent.KEYCODE_DPAD_LEFT,
            AndroidKeys.KEYCODE_DPAD_RIGHT to KeyEvent.KEYCODE_DPAD_RIGHT,
            AndroidKeys.KEYCODE_A to KeyEvent.KEYCODE_A,
            AndroidKeys.KEYCODE_COMMA to KeyEvent.KEYCODE_COMMA,
            AndroidKeys.KEYCODE_PERIOD to KeyEvent.KEYCODE_PERIOD,
            AndroidKeys.KEYCODE_TAB to KeyEvent.KEYCODE_TAB,
            AndroidKeys.KEYCODE_SPACE to KeyEvent.KEYCODE_SPACE,
            AndroidKeys.KEYCODE_ENTER to KeyEvent.KEYCODE_ENTER,
            AndroidKeys.KEYCODE_DEL to KeyEvent.KEYCODE_DEL,
            AndroidKeys.KEYCODE_GRAVE to KeyEvent.KEYCODE_GRAVE,
            AndroidKeys.KEYCODE_MINUS to KeyEvent.KEYCODE_MINUS,
            AndroidKeys.KEYCODE_EQUALS to KeyEvent.KEYCODE_EQUALS,
            AndroidKeys.KEYCODE_LEFT_BRACKET to KeyEvent.KEYCODE_LEFT_BRACKET,
            AndroidKeys.KEYCODE_RIGHT_BRACKET to KeyEvent.KEYCODE_RIGHT_BRACKET,
            AndroidKeys.KEYCODE_BACKSLASH to KeyEvent.KEYCODE_BACKSLASH,
            AndroidKeys.KEYCODE_SEMICOLON to KeyEvent.KEYCODE_SEMICOLON,
            AndroidKeys.KEYCODE_APOSTROPHE to KeyEvent.KEYCODE_APOSTROPHE,
            AndroidKeys.KEYCODE_SLASH to KeyEvent.KEYCODE_SLASH,
            AndroidKeys.KEYCODE_PAGE_UP to KeyEvent.KEYCODE_PAGE_UP,
            AndroidKeys.KEYCODE_PAGE_DOWN to KeyEvent.KEYCODE_PAGE_DOWN,
            AndroidKeys.KEYCODE_ESCAPE to KeyEvent.KEYCODE_ESCAPE,
            AndroidKeys.KEYCODE_FORWARD_DEL to KeyEvent.KEYCODE_FORWARD_DEL,
            AndroidKeys.KEYCODE_MOVE_HOME to KeyEvent.KEYCODE_MOVE_HOME,
            AndroidKeys.KEYCODE_MOVE_END to KeyEvent.KEYCODE_MOVE_END,
            AndroidKeys.KEYCODE_INSERT to KeyEvent.KEYCODE_INSERT,
            AndroidKeys.KEYCODE_F1 to KeyEvent.KEYCODE_F1,
        )
        for ((ours, platform) in pairs) ours shouldBe platform
    }

    test("letters, digits and function keys are consecutive in the platform (the mapper relies on it)") {
        KeyEvent.KEYCODE_Z - KeyEvent.KEYCODE_A shouldBe 25
        KeyEvent.KEYCODE_9 - KeyEvent.KEYCODE_0 shouldBe 9
        KeyEvent.KEYCODE_F12 - KeyEvent.KEYCODE_F1 shouldBe 11
    }
})
