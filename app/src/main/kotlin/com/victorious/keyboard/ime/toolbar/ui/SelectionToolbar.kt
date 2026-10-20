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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.victorious.keyboard.R
import com.victorious.keyboard.ime.text.SelectionAction
import com.victorious.keyboard.ime.theme.rememberPanelColors
import com.victorious.keyboard.keyboardManager
import org.florisboard.lib.compose.stringRes

/** Actions for a selection made by holding the space bar and dragging. */
@Composable
fun SelectionToolbar(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val keyboardManager by context.keyboardManager()
    val colors = rememberPanelColors()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.background)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 4.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        SelectionChip(stringRes(R.string.selection__word)) { keyboardManager.performSelection(SelectionAction.WORD) }
        SelectionChip(stringRes(R.string.selection__line)) { keyboardManager.performSelection(SelectionAction.LINE) }
        SelectionChip(stringRes(R.string.selection__all)) { keyboardManager.performSelection(SelectionAction.ALL) }
        SelectionChip(stringRes(R.string.selection__cut)) { keyboardManager.performSelection(SelectionAction.CUT) }
        SelectionChip(stringRes(R.string.selection__copy)) { keyboardManager.performSelection(SelectionAction.COPY) }
        SelectionChip(stringRes(R.string.selection__paste)) { keyboardManager.performSelection(SelectionAction.PASTE) }
        SelectionChip(stringRes(R.string.selection__home)) { keyboardManager.performSelection(SelectionAction.HOME) }
        SelectionChip(stringRes(R.string.selection__end)) { keyboardManager.performSelection(SelectionAction.END) }
        SelectionChip(stringRes(R.string.selection__word_back)) { keyboardManager.performSelection(SelectionAction.WORD_BACK) }
        SelectionChip(stringRes(R.string.selection__word_forward)) { keyboardManager.performSelection(SelectionAction.WORD_FORWARD) }
        SelectionChip(stringRes(R.string.selection__close)) { keyboardManager.performSelection(SelectionAction.CLOSE) }
    }
}

@Composable
private fun SelectionChip(label: String, onClick: () -> Unit) {
    val colors = rememberPanelColors()
    BasicText(
        text = label,
        modifier = Modifier
            .background(colors.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        style = TextStyle(color = colors.ink, fontSize = 12.sp),
    )
}
