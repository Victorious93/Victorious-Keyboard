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

package com.victorious.keyboard.ime.snippets.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.victorious.keyboard.R
import com.victorious.keyboard.app.FlorisPreferenceStore
import com.victorious.keyboard.ime.ImeUiMode
import com.victorious.keyboard.ime.developer.ui.rememberDeveloperRowExtraHeight
import com.victorious.keyboard.ime.input.LocalInputFeedbackController
import com.victorious.keyboard.ime.keyboard.FlorisImeSizing
import com.victorious.keyboard.ime.media.KeyboardLikeButton
import com.victorious.keyboard.ime.snippets.Snippet
import com.victorious.keyboard.ime.snippets.SnippetInsertOutcome
import com.victorious.keyboard.ime.snippets.SnippetPickerModel
import com.victorious.keyboard.ime.snippets.SnippetPreview
import com.victorious.keyboard.ime.text.keyboard.TextKeyData
import com.victorious.keyboard.ime.theme.FlorisImeUi
import com.victorious.keyboard.keyboardManager
import com.victorious.keyboard.snippetManager
import dev.patrickgold.jetpref.datastore.model.observeAsState
import kotlinx.coroutines.launch
import org.florisboard.lib.android.AndroidKeyguardManager
import org.florisboard.lib.android.showShortToast
import org.florisboard.lib.android.systemService
import org.florisboard.lib.compose.florisHorizontalScroll
import org.florisboard.lib.compose.rippleClickable
import org.florisboard.lib.compose.stringRes
import org.florisboard.lib.snygg.SnyggQueryAttributes
import org.florisboard.lib.snygg.ui.SnyggBox
import org.florisboard.lib.snygg.ui.SnyggChip
import org.florisboard.lib.snygg.ui.SnyggColumn
import org.florisboard.lib.snygg.ui.SnyggIcon
import org.florisboard.lib.snygg.ui.SnyggIconButton
import org.florisboard.lib.snygg.ui.SnyggRow
import org.florisboard.lib.snygg.ui.SnyggText

// Reuses the clipboard panel's theme elements, so every shipped and user theme already styles this panel.
private val RowAttributes: SnyggQueryAttributes = mapOf("type" to "text")
private val ChipActive: SnyggQueryAttributes = mapOf("state" to "active")
private val ChipInactive: SnyggQueryAttributes = mapOf("state" to "inactive")
private const val PREVIEW_MAX_CODE_POINTS = 120

/**
 * The in-keyboard snippet picker: tap a snippet to insert it at the caret (replacing a selection), optionally narrowed by
 * tag. A thin shell; what is inserted, and the privacy rule for sensitive fields, is decided by the pure
 * `SnippetInsertion` core. Titles and previews come from the raw template and are only drawn, never logged or formatted
 * through `stringRes` arguments. Compiled and unit-tested in its pure parts; never rendered on a device.
 */
@Composable
fun SnippetInputLayout(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val keyboardManager by context.keyboardManager()
    val inputFeedbackController = LocalInputFeedbackController.current
    val keyguardManager = remember { context.systemService(AndroidKeyguardManager::class) }
    // Same gate as the clipboard panel: nothing personal on the lock screen.
    val deviceLocked = keyguardManager.let { it.isDeviceLocked || it.isKeyguardLocked }
    val developerRowExtra = rememberDeveloperRowExtraHeight()

    val insertLabel = stringRes(R.string.snippets__picker_insert)
    val tooLargeText = stringRes(R.string.snippets__picker_too_large)
    val nothingText = stringRes(R.string.snippets__picker_nothing)
    val failedText = stringRes(R.string.snippets__picker_failed)

    val onInsert: (Snippet) -> Unit = { snippet ->
        inputFeedbackController.keyPress()
        when (keyboardManager.insertSnippet(snippet)) {
            SnippetInsertOutcome.INSERTED -> keyboardManager.activeState.imeUiMode = ImeUiMode.TEXT
            SnippetInsertOutcome.TOO_LARGE -> scope.launch { context.showShortToast(tooLargeText) }
            SnippetInsertOutcome.NOTHING -> scope.launch { context.showShortToast(nothingText) }
            SnippetInsertOutcome.FAILED -> scope.launch { context.showShortToast(failedText) }
        }
    }

    SnyggColumn(
        modifier = modifier
            .fillMaxWidth()
            .height(FlorisImeSizing.imeUiHeight() + developerRowExtra),
    ) {
        SnyggRow(
            elementName = FlorisImeUi.ClipboardHeader.elementName,
            modifier = Modifier
                .fillMaxWidth()
                .height(FlorisImeSizing.smartbarHeight),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val sizeModifier = Modifier
                .sizeIn(maxHeight = FlorisImeSizing.smartbarHeight)
                .aspectRatio(1f)
            SnyggIconButton(
                elementName = FlorisImeUi.ClipboardHeaderButton.elementName,
                onClick = { keyboardManager.activeState.imeUiMode = ImeUiMode.TEXT },
                modifier = sizeModifier,
            ) {
                SnyggIcon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringRes(R.string.clip__back_to_text_input),
                )
            }
            SnyggText(
                elementName = FlorisImeUi.ClipboardHeaderText.elementName,
                modifier = Modifier.weight(1f),
                text = stringRes(R.string.snippets__title),
            )
            KeyboardLikeButton(
                modifier = sizeModifier,
                inputEventDispatcher = keyboardManager.inputEventDispatcher,
                keyData = TextKeyData.DELETE,
                elementName = FlorisImeUi.ClipboardHeaderButton.elementName,
            ) {
                SnyggIcon(
                    imageVector = Icons.AutoMirrored.Outlined.Backspace,
                    contentDescription = stringRes(R.string.snippets__picker_backspace),
                )
            }
        }
        when {
            deviceLocked -> MessageView(R.string.snippets__picker_locked_title, R.string.snippets__picker_locked_message)
            else -> SnippetListBody(insertLabel = insertLabel, onInsert = onInsert)
        }
    }
}

