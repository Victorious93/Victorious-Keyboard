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

package dev.patrickgold.florisboard.app.settings.clipboard

import android.content.Intent
import androidx.annotation.StringRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.app.LocalNavController
import dev.patrickgold.florisboard.app.Routes
import dev.patrickgold.florisboard.clipboardManager
import dev.patrickgold.florisboard.ime.clipboard.history.ClipCategory
import dev.patrickgold.florisboard.ime.clipboard.history.ClipEntry
import dev.patrickgold.florisboard.ime.clipboard.history.ClipFilter
import dev.patrickgold.florisboard.ime.clipboard.history.ClipSearch
import dev.patrickgold.florisboard.ime.clipboard.provider.ClipboardItem
import dev.patrickgold.florisboard.ime.clipboard.provider.ItemType
import dev.patrickgold.florisboard.ime.clipboard.vault.toClipEntry
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import dev.patrickgold.jetpref.datastore.model.observeAsState
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.florisboard.lib.android.showShortToast
import org.florisboard.lib.compose.FlorisInfoCard
import org.florisboard.lib.compose.LocalLocalizedDateTimeFormatter
import org.florisboard.lib.compose.stringRes

/** Hands the ids chosen in the manager to the backup screen ("Export selected"). Process memory only. */
object ClipboardExportSelection {
    @Volatile
    var pendingIds: Set<String>? = null
}

@StringRes
private fun ClipCategory.labelRes(): Int = when (this) {
    ClipCategory.GENERAL -> R.string.clipboard_manager__category_general
    ClipCategory.URL -> R.string.clipboard_manager__category_url
    ClipCategory.CODE -> R.string.clipboard_manager__category_code
    ClipCategory.SHELL -> R.string.clipboard_manager__category_shell
    ClipCategory.AI_PROMPT -> R.string.clipboard_manager__category_ai_prompt
    ClipCategory.JSON -> R.string.clipboard_manager__category_json
    ClipCategory.MARKDOWN -> R.string.clipboard_manager__category_markdown
    ClipCategory.SNIPPET -> R.string.clipboard_manager__category_snippet
}

