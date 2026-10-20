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

package com.victorious.keyboard.ime.clipedit

import com.victorious.keyboard.ime.text.key.KeyCode

/**
 * Decides which hardware and on-screen keys edit the in-keyboard buffer instead of the host field.
 * Shift, keyboard-mode changes, and settings keys are left alone so the layout can still change.
 */
object EditorKeyRouter {
    fun consumes(code: Int, character: Boolean): Boolean {
        if (character) return true
        return code in CONSUMED
    }

    fun apply(buffer: EditorBuffer, input: EditorKeyInput): EditorBuffer {
        val shift = input.shift
        return when (input.code) {
            KeyCode.DELETE -> buffer.deleteBackward(words = shift)
            KeyCode.DELETE_WORD -> buffer.deleteBackward(words = true)
            KeyCode.FORWARD_DELETE -> buffer.deleteForward(words = shift)
            KeyCode.FORWARD_DELETE_WORD -> buffer.deleteForward(words = true)
            KeyCode.ARROW_LEFT -> buffer.moveHorizontal(-1, words = shift, extend = input.extend)
            KeyCode.ARROW_RIGHT -> buffer.moveHorizontal(1, words = shift, extend = input.extend)
            KeyCode.ARROW_UP -> buffer.moveVertical(-1, extend = input.extend)
            KeyCode.ARROW_DOWN -> buffer.moveVertical(1, extend = input.extend)
            KeyCode.MOVE_START_OF_LINE, KeyCode.MOVE_START_OF_PAGE -> buffer.moveLineEdge(toEnd = false, extend = input.extend)
            KeyCode.MOVE_END_OF_LINE, KeyCode.MOVE_END_OF_PAGE -> buffer.moveLineEdge(toEnd = true, extend = input.extend)
            KeyCode.UNDO -> buffer.undo()
            KeyCode.REDO -> buffer.redo()
            KeyCode.CLIPBOARD_SELECT_ALL -> buffer.selectAll()
            KeyCode.ENTER -> if (input.singleLine) buffer else buffer.insert("\n")
            KeyCode.TAB -> buffer.insert("\t")
            KeyCode.SPACE -> buffer.insert(input.text.ifEmpty { " " })
            else -> {
                if (!input.character || input.text.isEmpty()) buffer
                else buffer.insert(paired(input))
            }
        }
    }

    private fun paired(input: EditorKeyInput): String {
        if (!input.pairBrackets || input.text.length != 1) return input.text
        val closer = when (input.text) {
            "(" -> ")"
            "[" -> "]"
            "{" -> "}"
            "\"" -> "\""
            "'" -> "'"
            else -> null
        } ?: return input.text
        return input.text + closer
    }

    /** After an auto-paired insert the caret sits between the two characters. */
    fun caretAfterPair(before: EditorBuffer, after: EditorBuffer, input: EditorKeyInput): EditorBuffer {
        if (!input.pairBrackets || input.text.length != 1) return after
        if (after.text.length != before.text.length - before.selectedText.length + 2) return after
        val closer = paired(input)
        if (closer.length != 2) return after
        val caret = (after.end - 1).coerceAtLeast(0)
        return after.copy(start = caret, end = caret)
    }

    private val CONSUMED = setOf(
        KeyCode.DELETE,
        KeyCode.DELETE_WORD,
        KeyCode.FORWARD_DELETE,
        KeyCode.FORWARD_DELETE_WORD,
        KeyCode.ARROW_LEFT,
        KeyCode.ARROW_RIGHT,
        KeyCode.ARROW_UP,
        KeyCode.ARROW_DOWN,
        KeyCode.MOVE_START_OF_LINE,
        KeyCode.MOVE_END_OF_LINE,
        KeyCode.MOVE_START_OF_PAGE,
        KeyCode.MOVE_END_OF_PAGE,
        KeyCode.UNDO,
        KeyCode.REDO,
        KeyCode.CLIPBOARD_SELECT_ALL,
        KeyCode.ENTER,
        KeyCode.TAB,
        KeyCode.SPACE,
        KeyCode.ESCAPE,
        KeyCode.CTRL,
        KeyCode.ALT,
        KeyCode.CTRL_LOCK,
        KeyCode.ALT_LOCK,
        KeyCode.CLIPBOARD_COPY,
        KeyCode.CLIPBOARD_CUT,
        KeyCode.CLIPBOARD_PASTE,
    )
}

data class EditorKeyInput(
    val code: Int,
    val character: Boolean,
    val text: String = "",
    val shift: Boolean = false,
    val extend: Boolean = false,
    val singleLine: Boolean = false,
    val pairBrackets: Boolean = false,
)
