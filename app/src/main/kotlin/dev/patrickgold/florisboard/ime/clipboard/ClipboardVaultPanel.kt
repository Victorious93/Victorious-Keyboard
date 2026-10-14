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

package dev.patrickgold.florisboard.ime.clipboard

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.clipboardManager
import dev.patrickgold.florisboard.keyboardManager
import dev.patrickgold.florisboard.ime.clipboard.lanes.VaultMask
import dev.patrickgold.florisboard.ime.clipboard.lanes.VaultPlain
import dev.patrickgold.florisboard.ime.clipboard.lanes.VaultSession
import dev.patrickgold.florisboard.ime.theme.FlorisImeUi
import dev.patrickgold.jetpref.datastore.model.observeAsState
import kotlinx.coroutines.delay
import org.florisboard.lib.compose.florisVerticalScroll
import org.florisboard.lib.compose.stringRes
import org.florisboard.lib.snygg.ui.SnyggButton
import org.florisboard.lib.snygg.ui.SnyggColumn
import org.florisboard.lib.snygg.ui.SnyggIcon
import org.florisboard.lib.snygg.ui.SnyggIconButton
import org.florisboard.lib.snygg.ui.SnyggRow
import org.florisboard.lib.snygg.ui.SnyggText

@Composable
fun ClipboardVaultPanel(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val clipboardManager by context.clipboardManager()
    val keyboardManager by context.keyboardManager()
    val prefs by FlorisPreferenceStore
    val minutes by prefs.clipboard.vaultLockAfterMinutes.observeAsState()
    val unlockedUntil by VaultSession.unlockedUntilMs.collectAsState()
    val locked by clipboardManager.vaultLockedFlow.collectAsState()
    val count by clipboardManager.vaultCountFlow.collectAsState()
    val items by clipboardManager.vaultItemsFlow.collectAsState()
    val revealed = remember { mutableStateSetOf<String>() }

    LaunchedEffect(unlockedUntil, minutes) {
        while (true) {
            clipboardManager.syncVaultVisibility()
            delay(1_000)
        }
    }
    LaunchedEffect(locked) {
        if (locked) revealed.clear()
    }

    if (locked) {
        SnyggColumn(
            FlorisImeUi.ClipboardContent.elementName,
            modifier = modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            SnyggIcon(imageVector = Icons.Filled.Lock)
            SnyggText(
                modifier = Modifier.padding(top = 8.dp),
                text = stringRes(R.string.clipboard__vault_locked_title),
            )
            SnyggText(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                text = stringRes(R.string.clipboard__vault_locked_message),
            )
            SnyggText(text = stringRes(R.string.clipboard__vault_count, "n" to count))
            SnyggButton(
                elementName = FlorisImeUi.ClipboardHistoryDisabledButton.elementName,
                onClick = {
                    context.startActivity(
                        Intent(context, VaultUnlockActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            putExtra(VaultUnlockActivity.EXTRA_TIMEOUT_MS, minutes.coerceIn(1, 60) * 60_000L)
                        },
                    )
                },
                modifier = Modifier.padding(top = 8.dp),
            ) {
                SnyggText(text = stringRes(R.string.clipboard__vault_unlock))
            }
        }
        return
    }

    if (items.isEmpty()) {
        SnyggColumn(
            FlorisImeUi.ClipboardContent.elementName,
            modifier = modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            SnyggText(text = stringRes(R.string.clipboard__vault_empty))
        }
        return
    }

    SnyggColumn(
        FlorisImeUi.ClipboardContent.elementName,
        modifier = modifier.fillMaxSize().florisVerticalScroll(),
    ) {
        items.forEach { item ->
            VaultRow(
                item = item,
                revealed = item.id in revealed,
                onReveal = {
                    if (!revealed.add(item.id)) revealed.remove(item.id)
                },
                onPin = { clipboardManager.setVaultPinned(item.id, !item.pinned) },
                onMove = { clipboardManager.moveVaultToClipboard(item.id) },
                onDelete = { clipboardManager.deleteVaultItem(item.id) },
                onEdit = {
                    keyboardManager.openClipEditor(
                        dev.patrickgold.florisboard.ime.clipedit.ClipEditorOrigin.VAULT,
                        item.id,
                        item.text,
                        sensitive = true,
                    )
                },
            )
        }
    }
}

@Composable
private fun VaultRow(
    item: VaultPlain,
    revealed: Boolean,
    onReveal: () -> Unit,
    onPin: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
) {
    SnyggColumn(
        FlorisImeUi.ClipboardItem.elementName,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        SnyggText(
            text = if (revealed) item.text else VaultMask.bullets(item.text.length),
        )
        SnyggText(text = stringRes(R.string.clipboard__vault_chars, "n" to item.text.length))
        SnyggRow(verticalAlignment = Alignment.CenterVertically) {
            SnyggIconButton(onClick = onReveal) {
                SnyggIcon(imageVector = if (revealed) Icons.Filled.VisibilityOff else Icons.Filled.Visibility)
            }
            SnyggIconButton(onClick = onPin) {
                SnyggIcon(imageVector = Icons.Outlined.PushPin)
            }
            SnyggIconButton(onClick = onMove) {
                SnyggIcon(imageVector = Icons.AutoMirrored.Filled.ArrowBack)
            }
            SnyggIconButton(onClick = onEdit) {
                SnyggIcon(imageVector = Icons.Filled.Edit)
            }
            SnyggIconButton(onClick = onDelete) {
                SnyggIcon(imageVector = Icons.Filled.Delete)
            }
        }
    }
}
