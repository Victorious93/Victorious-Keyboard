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

package dev.patrickgold.florisboard.ime.toolbar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.patrickgold.florisboard.ime.toolbar.ToolbarCatalog
import dev.patrickgold.florisboard.ime.toolbar.ToolbarPage
import dev.patrickgold.florisboard.ime.toolbar.ToolbarRuntime
import dev.patrickgold.florisboard.keyboardManager

private val BarBg = Color(0xFF0A0A0A)
private val ChipBg = Color(0xFF161616)
private val ChipText = Color(0xFFE8E8E8)
private val ChipLine = Color(0xFF3A3A3A)

/**
 * Scrollable action pages shown in place of the old fit-only quick-action row.
 * Suggestions stay on the other side of the smartbar toggle. Long-press hides a button.
 */
@Composable
fun AdvancedToolbar(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val keyboardManager by context.keyboardManager()
    LaunchedEffect(Unit) { ToolbarRuntime.ensure(context) }
    val config by ToolbarRuntime.config.collectAsState()
    val page = ToolbarPage.entries.firstOrNull { it.name == config.page } ?: ToolbarPage.TERMINAL
    val actions = ToolbarCatalog.visible(page, config.hiddenSet(), config.pageOrder(page))
    val commands = if (page == ToolbarPage.TERMINAL) {
        ToolbarCatalog.visibleCommands(config.hiddenSet(), config.extraCommands())
    } else {
        emptyList()
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .background(BarBg)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Chip(page.title, emphasized = true, onClick = {
            val next = ToolbarPage.entries[(page.ordinal + 1) % ToolbarPage.entries.size]
            ToolbarRuntime.update(context) { it.withPage(next) }
        })
        for (action in actions) {
            Chip(
                label = action.label,
                onClick = { keyboardManager.performToolbarAction(action.id) },
                onLongClick = { ToolbarRuntime.update(context) { it.hiding(action.id.name) } },
            )
        }
        for (command in commands) {
            Chip(
                label = command.label,
                onClick = { keyboardManager.insertSavedCommand(command.text) },
                onLongClick = { ToolbarRuntime.update(context) { it.hiding(ToolbarCatalog.commandId(command.id)) } },
            )
        }
    }
}

@Composable
private fun Chip(
    label: String,
    emphasized: Boolean = false,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    Text(
        text = label,
        color = ChipText,
        fontSize = 12.sp,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .fillMaxHeight()
            .padding(vertical = 4.dp)
            .background(ChipBg, RoundedCornerShape(4.dp))
            .border(1.dp, if (emphasized) ChipText else ChipLine, RoundedCornerShape(4.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 8.dp),
    )
}
