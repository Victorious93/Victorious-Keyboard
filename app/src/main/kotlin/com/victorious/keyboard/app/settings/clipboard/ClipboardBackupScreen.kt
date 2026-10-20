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

package com.victorious.keyboard.app.settings.clipboard

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.victorious.keyboard.R
import com.victorious.keyboard.app.settings.advanced.RadioListItem
import com.victorious.keyboard.clipboardManager
import com.victorious.keyboard.ime.clipboard.backup.BACKUP_FORMAT_VERSION
import com.victorious.keyboard.ime.clipboard.backup.BackupCrypto
import com.victorious.keyboard.ime.clipboard.backup.BackupException
import com.victorious.keyboard.ime.clipboard.backup.BackupKind
import com.victorious.keyboard.ime.clipboard.backup.BackupReader
import com.victorious.keyboard.ime.clipboard.backup.DropReason
import com.victorious.keyboard.ime.clipboard.backup.ImportMode
import com.victorious.keyboard.ime.clipboard.backup.ImportPlan
import com.victorious.keyboard.ime.clipboard.vault.BackupLibrary
import com.victorious.keyboard.ime.clipboard.vault.BackupLibraryEntry
import com.victorious.keyboard.ime.clipboard.vault.Confirmation
import com.victorious.keyboard.ime.clipboard.vault.ConfirmationRequiredException
import com.victorious.keyboard.ime.clipboard.vault.LaneImport
import com.victorious.keyboard.ime.clipboard.vault.ExportFormat
import com.victorious.keyboard.ime.clipboard.vault.ExportOptions
import com.victorious.keyboard.ime.clipboard.vault.Inspection
import com.victorious.keyboard.lib.compose.FlorisScreen
import java.io.IOException
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.florisboard.lib.android.stringRes
import org.florisboard.lib.compose.FlorisInfoCard
import org.florisboard.lib.compose.FlorisOutlinedBox
import org.florisboard.lib.compose.FlorisOutlinedButton
import org.florisboard.lib.compose.LocalLocalizedDateTimeFormatter
import org.florisboard.lib.compose.defaultFlorisOutlinedBox
import org.florisboard.lib.compose.stringRes

private sealed interface Busy {
    data object Idle : Busy
    data class Working(val messageRes: Int) : Busy
    data class Done(val message: String) : Busy
    data class Failed(val message: String) : Busy
}

private class LoadedBackup(val inspection: Inspection, val uri: Uri?, val displayName: String)

private class PendingFile(val bytes: ByteArray, val uri: Uri?, val displayName: String, val error: String?)

/**
 * Export and restore clipboard backups. Everything here is user-initiated and goes through the system file picker.
 * The screen only composes already-tested logic ([com.victorious.keyboard.ime.clipboard.vault.ClipboardBackupService],
 * [ImportPlan]) — it is compiled but **has not been run on a device**.
 */
