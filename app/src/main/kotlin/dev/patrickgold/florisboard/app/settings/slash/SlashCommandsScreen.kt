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

package dev.patrickgold.florisboard.app.settings.slash

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.ime.slash.CustomSlashCommand
import dev.patrickgold.florisboard.ime.slash.SlashCatalog
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import dev.patrickgold.florisboard.slashCommands
import dev.patrickgold.jetpref.datastore.ui.ExperimentalJetPrefDatastoreUi
import dev.patrickgold.jetpref.datastore.ui.Preference
import dev.patrickgold.jetpref.datastore.ui.PreferenceGroup
import dev.patrickgold.jetpref.datastore.ui.SwitchPreference
import org.florisboard.lib.compose.FlorisInfoCard
import org.florisboard.lib.compose.stringRes

@OptIn(ExperimentalJetPrefDatastoreUi::class)
@Composable
fun SlashCommandsScreen() = FlorisScreen {
    title = stringRes(R.string.settings__slash__title)
    previewFieldVisible = true

    val context = LocalContext.current
    val manager by context.slashCommands()
    val items by manager.items.collectAsState()
    val prefs by FlorisPreferenceStore
    var editing by remember { mutableStateOf<CustomSlashCommand?>(null) }
    var creating by remember { mutableStateOf(false) }

    content {
        SwitchPreference(
            prefs.slash.enabled,
            title = stringRes(R.string.settings__slash__enabled),
            summary = stringRes(R.string.settings__slash__enabled_summary),
        )
        FlorisInfoCard(text = stringRes(R.string.settings__slash__info))
        PreferenceGroup(title = stringRes(R.string.settings__slash__builtins)) {
            for (builtin in SlashCatalog.builtins) {
                Preference(
                    title = "/" + builtin.name,
                    summary = builtin.description,
                    enabledIf = { prefs.slash.enabled isEqualTo true },
                )
            }
        }
        PreferenceGroup(title = stringRes(R.string.settings__slash__custom)) {
            Preference(
                icon = Icons.Default.Add,
                title = stringRes(R.string.settings__slash__add),
                onClick = { creating = true },
            )
            if (items.isEmpty()) {
                Text(
                    text = stringRes(R.string.settings__slash__empty),
                    modifier = Modifier.padding(16.dp),
                )
            }
            for (item in items.sortedBy { it.order }) {
                Preference(
                    title = "/" + item.name,
                    summary = item.description.ifEmpty { item.template },
                    onClick = { editing = item },
                )
            }
        }
    }

    if (creating || editing != null) {
        SlashEditorDialog(
            initial = editing,
            onDismiss = {
                creating = false
                editing = null
            },
            onSave = { name, description, template, enabled ->
                val current = editing
                val ok = if (current == null) {
                    manager.add(name, description, template)
                } else {
                    manager.update(current.id, name, description, template, enabled)
                }
                if (ok) {
                    creating = false
                    editing = null
                }
                ok
            },
            onDelete = editing?.let { current ->
                {
                    manager.delete(current.id)
                    editing = null
                }
            },
            onMove = editing?.let { current ->
                { direction: Int -> manager.move(current.id, direction) }
            },
        )
    }
}

@Composable
private fun SlashEditorDialog(
    initial: CustomSlashCommand?,
    onDismiss: () -> Unit,
    onSave: (String, String, String, Boolean) -> Boolean,
    onDelete: (() -> Unit)?,
    onMove: ((Int) -> Boolean)?,
) {
    var name by remember(initial?.id) { mutableStateOf(initial?.name.orEmpty()) }
    var description by remember(initial?.id) { mutableStateOf(initial?.description.orEmpty()) }
    var template by remember(initial?.id) { mutableStateOf(initial?.template.orEmpty()) }
    var enabled by remember(initial?.id) { mutableStateOf(initial?.enabled ?: true) }
    var rejected by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringRes(if (initial == null) R.string.settings__slash__add else R.string.settings__slash__edit)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; rejected = false },
                    label = { Text(stringRes(R.string.settings__slash__name)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it; rejected = false },
                    label = { Text(stringRes(R.string.settings__slash__description)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                )
                OutlinedTextField(
                    value = template,
                    onValueChange = { template = it; rejected = false },
                    label = { Text(stringRes(R.string.settings__slash__output)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    minLines = 2,
                )
                Text(
                    text = stringRes(R.string.settings__slash__variables),
                    modifier = Modifier.padding(top = 8.dp),
                )
                if (initial != null) {
                    TextButton(onClick = { enabled = !enabled }) {
                        Text(if (enabled) stringRes(R.string.settings__slash__disable) else stringRes(R.string.settings__slash__enable_one))
                    }
                }
                if (rejected) {
                    Text(text = stringRes(R.string.settings__slash__rejected))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { rejected = !onSave(name, description, template, enabled) }) {
                Text(stringRes(R.string.action__save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringRes(R.string.action__cancel)) }
            if (onMove != null) {
                TextButton(onClick = { onMove(-1) }) {
                    androidx.compose.material3.Icon(Icons.Default.ArrowUpward, contentDescription = null)
                }
                TextButton(onClick = { onMove(1) }) {
                    androidx.compose.material3.Icon(Icons.Default.ArrowDownward, contentDescription = null)
                }
            }
            if (onDelete != null) {
                TextButton(onClick = onDelete) {
                    androidx.compose.material3.Icon(Icons.Default.Delete, contentDescription = null)
                }
            }
        },
    )
}
