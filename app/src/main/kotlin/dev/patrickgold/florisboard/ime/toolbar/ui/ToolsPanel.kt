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

package dev.patrickgold.florisboard.ime.toolbar.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.patrickgold.florisboard.FlorisImeService
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.ime.input.LocalInputFeedbackController
import dev.patrickgold.florisboard.ime.keyboard.FlorisImeSizing
import dev.patrickgold.florisboard.ime.mode.TerminalKeyRow
import dev.patrickgold.florisboard.ime.text.keyboard.TextKeyData
import dev.patrickgold.florisboard.ime.theme.FlorisImeUi
import dev.patrickgold.florisboard.ime.screenshot.ScreenshotPolicy
import dev.patrickgold.florisboard.ime.toolbar.SavedCommand
import dev.patrickgold.florisboard.ime.toolbar.ToolbarActionId
import dev.patrickgold.florisboard.ime.toolbar.ToolbarCatalog
import dev.patrickgold.florisboard.ime.toolbar.ToolTileFit
import dev.patrickgold.florisboard.ime.toolbar.ToolbarRuntime
import dev.patrickgold.florisboard.ime.toolbar.ToolsCatalog
import dev.patrickgold.florisboard.ime.toolbar.ToolsTab
import dev.patrickgold.florisboard.keyboardManager
import dev.patrickgold.jetpref.datastore.model.observeAsState
import org.florisboard.lib.compose.stringRes
import org.florisboard.lib.snygg.ui.SnyggBox
import org.florisboard.lib.snygg.ui.SnyggChip
import org.florisboard.lib.snygg.ui.SnyggIcon
import org.florisboard.lib.snygg.ui.SnyggIconButton
import org.florisboard.lib.snygg.ui.SnyggText

/** Tiles per visible screen of a tab; taller panels get taller tiles, never more rows. */
private const val VisibleRows = 3

/**
 * Replaces the key area: a row of tabs, then a grid of icon tiles for the selected tab. Everything is drawn with the
 * keyboard theme's own elements (the quick-action tile, the clipboard filter chip), so every theme and the keyboard
 * colors apply to it. Nothing in this panel runs a shell command; saved commands are inserted as text.
 */
@Composable
fun ToolsPanel(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val keyboardManager by context.keyboardManager()
    val prefs by FlorisPreferenceStore
    LaunchedEffect(Unit) { ToolbarRuntime.ensure(context) }
    val config by ToolbarRuntime.config.collectAsState()
    val tab by keyboardManager.toolsTab.collectAsState()
    val imeState by keyboardManager.activeState.collectAsState()
    val learning by prefs.suggestion.learnFromTyping.observeAsState()
    val keyRow by prefs.devrow.terminalKeyRow.observeAsState()
    val bottomGap by prefs.keyboard.bottomGapDp.observeAsState()
    val showScreenshot by prefs.tools.screenshotTile.observeAsState()

    val actions = remember(tab, config, showScreenshot) {
        val hidden = ScreenshotPolicy.panelHidden(config.hiddenSet(), showScreenshot)
        ToolsCatalog.visible(tab, hidden, config.tabOrder(tab))
    }
    val commands = remember(tab, config) {
        if (tab != ToolsTab.TERMINAL) {
            emptyList()
        } else {
            config.extraCommands().filter { ToolbarCatalog.commandId(it.id) !in config.hiddenSet() }
        }
    }
    val on = stringRes(R.string.tools__state__on)
    val off = stringRes(R.string.tools__state__off)
    val keyRowState = when (keyRow) {
        TerminalKeyRow.AUTO -> stringRes(R.string.enum__terminal_key_row__auto)
        TerminalKeyRow.ALWAYS -> stringRes(R.string.enum__terminal_key_row__always)
        TerminalKeyRow.NEVER -> stringRes(R.string.enum__terminal_key_row__never)
    }
    val stateOf: (ToolbarActionId) -> String? = { id ->
        when (id) {
            ToolbarActionId.INCOGNITO -> if (imeState.isIncognitoMode) on else off
            ToolbarActionId.LEARNING -> if (learning) on else off
            ToolbarActionId.DEVROW -> keyRowState
            ToolbarActionId.BOTTOM_GAP -> "$bottomGap dp"
            else -> null
        }
    }

    ToolsPanelContent(
        tab = tab,
        actions = actions,
        commands = commands,
        stateOf = stateOf,
        onTab = { keyboardManager.setToolsTab(it) },
        onAction = { id ->
            keyboardManager.setToolsOpen(false)
            keyboardManager.performToolbarAction(id)
        },
        onCommand = { command ->
            keyboardManager.setToolsOpen(false)
            keyboardManager.insertSavedCommand(command.text)
        },
        onCustomize = { FlorisImeService.launchSettingsAt("settings/smartbar/toolbar") },
        onClose = { keyboardManager.setToolsOpen(false) },
        modifier = modifier,
    )
}

