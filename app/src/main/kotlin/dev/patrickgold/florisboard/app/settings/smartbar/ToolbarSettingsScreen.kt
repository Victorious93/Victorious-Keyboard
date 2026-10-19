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

package dev.patrickgold.florisboard.app.settings.smartbar

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.ime.screenshot.ScreenshotPolicy
import dev.patrickgold.florisboard.ime.toolbar.PinnedToolbar
import dev.patrickgold.florisboard.ime.toolbar.SavedCommandDto
import dev.patrickgold.florisboard.ime.toolbar.ToolbarActionId
import dev.patrickgold.jetpref.datastore.model.observeAsState
import dev.patrickgold.florisboard.ime.toolbar.SavedCommandRules
import dev.patrickgold.florisboard.ime.toolbar.ToolbarCatalog
import dev.patrickgold.florisboard.ime.toolbar.ToolbarRuntime
import dev.patrickgold.florisboard.ime.toolbar.ToolsCatalog
import dev.patrickgold.florisboard.ime.toolbar.ToolsTab
import dev.patrickgold.florisboard.ime.toolbar.ui.ToolsPanelItems
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
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
        val pinnedIds = PinnedToolbar.sanitized(config.pinned)
        if (pinnedIds.isEmpty()) {
            Text(
                text = stringRes(R.string.settings__toolbar__row_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
        for ((index, id) in pinnedIds.withIndex()) {
            val name = ToolsPanelItems.item(id)?.let { stringRes(it.label) } ?: ToolsCatalog.label(id)
            ItemRow(text = name, hidden = false) {
                TextButton(
                    onClick = { ToolbarRuntime.update(context) { it.pinnedEarlier(id) } },
                    enabled = index > 0,
                ) {
                    Text(stringRes(R.string.settings__toolbar__earlier))
                }
                TextButton(
                    onClick = { ToolbarRuntime.update(context) { it.pinnedLater(id) } },
                    enabled = index < pinnedIds.lastIndex,
                ) {
                    Text(stringRes(R.string.settings__toolbar__later))
                }
                TextButton(onClick = { ToolbarRuntime.update(context) { it.unpinning(id) } }) {
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
        val full = pinnedIds.size >= PinnedToolbar.MAX
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
                ItemRow(text = name, hidden = hidden) {
                    TextButton(
                        onClick = { ToolbarRuntime.update(context) { it.movingEarlier(tab, id) } },
                        enabled = !hidden && shown.indexOf(id) > 0,
                    ) {
                        Text(stringRes(R.string.settings__toolbar__earlier))
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
