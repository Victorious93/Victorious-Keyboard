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

package dev.patrickgold.florisboard.app.settings.snippets

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.app.settings.clipboard.BackupIo
import dev.patrickgold.florisboard.ime.snippets.DecodeResult
import dev.patrickgold.florisboard.ime.snippets.ImportMode
import dev.patrickgold.florisboard.ime.snippets.LibraryError
import dev.patrickgold.florisboard.ime.snippets.LibraryResult
import dev.patrickgold.florisboard.ime.snippets.Snippet
import dev.patrickgold.florisboard.ime.snippets.SnippetBackup
import dev.patrickgold.florisboard.ime.snippets.SnippetFileError
import dev.patrickgold.florisboard.ime.snippets.SnippetIssue
import dev.patrickgold.florisboard.ime.snippets.SnippetLimits
import dev.patrickgold.florisboard.ime.snippets.SnippetTemplate
import dev.patrickgold.florisboard.ime.snippets.SnippetValidator
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import dev.patrickgold.florisboard.snippetManager
import dev.patrickgold.jetpref.datastore.model.observeAsState
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.florisboard.lib.android.showShortToast
import org.florisboard.lib.compose.FlorisInfoCard
import org.florisboard.lib.compose.stringRes

@StringRes
private fun LibraryError.messageRes(): Int = when (this) {
    LibraryError.INVALID -> R.string.snippets__error_invalid
    LibraryError.DUPLICATE_ID -> R.string.snippets__error_duplicate
    LibraryError.DUPLICATE_ABBREVIATION -> R.string.snippets__error_duplicate_abbreviation
    LibraryError.FULL -> R.string.snippets__error_full
    LibraryError.NOT_FOUND -> R.string.snippets__error_not_found
    LibraryError.SAVE_FAILED -> R.string.snippets__error_save_failed
}

@StringRes
private fun SnippetFileError.messageRes(): Int = when (this) {
    SnippetFileError.TOO_LARGE -> R.string.snippets__file_too_large
    SnippetFileError.MALFORMED -> R.string.snippets__file_malformed
    SnippetFileError.WRONG_FORMAT -> R.string.snippets__file_wrong_format
    SnippetFileError.NEWER_VERSION -> R.string.snippets__file_newer_version
    SnippetFileError.TOO_MANY -> R.string.snippets__file_too_many
}

@StringRes
private fun SnippetIssue.messageRes(): Int = when (this) {
    SnippetIssue.BAD_ID -> R.string.snippets__issue_bad_id
    SnippetIssue.BLANK_TITLE -> R.string.snippets__issue_blank_title
    SnippetIssue.TITLE_TOO_LONG -> R.string.snippets__issue_title_too_long
    SnippetIssue.BAD_TITLE_CHARS -> R.string.snippets__issue_bad_title_chars
    SnippetIssue.EMPTY_BODY -> R.string.snippets__issue_empty_body
    SnippetIssue.BODY_TOO_LONG -> R.string.snippets__issue_body_too_long
    SnippetIssue.BAD_BODY_CHARS -> R.string.snippets__issue_bad_body_chars
    SnippetIssue.BAD_ABBREVIATION -> R.string.snippets__issue_bad_abbreviation
    SnippetIssue.TOO_MANY_TAGS -> R.string.snippets__issue_too_many_tags
    SnippetIssue.BAD_TAG -> R.string.snippets__issue_bad_tag
}

private class PendingImport(val snippets: List<Snippet>, val invalid: Int)

