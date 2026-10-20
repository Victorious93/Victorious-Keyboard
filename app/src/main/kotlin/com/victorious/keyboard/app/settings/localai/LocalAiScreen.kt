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

package com.victorious.keyboard.app.settings.localai

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.victorious.keyboard.R
import com.victorious.keyboard.ime.ai.AiConfigRules
import com.victorious.keyboard.ime.ai.ImportDecision
import com.victorious.keyboard.ime.ai.LlamaNative
import com.victorious.keyboard.lib.compose.FlorisScreen
import com.victorious.keyboard.localAiManager
import dev.patrickgold.jetpref.datastore.ui.ExperimentalJetPrefDatastoreUi
import dev.patrickgold.jetpref.datastore.ui.Preference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.florisboard.lib.compose.stringRes

@OptIn(ExperimentalJetPrefDatastoreUi::class)
@Composable
fun LocalAiScreen() = FlorisScreen {
    title = stringRes(R.string.settings__local_ai__title)
    previewFieldVisible = true

    val context = LocalContext.current
    val localAi by context.localAiManager()
    val models by localAi.models.collectAsState()
    val config by localAi.config.collectAsState()
    val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf<Uri?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var deleteId by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val decision = withContext(Dispatchers.IO) { localAi.importModel(uri, acknowledged = false) }
            if (decision == ImportDecision.WARN) {
                pending = uri
            } else if (decision != ImportDecision.ACCEPT) {
                message = decision.name
            }
        }
    }

    content {
            Text(
                stringRes(R.string.settings__local_ai__summary),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            Text(
                stringRes(R.string.settings__local_ai__engine) + " " + LlamaNative.PIN +
                    if (LlamaNative.available) "" else " (" + stringRes(R.string.local_ai__native_missing) + ")",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            Preference(
                title = stringRes(R.string.settings__local_ai__import),
                onClick = { picker.launch(arrayOf("*/*")) },
            )
            models.forEach { model ->
                val selected = model.id == config.selectedModelId
                Preference(
                    title = model.displayName + if (selected) "  \u2713" else "",
                    summary = (model.bytes / (1024 * 1024)).toString() + " MB",
                    onClick = { localAi.updateConfig(config.copy(selectedModelId = model.id)) },
                )
                Preference(
                    title = stringRes(R.string.settings__local_ai__delete),
                    onClick = { deleteId = model.id },
                )
            }
            Text(stringRes(R.string.settings__local_ai__context), modifier = Modifier.padding(16.dp, 8.dp))
            AiConfigRules.CONTEXT_CHOICES.forEach { choice ->
                Preference(
                    title = choice.toString(),
                    summary = if (choice == config.contextSize) stringRes(R.string.settings__local_ai__selected) else "",
                    onClick = { localAi.updateConfig(config.copy(contextSize = choice)) },
                )
            }
            Text(
                stringRes(R.string.settings__local_ai__threads) + " " + config.threads,
                modifier = Modifier.padding(16.dp, 8.dp),
            )
            Slider(
                value = config.threads.toFloat(),
                onValueChange = { localAi.updateConfig(config.copy(threads = it.toInt())) },
                valueRange = 1f..8f,
                steps = 6,
                modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
            )
            Text(
                stringRes(R.string.settings__local_ai__temperature) + " " + config.temperature,
                modifier = Modifier.padding(16.dp, 8.dp),
            )
            Slider(
                value = config.temperature,
                onValueChange = { localAi.updateConfig(config.copy(temperature = it)) },
                valueRange = 0f..1.5f,
                modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
            )
            Text(
                stringRes(R.string.settings__local_ai__max_tokens) + " " + config.maxTokens,
                modifier = Modifier.padding(16.dp, 8.dp),
            )
            Slider(
                value = config.maxTokens.toFloat(),
                onValueChange = { localAi.updateConfig(config.copy(maxTokens = it.toInt())) },
                valueRange = 16f..1024f,
                modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
            )
            OutlinedTextField(
                value = config.systemPrompt,
                onValueChange = { localAi.updateConfig(config.copy(systemPrompt = it.take(AiConfigRules.MAX_SYSTEM))) },
                label = { Text(stringRes(R.string.settings__local_ai__system)) },
                modifier = Modifier.padding(16.dp).fillMaxWidth(),
            )
            OutlinedTextField(
                value = config.translateLanguage,
                onValueChange = { localAi.updateConfig(config.copy(translateLanguage = it.take(AiConfigRules.MAX_LANGUAGE))) },
                label = { Text(stringRes(R.string.settings__local_ai__language)) },
                modifier = Modifier.padding(16.dp).fillMaxWidth(),
            )
            Preference(
                title = stringRes(R.string.settings__local_ai__suggestions),
                summary = if (config.suggestionsEnabled) {
                    stringRes(R.string.settings__local_ai__on)
                } else {
                    stringRes(R.string.settings__local_ai__off)
                },
                onClick = { localAi.updateConfig(config.copy(suggestionsEnabled = !config.suggestionsEnabled)) },
            )
            if (pending != null) {
                AlertDialog(
                    onDismissRequest = { pending = null },
                    title = { Text(stringRes(R.string.settings__local_ai__warn_title)) },
                    text = { Text(stringRes(R.string.settings__local_ai__warn_body)) },
                    confirmButton = {
                        TextButton(onClick = {
                            val uri = pending
                            pending = null
                            if (uri != null) {
                                scope.launch {
                                    val decision = withContext(Dispatchers.IO) { localAi.importModel(uri, acknowledged = true) }
                                    if (decision != ImportDecision.ACCEPT) message = decision.name
                                }
                            }
                        }) { Text(stringRes(R.string.action__ok)) }
                    },
                    dismissButton = {
                        TextButton(onClick = { pending = null }) { Text(stringRes(R.string.action__cancel)) }
                    },
                )
            }
            if (deleteId != null) {
                AlertDialog(
                    onDismissRequest = { deleteId = null },
                    title = { Text(stringRes(R.string.settings__local_ai__delete)) },
                    text = { Text(stringRes(R.string.settings__local_ai__delete_body)) },
                    confirmButton = {
                        TextButton(onClick = {
                            deleteId?.let { localAi.deleteModel(it) }
                            deleteId = null
                        }) { Text(stringRes(R.string.action__ok)) }
                    },
                    dismissButton = {
                        TextButton(onClick = { deleteId = null }) { Text(stringRes(R.string.action__cancel)) }
                    },
                )
            }
            message?.let { code ->
                AlertDialog(
                    onDismissRequest = { message = null },
                    text = { Text(importMessage(code)) },
                    confirmButton = {
                        TextButton(onClick = { message = null }) { Text(stringRes(R.string.action__ok)) }
                    },
                )
            }
    }
}

@Composable
private fun importMessage(code: String): String = when (code) {
    ImportDecision.REJECT_MAGIC.name -> stringRes(R.string.settings__local_ai__bad_file)
    ImportDecision.REJECT_TOO_LARGE.name -> stringRes(R.string.settings__local_ai__too_large)
    ImportDecision.REJECT_NO_SPACE.name -> stringRes(R.string.settings__local_ai__no_space)
    ImportDecision.REJECT_TOO_MANY.name -> stringRes(R.string.settings__local_ai__too_many)
    ImportDecision.REJECT_EMPTY.name -> stringRes(R.string.settings__local_ai__empty_file)
    else -> stringRes(R.string.settings__local_ai__import_failed)
}
