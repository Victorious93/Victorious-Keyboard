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

package dev.patrickgold.florisboard.ime.clipedit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.platform.LocalContext
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.ime.clipedit.ClipEditorSession
import dev.patrickgold.florisboard.ime.clipedit.EditorField
import dev.patrickgold.florisboard.ime.toolbar.TextTransforms
import dev.patrickgold.florisboard.keyboardManager
import org.florisboard.lib.compose.stringRes

private val EditorBg = Color(0xFF050505)
private val EditorKey = Color(0xFF161616)
private val EditorText = Color(0xFFE8E8E8)
private val EditorMuted = Color(0xFFBDBDBD)
private val EditorLine = Color(0xFF3A3A3A)
private val EditorSelect = Color(0xFF3A3A3A)

/**
 * Multi-line editor drawn above the keys. Keys are redirected by the keyboard manager, so this
 * panel does not use a platform text field. The window's existing bottom inset keeps the gap.
 */
@Composable
fun ClipEditorPanel(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val keyboardManager by context.keyboardManager()
    val session by keyboardManager.clipEditor.collectAsState()
    val current = session ?: return
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(EditorBg)
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            EditorChip(stringRes(R.string.clip_editor__paste)) { keyboardManager.pasteClipEditor() }
            EditorChip(stringRes(R.string.clip_editor__save)) { keyboardManager.saveClipEditor(asNew = false) }
            EditorChip(stringRes(R.string.clip_editor__save_new)) { keyboardManager.saveClipEditor(asNew = true) }
            EditorChip(stringRes(R.string.clip_editor__cancel)) { keyboardManager.closeClipEditor() }
            val counts = TextTransforms.counts(current.body.text)
            BasicText(
                text = stringRes(
                    R.string.clip_editor__counts,
                    "chars" to counts.characters.toString(),
                    "words" to counts.words.toString(),
                    "tokens" to counts.tokens.toString(),
                ),
                style = androidx.compose.ui.text.TextStyle(color = EditorMuted, fontSize = 11.sp),
                modifier = Modifier.padding(start = 4.dp),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            FieldChip(stringRes(R.string.clip_editor__find), current.find.text, current.focus == EditorField.FIND) {
                keyboardManager.editClipSession { it.focus(EditorField.FIND) }
            }
            EditorChip(stringRes(R.string.clip_editor__next)) {
                keyboardManager.editClipSession { sessionNow ->
                    val hit = sessionNow.body.findNext(sessionNow.find.text)
                    sessionNow.copy(body = hit.buffer, focus = EditorField.BODY, revision = sessionNow.revision + 1)
                }
            }
            FieldChip(stringRes(R.string.clip_editor__replace), current.replace.text, current.focus == EditorField.REPLACE) {
                keyboardManager.editClipSession { it.focus(EditorField.REPLACE) }
            }
            EditorChip(stringRes(R.string.clip_editor__replace_one)) {
                keyboardManager.editClipSession { sessionNow ->
                    val base = if (sessionNow.body.selectedText.equals(sessionNow.find.text, ignoreCase = true)) {
                        sessionNow.body
                    } else {
                        sessionNow.body.findNext(sessionNow.find.text).buffer
                    }
                    sessionNow.copy(
                        body = base.replaceMatch(sessionNow.find.text, sessionNow.replace.text),
                        focus = EditorField.BODY,
                        revision = sessionNow.revision + 1,
                    )
                }
            }
            EditorChip(stringRes(R.string.clip_editor__replace_all)) {
                keyboardManager.editClipSession { sessionNow ->
                    val (next, _) = sessionNow.body.replaceAll(sessionNow.find.text, sessionNow.replace.text)
                    sessionNow.copy(body = next, focus = EditorField.BODY, revision = sessionNow.revision + 1)
                }
            }
        }
        EditorBody(current)
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            EditorChip(stringRes(R.string.clip_editor__word)) {
                keyboardManager.editClipSession { it.copy(body = it.body.selectWord(), focus = EditorField.BODY, revision = it.revision + 1) }
            }
            EditorChip(stringRes(R.string.clip_editor__line)) {
                keyboardManager.editClipSession { it.copy(body = it.body.selectLine(), focus = EditorField.BODY, revision = it.revision + 1) }
            }
            EditorChip(stringRes(R.string.clip_editor__all)) {
                keyboardManager.editClipSession { it.copy(body = it.body.selectAll(), focus = EditorField.BODY, revision = it.revision + 1) }
            }
            EditorChip(stringRes(R.string.clip_editor__undo)) {
                keyboardManager.editClipSession { it.copy(body = it.body.undo(), focus = EditorField.BODY, revision = it.revision + 1) }
            }
            EditorChip(stringRes(R.string.clip_editor__redo)) {
                keyboardManager.editClipSession { it.copy(body = it.body.redo(), focus = EditorField.BODY, revision = it.revision + 1) }
            }
        }
    }
}

@Composable
private fun EditorBody(session: ClipEditorSession) {
    val context = LocalContext.current
    val keyboardManager by context.keyboardManager()
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val body = session.body
    val styled = buildAnnotatedString {
        val from = body.selectionMin
        val to = body.selectionMax
        append(body.text.substring(0, from))
        if (from != to) {
            withStyle(SpanStyle(background = EditorSelect, color = EditorText)) {
                append(body.text.substring(from, to))
            }
        }
        append(body.text.substring(to))
        if (body.text.isEmpty()) append("")
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(132.dp)
            .background(EditorKey)
            .border(1.dp, if (session.focus == EditorField.BODY) EditorText else EditorLine)
            .verticalScroll(rememberScrollState())
            .pointerInput(session.revision) {
                detectTapGestures(
                    onLongPress = {
                        keyboardManager.editClipSession {
                            it.copy(body = it.body.selectWord(), focus = EditorField.BODY, revision = it.revision + 1)
                        }
                    },
                    onTap = { position ->
                        val offset = layout?.getOffsetForPosition(position) ?: return@detectTapGestures
                        keyboardManager.editClipSession {
                            it.copy(body = it.body.setCaret(offset), focus = EditorField.BODY, revision = it.revision + 1)
                        }
                    },
                )
            }
            .padding(6.dp),
    ) {
        BasicText(
            text = if (body.text.isEmpty()) buildAnnotatedString { append(" ") } else styled,
            style = androidx.compose.ui.text.TextStyle(color = EditorText, fontSize = 15.sp),
            onTextLayout = { layout = it },
        )
    }
}

@Composable
private fun EditorChip(label: String, onClick: () -> Unit) {
    BasicText(
        text = label,
        style = androidx.compose.ui.text.TextStyle(color = EditorText, fontSize = 12.sp),
        modifier = Modifier
            .background(EditorKey)
            .border(1.dp, EditorLine)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

@Composable
private fun FieldChip(label: String, value: String, focused: Boolean, onClick: () -> Unit) {
    val shown = if (value.isEmpty()) label else value
    BasicText(
        text = shown,
        style = androidx.compose.ui.text.TextStyle(color = if (value.isEmpty()) EditorMuted else EditorText, fontSize = 12.sp),
        maxLines = 1,
        modifier = Modifier
            .heightIn(min = 24.dp)
            .background(EditorKey)
            .border(1.dp, if (focused) EditorText else EditorLine)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}