@Composable
fun ClipboardBackupScreen() = FlorisScreen {
    title = stringRes(R.string.clipboard_backup__title)
    previewFieldVisible = false

    val context = LocalContext.current
    val resolver = context.contentResolver
    val manager by context.clipboardManager()
    val service = remember { manager.backupService() }
    val scope = rememberCoroutineScope()
    val formatter = LocalLocalizedDateTimeFormatter.current
    val libraryStore = remember { BackupLibraryStore(context) }

    // The ids chosen in the manager ("Export selected") are consumed exactly once.
    val onlyIds = remember { ClipboardExportSelection.pendingIds.also { ClipboardExportSelection.pendingIds = null } }

    var library by remember { mutableStateOf(BackupLibrary.EMPTY) }
    LaunchedEffect(Unit) { library = withContext(Dispatchers.IO) { libraryStore.load() } }

    fun updateLibrary(transform: (BackupLibrary) -> BackupLibrary) {
        val next = transform(library)
        library = next
        scope.launch(Dispatchers.IO) { runCatching { libraryStore.save(next) } }
    }

    // --- export state ---------------------------------------------------------------------------------------
    var format by remember { mutableStateOf(ExportFormat.ENCRYPTED_ZIP) }
    var password by remember { mutableStateOf("") }
    var password2 by remember { mutableStateOf("") }
    var includeSensitive by remember { mutableStateOf(false) }
    var includeVault by remember { mutableStateOf(false) }
    var exportState by remember { mutableStateOf<Busy>(Busy.Idle) }

    val passwordProblem: String? = when {
        format != ExportFormat.ENCRYPTED_ZIP -> null
        password.length < BackupCrypto.MIN_PASSWORD_LENGTH ->
            stringRes(R.string.clipboard_backup__password_hint, "n" to BackupCrypto.MIN_PASSWORD_LENGTH)
        password != password2 -> stringRes(R.string.clipboard_backup__password_mismatch)
        else -> null
    }

    fun runExport(destination: Uri) {
        scope.launch {
            exportState = Busy.Working(R.string.clipboard_backup__creating)
            try {
                val wantVault = includeVault && format == ExportFormat.ENCRYPTED_ZIP
                if (wantVault && manager.vaultLockedFlow.value) {
                    exportState = Busy.Failed(context.stringRes(R.string.clipboard__vault_locked_export))
                    return@launch
                }
                val vaultEntries = if (wantVault) manager.exportVaultEntries() else emptyList()
                val result = service.export(
                    ExportOptions(
                        format = format,
                        password = if (format == ExportFormat.ENCRYPTED_ZIP) password.toCharArray() else null,
                        includeSensitive = includeSensitive,
                        onlyIds = onlyIds,
                        includeVault = wantVault,
                        vaultEntries = vaultEntries,
                    )
                )
                withContext(Dispatchers.IO) { BackupIo.writeAndVerify(resolver, destination, result.bytes) }
                BackupIo.takePersistable(resolver, destination, write = true)
                if (format.restorable) {
                    updateLibrary {
                        it.add(
                            BackupLibraryEntry(
                                uri = destination.toString(),
                                displayName = BackupIo.queryMeta(resolver, destination).displayName,
                                addedAtMs = System.currentTimeMillis(),
                                sizeBytes = result.bytes.size.toLong(),
                                kind = when (format) {
                                    ExportFormat.ENCRYPTED_ZIP -> "encrypted"
                                    ExportFormat.ZIP -> "zip"
                                    else -> "json"
                                },
                                itemCount = result.itemCount,
                                formatVersion = BACKUP_FORMAT_VERSION,
                                createdAtMs = System.currentTimeMillis(),
                            )
                        )
                    }
                }
                password = ""
                password2 = ""
                exportState = Busy.Done(
                    context.stringRes(
                        R.string.clipboard_backup__created,
                        "n" to result.itemCount,
                        "sensitive" to result.skippedSensitive,
                        "unsupported" to result.skippedUnsupported,
                    )
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: BackupException) {
                exportState = Busy.Failed(e.message.orEmpty())
            } catch (e: IOException) {
                exportState = Busy.Failed(e.message.orEmpty())
            } catch (e: Exception) {
                exportState = Busy.Failed(context.stringRes(R.string.clipboard__vault_error))
            }
        }
    }

    val createLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(format.mime)) { uri ->
        if (uri != null) runExport(uri)
    }

    // --- restore state --------------------------------------------------------------------------------------
    var restoreBusy by remember { mutableStateOf<Busy>(Busy.Idle) }
    var pendingFile by remember { mutableStateOf<PendingFile?>(null) }
    var loaded by remember { mutableStateOf<LoadedBackup?>(null) }
    var mode by remember { mutableStateOf(ImportMode.MERGE) }
    var confirmPlan by remember { mutableStateOf<LaneImport?>(null) }
    var deleteTarget by remember { mutableStateOf<BackupLibraryEntry?>(null) }
    var planRefresh by remember { mutableIntStateOf(0) }

    fun inspect(bytes: ByteArray, pw: CharArray?, uri: Uri?, name: String) {
        scope.launch {
            restoreBusy = Busy.Working(R.string.clipboard_backup__reading)
            try {
                val inspection = service.inspect(bytes, pw)
                loaded = LoadedBackup(inspection, uri, name)
                pendingFile = null
                restoreBusy = Busy.Idle
                if (uri != null) {
                    updateLibrary { lib ->
                        lib.add(
                            (lib.find(uri.toString()) ?: BackupLibraryEntry(uri.toString(), name, System.currentTimeMillis()))
                                .copy(
                                    kind = when (inspection.kind) {
                                        BackupKind.ENCRYPTED -> "encrypted"
                                        BackupKind.ZIP -> "zip"
                                        else -> "json"
                                    },
                                    itemCount = inspection.parsed.manifest.itemCount,
                                    formatVersion = inspection.parsed.manifest.formatVersion,
                                    createdAtMs = inspection.parsed.manifest.createdAtMs,
                                    sizeBytes = bytes.size.toLong(),
                                )
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: BackupException.WrongPasswordOrTampered) {
                pendingFile = PendingFile(bytes, uri, name, e.message)
                restoreBusy = Busy.Idle
            } catch (e: BackupException) {
                loaded = null
                restoreBusy = Busy.Failed(context.stringRes(R.string.clipboard_backup__failed, "error" to e.message.orEmpty()))
            } catch (e: Exception) {
                loaded = null
                restoreBusy = Busy.Failed(context.stringRes(R.string.clipboard_backup__failed, "error" to ""))
            }
        }
    }

    fun openFile(uri: Uri) {
        scope.launch {
            restoreBusy = Busy.Working(R.string.clipboard_backup__reading)
            loaded = null
            try {
                val bytes = withContext(Dispatchers.IO) { BackupIo.readBytes(resolver, uri) }
                val name = BackupIo.queryMeta(resolver, uri).displayName
                if (BackupReader.detect(bytes) == BackupKind.ENCRYPTED) {
                    pendingFile = PendingFile(bytes, uri, name, null)
                    restoreBusy = Busy.Idle
                } else {
                    inspect(bytes, null, uri, name)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: BackupException) {
                restoreBusy = Busy.Failed(context.stringRes(R.string.clipboard_backup__failed, "error" to e.message.orEmpty()))
            } catch (e: Exception) {
                restoreBusy = Busy.Failed(context.stringRes(R.string.clipboard_backup__failed, "error" to (e.message ?: "")))
            }
        }
    }

    val openLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            BackupIo.takePersistable(resolver, uri, write = false)
            openFile(uri)
        }
    }

    val plan by produceState<LaneImport?>(initialValue = null, loaded, mode, planRefresh) {
        val l = loaded
        value = if (l == null) null else runCatching { service.previewLanes(l.inspection, mode) }.getOrNull()
    }

    fun runRestore(confirmation: Confirmation?) {
        val l = loaded ?: return
        scope.launch {
            restoreBusy = Busy.Working(R.string.clipboard_backup__restoring)
            try {
                val r = service.restore(l.inspection, mode, confirmation)
                loaded = null
                confirmPlan = null
                restoreBusy = Busy.Done(
                    context.stringRes(
                        R.string.clipboard_backup__restored,
                        "add" to r.added, "merge" to r.merged, "skip" to r.skippedDuplicates, "result" to r.finalCount,
                    )
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: ConfirmationRequiredException) {
                // Either consent was never given, or the history changed since it was: show the new numbers.
                planRefresh++
                confirmPlan = e.lanes ?: LaneImport(e.plan, null)
                restoreBusy = if (confirmation != null) {
                    Busy.Failed(context.stringRes(R.string.clipboard_backup__confirm_changed))
                } else Busy.Idle
            } catch (e: Exception) {
                // The repository applies atomically: a failure here means nothing was changed.
                restoreBusy = Busy.Failed(context.stringRes(R.string.clipboard_backup__failed, "error" to (e.message ?: "")))
            }
        }
    }

    content {
        // ============================ create ====================================================================
        FlorisOutlinedBox(
            modifier = Modifier.defaultFlorisOutlinedBox(),
            title = stringRes(R.string.clipboard_backup__create_title),
        ) {
            if (onlyIds != null) {
                Text(
                    modifier = Modifier.padding(8.dp),
                    text = stringRes(R.string.clipboard_backup__selected_only, "n" to onlyIds.size),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            RadioListItem(
                onClick = { format = ExportFormat.ENCRYPTED_ZIP },
                selected = format == ExportFormat.ENCRYPTED_ZIP,
                text = stringRes(R.string.clipboard_backup__format_encrypted),
                secondaryText = stringRes(R.string.clipboard_backup__format_encrypted_desc),
            )
            RadioListItem(
                onClick = { format = ExportFormat.ZIP },
                selected = format == ExportFormat.ZIP,
                text = stringRes(R.string.clipboard_backup__format_zip),
            )
            RadioListItem(
                onClick = { format = ExportFormat.JSON },
                selected = format == ExportFormat.JSON,
                text = stringRes(R.string.clipboard_backup__format_json),
            )
            RadioListItem(
                onClick = { format = ExportFormat.MARKDOWN },
                selected = format == ExportFormat.MARKDOWN,
                text = stringRes(R.string.clipboard_backup__format_markdown),
            )
            RadioListItem(
                onClick = { format = ExportFormat.TEXT },
                selected = format == ExportFormat.TEXT,
                text = stringRes(R.string.clipboard_backup__format_text),
            )
            if (format == ExportFormat.ENCRYPTED_ZIP) {
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                    value = password,
                    onValueChange = { password = it },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    label = { Text(stringRes(R.string.clipboard_backup__password)) },
                )
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                    value = password2,
                    onValueChange = { password2 = it },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    label = { Text(stringRes(R.string.clipboard_backup__password_repeat)) },
                    isError = password2.isNotEmpty() && password != password2,
                )
                passwordProblem?.let {
                    Text(modifier = Modifier.padding(horizontal = 12.dp), text = it, style = MaterialTheme.typography.bodySmall)
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(modifier = Modifier.weight(1f), text = stringRes(R.string.clipboard_backup__include_sensitive))
                Switch(checked = includeSensitive, onCheckedChange = { includeSensitive = it })
            }
            if (format == ExportFormat.ENCRYPTED_ZIP) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(text = stringRes(R.string.clipboard_backup__include_vault))
                        Text(
                            text = stringRes(R.string.clipboard_backup__include_vault_summary),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Switch(checked = includeVault, onCheckedChange = { includeVault = it })
                }
            }
            if (includeSensitive && format != ExportFormat.ENCRYPTED_ZIP) {
                FlorisInfoCard(
                    modifier = Modifier.padding(8.dp),
                    text = stringRes(R.string.clipboard_backup__include_sensitive_warning),
                )
            }
            FlorisOutlinedButton(
                modifier = Modifier.padding(8.dp),
                onClick = {
                    exportState = Busy.Idle
                    createLauncher.launch(service.suggestedFileName(format))
                },
                text = stringRes(R.string.clipboard_backup__create_button),
                enabled = passwordProblem == null && exportState !is Busy.Working,
            )
            BusyView(exportState)
        }

        // ============================ restore ===================================================================
        FlorisOutlinedBox(
            modifier = Modifier.defaultFlorisOutlinedBox(),
            title = stringRes(R.string.clipboard_backup__restore_title),
        ) {
            FlorisOutlinedButton(
                modifier = Modifier.padding(8.dp),
                onClick = { restoreBusy = Busy.Idle; openLauncher.launch(arrayOf("*/*")) },
                text = stringRes(R.string.clipboard_backup__choose_file),
                enabled = restoreBusy !is Busy.Working,
            )
            BusyView(restoreBusy)

            val l = loaded
            if (l != null) {
                val m = l.inspection.parsed.manifest
                Text(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.titleSmall,
                    text = stringRes(R.string.clipboard_backup__preview_title) + " · " + l.displayName,
                )
                Text(
                    modifier = Modifier.padding(horizontal = 12.dp),
                    style = MaterialTheme.typography.bodySmall,
                    text = stringRes(
                        R.string.clipboard_backup__preview_meta,
                        "items" to m.itemCount,
                        "created" to formatter.format(Instant.ofEpochMilli(m.createdAtMs)),
                        "version" to m.appVersion,
                    ),
                )
                if (l.inspection.parsed.unsupportedEntries > 0) {
                    Text(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        text = stringRes(
                            R.string.clipboard_backup__preview_unsupported,
                            "n" to l.inspection.parsed.unsupportedEntries,
                        ),
                    )
                }
                RadioListItem(
                    onClick = { mode = ImportMode.MERGE },
                    selected = mode == ImportMode.MERGE,
                    text = stringRes(R.string.clipboard_backup__mode_merge),
                    secondaryText = stringRes(R.string.clipboard_backup__mode_merge_desc),
                )
                RadioListItem(
                    onClick = { mode = ImportMode.SKIP_DUPLICATES },
                    selected = mode == ImportMode.SKIP_DUPLICATES,
                    text = stringRes(R.string.clipboard_backup__mode_skip),
                    secondaryText = stringRes(R.string.clipboard_backup__mode_skip_desc),
                )
                RadioListItem(
                    onClick = { mode = ImportMode.REPLACE },
                    selected = mode == ImportMode.REPLACE,
                    text = stringRes(R.string.clipboard_backup__mode_replace),
                    secondaryText = stringRes(R.string.clipboard_backup__mode_replace_desc),
                )
                val p = plan
                if (p != null) {
                    PlanSummary(stringRes(R.string.clipboard_zip__store_regular), p.regular)
                    p.vault?.let { PlanSummary(stringRes(R.string.clipboard_zip__store_vault), it) }
                }
                Row(modifier = Modifier.padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FlorisOutlinedButton(
                        onClick = {
                            val current = plan
                            if (current != null && current.requiresConfirmation) {
                                confirmPlan = current
                            } else {
                                runRestore(null)
                            }
                        },
                        text = stringRes(R.string.clipboard_backup__restore_button),
                        enabled = plan != null && restoreBusy !is Busy.Working,
                    )
                }
            }
        }

        // ============================ library ===================================================================
        FlorisOutlinedBox(
            modifier = Modifier.defaultFlorisOutlinedBox(),
            title = stringRes(R.string.clipboard_backup__library_title),
        ) {
            val availability by produceState(initialValue = emptyMap<String, Boolean>(), library) {
                value = withContext(Dispatchers.IO) {
                    library.entries.associate { it.uri to BackupIo.isReadable(resolver, it.uri.toUri()) }
                }
            }
            if (library.entries.isEmpty()) {
                Text(
                    modifier = Modifier.padding(12.dp),
                    text = stringRes(R.string.clipboard_backup__library_empty),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            for (entry in library.entries) {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
                    Text(text = entry.displayName, style = MaterialTheme.typography.titleSmall)
                    val ok = availability[entry.uri]
                    Text(
                        style = MaterialTheme.typography.bodySmall,
                        text = buildString {
                            entry.kind?.let { append(it).append(" · ") }
                            entry.itemCount?.let { append(it).append(" items · ") }
                            entry.createdAtMs?.let { append(formatter.format(Instant.ofEpochMilli(it))).append(" · ") }
                            append(
                                when (ok) {
                                    true -> stringRes(R.string.clipboard_backup__library_available)
                                    false -> stringRes(R.string.clipboard_backup__library_unavailable)
                                    null -> ""
                                }
                            )
                        },
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(
                            enabled = ok == true && restoreBusy !is Busy.Working,
                            onClick = { openFile(entry.uri.toUri()) },
                        ) { Text(stringRes(R.string.clipboard_backup__library_restore)) }
                        TextButton(onClick = {
                            BackupIo.release(resolver, entry.uri.toUri())
                            updateLibrary { it.remove(entry.uri) }
                        }) { Text(stringRes(R.string.clipboard_backup__library_remove)) }
                        TextButton(enabled = ok == true, onClick = { deleteTarget = entry }) {
                            Text(stringRes(R.string.clipboard_backup__library_delete), color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
            FlorisOutlinedButton(
                modifier = Modifier.padding(8.dp),
                onClick = { openLauncher.launch(arrayOf("*/*")) },
                text = stringRes(R.string.clipboard_backup__library_add),
            )
        }
    }

    // --- dialogs ---------------------------------------------------------------------------------------------
    pendingFile?.let { pf ->
        var pw by remember(pf) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { pendingFile = null },
            title = { Text(stringRes(R.string.clipboard_backup__password_prompt_title)) },
            text = {
                Column {
                    pf.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    OutlinedTextField(
                        value = pw,
                        onValueChange = { pw = it },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val chars = pw.toCharArray()
                    inspect(pf.bytes, chars, pf.uri, pf.displayName)
                }) { Text(stringRes(R.string.action__ok)) }
            },
            dismissButton = { TextButton(onClick = { pendingFile = null }) { Text(stringRes(R.string.action__cancel)) } },
        )
    }

    confirmPlan?.let { p ->
        AlertDialog(
            onDismissRequest = { confirmPlan = null },
            title = { Text(stringRes(R.string.clipboard_backup__confirm_title)) },
            text = {
                val vaultPlan = p.vault
                Text(
                    stringRes(
                        R.string.clipboard_backup__confirm_message,
                        "remove" to p.regular.removedExisting.size + (vaultPlan?.removedExisting?.size ?: 0),
                        "pinned" to p.regular.removedPinned + (vaultPlan?.removedPinned ?: 0),
                        "drop" to p.regular.droppedIncoming.count { it.reason == DropReason.OVER_CAPACITY } +
                            (vaultPlan?.droppedIncoming?.count { it.reason == DropReason.OVER_CAPACITY } ?: 0),
                        "cap" to com.victorious.keyboard.ime.clipboard.history.MAX_HISTORY_ENTRIES,
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    // Consent is bound to exactly what this dialog showed; if the history changed, the service asks again.
                    val consent = Confirmation.of(p.regular, p.vault)
                    confirmPlan = null
                    runRestore(consent)
                }) {
                    Text(stringRes(R.string.clipboard_backup__restore_button), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmPlan = null }) { Text(stringRes(R.string.action__cancel)) } },
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringRes(R.string.clipboard_backup__library_delete_title)) },
            text = { Text(target.displayName + "\n\n" + stringRes(R.string.clipboard_backup__library_delete_message)) },
            confirmButton = {
                TextButton(onClick = {
                    val deleted = BackupIo.delete(resolver, target.uri.toUri())
                    if (deleted) {
                        BackupIo.release(resolver, target.uri.toUri())
                        updateLibrary { it.remove(target.uri) }
                    } else {
                        restoreBusy = Busy.Failed(context.stringRes(R.string.clipboard_backup__library_delete_failed))
                    }
                    deleteTarget = null
                }) { Text(stringRes(R.string.clipboard_backup__library_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text(stringRes(R.string.action__cancel)) } },
        )
    }
}

@Composable
private fun PlanSummary(title: String, plan: ImportPlan) {
    Text(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        style = MaterialTheme.typography.bodyMedium,
        text = title + " — " + stringRes(
            R.string.clipboard_backup__plan_summary,
            "add" to plan.added.size, "merge" to plan.mergedExisting.size,
            "skip" to plan.skippedDuplicates.size, "remove" to plan.removedExisting.size,
            "pinned" to plan.removedPinned,
            "drop" to plan.droppedIncoming.count { it.reason == DropReason.OVER_CAPACITY },
            "cap" to com.victorious.keyboard.ime.clipboard.history.MAX_HISTORY_ENTRIES,
            "result" to plan.finalEntries.size,
        ),
    )
}

@Composable
private fun BusyView(state: Busy) {
    when (state) {
        Busy.Idle -> Unit
        is Busy.Working -> Column(modifier = Modifier.padding(12.dp)) {
            Text(stringRes(state.messageRes), style = MaterialTheme.typography.bodyMedium)
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
        }
        is Busy.Done -> Text(modifier = Modifier.padding(12.dp), text = state.message, style = MaterialTheme.typography.bodyMedium)
        is Busy.Failed -> Text(
            modifier = Modifier.padding(12.dp),
            text = state.message,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
