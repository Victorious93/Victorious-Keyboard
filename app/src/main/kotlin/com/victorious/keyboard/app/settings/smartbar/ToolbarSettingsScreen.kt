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

package com.victorious.keyboard.app.settings.smartbar

import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.victorious.keyboard.R
import com.victorious.keyboard.app.FlorisPreferenceStore
import com.victorious.keyboard.ime.screenshot.ScreenshotPolicy
import com.victorious.keyboard.ime.slash.CustomSlashCommand
import com.victorious.keyboard.ime.toolbar.KnownTool
import com.victorious.keyboard.ime.toolbar.PinnedToolbar
import com.victorious.keyboard.ime.toolbar.SavedCommandDto
import com.victorious.keyboard.ime.toolbar.ToolbarActionId
import com.victorious.keyboard.ime.toolbar.ToolRef
import com.victorious.keyboard.ime.toolbar.ToolsLayout
import com.victorious.keyboard.slashCommands
import dev.patrickgold.jetpref.datastore.model.observeAsState
import com.victorious.keyboard.ime.toolbar.SavedCommandRules
import com.victorious.keyboard.ime.toolbar.ToolbarCatalog
import com.victorious.keyboard.ime.toolbar.ToolbarRuntime
import com.victorious.keyboard.ime.toolbar.ToolsCatalog
import com.victorious.keyboard.ime.toolbar.ToolsTab
import com.victorious.keyboard.ime.toolbar.ui.ToolsPanelItems
import com.victorious.keyboard.lib.compose.FlorisScreen
import org.florisboard.lib.compose.stringRes

/**
 * Settings for the tools panel: clip-editor switches, the user's own commands (shown on the Terminal tab, inserted as
 * text and never run), and which buttons each tab shows and in what order.
 */
