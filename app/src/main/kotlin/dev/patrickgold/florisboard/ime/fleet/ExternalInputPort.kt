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


package dev.patrickgold.florisboard.ime.fleet

import android.content.Context
import android.view.KeyEvent
import dev.patrickgold.florisboard.FlorisImeService
import dev.patrickgold.florisboard.editorInstance
import dev.patrickgold.florisboard.ime.editor.isPasswordLike
import dev.patrickgold.florisboard.lib.fleet.BridgeInput

/**
 * Android side of [BridgeInput.Port]: types into, and presses keys in, the field the keyboard is attached to. It
 * never reads the field's contents and never logs what it sends. Call it only on the main thread, like the bridge
 * service's handler does.
 */
class ExternalInputPort(context: Context) : BridgeInput.Port {
    private val editorInstance by context.editorInstance()

    override fun imeRunning(): Boolean = FlorisImeService.isRunning()

    override fun field(): BridgeInput.FieldState {
        val up = FlorisImeService.isRunning()
        val hasField = up && FlorisImeService.currentInputConnection() != null
        // Same password test the rest of the keyboard uses. EXTRA_IS_SENSITIVE describes copied items, not editors,
        // so it has no counterpart here.
        val sensitive = hasField && editorInstance.activeInfo.inputAttributes.isPasswordLike()
        return BridgeInput.FieldState(keyboardUp = up, hasField = hasField, sensitive = sensitive)
    }

    override fun perform(action: BridgeInput.Action): Boolean = when (action) {
        is BridgeInput.Action.Text -> editorInstance.commitText(action.text)
        is BridgeInput.Action.Press -> {
            val code = action.key?.let(::keyCode) ?: action.letter?.let { KeyEvent.KEYCODE_A + (it - 'a') }
            // The meta state is explicit: it must not pick up the on-screen Ctrl/Shift the user may be holding.
            val meta = if (action.ctrl) KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON else 0
            code != null && editorInstance.sendDownUpKeyEvent(code, meta, action.count)
        }
    }

    private fun keyCode(key: BridgeInput.Key): Int = when (key) {
        BridgeInput.Key.ENTER -> KeyEvent.KEYCODE_ENTER
        BridgeInput.Key.TAB -> KeyEvent.KEYCODE_TAB
        BridgeInput.Key.UP -> KeyEvent.KEYCODE_DPAD_UP
        BridgeInput.Key.DOWN -> KeyEvent.KEYCODE_DPAD_DOWN
        BridgeInput.Key.LEFT -> KeyEvent.KEYCODE_DPAD_LEFT
        BridgeInput.Key.RIGHT -> KeyEvent.KEYCODE_DPAD_RIGHT
    }
}