/**
 * The list part. Composed only while the device is unlocked: it is the only code that touches the snippet store, and
 * before the first unlock (Direct Boot) that storage is not available.
 */
@Composable
private fun SnippetListBody(insertLabel: String, onInsert: (Snippet) -> Unit) {
    val context = LocalContext.current
    val prefs by FlorisPreferenceStore
    val keyboardManager by context.keyboardManager()
    val snippetManager by context.snippetManager()
    val prefix by prefs.snippets.prefix.observeAsState()
    // The library loads asynchronously after app start: observe it, never read a one-off snapshot for drawing.
    val state by snippetManager.state.collectAsState()
    LaunchedEffect(Unit) { snippetManager.ensureLoaded() }
    var selectedTag by remember { mutableStateOf<String?>(null) }
    val tags = remember(state.library) { SnippetPickerModel.tags(state.library) }
    val activeTag = selectedTag?.takeIf { sel -> tags.any { it.equals(sel, ignoreCase = true) } }
    val items = remember(state.library, activeTag) { SnippetPickerModel.items(state.library, activeTag) }
    val listState = rememberLazyListState()
    LaunchedEffect(activeTag) { listState.scrollToItem(0) }

    when {
        // Not loaded yet is not "empty": draw nothing until the first load finishes.
        !state.loaded -> SnyggBox(
            elementName = FlorisImeUi.ClipboardContent.elementName,
            modifier = Modifier.fillMaxSize(),
        ) {}
        state.unreadable -> MessageView(R.string.snippets__picker_unreadable_title, R.string.snippets__picker_unreadable_message)
        items.isEmpty() -> MessageView(R.string.snippets__picker_empty_title, R.string.snippets__picker_empty_message)
        else -> SnyggBox(
            elementName = FlorisImeUi.ClipboardContent.elementName,
            modifier = Modifier.fillMaxSize(),
        ) {
            Column(modifier = Modifier.matchParentSize()) {
                if (tags.isNotEmpty()) {
                    SnyggRow(
                        elementName = FlorisImeUi.ClipboardFilterRow.elementName,
                        modifier = Modifier.fillMaxWidth(),
                        clickAndSemanticsModifier = Modifier.florisHorizontalScroll(),
                    ) {
                        for (tag in tags) {
                            val active = tag.equals(activeTag, ignoreCase = true)
                            SnyggChip(
                                elementName = FlorisImeUi.ClipboardFilterChip.elementName,
                                attributes = if (active) ChipActive else ChipInactive,
                                onClick = { selectedTag = if (active) null else tag },
                                text = tag,
                            )
                        }
                    }
                }
                SnyggBox(
                    elementName = FlorisImeUi.ClipboardGrid.elementName,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                ) {
                    LazyColumn(modifier = Modifier.fillMaxSize(), state = listState) {
                        items(items, key = { it.id }) { snippet ->
                            SnippetRow(snippet, prefix.text, insertLabel, onInsert) {
                                keyboardManager.openClipEditor(
                                    com.victorious.keyboard.ime.clipedit.ClipEditorOrigin.SNIPPET,
                                    snippet.id,
                                    snippet.body,
                                    sensitive = false,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SnippetRow(
    snippet: Snippet,
    prefix: String,
    insertLabel: String,
    onInsert: (Snippet) -> Unit,
    onEdit: () -> Unit,
) {
    val preview = remember(snippet.body) { SnippetPreview.of(snippet.body, PREVIEW_MAX_CODE_POINTS) }
    SnyggBox(
        elementName = FlorisImeUi.ClipboardItem.elementName,
        attributes = RowAttributes,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
        clickAndSemanticsModifier = Modifier.rippleClickable(onClickLabel = insertLabel, role = Role.Button) {
            onInsert(snippet)
        },
    ) {
        androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                SnyggText(text = snippet.title)
                snippet.abbreviation?.let {
                    SnyggText(elementName = FlorisImeUi.ClipboardItemDescription.elementName, text = prefix + it)
                }
                SnyggText(text = preview)
            }
            SnyggIconButton(onClick = onEdit) {
                SnyggIcon(imageVector = Icons.Filled.Edit)
            }
        }
    }
}

@Composable
private fun MessageView(@StringRes title: Int, @StringRes message: Int) {
    SnyggColumn(
        elementName = FlorisImeUi.ClipboardContent.elementName,
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SnyggText(text = stringRes(title))
        SnyggText(text = stringRes(message))
    }
}
