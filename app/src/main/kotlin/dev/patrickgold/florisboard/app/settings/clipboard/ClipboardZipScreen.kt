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
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.settings.advanced.RadioListItem
import dev.patrickgold.florisboard.clipboardManager
import dev.patrickgold.florisboard.ime.clipboard.backup.BackupCrypto
import dev.patrickgold.florisboard.ime.clipboard.backup.BackupException
import dev.patrickgold.florisboard.ime.clipboard.backup.BackupWriter
import dev.patrickgold.florisboard.ime.clipboard.backup.ImportMode
import dev.patrickgold.florisboard.ime.clipboard.backup.ZipExportPolicy
import dev.patrickgold.florisboard.ime.clipboard.backup.ZipScope
import dev.patrickgold.florisboard.ime.clipboard.vault.Confirmation
import dev.patrickgold.florisboard.ime.clipboard.vault.ConfirmationRequiredException
import dev.patrickgold.florisboard.ime.clipboard.vault.Inspection
import dev.patrickgold.florisboard.ime.clipboard.vault.LaneImport
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
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

private sealed interface ZipBusy {
    data object Idle : ZipBusy
    data class Working(val messageRes: Int) : ZipBusy
    data class Done(val message: String) : ZipBusy
    data class Failed(val message: String) : ZipBusy
}

private class ZipLoaded(val inspection: Inspection, val displayName: String)

private class ZipPending(val bytes: ByteArray, val displayName: String, val error: String?)

/**
 * Export and import a clipboard ZIP through the system file picker.
 * Vault rows require a password. This screen is compiled and has not been run on a device.
 */
