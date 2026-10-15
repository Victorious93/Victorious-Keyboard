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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.patrickgold.florisboard.ime.toolbar.ToolbarActionId
import dev.patrickgold.florisboard.ime.toolbar.ToolsCatalog
import dev.patrickgold.florisboard.lib.fleet.FleetPanelState
import dev.patrickgold.florisboard.lib.fleet.FleetSnippet
import dev.patrickgold.florisboard.lib.fleet.FleetSnippetKind

/**
 * Replaces the key area. Tiles call the existing toolbar actions. Nothing in this grid is executed
 * as a shell command.
 */
@Composable
fun ToolsPanel(
    onAction: (ToolbarActionId) -> Unit,
    onClose: () -> Unit,
    fleet: FleetPanelState = FleetPanelState.Hidden,
    onAskHub: () -> Unit = {},
    onSnippet: (FleetSnippet) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF050505))
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Tools",
                color = Color(0xFFE8E8E8),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
            )
            if (fleet.showDot) {
                Text(
                    text = if (fleet.connected) "Hub" else "Hub off",
                    color = Color(0xFF9A9A9A),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(start = 8.dp),
                )
                Box(
                    modifier = Modifier
                        .padding(start = 6.dp)
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (fleet.connected) Color(0xFF3DDC84) else Color(0xFF6E6E6E)),
                )
            }
            Box(modifier = Modifier.weight(1f))
            TextButton(onClick = onClose) {
                Text("Keyboard", color = Color(0xFFE8E8E8))
            }
        }
        for (group in ToolsCatalog.groups) {
            Text(
                text = group.title,
                color = Color(0xFF9A9A9A),
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
            )
            group.actions.chunked(4).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    for (id in row) {
                        TextButton(
                            onClick = { onAction(id) },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(
                                text = ToolsCatalog.label(id),
                                color = Color(0xFFE8E8E8),
                                fontSize = 13.sp,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
            if (group.title == "Terminal keys" && fleet.connected) {
                HubSnippetBlock(fleet.snippets, onAskHub, onSnippet)
            }
        }
    }
}

@Composable
private fun HubSnippetBlock(
    snippets: List<FleetSnippet>,
    onAskHub: () -> Unit,
    onSnippet: (FleetSnippet) -> Unit,
) {
    Text(
        text = "Hub",
        color = Color(0xFF9A9A9A),
        fontSize = 12.sp,
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
    )
    TextButton(onClick = onAskHub, modifier = Modifier.fillMaxWidth()) {
        Text("Ask hub", color = Color(0xFFE8E8E8), fontSize = 13.sp)
    }
    for (kind in FleetSnippetKind.entries) {
        val rows = snippets.filter { it.kind == kind }
        if (rows.isEmpty()) continue
        Text(
            text = kind.name.lowercase(),
            color = Color(0xFF9A9A9A),
            fontSize = 11.sp,
            modifier = Modifier.padding(top = 4.dp),
        )
        rows.chunked(2).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                for (snippet in row) {
                    TextButton(
                        onClick = { onSnippet(snippet) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            text = snippet.label,
                            color = Color(0xFFE8E8E8),
                            fontSize = 13.sp,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}