@OptIn(ExperimentalFoundationApi::class, FlowPreview::class)
@Composable
fun ClipboardManagerScreen() = FlorisScreen {
    title = stringRes(R.string.clipboard_manager__title)
    previewFieldVisible = false
    // The list is a LazyColumn with a bounded height; the page itself must not scroll.
    scrollable = false

    val context = LocalContext.current
    val navController = LocalNavController.current
    val prefs by FlorisPreferenceStore
    val manager by context.clipboardManager()

    val history by manager.historyFlow.collectAsState()
    val unreadable by manager.unreadableCountFlow.collectAsState()
    val historyEnabled by prefs.clipboard.historyEnabled.observeAsState()

    var query by rememberSaveable { mutableStateOf("") }
    var pinnedOnly by rememberSaveable { mutableStateOf(false) }
    var favoritesOnly by rememberSaveable { mutableStateOf(false) }
    var categoryFilter by rememberSaveable { mutableStateOf<ClipCategory?>(null) }

    val selection = remember { mutableStateSetOf<String>() }
    var editing by remember { mutableStateOf<ClipboardItem?>(null) }
    var confirmDelete by remember { mutableStateOf<List<ClipboardItem>?>(null) }
    var confirmReset by remember { mutableStateOf(false) }
    var tagDialog by remember { mutableStateOf(false) }

    val itemsByUuid = remember(history) { history.all.associateBy { it.uuid } }
    val allEntries = remember(history) { history.all.map { it.toClipEntry() } }
    val filter = ClipFilter(category = categoryFilter, pinnedOnly = pinnedOnly, favoritesOnly = favoritesOnly)

    // Search runs off the main thread and is debounced: 500 long entries are scanned per query.
    val results by produceState(initialValue = emptyList<ClipEntry>(), allEntries, pinnedOnly, favoritesOnly, categoryFilter) {
        snapshotFlow { query }.debounce(150).collectLatest { q ->
            value = withContext(Dispatchers.Default) { ClipSearch.search(allEntries, q, filter) }
        }
    }

    // Drop selected ids that no longer exist (deleted elsewhere).
    LaunchedEffect(itemsByUuid) { selection.retainAll(itemsByUuid.keys) }

    content {
        if (!historyEnabled) {
            FlorisInfoCard(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                text = stringRes(R.string.clipboard_manager__history_disabled),
            )
        }
        if (unreadable > 0) {
            FlorisInfoCard(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                text = stringRes(R.string.clipboard_manager__unreadable_banner, "n" to unreadable),
            )
            TextButton(
                modifier = Modifier.padding(horizontal = 8.dp),
                onClick = { confirmReset = true },
            ) { Text(stringRes(R.string.clipboard_manager__reset_vault)) }
        }

        OutlinedTextField(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            placeholder = { Text(stringRes(R.string.clipboard_manager__search_hint)) },
        )

        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = !pinnedOnly && !favoritesOnly && categoryFilter == null,
                onClick = { pinnedOnly = false; favoritesOnly = false; categoryFilter = null },
                label = { Text(stringRes(R.string.clipboard_manager__filter_all)) },
            )
            FilterChip(
                selected = pinnedOnly,
                onClick = { pinnedOnly = !pinnedOnly },
                label = { Text(stringRes(R.string.clipboard_manager__filter_pinned)) },
            )
            FilterChip(
                selected = favoritesOnly,
                onClick = { favoritesOnly = !favoritesOnly },
                label = { Text(stringRes(R.string.clipboard_manager__filter_favorites)) },
            )
            for (c in ClipCategory.entries.filter { it != ClipCategory.SNIPPET }) {
                FilterChip(
                    selected = categoryFilter == c,
                    onClick = { categoryFilter = if (categoryFilter == c) null else c },
                    label = { Text(stringRes(c.labelRes())) },
                )
            }
        }

        if (selection.isEmpty()) {
            Text(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelMedium,
                text = stringRes(
                    R.string.clipboard_manager__count,
                    "shown" to results.size, "total" to allEntries.size,
                ),
            )
        } else {
            val selectedItems = selection.mapNotNull { itemsByUuid[it] }
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    text = stringRes(R.string.clipboard_manager__selected_count, "n" to selection.size),
                    style = MaterialTheme.typography.labelLarge,
                )
                TextButton(onClick = { selection.addAll(results.map { it.id }) }) {
                    Text(stringRes(R.string.clipboard_manager__select_all))
                }
                TextButton(onClick = { manager.setPinned(selectedItems, true) }) {
                    Text(stringRes(R.string.clipboard_manager__pin))
                }
                TextButton(onClick = { manager.setPinned(selectedItems, false) }) {
                    Text(stringRes(R.string.clipboard_manager__unpin))
                }
                TextButton(onClick = { tagDialog = true }) { Text(stringRes(R.string.clipboard_manager__add_tag)) }
                TextButton(onClick = {
                    ClipboardExportSelection.pendingIds = selection.toSet()
                    navController.navigate(Routes.Settings.ClipboardBackup)
                }) { Text(stringRes(R.string.clipboard_manager__export_selected)) }
                TextButton(onClick = { confirmDelete = selectedItems }) {
                    Text(stringRes(R.string.clipboard_manager__delete), color = MaterialTheme.colorScheme.error)
                }
            }
        }
        HorizontalDivider()

        if (results.isEmpty()) {
            Text(
                modifier = Modifier.padding(24.dp),
                text = stringRes(R.string.clipboard_manager__empty),
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        val formatter = LocalLocalizedDateTimeFormatter.current
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(results, key = { it.id }) { entry ->
                val item = itemsByUuid[entry.id] ?: return@items
                val isSelected = entry.id in selection
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .combinedClickable(
                            onClick = {
                                if (selection.isNotEmpty()) {
                                    if (!selection.add(entry.id)) selection.remove(entry.id)
                                } else {
                                    editing = item
                                }
                            },
                            onLongClick = { if (!selection.add(entry.id)) selection.remove(entry.id) },
                        )
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    if (selection.isNotEmpty()) {
                        Checkbox(checked = isSelected, onCheckedChange = null)
                    }
                    Column(modifier = Modifier.weight(1f).padding(start = if (selection.isNotEmpty()) 8.dp else 0.dp)) {
                        val preview = if (item.type == ItemType.TEXT) {
                            item.displayText(context).take(200)
                        } else {
                            item.stringRepresentation().take(200)
                        }
                        Text(text = preview, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        Text(
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            text = buildString {
                                append(formatter.format(Instant.ofEpochMilli(entry.createdAtMs)))
                                append(" · ").append(stringRes(entry.category.labelRes()))
                                if (item.type == ItemType.TEXT) {
                                    append(" · ").append(stringRes(R.string.clipboard_manager__meta, "chars" to entry.charCount))
                                }
                                if (entry.tags.isNotEmpty()) append(" · ").append(entry.tags.sorted().joinToString(" ") { "#$it" })
                            },
                        )
                    }
                    if (entry.favorite) Icon(Icons.Default.Star, contentDescription = null)
                    if (entry.pinned) Icon(Icons.Outlined.PushPin, contentDescription = null)
                }
                HorizontalDivider()
            }
        }
    }

    // --- dialogs ---------------------------------------------------------------------------------------------
    editing?.let { item ->
        EditClipDialog(
            item = item,
            onDismiss = { editing = null },
            onDelete = { editing = null; confirmDelete = listOf(item) },
        )
    }

    confirmDelete?.let { toDelete ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text(stringRes(R.string.clipboard_manager__delete_confirm_title)) },
            text = { Text(stringRes(R.string.clipboard_manager__delete_confirm_message, "n" to toDelete.size)) },
            confirmButton = {
                TextButton(onClick = {
                    manager.deleteClips(toDelete)
                    selection.clear()
                    confirmDelete = null
                }) { Text(stringRes(R.string.clipboard_manager__delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = null }) { Text(stringRes(R.string.action__cancel)) }
            },
        )
    }

    if (tagDialog) {
        var tag by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { tagDialog = false },
            title = { Text(stringRes(R.string.clipboard_manager__add_tag)) },
            text = {
                OutlinedTextField(value = tag, onValueChange = { tag = it }, singleLine = true)
            },
            confirmButton = {
                TextButton(onClick = {
                    manager.addTag(selection.mapNotNull { itemsByUuid[it] }, tag)
                    tagDialog = false
                }) { Text(stringRes(R.string.action__ok)) }
            },
            dismissButton = { TextButton(onClick = { tagDialog = false }) { Text(stringRes(R.string.action__cancel)) } },
        )
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text(stringRes(R.string.clipboard_manager__reset_vault_title)) },
            text = { Text(stringRes(R.string.clipboard_manager__reset_vault_message)) },
            confirmButton = {
                TextButton(onClick = { manager.resetVault(); confirmReset = false }) {
                    Text(stringRes(R.string.clipboard_manager__reset_vault_title), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text(stringRes(R.string.action__cancel)) } },
        )
    }
}