/** Stateless body of [ToolsPanel], so it can be drawn without a running keyboard. */
@Composable
fun ToolsPanelContent(
    tab: ToolsTab,
    actions: List<ToolbarActionId>,
    commands: List<SavedCommand>,
    stateOf: (ToolbarActionId) -> String?,
    onTab: (ToolsTab) -> Unit,
    onAction: (ToolbarActionId) -> Unit,
    onCommand: (SavedCommand) -> Unit,
    onCustomize: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SnyggBox(
        elementName = FlorisImeUi.SmartbarActionsOverflow.elementName,
        modifier = modifier.fillMaxWidth(),
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val headerHeight = FlorisImeSizing.smartbarHeight
            val tileHeight: Dp = ((maxHeight - headerHeight) / VisibleRows).coerceIn(52.dp, 88.dp)
            Column(modifier = Modifier.fillMaxSize()) {
                ToolsHeader(tab, headerHeight, onTab, onCustomize, onClose)
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 72.dp),
                    modifier = Modifier.fillMaxWidth().weight(1f),
                ) {
                    items(actions, key = { it.name }) { id ->
                        val item = ToolsPanelItems.item(id) ?: return@items
                        val label = stringRes(item.label)
                        ToolTile(
                            glyph = item.glyph,
                            label = label,
                            state = stateOf(id),
                            height = tileHeight,
                            onClick = { onAction(id) },
                        )
                    }
                    if (actions.isEmpty() && commands.isEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            SnyggText(
                                elementName = FlorisImeUi.SmartbarActionsEditorSubheader.elementName,
                                text = stringRes(R.string.tools__empty_tab),
                                modifier = Modifier.fillMaxWidth().clickable(onClick = onCustomize),
                            )
                        }
                    }
                    if (commands.isNotEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            SnyggText(
                                elementName = FlorisImeUi.SmartbarActionsEditorSubheader.elementName,
                                text = stringRes(R.string.tools__my_commands),
                            )
                        }
                        items(commands, key = { "cmd:" + it.id }) { command ->
                            ToolTile(
                                glyph = ToolGlyph.Vector(Icons.Outlined.Terminal),
                                label = command.label,
                                state = null,
                                height = tileHeight,
                                onClick = { onCommand(command) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolsHeader(
    tab: ToolsTab,
    height: Dp,
    onTab: (ToolsTab) -> Unit,
    onCustomize: () -> Unit,
    onClose: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(height),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            for (entry in ToolsCatalog.tabs.keys) {
                SnyggChip(
                    elementName = FlorisImeUi.ClipboardFilterChip.elementName,
                    attributes = mapOf("state" to if (entry == tab) "active" else "inactive"),
                    onClick = { onTab(entry) },
                    text = stringRes(ToolsPanelItems.tabLabel(entry)),
                )
            }
        }
        HeaderButton(Icons.Outlined.Tune, stringRes(R.string.settings__toolbar__title), height, onCustomize)
        HeaderButton(Icons.Outlined.Keyboard, stringRes(R.string.tools__back_to_keyboard), height, onClose)
    }
}

@Composable
private fun HeaderButton(
    imageVector: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    size: Dp,
    onClick: () -> Unit,
) {
    SnyggIconButton(
        elementName = FlorisImeUi.ClipboardHeaderButton.elementName,
        onClick = onClick,
        modifier = Modifier.sizeIn(maxHeight = size).aspectRatio(1f),
    ) {
        SnyggIcon(imageVector = imageVector, contentDescription = description)
    }
}

@Composable
private fun ToolTile(
    glyph: ToolGlyph,
    label: String,
    state: String?,
    height: Dp,
    onClick: () -> Unit,
) {
    val inputFeedbackController = LocalInputFeedbackController.current
    val elementName = FlorisImeUi.SmartbarActionTile.elementName
    val description = if (state == null) label else "$label, $state"
    SnyggBox(
        elementName = elementName,
        modifier = Modifier.fillMaxWidth().height(height),
        clickAndSemanticsModifier = Modifier
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = description
            }
            .clickable {
                inputFeedbackController.keyPress(TextKeyData.UNSPECIFIED)
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            when (glyph) {
                is ToolGlyph.Vector -> SnyggBox(elementName = "$elementName-icon") {
                    SnyggIcon(imageVector = glyph.imageVector)
                }
                is ToolGlyph.Drawable -> SnyggBox(elementName = "$elementName-icon") {
                    SnyggIcon(painter = painterResource(glyph.id))
                }
                is ToolGlyph.Text -> SnyggText(
                    elementName = "$elementName-text",
                    text = glyph.text,
                    fontSize = 18.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                    softWrap = false,
                )
            }
            SnyggText(
                elementName = "$elementName-text",
                text = if (state == null) label else "$label · $state",
                fontSize = 12.sp,
                maxLines = ToolTileFit.labelLines(height.value),
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