@OptIn(FlowPreview::class)
@Composable
fun SnippetsScreen() = FlorisScreen {
    title = stringRes(R.string.snippets__title)
    previewFieldVisible = false
    // The list is a LazyColumn with a bounded height; the page itself must not scroll.
    scrollable = false

    val context = LocalContext.current
    val resolver = context.contentResolver
    val manager by context.snippetManager()
    val scope = rememberCoroutineScope()
    val state by manager.state.collectAsState()
    val prefs by FlorisPreferenceStore
    val prefix by prefs.snippets.prefix.observeAsState()
    val prefixText = prefix.text

    var query by rememberSaveable { mutableStateOf("") }
    var editing by remember { mutableStateOf<Snippet?>(null) }
    var creating by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<Snippet?>(null) }
    var pendingImport by remember { mutableStateOf<PendingImport?>(null) }
    // Resource id, resolved during composition. Reading it with LocalContext.current.getString
    // from the save callback is a Compose lint error (LocalContextGetResourceValueCall).
    var editorError by remember { mutableStateOf<Int?>(null) }

    // Search scans up to 500 bodies, so it runs off the main thread and is debounced.
    val results by produceState(initialValue = emptyList<Snippet>(), state.library) {
        snapshotFlow { query }.debounce(150).collectLatest { q ->
            value = withContext(Dispatchers.Default) { state.library.search(q) }
        }
    }

    val openLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val bytes = withContext(Dispatchers.IO) {
                runCatching { BackupIo.readBytes(resolver, uri, SnippetLimits.MAX_FILE_BYTES) }.getOrNull()
            }
            if (bytes == null) {
                context.showShortToast(R.string.snippets__import_unreadable)
                return@launch
            }
            when (val d = SnippetBackup.decode(bytes)) {
                is DecodeResult.Ok -> pendingImport = PendingImport(d.snippets, d.invalid.size)
                is DecodeResult.Failed -> context.showShortToast(d.error.messageRes())
            }
        }
    }

    val createLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching { BackupIo.writeAndVerify(resolver, uri, manager.exportBytes()) }.isSuccess
            }
            context.showShortToast(if (ok) R.string.snippets__export_done else R.string.snippets__export_failed)
        }
    }

    content {
        if (state.unreadable) {
            FlorisInfoCard(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                text = stringRes(R.string.snippets__unreadable_banner),
            )
        }
        if (state.invalidOnLoad > 0) {
            FlorisInfoCard(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                text = stringRes(R.string.snippets__invalid_banner, "n" to state.invalidOnLoad),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(onClick = { editorError = null; creating = true }) { Text(stringRes(R.string.snippets__add)) }
            OutlinedButton(onClick = { openLauncher.launch(arrayOf("*/*")) }) { Text(stringRes(R.string.snippets__import)) }
            OutlinedButton(
                enabled = state.library.snippets.isNotEmpty(),
                onClick = { createLauncher.launch("snippets.json") },
            ) { Text(stringRes(R.string.snippets__export)) }
        }
        Text(
            modifier = Modifier.padding(horizontal = 16.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            text = stringRes(R.string.snippets__export_warning),
        )
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            label = { Text(stringRes(R.string.snippets__search)) },
        )
        if (state.library.snippets.isEmpty() && state.loaded) {
            Text(
                modifier = Modifier.padding(16.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                text = stringRes(R.string.snippets__empty),
            )
        }
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(results, key = { it.id }) { snippet ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { editorError = null; editing = snippet }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            modifier = Modifier.weight(1f, fill = false),
                            text = snippet.title,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        snippet.abbreviation?.let {
                            Text(
                                modifier = Modifier.padding(start = 8.dp),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                text = prefixText + it,
                            )
                        }
                        if (!snippet.enabled) {
                            Text(
                                modifier = Modifier.padding(start = 8.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                text = stringRes(R.string.snippets__disabled),
                            )
                        }
                    }
                    Text(
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        text = snippet.body.take(160),
                    )
                }
            }
        }
    }

    // --- dialogs -----------------------------------------------------------------------------------------------
    if (creating || editing != null) {
        val initial = editing
        SnippetEditorDialog(
            initial = initial,
            prefixText = prefixText,
            serverError = editorError?.let { stringRes(it) },
            onDismiss = { creating = false; editing = null },
            onSave = { s ->
                scope.launch {
                    val r = if (initial == null) manager.add(s) else manager.update(s)
                    when (r) {
                        is LibraryResult.Ok -> { creating = false; editing = null }
                        is LibraryResult.Rejected -> editorError = r.error.messageRes()
                    }
                }
            },
            onDelete = initial?.let { snippet -> { confirmDelete = snippet } },
        )
    }

    confirmDelete?.let { snippet ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text(stringRes(R.string.snippets__delete_title)) },
            // User text is never passed through the {name} formatter: a value containing its own placeholder name
            // would make that formatter loop forever.
            text = {
                Column {
                    Text(stringRes(R.string.snippets__delete_text))
                    Text(modifier = Modifier.padding(top = 8.dp), fontWeight = FontWeight.SemiBold, text = snippet.title)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        manager.remove(snippet.id)
                        confirmDelete = null
                        editing = null
                    }
                }) { Text(stringRes(R.string.snippets__delete)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text(stringRes(R.string.action__cancel)) } },
        )
    }

    pendingImport?.let { import ->
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            title = { Text(stringRes(R.string.snippets__import_title)) },
            text = {
                Text(
                    stringRes(R.string.snippets__import_text, "n" to import.snippets.size) +
                        if (import.invalid > 0) "\n" + stringRes(R.string.snippets__import_invalid, "n" to import.invalid) else "",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { finishImport(context, manager.importMerge(import.snippets, ImportMode.OVERWRITE_BY_ID)); pendingImport = null }
                }) { Text(stringRes(R.string.snippets__import_overwrite)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    scope.launch { finishImport(context, manager.importMerge(import.snippets, ImportMode.SKIP_EXISTING)); pendingImport = null }
                }) { Text(stringRes(R.string.snippets__import_skip)) }
            },
        )
    }
}