@Composable
private fun EditClipDialog(item: ClipboardItem, onDismiss: () -> Unit, onDelete: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val manager by context.clipboardManager()
    val isText = item.type == ItemType.TEXT
    val entry = remember(item) { item.toClipEntry() }

    var revealed by remember { mutableStateOf(!item.isSensitive) }
    var text by remember { mutableStateOf(item.text.orEmpty()) }
    var tags by remember { mutableStateOf(entry.tags.sorted().joinToString(", ")) }
    var category by remember { mutableStateOf(entry.category) }
    var favorite by remember { mutableStateOf(entry.favorite) }
    var pinned by remember { mutableStateOf(entry.pinned) }
    var invalid by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringRes(R.string.clipboard_manager__edit_title)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                if (isText) {
                    if (revealed) {
                        OutlinedTextField(
                            modifier = Modifier.fillMaxWidth(),
                            value = text,
                            onValueChange = { text = it; invalid = false },
                            label = { Text(stringRes(R.string.clipboard_manager__edit_text)) },
                            minLines = 3,
                            maxLines = 10,
                            isError = invalid,
                            supportingText = if (invalid) {
                                { Text(stringRes(R.string.clipboard_manager__edit_invalid)) }
                            } else null,
                        )
                    } else {
                        Text(stringRes(R.string.clipboard_manager__edit_sensitive_hidden))
                        TextButton(onClick = { revealed = true }) { Text(stringRes(R.string.clipboard_manager__edit_reveal)) }
                    }
                }
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    value = tags,
                    onValueChange = { tags = it },
                    label = { Text(stringRes(R.string.clipboard_manager__edit_tags)) },
                    singleLine = true,
                )
                Text(
                    modifier = Modifier.padding(top = 12.dp),
                    style = MaterialTheme.typography.labelMedium,
                    text = stringRes(R.string.clipboard_manager__edit_category),
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    for (c in ClipCategory.entries) {
                        FilterChip(
                            selected = category == c,
                            onClick = { category = c },
                            label = { Text(stringRes(c.labelRes())) },
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(modifier = Modifier.weight(1f), text = stringRes(R.string.clipboard_manager__edit_favorite))
                    Switch(checked = favorite, onCheckedChange = { favorite = it })
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(modifier = Modifier.weight(1f), text = stringRes(R.string.clipboard_manager__edit_pinned))
                    Switch(checked = pinned, onCheckedChange = { pinned = it })
                }
                if (isText && revealed) {
                    Row {
                        TextButton(onClick = {
                            manager.updatePrimaryClip(item)
                            scope.launch { context.showShortToast(R.string.clipboard_manager__copied) }
                        }) { Text(stringRes(R.string.clipboard_manager__copy)) }
                        TextButton(onClick = {
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, text)
                            }
                            context.startActivity(Intent.createChooser(send, null))
                        }) { Text(stringRes(R.string.clipboard_manager__share)) }
                        TextButton(onClick = onDelete) {
                            Icon(Icons.Default.Delete, contentDescription = null)
                            Text(stringRes(R.string.clipboard_manager__delete), color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val newText = if (isText && revealed && text != item.text) text else null
                val ok = manager.updateUserFields(
                    item = item,
                    newText = newText,
                    rawTags = tags.split(',', ' ', ';'),
                    category = category,
                    favorite = favorite,
                    pinned = pinned,
                )
                if (ok) onDismiss() else invalid = true
            }) { Text(stringRes(R.string.action__save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringRes(R.string.action__cancel)) } },
    )
}
