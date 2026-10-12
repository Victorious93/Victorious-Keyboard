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

package dev.patrickgold.florisboard.ime.developer.ui

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.ime.developer.DevKey
import dev.patrickgold.florisboard.ime.developer.DevKeyAction
import dev.patrickgold.florisboard.ime.developer.DeveloperRows
import dev.patrickgold.florisboard.ime.input.LocalInputFeedbackController
import dev.patrickgold.florisboard.ime.keyboard.FlorisImeSizing
import dev.patrickgold.florisboard.ime.keyboard.KeyboardManager
import dev.patrickgold.florisboard.ime.terminal.ModifierDisplay
import dev.patrickgold.florisboard.ime.terminal.SpecialKey
import dev.patrickgold.florisboard.ime.terminal.TerminalModifier
import dev.patrickgold.florisboard.ime.text.keyboard.TextKeyData
import dev.patrickgold.florisboard.ime.theme.FlorisImeUi
import dev.patrickgold.florisboard.keyboardManager
import dev.patrickgold.jetpref.datastore.model.observeAsState
import org.florisboard.lib.compose.stringRes
import org.florisboard.lib.snygg.SnyggSelector
import org.florisboard.lib.snygg.ui.SnyggBox
import org.florisboard.lib.snygg.ui.SnyggRow
import org.florisboard.lib.snygg.ui.SnyggText

private const val HOLD_MS = 350L
private const val REPEAT_MS = 60L

private val REPEATABLE = setOf(
    SpecialKey.UP, SpecialKey.DOWN, SpecialKey.LEFT, SpecialKey.RIGHT, SpecialKey.PAGE_UP, SpecialKey.PAGE_DOWN,
)

/**
 * Thin Compose shell for developer mode: one horizontally scrollable row of [DevKey]s shown between the Smartbar and the
 * keyboard. All decisions live in the pure `ime.developer` / `ime.terminal` code; this only draws and forwards gestures.
 *
 * Reuses the Smartbar action key styling, so every theme already styles it. Armed Ctrl/Alt are drawn pressed; locked
 * ones are pressed *and* upper-case. Keys fire on release (so scrolling the row never types); arrows repeat while held.
 */
@Composable
fun DeveloperRowBar(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val keyboardManager by context.keyboardManager()
    val prefs by FlorisPreferenceStore

    val profile by prefs.devrow.profile.observeAsState()
    val terminalKeys by prefs.devrow.terminalKeys.observeAsState()
    val modifierState by keyboardManager.terminalModifierState.collectAsState()
    val keys = remember(profile, terminalKeys) { DeveloperRows.row(profile, terminalKeys) }

    SnyggRow(
        elementName = FlorisImeUi.SmartbarSharedActionsRow.elementName,
        modifier = modifier
            .fillMaxWidth()
            .height(FlorisImeSizing.smartbarHeight)
            .horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (key in keys) {
            val action = key.action
            val display = if (action is DevKeyAction.Modifier) modifierState.of(action.modifier) else ModifierDisplay.OFF
            DevKeyButton(key = key, display = display, keyboardManager = keyboardManager)
        }
    }
}

@Composable
private fun DevKeyButton(key: DevKey, display: ModifierDisplay, keyboardManager: KeyboardManager) {
    val inputFeedbackController = LocalInputFeedbackController.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val elementName = FlorisImeUi.SmartbarActionKey.elementName
    val selector = if (isPressed || display != ModifierDisplay.OFF) SnyggSelector.PRESSED else null
    val action = key.action

    // A key that disappears while held (e.g. the preference is switched off) must not leave Ctrl stuck.
    DisposableEffect(key) {
        onDispose {
            if (action is DevKeyAction.Modifier) keyboardManager.terminalModifierCancel(action.modifier)
        }
    }

    val label = if (display == ModifierDisplay.LOCKED) key.label.uppercase() else key.label
    val description = when {
        action is DevKeyAction.Modifier && display == ModifierDisplay.LOCKED -> stringRes(
            if (action.modifier == TerminalModifier.CTRL) R.string.devrow__ctrl_locked else R.string.devrow__alt_locked,
        )
        action is DevKeyAction.Modifier && display == ModifierDisplay.ARMED -> stringRes(
            if (action.modifier == TerminalModifier.CTRL) R.string.devrow__ctrl_armed else R.string.devrow__alt_armed,
        )
        else -> key.description
    }

    SnyggBox(
        elementName = elementName,
        selector = selector,
        modifier = Modifier.height(FlorisImeSizing.smartbarHeight).widthIn(min = FlorisImeSizing.smartbarHeight),
        clickAndSemanticsModifier = Modifier
            .indication(interactionSource, LocalIndication.current)
            .semantics {
                role = Role.Button
                contentDescription = description
            }
            .pointerInput(key) {
                awaitEachGesture {
                    // Not consumed, so a horizontal drag still scrolls the row (that arrives here as a cancellation).
                    val down = awaitFirstDown(requireUnconsumed = true)
                    val press = PressInteraction.Press(down.position)
                    interactionSource.tryEmit(press)
                    inputFeedbackController.keyPress(TextKeyData.UNSPECIFIED)

                    var released = false
                    when {
                        action is DevKeyAction.Modifier -> {
                            keyboardManager.terminalModifierDown(action.modifier)
                            val up = waitForUpOrCancellation()
                            if (up != null) {
                                up.consume()
                                keyboardManager.terminalModifierUp(action.modifier)
                            } else {
                                keyboardManager.terminalModifierCancel(action.modifier)
                            }
                            released = up != null
                        }
                        action is DevKeyAction.Special && action.key in REPEATABLE -> {
                            var tapUp = false
                            // The block always returns Unit, so null can only mean "timed out" (= the key is held).
                            val held = withTimeoutOrNull(HOLD_MS) {
                                val up = waitForUpOrCancellation()
                                if (up != null) {
                                    up.consume()
                                    tapUp = true
                                }
                                Unit
                            } == null
                            if (held) {
                                keyboardManager.performDeveloperKey(action)
                                // Same rule: Unit when the finger lifted or the gesture was cancelled, null on timeout.
                                while (
                                    withTimeoutOrNull(REPEAT_MS) {
                                        waitForUpOrCancellation()?.consume()
                                        Unit
                                    } == null
                                ) {
                                    keyboardManager.performDeveloperKey(action)
                                }
                                released = true
                            } else if (tapUp) {
                                keyboardManager.performDeveloperKey(action)
                                released = true
                            }
                        }
                        else -> {
                            val up = waitForUpOrCancellation()
                            if (up != null) {
                                up.consume()
                                keyboardManager.performDeveloperKey(action)
                                released = true
                            }
                        }
                    }
                    interactionSource.tryEmit(if (released) PressInteraction.Release(press) else PressInteraction.Cancel(press))
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        SnyggText(
            elementName = "$elementName-text",
            selector = selector,
            text = label,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
    }
}
