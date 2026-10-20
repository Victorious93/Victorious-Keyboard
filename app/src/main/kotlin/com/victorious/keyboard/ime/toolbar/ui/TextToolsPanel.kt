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
import com.victorious.keyboard.ime.toolbar.TextToolPlace
import com.victorious.keyboard.ime.toolbar.TextToolSource
import com.victorious.keyboard.ime.toolbar.TextTools
import com.victorious.keyboard.ime.toolbar.TextTransform
import com.victorious.keyboard.keyboardManager
import org.florisboard.lib.compose.stringRes

/** Preview for the Text Tools tile. The result is not written until Replace, Insert, or Copy. */
@Composable
fun TextToolsPanel(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val keyboardManager by context.keyboardManager()
    val colors = rememberPanelColors()
    val preview by keyboardManager.textTools.collectAsState()
    val current = preview ?: return
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.background)
            .padding(horizontal = 6.dp, vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            BasicText(
                text = stringRes(R.string.text_tools__title),
                style = androidx.compose.ui.text.TextStyle(color = colors.ink, fontSize = 13.sp),
            )
            BasicText(
                text = when (current.source) {
                    TextToolSource.SELECTION -> stringRes(R.string.text_tools__from_selection)
                    TextToolSource.CLIPBOARD -> stringRes(R.string.text_tools__from_clipboard)
                },
                style = androidx.compose.ui.text.TextStyle(color = colors.muted, fontSize = 12.sp),
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            TextTools.offered.forEach { op ->
                Chip(toolLabel(op), current.op == op) { keyboardManager.chooseTextTool(op) }
            }
        }
        BasicText(
            text = current.output ?: stringRes(R.string.toolbar__transform_failed),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 36.dp, max = 96.dp)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 4.dp),
            style = androidx.compose.ui.text.TextStyle(color = colors.ink, fontSize = 14.sp),
        )
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Chip(stringRes(R.string.text_tools__replace), false) { keyboardManager.applyTextTool(TextToolPlace.REPLACE) }
            Chip(stringRes(R.string.text_tools__insert), false) { keyboardManager.applyTextTool(TextToolPlace.INSERT) }
            Chip(stringRes(R.string.text_tools__copy), false) { keyboardManager.applyTextTool(TextToolPlace.COPY) }
            Chip(stringRes(R.string.text_tools__close), false) { keyboardManager.closeTextTools() }
        }
    }
}

@Composable
private fun toolLabel(op: TextTransform): String = when (op) {
    TextTransform.BASE64_ENCODE -> stringRes(R.string.text_tools__base64_encode)
    TextTransform.BASE64_DECODE -> stringRes(R.string.text_tools__base64_decode)
    TextTransform.URL_ENCODE -> stringRes(R.string.text_tools__url_encode)
    TextTransform.URL_DECODE -> stringRes(R.string.text_tools__url_decode)
    TextTransform.HEX_ENCODE -> stringRes(R.string.text_tools__hex_encode)
    TextTransform.HEX_DECODE -> stringRes(R.string.text_tools__hex_decode)
    TextTransform.JSON_PRETTY -> stringRes(R.string.text_tools__json_pretty)
    TextTransform.JSON_MINIFY -> stringRes(R.string.text_tools__json_minify)
    TextTransform.MD5 -> stringRes(R.string.text_tools__md5)
    TextTransform.SHA1 -> stringRes(R.string.text_tools__sha1)
    TextTransform.SHA256 -> stringRes(R.string.text_tools__sha256)
    TextTransform.UPPER -> stringRes(R.string.text_tools__upper)
    TextTransform.LOWER -> stringRes(R.string.text_tools__lower)
    TextTransform.TITLE -> stringRes(R.string.text_tools__title_case)
    TextTransform.CAMEL -> stringRes(R.string.text_tools__camel)
    TextTransform.SNAKE -> stringRes(R.string.text_tools__snake)
    TextTransform.KEBAB -> stringRes(R.string.text_tools__kebab)
    TextTransform.TRIM_LINES -> stringRes(R.string.text_tools__trim)
    TextTransform.SORT_LINES -> stringRes(R.string.text_tools__sort)
    TextTransform.DEDUPE_LINES -> stringRes(R.string.text_tools__dedupe)
    TextTransform.ROT13 -> stringRes(R.string.text_tools__rot13)
    TextTransform.EXTRACT_URL -> stringRes(R.string.text_tools__url)
    TextTransform.EXTRACT_EMAIL -> stringRes(R.string.text_tools__email)
    TextTransform.EXTRACT_IP -> stringRes(R.string.text_tools__ip)
    TextTransform.JWT_DECODE -> stringRes(R.string.text_tools__jwt)
    TextTransform.UNIX_TO_DATE -> stringRes(R.string.text_tools__unix_date)
    TextTransform.DATE_TO_UNIX -> stringRes(R.string.text_tools__date_unix)
    TextTransform.SHELL_ESCAPE -> stringRes(R.string.text_tools__shell)
    TextTransform.JSON_ESCAPE -> stringRes(R.string.text_tools__json_escape)
    TextTransform.REGEX_ESCAPE -> stringRes(R.string.text_tools__regex)
    else -> op.name
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
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
