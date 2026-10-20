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

package com.victorious.keyboard.app.settings.suggestions

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
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
import com.victorious.keyboard.R
import com.victorious.keyboard.customSuggestions
import com.victorious.keyboard.ime.nlp.custom.CustomSuggestion
import com.victorious.keyboard.lib.compose.FlorisScreen
import dev.patrickgold.jetpref.datastore.ui.Preference
import org.florisboard.lib.compose.FlorisInfoCard
import org.florisboard.lib.compose.stringRes

@Composable
fun MySuggestionsScreen() = FlorisScreen {
    title = stringRes(R.string.settings__my_suggestions__title)
    previewFieldVisible = true

    val context = LocalContext.current
    val manager by context.customSuggestions()
    val items by manager.items.collectAsState()
    var editing by remember { mutableStateOf<CustomSuggestion?>(null) }
    var creating by remember { mutableStateOf(false) }

    content {
        FlorisInfoCard(
            text = stringRes(R.string.settings__my_suggestions__info),
        )
        Preference(
            icon = Icons.Default.Add,
            title = stringRes(R.string.settings__my_suggestions__add),
            onClick = { creating = true },
        )
        if (items.isEmpty()) {
            Text(
                text = stringRes(R.string.settings__my_suggestions__empty),
                modifier = Modifier.padding(16.dp),
            )
        }
        for (item in items) {
            Preference(
                title = item.text,
                summary = item.trigger.ifEmpty { stringRes(R.string.settings__my_suggestions__no_trigger) },
                onClick = { editing = item },
            )
        }
    }

    if (creating || editing != null) {
        SuggestionEditorDialog(
            initial = editing,
            onDismiss = {
                creating = false
                editing = null
            },
            onSave = { text, trigger ->
                val current = editing
                val ok = if (current == null) {
                    manager.add(text, trigger)
                } else {
                    manager.update(current.id, text, trigger)
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
        )
    }
}

@Composable
private fun SuggestionEditorDialog(
    initial: CustomSuggestion?,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Boolean,
    onDelete: (() -> Unit)?,
) {
    var text by remember(initial?.id) { mutableStateOf(initial?.text.orEmpty()) }
    var trigger by remember(initial?.id) { mutableStateOf(initial?.trigger.orEmpty()) }
    var rejected by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringRes(
                    if (initial == null) R.string.settings__my_suggestions__add else R.string.settings__my_suggestions__edit,
                ),
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it; rejected = false },
                    label = { Text(stringRes(R.string.settings__my_suggestions__phrase)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = trigger,
                    onValueChange = { trigger = it; rejected = false },
                    label = { Text(stringRes(R.string.settings__my_suggestions__trigger)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                )
                if (rejected) {
                    Text(
                        text = stringRes(R.string.settings__my_suggestions__rejected),
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { rejected = !onSave(text, trigger) }) {
                Text(stringRes(R.string.action__save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringRes(R.string.action__cancel))
            }
            if (onDelete != null) {
                TextButton(onClick = onDelete) {
                    androidx.compose.material3.Icon(Icons.Default.Delete, contentDescription = null)
                }
            }
        },
    )
}