@Composable
fun ClipboardZipScreen() = FlorisScreen {
    title = stringRes(R.string.clipboard_zip__title)
    previewFieldVisible = false

    val context = LocalContext.current
    val resolver = context.contentResolver
    val manager by context.clipboardManager()
    val service = remember { manager.backupService() }
    val scope = rememberCoroutineScope()
    val formatter = LocalLocalizedDateTimeFormatter.current

    var zipScope by remember { mutableStateOf(ZipScope.REGULAR) }
    var encryptOptional by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    var password2 by remember { mutableStateOf("") }
    var exportState by remember { mutableStateOf<ZipBusy>(ZipBusy.Idle) }
    val mustEncrypt = ZipExportPolicy.requiresPassword(zipScope)
    val encrypt = mustEncrypt || encryptOptional
    val passwordProblem: String? = when {
        !encrypt -> null
        password.length < BackupCrypto.MIN_PASSWORD_LENGTH ->
            stringRes(R.string.clipboard_backup__password_hint, "n" to BackupCrypto.MIN_PASSWORD_LENGTH)
        password != password2 -> stringRes(R.string.clipboard_backup__password_mismatch)
        else -> null
    }

    fun runExport(destination: Uri) {
        scope.launch {
            exportState = ZipBusy.Working(R.string.clipboard_backup__creating)
            val chars = if (encrypt) password.toCharArray() else null
            try {
                if (zipScope.includesVault() && manager.vaultLockedFlow.value) {
                    exportState = ZipBusy.Failed(context.stringRes(R.string.clipboard__vault_locked_export))
                    return@launch
                }
                val result = service.exportZip(zipScope, chars)
                withContext(Dispatchers.IO) { BackupIo.writeAndVerify(resolver, destination, result.bytes) }
                password = ""
                password2 = ""
                exportState = ZipBusy.Done(
                    context.stringRes(
                        R.string.clipboard_zip__exported,
                        "regular" to result.regularCount,
                        "vault" to result.vaultCount,
                    )
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: BackupException) {
                exportState = ZipBusy.Failed(e.message.orEmpty())
            } catch (e: IOException) {
                exportState = ZipBusy.Failed(e.message.orEmpty())
            } catch (e: Exception) {
                exportState = ZipBusy.Failed(context.stringRes(R.string.clipboard__vault_error))
            } finally {
                chars?.fill('\u0000')
            }
        }
    }

    val createLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(BackupWriter.MIME_ZIP),
    ) { uri ->
        if (uri != null) runExport(uri)
    }

    var restoreBusy by remember { mutableStateOf<ZipBusy>(ZipBusy.Idle) }
    var pendingFile by remember { mutableStateOf<ZipPending?>(null) }
    var loaded by remember { mutableStateOf<ZipLoaded?>(null) }
    var mode by remember { mutableStateOf(ImportMode.MERGE) }
    var confirmPlan by remember { mutableStateOf<LaneImport?>(null) }
    var planRefresh by remember { mutableIntStateOf(0) }

    fun inspect(bytes: ByteArray, pw: CharArray?, name: String) {
        scope.launch {
            restoreBusy = ZipBusy.Working(R.string.clipboard_backup__reading)
            try {
                val inspection = service.inspect(bytes, pw)
                loaded = ZipLoaded(inspection, name)
                pendingFile = null
                restoreBusy = ZipBusy.Idle
            } catch (e: CancellationException) {
                throw e
            } catch (e: BackupException.PasswordRequired) {
                pendingFile = ZipPending(bytes, name, null)
                restoreBusy = ZipBusy.Idle
            } catch (e: BackupException.WrongPasswordOrTampered) {
                pendingFile = ZipPending(bytes, name, e.message)
                restoreBusy = ZipBusy.Idle
            } catch (e: BackupException) {
                loaded = null
                restoreBusy = ZipBusy.Failed(
                    context.stringRes(R.string.clipboard_backup__failed, "error" to e.message.orEmpty()),
                )
            } catch (e: Exception) {
                loaded = null
                restoreBusy = ZipBusy.Failed(context.stringRes(R.string.clipboard_backup__failed, "error" to ""))
            } finally {
                pw?.fill('\u0000')
            }
        }
    }

    fun openFile(uri: Uri) {
        scope.launch {
            restoreBusy = ZipBusy.Working(R.string.clipboard_backup__reading)
            loaded = null
            try {
                val bytes = withContext(Dispatchers.IO) { BackupIo.readBytes(resolver, uri) }
                val name = BackupIo.queryMeta(resolver, uri).displayName
                inspect(bytes, null, name)
            } catch (e: CancellationException) {
                throw e
            } catch (e: BackupException) {
                restoreBusy = ZipBusy.Failed(
                    context.stringRes(R.string.clipboard_backup__failed, "error" to e.message.orEmpty()),
                )
            } catch (e: Exception) {
                restoreBusy = ZipBusy.Failed(
                    context.stringRes(R.string.clipboard_backup__failed, "error" to (e.message ?: "")),
                )
            }
        }
    }

    val openLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) openFile(uri)
    }

    val plan by produceState<LaneImport?>(initialValue = null, loaded, mode, planRefresh) {
        val current = loaded
        value = if (current == null) null else runCatching { service.previewLanes(current.inspection, mode) }.getOrNull()
    }

    fun runRestore(confirmation: Confirmation?) {
        val current = loaded ?: return
        scope.launch {
            restoreBusy = ZipBusy.Working(R.string.clipboard_backup__restoring)
            try {
                val result = service.restore(current.inspection, mode, confirmation)
                loaded = null
                confirmPlan = null
                restoreBusy = ZipBusy.Done(
                    context.stringRes(
                        R.string.clipboard_backup__restored,
                        "add" to result.added,
                        "merge" to result.merged,
                        "skip" to result.skippedDuplicates,
                        "result" to result.finalCount,
                    )
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: ConfirmationRequiredException) {
                planRefresh++
                confirmPlan = e.lanes ?: LaneImport(e.plan, null)
                restoreBusy = if (confirmation != null) {
                    ZipBusy.Failed(context.stringRes(R.string.clipboard_backup__confirm_changed))
                } else {
                    ZipBusy.Idle
                }
            } catch (e: Exception) {
                restoreBusy = ZipBusy.Failed(
                    context.stringRes(R.string.clipboard_backup__failed, "error" to (e.message ?: "")),
                )
            }
        }
    }

    content {
        FlorisOutlinedBox(
            modifier = Modifier.defaultFlorisOutlinedBox(),
            title = stringRes(R.string.clipboard_zip__export_title),
        ) {
            RadioListItem(
                onClick = { zipScope = ZipScope.REGULAR },
                selected = zipScope == ZipScope.REGULAR,
                text = stringRes(R.string.clipboard_zip__scope_regular),
                secondaryText = stringRes(R.string.clipboard_zip__scope_regular_desc),
            )
            RadioListItem(
                onClick = { zipScope = ZipScope.VAULT },
                selected = zipScope == ZipScope.VAULT,
                text = stringRes(R.string.clipboard_zip__scope_vault),
                secondaryText = stringRes(R.string.clipboard_zip__scope_vault_desc),
            )
            RadioListItem(
                onClick = { zipScope = ZipScope.BOTH },
                selected = zipScope == ZipScope.BOTH,
                text = stringRes(R.string.clipboard_zip__scope_both),
                secondaryText = stringRes(R.string.clipboard_zip__scope_both_desc),
            )
            Text(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                style = MaterialTheme.typography.bodySmall,
                text = stringRes(R.string.clipboard_zip__images_note),
            )
            if (mustEncrypt) {
                FlorisInfoCard(
                    modifier = Modifier.padding(8.dp),
                    text = stringRes(R.string.clipboard_zip__vault_warning),
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(text = stringRes(R.string.clipboard_zip__encrypt))
                    Switch(checked = encryptOptional, onCheckedChange = { encryptOptional = it })
                }
            }
            if (encrypt) {
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
                    Text(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        text = it,
                    )
                }
            }
            FlorisOutlinedButton(
                modifier = Modifier.padding(8.dp),
                onClick = {
                    exportState = ZipBusy.Idle
                    createLauncher.launch(service.suggestedZipName())
                },
                text = stringRes(R.string.clipboard_zip__export_button),
                enabled = passwordProblem == null && exportState !is ZipBusy.Working,
            )
            ZipBusyView(exportState)
        }

        FlorisOutlinedBox(
            modifier = Modifier.defaultFlorisOutlinedBox(),
            title = stringRes(R.string.clipboard_zip__import_title),
        ) {
            FlorisOutlinedButton(
                modifier = Modifier.padding(8.dp),
                onClick = { openLauncher.launch(arrayOf(BackupWriter.MIME_ZIP, "*/*")) },
                text = stringRes(R.string.clipboard_zip__import_button),
                enabled = restoreBusy !is ZipBusy.Working,
            )
            ZipBusyView(restoreBusy)
            val current = loaded
            if (current != null) {
                val manifest = current.inspection.parsed.manifest
                Text(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.titleSmall,
                    text = current.displayName,
                )
                Text(
                    modifier = Modifier.padding(horizontal = 12.dp),
                    style = MaterialTheme.typography.bodySmall,
                    text = stringRes(
                        R.string.clipboard_backup__preview_meta,
                        "items" to manifest.itemCount,
                        "created" to formatter.format(Instant.ofEpochMilli(manifest.createdAtMs)),
                        "version" to manifest.appVersion,
                    ),
                )
                RadioListItem(
                    onClick = { mode = ImportMode.MERGE },
                    selected = mode == ImportMode.MERGE,
                    text = stringRes(R.string.clipboard_backup__mode_merge),
                    secondaryText = stringRes(R.string.clipboard_backup__mode_merge_desc),
                )
                RadioListItem(
                    onClick = { mode = ImportMode.REPLACE },
                    selected = mode == ImportMode.REPLACE,
                    text = stringRes(R.string.clipboard_backup__mode_replace),
                    secondaryText = stringRes(R.string.clipboard_backup__mode_replace_desc),
                )
                RadioListItem(
                    onClick = { mode = ImportMode.SKIP_DUPLICATES },
                    selected = mode == ImportMode.SKIP_DUPLICATES,
                    text = stringRes(R.string.clipboard_backup__mode_skip),
                    secondaryText = stringRes(R.string.clipboard_backup__mode_skip_desc),
                )
                val currentPlan = plan
                if (currentPlan != null) {
                    ZipPlanLine(stringRes(R.string.clipboard_zip__store_regular), currentPlan.regular.added.size, currentPlan.regular.removedExisting.size)
                    currentPlan.vault?.let {
                        ZipPlanLine(stringRes(R.string.clipboard_zip__store_vault), it.added.size, it.removedExisting.size)
                    }
                }
                FlorisOutlinedButton(
                    modifier = Modifier.padding(8.dp),
                    onClick = {
                        val chosen = plan
                        if (chosen != null && chosen.requiresConfirmation) confirmPlan = chosen else runRestore(null)
                    },
                    text = stringRes(R.string.clipboard_backup__restore_button),
                    enabled = plan != null && restoreBusy !is ZipBusy.Working,
                )
            }
        }
    }

    pendingFile?.let { pending ->
        var pw by remember(pending) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { pendingFile = null },
            title = { Text(stringRes(R.string.clipboard_backup__password_prompt_title)) },
            text = {
                Column {
                    pending.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    OutlinedTextField(
                        value = pw,
                        onValueChange = { pw = it },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { inspect(pending.bytes, pw.toCharArray(), pending.displayName) }) {
                    Text(stringRes(R.string.action__ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingFile = null }) { Text(stringRes(R.string.action__cancel)) }
            },
        )
    }

    confirmPlan?.let { chosen ->
        val vaultPlan = chosen.vault
        AlertDialog(
            onDismissRequest = { confirmPlan = null },
            title = { Text(stringRes(R.string.clipboard_backup__confirm_title)) },
            text = {
                Text(
                    stringRes(
                        R.string.clipboard_backup__confirm_message,
                        "remove" to chosen.regular.removedExisting.size + (vaultPlan?.removedExisting?.size ?: 0),
                        "pinned" to chosen.regular.removedPinned + (vaultPlan?.removedPinned ?: 0),
                        "drop" to chosen.regular.droppedIncoming.count { it.reason == dev.patrickgold.florisboard.ime.clipboard.backup.DropReason.OVER_CAPACITY } +
                            (vaultPlan?.droppedIncoming?.count { it.reason == dev.patrickgold.florisboard.ime.clipboard.backup.DropReason.OVER_CAPACITY } ?: 0),
                        "cap" to dev.patrickgold.florisboard.ime.clipboard.history.MAX_HISTORY_ENTRIES,
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val consent = Confirmation.of(chosen.regular, chosen.vault)
                    confirmPlan = null
                    runRestore(consent)
                }) {
                    Text(stringRes(R.string.clipboard_backup__restore_button), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmPlan = null }) { Text(stringRes(R.string.action__cancel)) }
            },
        )
    }
}

@Composable
private fun ZipPlanLine(title: String, added: Int, removed: Int) {
    Text(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
        style = MaterialTheme.typography.bodyMedium,
        text = "$title: +$added, −$removed",
    )
}

@Composable
private fun ZipBusyView(state: ZipBusy) {
    when (state) {
        ZipBusy.Idle -> Unit
        is ZipBusy.Working -> Column(modifier = Modifier.padding(12.dp)) {
            Text(stringRes(state.messageRes), style = MaterialTheme.typography.bodyMedium)
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
        }
        is ZipBusy.Done -> Text(
            modifier = Modifier.padding(12.dp),
            text = state.message,
            style = MaterialTheme.typography.bodyMedium,
        )
        is ZipBusy.Failed -> Text(
            modifier = Modifier.padding(12.dp),
            text = state.message,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