private suspend fun finishImport(context: android.content.Context, outcome: dev.patrickgold.florisboard.ime.snippets.ImportOutcome?) {
    if (outcome == null) {
        context.showShortToast(R.string.snippets__error_save_failed)
    } else {
        context.showShortToast(
            R.string.snippets__import_result,
            "added" to outcome.added, "updated" to outcome.updated, "skipped" to outcome.skipped.size,
        )
    }
}

@Composable
private fun SnippetEditorDialog(
    initial: Snippet?,
    prefixText: String,
    serverError: String?,
    onDismiss: () -> Unit,
    onSave: (Snippet) -> Unit,
    onDelete: (() -> Unit)?,
) {
    var title by rememberSaveable { mutableStateOf(initial?.title.orEmpty()) }
    var abbreviation by rememberSaveable { mutableStateOf(initial?.abbreviation.orEmpty()) }
    var body by rememberSaveable { mutableStateOf(initial?.body.orEmpty()) }
    var tags by rememberSaveable { mutableStateOf(initial?.tags.orEmpty().joinToString(", ")) }
    var enabled by rememberSaveable { mutableStateOf(initial?.enabled ?: true) }
    var issues by remember { mutableStateOf<List<SnippetIssue>>(emptyList()) }
    val unknown = remember(body) { SnippetTemplate.unknownPlaceholders(body) }

    fun build() = Snippet(
        id = initial?.id ?: UUID.randomUUID().toString(),
        title = title.trim(),
        body = body,
        abbreviation = abbreviation.trim().ifEmpty { null },
        tags = tags.split(',', ' ').map { it.trim() }.filter { it.isNotEmpty() }.distinct(),
        enabled = enabled,
        updatedAtMillis = System.currentTimeMillis(),
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringRes(if (initial == null) R.string.snippets__new_title else R.string.snippets__edit_title)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = title,
                    onValueChange = { title = it },
                    singleLine = true,
                    label = { Text(stringRes(R.string.snippets__field_title)) },
                )
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    value = abbreviation,
                    onValueChange = { abbreviation = it },
                    singleLine = true,
                    label = { Text(stringRes(R.string.snippets__field_abbreviation)) },
                    prefix = { Text(prefixText) },
                )
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    value = body,
                    onValueChange = { body = it },
                    minLines = 3,
                    maxLines = 10,
                    label = { Text(stringRes(R.string.snippets__field_body)) },
                )
                Text(
                    modifier = Modifier.padding(top = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    text = stringRes(R.string.snippets__placeholder_help),
                )
                if (unknown.isNotEmpty()) {
                    Text(
                        modifier = Modifier.padding(top = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.tertiary,
                        text = stringRes(R.string.snippets__unknown_placeholders) + " " + unknown.joinToString(", ") { "{{$it}}" },
                    )
                }
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    value = tags,
                    onValueChange = { tags = it },
                    singleLine = true,
                    label = { Text(stringRes(R.string.snippets__field_tags)) },
                )
                Row(modifier = Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(modifier = Modifier.weight(1f), text = stringRes(R.string.snippets__field_enabled))
                    Switch(checked = enabled, onCheckedChange = { enabled = it })
                }
                for (issue in issues) {
                    Text(
                        modifier = Modifier.padding(top = 4.dp),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelMedium,
                        text = stringRes(issue.messageRes()),
                    )
                }
                if (serverError != null) {
                    Text(
                        modifier = Modifier.padding(top = 4.dp),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelMedium,
                        text = serverError,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val s = build()
                val v = SnippetValidator.validate(s)
                issues = v.errors
                if (v.isValid) onSave(s)
            }) { Text(stringRes(R.string.action__save)) }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) { Text(stringRes(R.string.snippets__delete)) }
                }
                TextButton(onClick = onDismiss) { Text(stringRes(R.string.action__cancel)) }
            }
        },
    )
}
