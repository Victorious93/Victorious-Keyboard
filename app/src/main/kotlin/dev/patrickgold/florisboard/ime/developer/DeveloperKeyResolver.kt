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

import dev.patrickgold.florisboard.ime.terminal.Modifiers
import dev.patrickgold.florisboard.ime.terminal.SpecialKey
import dev.patrickgold.florisboard.ime.terminal.TerminalAction
import dev.patrickgold.florisboard.ime.terminal.TerminalKeyMapper

/** One thing the input layer has to do, in order. */
sealed interface DevStep {
    /** Send a real down+up `KeyEvent`. */
    data class Key(val keyCode: Int, val metaState: Int) : DevStep

    /** Commit text (no modifier applies). */
    data class Text(val text: String) : DevStep

    /** Move the cursor [count] characters to the left (used to land between a pair such as `()`). */
    data class CursorLeft(val count: Int) : DevStep
}

/** [consumesModifiers]: one-shot Ctrl/Alt were used by a key event and must be spent. */
data class DevResolution(val steps: List<DevStep>, val consumesModifiers: Boolean)

/**
 * Pure decision: what does tapping a developer-row key do under the current modifiers?
 *
 * * [DevKeyAction.Modifier] keys are not resolved here (they are press/release gestures on the state machine).
 * * [DevKeyAction.Special] always sends a key event carrying the modifiers.
 * * A single-character [DevKeyAction.Insert] becomes a key event while Ctrl/Alt is active (so Ctrl+`\` works) and is
 *   committed as text when no modifier is active or the character has no key; multi-character inserts (pairs,
 *   `=>`, code fences) are always committed as text, followed by moving the cursor into the pair.
 * * One-shot modifiers are spent only when a key event was actually sent.
 */
object DeveloperKeyResolver {
    fun resolve(action: DevKeyAction, mods: Modifiers): DevResolution = when (action) {
        is DevKeyAction.Modifier -> DevResolution(emptyList(), false)
        is DevKeyAction.Special -> {
            val key = TerminalKeyMapper.mapSpecial(action.key, mods) as TerminalAction.Key
            DevResolution(listOf(DevStep.Key(key.keyCode, key.metaState)), true)
        }
        is DevKeyAction.Insert -> resolveInsert(action, mods)
        is DevKeyAction.Play -> resolvePlay(action)
    }

    private fun resolvePlay(action: DevKeyAction.Play): DevResolution {
        val steps = action.steps.flatMap { step ->
            when (step) {
                is MacroStep.Text -> listOf(DevStep.Text(step.text))
                MacroStep.Enter -> {
                    val key = TerminalKeyMapper.mapSpecial(SpecialKey.ENTER, Modifiers()) as TerminalAction.Key
                    listOf(DevStep.Key(key.keyCode, key.metaState))
                }
                is MacroStep.Ctrl -> {
                    val letter = step.letter.lowercaseChar()
                    when (val mapped = TerminalKeyMapper.mapChar(letter.toString(), Modifiers(ctrl = true))) {
                        is TerminalAction.Key -> listOf(DevStep.Key(mapped.keyCode, mapped.metaState))
                        is TerminalAction.Text -> listOf(DevStep.Text(mapped.text))
                    }
                }
            }
        }
        return DevResolution(steps, consumesModifiers = false)
    }

    private fun resolveInsert(action: DevKeyAction.Insert, mods: Modifiers): DevResolution {
        val single = action.text.isNotEmpty() && action.text.codePointCount(0, action.text.length) == 1
        if (single && mods.any) {
            val mapped = TerminalKeyMapper.mapChar(action.text, mods)
            if (mapped is TerminalAction.Key) {
                return DevResolution(listOf(DevStep.Key(mapped.keyCode, mapped.metaState)), true)
            }
        }
        val steps = buildList {
            add(DevStep.Text(action.text))
            if (action.cursorBack > 0) add(DevStep.CursorLeft(action.cursorBack))
        }
        return DevResolution(steps, false)
    }
}
