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

package com.victorious.keyboard.ime.text

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import com.victorious.keyboard.R
import com.victorious.keyboard.app.FlorisPreferenceStore
import com.victorious.keyboard.ime.developer.DeveloperRowTiming
import com.victorious.keyboard.ime.developer.ui.DeveloperRowBar
import com.victorious.keyboard.ime.developer.ui.DeveloperRowStyle
import com.victorious.keyboard.ime.developer.ui.rememberDeveloperRowExtraHeight
import com.victorious.keyboard.ime.developer.ui.rememberTerminalRowPlan
import com.victorious.keyboard.ime.keyboard.FlorisImeSizing
import com.victorious.keyboard.ime.toolbar.ui.ToolsPanel
import com.victorious.keyboard.ime.smartbar.IncognitoDisplayMode
import com.victorious.keyboard.ime.smartbar.InlineSuggestionsStyleCache
import com.victorious.keyboard.ime.clipedit.ui.ClipEditorPanel
import com.victorious.keyboard.ime.ai.ui.LocalAiPanel
import com.victorious.keyboard.ime.toolbar.ui.CommandHistoryPanel
import com.victorious.keyboard.ime.toolbar.ui.SelectionToolbar
import com.victorious.keyboard.ime.toolbar.ui.TextToolsPanel
import com.victorious.keyboard.ime.smartbar.Smartbar
import com.victorious.keyboard.ime.toolbar.ToolbarRuntime
import com.victorious.keyboard.ime.smartbar.quickaction.QuickActionsOverflowPanel
import com.victorious.keyboard.ime.text.keyboard.TextKeyboardLayout
import com.victorious.keyboard.ime.theme.FlorisImeUi
import com.victorious.keyboard.keyboardManager
import com.victorious.keyboard.localAiManager
import dev.patrickgold.jetpref.datastore.model.observeAsState
import org.florisboard.lib.snygg.ui.SnyggIcon

@Composable
fun TextInputLayout(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val keyboardManager by context.keyboardManager()
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
        val textTools by keyboardManager.textTools.collectAsState()
        val commandHistory by keyboardManager.commandHistory.collectAsState()
        val selectionBar by keyboardManager.selectionBar.collectAsState()
        if (selectionBar) {
            SelectionToolbar()
        }
        if (aiPanel != null) {
            LocalAiPanel()
        }
        if (textTools != null && aiPanel == null) {
            TextToolsPanel()
        }
        if (commandHistory != null && aiPanel == null && textTools == null) {
            CommandHistoryPanel()
        }
        if (clipEditor != null) {
            ClipEditorPanel()
        }
        val toolsOpen by keyboardManager.toolsOpen.collectAsState()
        val mediaOpen by keyboardManager.mediaOpen.collectAsState()
        val terminalRow = rememberTerminalRowPlan()
        if (terminalRow.visible && !state.isActionsOverflowVisible && !toolsOpen && !mediaOpen && aiPanel == null && textTools == null && commandHistory == null && clipEditor == null) {
            val size by prefs.devrow.size.observeAsState()
            val holdMs by prefs.devrow.holdDelayMs.observeAsState()
            val repeatMs by prefs.devrow.repeatIntervalMs.observeAsState()
            val haptics by prefs.devrow.haptics.observeAsState()
            DeveloperRowBar(
                profile = terminalRow.profile,
                options = terminalRow.options,
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
        } else if (mediaOpen && aiPanel == null && clipEditor == null) {
            com.victorious.keyboard.ime.media.ui.MediaPanel(
                modifier = Modifier.height(FlorisImeSizing.keyboardUiHeight() + rememberDeveloperRowExtraHeight()),
            )
        } else if (toolsOpen && aiPanel == null && clipEditor == null) {
            // The terminal row is hidden while the panel is open; its height goes to the panel so the window keeps
            // its size and the app behind the keyboard does not jump.
            ToolsPanel(
                modifier = Modifier.height(FlorisImeSizing.keyboardUiHeight() + rememberDeveloperRowExtraHeight()),
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