@Composable
fun ToolbarSettingsScreen() = FlorisScreen {
    title = stringRes(R.string.settings__toolbar__title)
    previewFieldVisible = true
    content {
        val context = LocalContext.current
        val prefs by FlorisPreferenceStore
        val showScreenshot by prefs.tools.screenshotTile.observeAsState()
        val slashManager by context.slashCommands()
        val slashItems by slashManager.items.collectAsState()
        LaunchedEffect(Unit) { ToolbarRuntime.ensure(context) }
        val config by ToolbarRuntime.config.collectAsState()
        Text(
            text = stringRes(R.string.settings__toolbar__summary),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )

        GroupTitle(stringRes(R.string.settings__toolbar__group_row))
        Text(
            text = stringRes(R.string.settings__toolbar__row_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        ToggleRow(stringRes(R.string.settings__toolbar__row_on), config.pinnedRow) {
            ToolbarRuntime.update(context) { it.copy(pinnedRow = !it.pinnedRow) }
        }
        val pinnedRefs = PinnedToolbar.tokens(config.pinned)
        if (pinnedRefs.isEmpty()) {
            Text(
                text = stringRes(R.string.settings__toolbar__row_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
        for ((index, ref) in pinnedRefs.withIndex()) {
            ItemRow(text = toolLabel(ref, slashItems, config.knownFleet), hidden = false) {
                DragHandle(enabled = pinnedRefs.size > 1) { delta ->
                    ToolbarRuntime.update(context) {
                        if (delta < 0) it.pinnedEarlierRef(ref) else it.pinnedLaterRef(ref)
                    }
                }
                TextButton(
                    onClick = { ToolbarRuntime.update(context) { it.pinnedEarlierRef(ref) } },
                    enabled = index > 0,
                ) {
                    Text(stringRes(R.string.settings__toolbar__earlier))
                }
                TextButton(
                    onClick = { ToolbarRuntime.update(context) { it.pinnedLaterRef(ref) } },
                    enabled = index < pinnedRefs.lastIndex,
                ) {
                    Text(stringRes(R.string.settings__toolbar__later))
                }
                TextButton(onClick = { ToolbarRuntime.update(context) { it.unpinningRef(ref) } }) {
                    Text(stringRes(R.string.settings__toolbar__unpin))
                }
            }
        }
        TextButton(
            onClick = { ToolbarRuntime.update(context) { it.withDefaultPinned() } },
            modifier = Modifier.padding(horizontal = 8.dp),
        ) {
            Text(stringRes(R.string.settings__toolbar__row_restore))
        }
        val candidates = PinnedToolbar.candidates(config.pinned, showScreenshot)
        val full = pinnedRefs.size >= PinnedToolbar.MAX
        Text(
            text = if (full) {
                stringRes(R.string.settings__toolbar__row_full, "n" to PinnedToolbar.MAX)
            } else {
                stringRes(R.string.settings__toolbar__row_add)
            },
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp),
        )
        for (tab in ToolsTab.entries) {
            val inTab = candidates.filter { ToolsCatalog.tabOf(it) == tab }
            if (inTab.isEmpty()) continue
            Text(
                text = stringRes(ToolsPanelItems.tabLabel(tab)),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, top = 8.dp),
            )
            for (id in inTab) {
                val name = ToolsPanelItems.item(id)?.let { stringRes(it.label) } ?: ToolsCatalog.label(id)
                ItemRow(text = name, hidden = false) {
                    TextButton(
                        onClick = { ToolbarRuntime.update(context) { it.pinning(id) } },
                        enabled = !full,
                    ) {
                        Text(stringRes(R.string.settings__toolbar__pin))
                    }
                }
            }
        }

        GroupTitle(stringRes(R.string.settings__toolbar__group_editor))
        ToggleRow(stringRes(R.string.settings__toolbar__shell_history), config.shellHistoryEnabled) {
            ToolbarRuntime.update(context) { it.copy(shellHistoryEnabled = !it.shellHistoryEnabled) }
        }
        ToggleRow(stringRes(R.string.settings__toolbar__bracket_pairing), config.bracketPairing) {
            ToolbarRuntime.update(context) { it.copy(bracketPairing = !it.bracketPairing) }
        }
        TextButton(
            onClick = { ToolbarRuntime.update(context) { it.copy(recentCommands = emptyList()) } },
            modifier = Modifier.padding(horizontal = 8.dp),
        ) {
            Text(stringRes(R.string.settings__toolbar__clear_history))
        }

        GroupTitle(stringRes(R.string.settings__toolbar__group_commands))
        for (command in config.extraCommands()) {
            val hiddenId = ToolbarCatalog.commandId(command.id)
            val hidden = hiddenId in config.hiddenSet()
            ItemRow(
                text = command.label + "  ·  " + command.text.lineSequence().first(),
                hidden = hidden,
            ) {
                TextButton(onClick = { ToolbarRuntime.update(context) { it.hiding(hiddenId) } }) {
                    Text(if (hidden) stringRes(R.string.settings__toolbar__show) else stringRes(R.string.settings__toolbar__hide))
                }
                TextButton(onClick = {
                    ToolbarRuntime.update(context) { current ->
                        current.copy(
                            commands = current.commands.filter { it.id != command.id },
                            hidden = current.hidden - hiddenId,
                        )
                    }
                }) {
                    Text(stringRes(R.string.settings__toolbar__delete))
                }
            }
        }
        var label by remember { mutableStateOf("") }
        var command by remember { mutableStateOf("") }
        var tag by remember { mutableStateOf("") }
        OutlinedTextField(
            value = label,
            onValueChange = { label = it },
            label = { Text(stringRes(R.string.settings__toolbar__label)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        )
        OutlinedTextField(
            value = command,
            onValueChange = { command = it },
            label = { Text(stringRes(R.string.settings__toolbar__command)) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        )
        OutlinedTextField(
            value = tag,
            onValueChange = { tag = it },
            label = { Text(stringRes(R.string.settings__toolbar__tag)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        )
        TextButton(
            onClick = {
                val next = SavedCommandRules.normalize(label, command, tag) ?: return@TextButton
                ToolbarRuntime.update(context) { current ->
                    if (current.commands.size >= SavedCommandRules.MAX_COMMANDS || current.commands.any { it.id == next.id }) {
                        current
                    } else {
                        current.copy(commands = current.commands + SavedCommandDto(next.id, next.label, next.text, next.tag))
                    }
                }
                label = ""
                command = ""
                tag = ""
            },
            modifier = Modifier.padding(horizontal = 8.dp),
        ) {
            Text(stringRes(R.string.settings__toolbar__add))
        }

        GroupTitle(stringRes(R.string.settings__toolbar__group_layout))
        LayoutChoice(
            selected = config.tileSize,
            options = listOf(
                ToolsLayout.FIT to stringRes(R.string.settings__toolbar__size_fit),
                ToolsLayout.SMALL to stringRes(R.string.settings__toolbar__size_small),
                ToolsLayout.MEDIUM to stringRes(R.string.settings__toolbar__size_medium),
                ToolsLayout.LARGE to stringRes(R.string.settings__toolbar__size_large),
            ),
            onPick = { name -> ToolbarRuntime.update(context) { it.withTileSize(name) } },
        )
        LayoutChoice(
            selected = config.columns.toString(),
            options = listOf(
                "0" to stringRes(R.string.settings__toolbar__columns_fit),
                "2" to stringRes(R.string.settings__toolbar__columns_n, "n" to 2),
                "3" to stringRes(R.string.settings__toolbar__columns_n, "n" to 3),
                "4" to stringRes(R.string.settings__toolbar__columns_n, "n" to 4),
                "5" to stringRes(R.string.settings__toolbar__columns_n, "n" to 5),
            ),
            onPick = { name -> ToolbarRuntime.update(context) { it.withColumns(name.toInt()) } },
        )
        TextButton(
            onClick = { ToolbarRuntime.update(context) { it.resetLayout() } },
            modifier = Modifier.padding(horizontal = 8.dp),
        ) {
            Text(stringRes(R.string.settings__toolbar__reset))
        }

        GroupTitle(stringRes(R.string.settings__toolbar__group_extra))
        Text(
            text = stringRes(R.string.settings__toolbar__extra_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        val extraShown = ToolsLayout.visibleExtras(config.extraTiles, config.hiddenSet())
        val extraOffers = extraOffers(slashItems, config.knownFleet)
        if (extraOffers.isEmpty() && extraShown.isEmpty()) {
            Text(
                text = stringRes(R.string.settings__toolbar__extra_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
        for ((index, ref) in extraShown.withIndex()) {
            ItemRow(text = toolLabel(ref, slashItems, config.knownFleet), hidden = false) {
                DragHandle(enabled = extraShown.size > 1) { delta ->
                    ToolbarRuntime.update(context) { current ->
                        val from = ToolsLayout.visibleExtras(current.extraTiles, current.hiddenSet()).indexOf(ref)
                        if (from < 0) current else current.movingExtra(from, from + delta)
                    }
                }
                TextButton(
                    onClick = { ToolbarRuntime.update(context) { it.movingExtra(index, index - 1) } },
                    enabled = index > 0,
                ) {
                    Text(stringRes(R.string.settings__toolbar__earlier))
                }
                TextButton(
                    onClick = { ToolbarRuntime.update(context) { it.movingExtra(index, index + 1) } },
                    enabled = index < extraShown.lastIndex,
                ) {
                    Text(stringRes(R.string.settings__toolbar__later))
                }
                TextButton(onClick = { ToolbarRuntime.update(context) { it.hiding(ref) } }) {
                    Text(stringRes(R.string.settings__toolbar__hide))
                }
            }
        }
        for ((ref, name) in extraOffers) {
            if (ref in extraShown) continue
            val pinned = ref in pinnedRefs
            ItemRow(text = name, hidden = false) {
                TextButton(onClick = { ToolbarRuntime.update(context) { it.showingExtra(ref) } }) {
                    Text(stringRes(R.string.settings__toolbar__add_tile))
                }
                TextButton(
                    onClick = {
                        ToolbarRuntime.update(context) { current ->
                            if (ref in PinnedToolbar.tokens(current.pinned)) current.unpinningRef(ref) else current.pinningRef(ref)
                        }
                    },
                    enabled = pinned || !full,
                ) {
                    Text(if (pinned) stringRes(R.string.settings__toolbar__unpin) else stringRes(R.string.settings__toolbar__pin))
                }
            }
        }

        GroupTitle(stringRes(R.string.settings__toolbar__group_buttons))
        TextButton(
            onClick = { ToolbarRuntime.update(context) { it.withDefaultTools() } },
            modifier = Modifier.padding(horizontal = 8.dp),
        ) {
            Text(stringRes(R.string.settings__toolbar__restore))
        }
        for (tab in ToolsTab.entries) {
            Text(
                text = stringRes(ToolsPanelItems.tabLabel(tab)),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp),
            )
            val hiddenNames = ScreenshotPolicy.panelHidden(config.hiddenSet(), showScreenshot)
            val shown = ToolsCatalog.visible(tab, hiddenNames, config.tabOrder(tab))
            val listed = shown + ToolsCatalog.actions(tab).filter { it !in shown }
            for (id in listed) {
                if (id == ToolbarActionId.SCREENSHOT && !showScreenshot) continue
                val hidden = id !in shown
                val name = ToolsPanelItems.item(id)?.let { stringRes(it.label) } ?: ToolsCatalog.label(id)
                val place = shown.indexOf(id)
                ItemRow(text = name, hidden = hidden) {
                    DragHandle(enabled = !hidden && shown.size > 1) { delta ->
                        ToolbarRuntime.update(context) { it.moving(tab, id, delta) }
                    }
                    TextButton(
                        onClick = { ToolbarRuntime.update(context) { it.movingEarlier(tab, id) } },
                        enabled = !hidden && place > 0,
                    ) {
                        Text(stringRes(R.string.settings__toolbar__earlier))
                    }
                    TextButton(
                        onClick = { ToolbarRuntime.update(context) { it.movingLater(tab, id) } },
                        enabled = !hidden && place >= 0 && place < shown.lastIndex,
                    ) {
                        Text(stringRes(R.string.settings__toolbar__later))
                    }
                    TextButton(onClick = { ToolbarRuntime.update(context) { it.hiding(id.name) } }) {
                        Text(if (hidden) stringRes(R.string.settings__toolbar__show) else stringRes(R.string.settings__toolbar__hide))
                    }
                }
            }
        }
    }
}

@Composable
private fun DragHandle(enabled: Boolean, onDelta: (Int) -> Unit) {
    val label = stringRes(R.string.settings__toolbar__drag)
    Text(
        text = "⋮⋮",
        color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .semantics { contentDescription = label }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                var accum = 0f
                val step = 28.dp.toPx()
                detectVerticalDragGestures(
                    onDragEnd = { accum = 0f },
                    onDragCancel = { accum = 0f },
                    onVerticalDrag = { _, dy ->
                        accum += dy
                        if (kotlin.math.abs(accum) < step) return@detectVerticalDragGestures
                        val delta = if (accum < 0) -1 else 1
                        accum = 0f
                        onDelta(delta)
                    },
                )
            }
            .padding(horizontal = 4.dp),
    )
}

@Composable
private fun LayoutChoice(selected: String, options: List<Pair<String, String>>, onPick: (String) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
        for ((value, label) in options) {
            TextButton(onClick = { onPick(value) }, enabled = value != selected) {
                Text(if (value == selected) label + " ✓" else label)
            }
        }
    }
}

@Composable
private fun toolLabel(ref: String, slash: List<CustomSlashCommand>, fleet: List<KnownTool>): String {
    val id = ToolbarActionId.entries.firstOrNull { it.name == ref }
    if (id != null) return ToolsPanelItems.item(id)?.let { stringRes(it.label) } ?: ToolsCatalog.label(id)
    val slashId = ToolRef.slashId(ref)
    if (slashId != null) return slash.firstOrNull { it.id == slashId }?.name ?: slashId
    return fleet.firstOrNull { it.ref == ref }?.label ?: ToolRef.fleetParts(ref)?.second ?: ref
}

private fun extraOffers(slash: List<CustomSlashCommand>, fleet: List<KnownTool>): List<Pair<String, String>> {
    val out = ArrayList<Pair<String, String>>()
    for (command in slash) {
        if (!command.enabled) continue
        val ref = ToolRef.slash(command.id)
        if (ToolRef.isSlash(ref)) out.add(ref to command.name)
    }
    for (tool in fleet) {
        if (ToolRef.isFleet(tool.ref)) out.add(tool.ref to tool.label)
    }
    return out
}

@Composable
private fun GroupTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun ItemRow(text: String, hidden: Boolean, actions: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = if (hidden) text + " · " + stringRes(R.string.settings__toolbar__hidden) else text,
            color = if (hidden) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(start = 8.dp),
        )
        actions()
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = { onToggle() })
    }
}
