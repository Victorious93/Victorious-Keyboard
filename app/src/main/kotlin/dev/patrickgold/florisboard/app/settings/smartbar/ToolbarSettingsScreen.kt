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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.ime.toolbar.SavedCommandDto
import dev.patrickgold.florisboard.ime.toolbar.SavedCommandRules
import dev.patrickgold.florisboard.ime.toolbar.ToolbarCatalog
import dev.patrickgold.florisboard.ime.toolbar.ToolbarPage
import dev.patrickgold.florisboard.ime.toolbar.ToolbarRuntime
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import org.florisboard.lib.compose.stringRes

@Composable
fun ToolbarSettingsScreen() = FlorisScreen {
    title = stringRes(R.string.settings__toolbar__title)
    previewFieldVisible = true
    content {
        val context = LocalContext.current
        LaunchedEffect(Unit) { ToolbarRuntime.ensure(context) }
        val config by ToolbarRuntime.config.collectAsState()
        Text(
            text = stringRes(R.string.settings__toolbar__summary),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        ToggleRow(stringRes(R.string.settings__toolbar__shell_history), config.shellHistoryEnabled) {
            ToolbarRuntime.update(context) { it.copy(shellHistoryEnabled = !it.shellHistoryEnabled) }
        }
        ToggleRow(stringRes(R.string.settings__toolbar__bracket_pairing), config.bracketPairing) {
            ToolbarRuntime.update(context) { it.copy(bracketPairing = !it.bracketPairing) }
        }
        TextButton(onClick = { ToolbarRuntime.update(context) { it.copy(hidden = emptyList(), order = emptyMap()) } }) {
            Text(stringRes(R.string.settings__toolbar__restore))
        }
        TextButton(onClick = { ToolbarRuntime.update(context) { it.copy(recentCommands = emptyList()) } }) {
            Text(stringRes(R.string.settings__toolbar__clear_history))
        }
        var label by remember { mutableStateOf("") }
        var command by remember { mutableStateOf("") }
        var tag by remember { mutableStateOf("") }
        OutlinedTextField(value = label, onValueChange = { label = it }, label = { Text(stringRes(R.string.settings__toolbar__label)) }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
        OutlinedTextField(value = command, onValueChange = { command = it }, label = { Text(stringRes(R.string.settings__toolbar__command)) }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp))
        OutlinedTextField(value = tag, onValueChange = { tag = it }, label = { Text(stringRes(R.string.settings__toolbar__tag)) }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
        TextButton(onClick = {
            val next = SavedCommandRules.normalize(label, command, tag) ?: return@TextButton
            ToolbarRuntime.update(context) { current ->
                if (current.commands.size >= SavedCommandRules.MAX_COMMANDS) current
                else current.copy(commands = current.commands + SavedCommandDto(next.id, next.label, next.text, next.tag))
            }
            label = ""
            command = ""
            tag = ""
        }) {
            Text(stringRes(R.string.settings__toolbar__add))
        }
        for (page in ToolbarPage.entries) {
            Text(
                text = page.title,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp),
            )
            val shown = ToolbarCatalog.visible(page, config.hiddenSet(), config.pageOrder(page))
            for (action in ToolbarCatalog.actions.filter { it.page == page }) {
                val hidden = action.id.name in config.hiddenSet()
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = action.label + if (hidden) " · " + stringRes(R.string.settings__toolbar__hidden) else "",
                        modifier = Modifier.weight(1f).padding(start = 8.dp),
                    )
                    TextButton(onClick = { ToolbarRuntime.update(context) { it.movingEarlier(page, action.id.name) } }, enabled = !hidden && shown.indexOf(action) > 0) {
                        Text(stringRes(R.string.settings__toolbar__earlier))
                    }
                    TextButton(onClick = { ToolbarRuntime.update(context) { it.hiding(action.id.name) } }) {
                        Text(if (hidden) stringRes(R.string.settings__toolbar__show) else stringRes(R.string.settings__toolbar__hide))
                    }
                }
            }
        }
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
