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

package com.victorious.keyboard.app.settings.keyboard

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.victorious.keyboard.R
import com.victorious.keyboard.app.FlorisPreferenceStore
import com.victorious.keyboard.ime.developer.TerminalRowLayout
import dev.patrickgold.jetpref.datastore.model.observeAsState
import kotlinx.coroutines.launch
import org.florisboard.lib.compose.stringRes

/**
 * Reorder the terminal row and add macro keys. Typing here is the settings app, not the keyboard.
 * Macro text is stored and is not logged.
 */
@Composable
fun TerminalKeyOrderSection() {
    val prefs by FlorisPreferenceStore
    val scope = rememberCoroutineScope()
    val orderRaw by prefs.devrow.keyOrder.observeAsState()
    val macrosRaw by prefs.devrow.macros.observeAsState()
    val ids = if (orderRaw.isBlank()) TerminalRowLayout.defaultIds else TerminalRowLayout.parseIds(orderRaw)
    val labels = TerminalRowLayout.slots.associate { it.id to it.key.label }
    var command by remember { mutableStateOf("") }
    var letter by remember { mutableStateOf("") }

    fun saveOrder(next: List<String>) {
        scope.launch { prefs.devrow.keyOrder.set(next.joinToString(",")) }
    }

    Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
        Text(
            text = stringRes(R.string.settings__developer__key_order_hint),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
        ids.forEachIndexed { index, id ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = labels[id] ?: id,
                    modifier = Modifier.weight(1f).padding(start = 8.dp),
                )
                TextButton(onClick = {
                    if (index == 0) return@TextButton
                    val next = ids.toMutableList()
                    val moved = next.removeAt(index)
                    next.add(index - 1, moved)
                    saveOrder(next)
                }, enabled = index > 0) { Text(stringRes(R.string.settings__developer__move_up)) }
                TextButton(onClick = {
                    if (index == ids.lastIndex) return@TextButton
                    val next = ids.toMutableList()
                    val moved = next.removeAt(index)
                    next.add(index + 1, moved)
                    saveOrder(next)
                }, enabled = index < ids.lastIndex) { Text(stringRes(R.string.settings__developer__move_down)) }
                TextButton(onClick = { saveOrder(ids.filterIndexed { i, _ -> i != index }) }) {
                    Text(stringRes(R.string.settings__developer__remove))
                }
            }
        }
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringRes(R.string.settings__developer__add_key),
                modifier = Modifier.padding(horizontal = 8.dp),
            )
            TerminalRowLayout.slots.filter { it.id !in ids }.forEach { slot ->
                TextButton(onClick = { saveOrder(ids + slot.id) }) { Text(slot.key.label) }
            }
        }
        OutlinedTextField(
            value = command,
            onValueChange = { command = it.take(TerminalRowLayout.MAX_TEXT) },
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            label = { Text(stringRes(R.string.settings__developer__macro_command)) },
            singleLine = true,
        )
        TextButton(onClick = {
            val macro = TerminalRowLayout.commandMacro(command) ?: return@TextButton
            scope.launch {
                prefs.devrow.macros.set(TerminalRowLayout.addMacro(macrosRaw, macro))
                command = ""
            }
        }) { Text(stringRes(R.string.settings__developer__add_command_enter)) }
        OutlinedTextField(
            value = letter,
            onValueChange = { letter = it.take(1) },
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            label = { Text(stringRes(R.string.settings__developer__macro_ctrl)) },
            singleLine = true,
        )
        TextButton(onClick = {
            val macro = letter.firstOrNull()?.let { TerminalRowLayout.ctrlMacro(it) } ?: return@TextButton
            scope.launch {
                prefs.devrow.macros.set(TerminalRowLayout.addMacro(macrosRaw, macro))
                letter = ""
            }
        }) { Text(stringRes(R.string.settings__developer__add_ctrl)) }
        TerminalRowLayout.parseMacros(macrosRaw).forEachIndexed { index, macro ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = macro.label, modifier = Modifier.weight(1f).padding(start = 8.dp))
                TextButton(onClick = {
                    val next = TerminalRowLayout.parseMacros(macrosRaw).filterIndexed { i, _ -> i != index }
                    scope.launch { prefs.devrow.macros.set(TerminalRowLayout.renderMacros(next)) }
                }) { Text(stringRes(R.string.settings__developer__remove)) }
            }
        }
        TextButton(onClick = {
            scope.launch {
                prefs.devrow.keyOrder.set("")
                prefs.devrow.macros.set("")
            }
        }) { Text(stringRes(R.string.settings__developer__reset_row)) }
    }
}
