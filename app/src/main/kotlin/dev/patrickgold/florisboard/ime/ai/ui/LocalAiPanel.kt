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

package dev.patrickgold.florisboard.ime.ai.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.patrickgold.florisboard.FlorisImeService
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.ime.ai.AiPhase
import dev.patrickgold.florisboard.ime.ai.AiRefusal
import dev.patrickgold.florisboard.ime.ai.AiSource
import dev.patrickgold.florisboard.ime.ai.AiTask
import dev.patrickgold.florisboard.ime.clipedit.ClipEditorOrigin
import dev.patrickgold.florisboard.ime.theme.rememberPanelColors
import dev.patrickgold.florisboard.keyboardManager
import dev.patrickgold.florisboard.localAiManager
import org.florisboard.lib.compose.stringRes

@Composable
fun LocalAiPanel(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val localAi by context.localAiManager()
    val keyboardManager by context.keyboardManager()
    val colors = rememberPanelColors()
    val panel by localAi.panel.collectAsState()
    val current = panel ?: return
    val editor by keyboardManager.clipEditor.collectAsState()
    val vaultOpen = editor?.origin == ClipEditorOrigin.VAULT || editor?.sensitive == true
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.background)
            .padding(horizontal = 6.dp, vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            sources(vaultOpen).forEach { source ->
                ChipText(
                    label = sourceLabel(source),
                    selected = current.source == source,
                    onClick = { keyboardManager.chooseLocalAiSource(source) },
                )
            }
        }
        val body = when {
            current.task == AiTask.ASK && current.phase == AiPhase.COMPOSING -> current.ask.text.ifEmpty { stringRes(R.string.local_ai__ask_hint) }
            current.output.isNotEmpty() -> current.output
            current.notice != null -> notice(current.notice)
            current.phase == AiPhase.RUNNING -> stringRes(R.string.local_ai__running)
            else -> ""
        }
        BasicText(
            text = body,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 36.dp, max = 96.dp)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 4.dp)
                .clickable(enabled = current.task == AiTask.ASK) { localAi.focusAsk() },
            style = androidx.compose.ui.text.TextStyle(color = colors.ink, fontSize = 14.sp),
        )
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (current.phase == AiPhase.RUNNING) {
                ChipText(stringRes(R.string.local_ai__cancel), false) { localAi.cancel() }
            }
            if (current.task == AiTask.ASK && current.phase != AiPhase.RUNNING) {
                ChipText(stringRes(R.string.local_ai__run), false) { keyboardManager.submitLocalAiAsk() }
            }
            if (current.phase == AiPhase.RESULT && current.output.isNotBlank()) {
                ChipText(stringRes(R.string.local_ai__insert), false) { keyboardManager.insertLocalAiResult() }
                if (!current.fromVault) {
                    ChipText(stringRes(R.string.local_ai__copy), false) { keyboardManager.copyLocalAiResult() }
                }
            }
            ChipText(stringRes(R.string.local_ai__models), false) { FlorisImeService.launchLocalAiSettings() }
            ChipText(stringRes(R.string.local_ai__close), false) { localAi.close() }
        }
    }
}

private fun sources(vaultOpen: Boolean): List<AiSource> = buildList {
    add(AiSource.SELECTION)
    add(AiSource.FIELD)
    add(AiSource.CLIPBOARD)
    add(AiSource.EDITOR)
    if (vaultOpen) add(AiSource.VAULT)
}

@Composable
private fun sourceLabel(source: AiSource): String = when (source) {
    AiSource.SELECTION -> stringRes(R.string.local_ai__source_selection)
    AiSource.FIELD -> stringRes(R.string.local_ai__source_field)
    AiSource.CLIPBOARD -> stringRes(R.string.local_ai__source_clipboard)
    AiSource.EDITOR -> stringRes(R.string.local_ai__source_editor)
    AiSource.VAULT -> stringRes(R.string.local_ai__source_vault)
}

@Composable
private fun notice(reason: AiRefusal): String = when (reason) {
    AiRefusal.NO_MODEL, AiRefusal.NATIVE_MISSING -> stringRes(R.string.local_ai__unavailable)
    AiRefusal.EMPTY -> stringRes(R.string.local_ai__empty)
    AiRefusal.PASSWORD -> stringRes(R.string.local_ai__password)
    AiRefusal.VAULT -> stringRes(R.string.local_ai__vault)
    AiRefusal.TOO_LONG -> stringRes(R.string.local_ai__too_long)
}

@Composable
private fun ChipText(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = rememberPanelColors()
    BasicText(
        text = label,
        modifier = Modifier
            .background(if (selected) colors.selected else colors.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        style = androidx.compose.ui.text.TextStyle(color = if (selected) colors.ink else colors.muted, fontSize = 12.sp),
    )
}
