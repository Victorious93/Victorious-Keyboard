/*
 * Copyright (C) 2021-2025 The FlorisBoard Contributors
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

package dev.patrickgold.florisboard.ime.text

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.ime.developer.CustomSymbols
import dev.patrickgold.florisboard.ime.developer.DeveloperRowTiming
import dev.patrickgold.florisboard.ime.developer.RowOptions
import dev.patrickgold.florisboard.ime.developer.ui.DeveloperRowBar
import dev.patrickgold.florisboard.ime.developer.ui.DeveloperRowStyle
import dev.patrickgold.florisboard.ime.editor.InputAttributes
import dev.patrickgold.florisboard.ime.keyboard.FlorisImeSizing
import dev.patrickgold.florisboard.ime.mode.DeveloperRowPrefs
import dev.patrickgold.florisboard.ime.mode.ModePresets
import dev.patrickgold.florisboard.ime.mode.TerminalRowPolicy
import dev.patrickgold.florisboard.ime.toolbar.ui.ToolsPanel
import dev.patrickgold.florisboard.editorInstance
import dev.patrickgold.florisboard.ime.smartbar.IncognitoDisplayMode
import dev.patrickgold.florisboard.ime.smartbar.InlineSuggestionsStyleCache
import dev.patrickgold.florisboard.ime.clipedit.ui.ClipEditorPanel
import dev.patrickgold.florisboard.ime.ai.ui.LocalAiPanel
import dev.patrickgold.florisboard.ime.smartbar.Smartbar
import dev.patrickgold.florisboard.ime.toolbar.ToolbarRuntime
import dev.patrickgold.florisboard.ime.smartbar.quickaction.QuickActionsOverflowPanel
import dev.patrickgold.florisboard.ime.text.keyboard.TextKeyboardLayout
import dev.patrickgold.florisboard.ime.theme.FlorisImeUi
import dev.patrickgold.florisboard.keyboardManager
import dev.patrickgold.florisboard.localAiManager
import dev.patrickgold.jetpref.datastore.model.observeAsState
import org.florisboard.lib.snygg.ui.SnyggIcon

@Composable
fun TextInputLayout(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val keyboardManager by context.keyboardManager()
    val editorInstance by context.editorInstance()
    val localAi by context.localAiManager()

    val prefs by FlorisPreferenceStore

    val state by keyboardManager.activeState.collectAsState()
    val evaluator by keyboardManager.activeEvaluator.collectAsState()

    InlineSuggestionsStyleCache()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight(),
    ) {
        Smartbar()
        LaunchedEffect(Unit) { ToolbarRuntime.ensure(context) }
        val clipEditor by keyboardManager.clipEditor.collectAsState()
        val aiPanel by localAi.panel.collectAsState()
        if (aiPanel != null) {
            LocalAiPanel()
        }
        if (clipEditor != null) {
            ClipEditorPanel()
        }
        val operatingMode by keyboardManager.activeMode.collectAsState()
        val toolsOpen by keyboardManager.toolsOpen.collectAsState()
        val devEnabled by prefs.devrow.enabled.observeAsState()
        val devProfile by prefs.devrow.profile.observeAsState()
        val devTerminalKeys by prefs.devrow.terminalKeys.observeAsState()
        val devNavigationKeys by prefs.devrow.navigationKeys.observeAsState()
        val devSymbols by prefs.devrow.symbols.observeAsState()
        val terminalKeyRow by prefs.devrow.terminalKeyRow.observeAsState()
        val developerRow = ModePresets.developerRow(
            operatingMode,
            DeveloperRowPrefs(devEnabled, devProfile, devTerminalKeys, devNavigationKeys, devSymbols),
        )
        val showTerminalRow = TerminalRowPolicy.shouldShow(
            terminalKeyRow,
            editorInstance.activeInfo.packageName,
            editorInstance.activeInfo.inputAttributes.type == InputAttributes.Type.NULL,
        )
        if (showTerminalRow && !state.isActionsOverflowVisible && !toolsOpen && aiPanel == null && clipEditor == null) {
            val customText by prefs.devrow.customSymbols.observeAsState()
            val customMode by prefs.devrow.customSymbolsMode.observeAsState()
            val size by prefs.devrow.size.observeAsState()
            val holdMs by prefs.devrow.holdDelayMs.observeAsState()
            val repeatMs by prefs.devrow.repeatIntervalMs.observeAsState()
            val haptics by prefs.devrow.haptics.observeAsState()
            val custom = remember(customText) { CustomSymbols.parse(customText).keys }
            DeveloperRowBar(
                profile = developerRow.profile,
                options = RowOptions(
                    terminalKeys = true,
                    navigationKeys = true,
                    symbols = devSymbols,
                    custom = custom,
                    customMode = customMode,
                ),
                style = DeveloperRowStyle(
                    heightScale = size.scale,
                    holdMs = DeveloperRowTiming.hold(holdMs),
                    repeatMs = DeveloperRowTiming.repeat(repeatMs),
                    haptics = haptics,
                ),
            )
        }
        if (state.isActionsOverflowVisible) {
            QuickActionsOverflowPanel()
        } else if (toolsOpen && aiPanel == null && clipEditor == null) {
            ToolsPanel(
                onAction = { id ->
                    keyboardManager.setToolsOpen(false)
                    keyboardManager.performToolbarAction(id)
                },
                onClose = { keyboardManager.setToolsOpen(false) },
                modifier = Modifier.height(FlorisImeSizing.keyboardUiHeight()),
            )
        } else {
            Box {
                val incognitoDisplayMode by prefs.keyboard.incognitoDisplayMode.observeAsState()
                val showIncognitoIcon = evaluator.state.isIncognitoMode &&
                    incognitoDisplayMode == IncognitoDisplayMode.DISPLAY_BEHIND_KEYBOARD
                if (showIncognitoIcon) {
                    SnyggIcon(
                        FlorisImeUi.IncognitoModeIndicator.elementName,
                        modifier = Modifier
                            .matchParentSize()
                            .align(Alignment.Center),
                        painter = painterResource(R.drawable.ic_incognito),
                    )
                }
                TextKeyboardLayout(evaluator = evaluator)
            }
        }
    }
}
