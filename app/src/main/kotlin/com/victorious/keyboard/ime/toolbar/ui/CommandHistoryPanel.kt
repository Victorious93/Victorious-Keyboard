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

package com.victorious.keyboard.ime.toolbar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import com.victorious.keyboard.R
import com.victorious.keyboard.ime.theme.rememberPanelColors
import com.victorious.keyboard.keyboardManager
import org.florisboard.lib.compose.stringRes

/** Local command history. The lines are shown to the person who typed them and are not logged. */
@Composable
fun CommandHistoryPanel(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val keyboardManager by context.keyboardManager()
    val colors = rememberPanelColors()
    val lines by keyboardManager.commandHistory.collectAsState()
    val current = lines ?: return
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.background)
            .padding(horizontal = 6.dp, vertical = 4.dp),
    ) {
        BasicText(
            text = stringRes(R.string.history__title),
            style = androidx.compose.ui.text.TextStyle(color = colors.ink, fontSize = 13.sp),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 36.dp, max = 96.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            if (current.isEmpty()) {
                BasicText(
                    text = stringRes(R.string.history__empty),
                    modifier = Modifier.padding(vertical = 4.dp),
                    style = androidx.compose.ui.text.TextStyle(color = colors.muted, fontSize = 13.sp),
                )
            }
            current.forEach { line ->
                BasicText(
                    text = line,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { keyboardManager.applyHistoryCommand(line) }
                        .padding(vertical = 3.dp),
                    style = androidx.compose.ui.text.TextStyle(color = colors.ink, fontSize = 14.sp),
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            HistoryChip(stringRes(R.string.history__older)) { keyboardManager.recallHistory(true) }
            HistoryChip(stringRes(R.string.history__newer)) { keyboardManager.recallHistory(false) }
            HistoryChip(stringRes(R.string.history__clear)) { keyboardManager.clearCommandHistory() }
            HistoryChip(stringRes(R.string.history__close)) { keyboardManager.closeCommandHistory() }
        }
    }
}

@Composable
private fun HistoryChip(label: String, onClick: () -> Unit) {
    val colors = rememberPanelColors()
    BasicText(
        text = label,
        modifier = Modifier
            .background(colors.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        style = androidx.compose.ui.text.TextStyle(color = colors.muted, fontSize = 12.sp),
    )
}
