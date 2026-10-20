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

package com.victorious.keyboard.app.settings.dictionary

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.platform.LocalContext
import com.victorious.keyboard.R
import com.victorious.keyboard.ime.dictionary.packs.DictionaryPack
import com.victorious.keyboard.ime.dictionary.packs.DictionaryPackInstaller
import com.victorious.keyboard.ime.dictionary.packs.InstallResult
import com.victorious.keyboard.ime.dictionary.packs.PackLoad
import com.victorious.keyboard.lib.compose.FlorisScreen
import dev.patrickgold.jetpref.datastore.ui.Preference
import kotlinx.coroutines.launch
import org.florisboard.lib.android.showLongToast
import org.florisboard.lib.compose.stringRes

/**
 * Ready-made word lists for the internal user dictionary. Titles and descriptions come from the pack files and are only
 * concatenated into the UI, never passed as format arguments. Compiled; never run or rendered on a device.
 */
@Composable
fun DictionaryPacksScreen() = FlorisScreen {
    title = stringRes(R.string.settings__dictionary_packs__title)
    previewFieldVisible = false

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var packs by remember { mutableStateOf<List<DictionaryPack>>(emptyList()) }
    val present = remember { mutableStateMapOf<String, Int>() }
    var selected by remember { mutableStateOf<DictionaryPack?>(null) }
    var confirmRemove by remember { mutableStateOf<DictionaryPack?>(null) }

    val unavailableText = stringRes(R.string.settings__dictionary_packs__unavailable)
    val failedText = stringRes(R.string.settings__dictionary_packs__failed)
    val invalidText = stringRes(R.string.settings__dictionary_packs__invalid_file)
    val addedText = stringRes(R.string.settings__dictionary_packs__added)
    val removedText = stringRes(R.string.settings__dictionary_packs__removed)

    suspend fun refresh(list: List<DictionaryPack>) {
        packs = list
        for (p in list) DictionaryPackInstaller.presentCount(p)?.let { present[p.manifest.id] = it }
    }
    LaunchedEffect(Unit) { refresh(DictionaryPackInstaller.bundled(context)) }

    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            when (val r = DictionaryPackInstaller.fromUri(context, uri)) {
                is PackLoad.Loaded -> refresh(packs.filterNot { it.manifest.id == r.pack.manifest.id } + r.pack)
                is PackLoad.Rejected -> context.showLongToast(invalidText)
            }
        }
    }

    suspend fun report(r: InstallResult, doneText: String) {
        when (r) {
            is InstallResult.Done -> context.showLongToast(doneText + " " + r.changed)
            is InstallResult.Unavailable -> context.showLongToast(unavailableText)
            is InstallResult.Failed -> context.showLongToast(failedText)
        }
    }

    content {
        Preference(
            title = stringRes(R.string.settings__dictionary_packs__note_title),
            summary = stringRes(R.string.settings__dictionary_packs__note),
            onClick = {},
        )
        for (p in packs) {
            val have = present[p.manifest.id]
            Preference(
                title = p.manifest.title + " (" + p.entries.size + ")",
                summary = p.manifest.description + if (have == null) "" else " [" + have + "/" + p.entries.size + "]",
                onClick = { selected = p },
            )
        }
        Preference(
            title = stringRes(R.string.settings__dictionary_packs__import),
            summary = stringRes(R.string.settings__dictionary_packs__import_summary),
            onClick = { pickFile.launch("application/zip") },
        )
    }

    selected?.let { p ->
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text(p.manifest.title) },
            text = { Text(p.manifest.description + "\n\n" + stringRes(R.string.settings__dictionary_packs__install_hint)) },
            confirmButton = {
                TextButton(onClick = {
                    selected = null
                    scope.launch {
                        report(DictionaryPackInstaller.install(p), addedText)
                        DictionaryPackInstaller.presentCount(p)?.let { present[p.manifest.id] = it }
                    }
                }) { Text(stringRes(R.string.settings__dictionary_packs__install)) }
            },
            dismissButton = {
                Row2(
                    onRemove = { selected = null; confirmRemove = p },
                    onCancel = { selected = null },
                )
            },
        )
    }

    confirmRemove?.let { p ->
        AlertDialog(
            onDismissRequest = { confirmRemove = null },
            title = { Text(p.manifest.title) },
            text = { Text(stringRes(R.string.settings__dictionary_packs__remove_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmRemove = null
                    scope.launch {
                        report(DictionaryPackInstaller.remove(p), removedText)
                        DictionaryPackInstaller.presentCount(p)?.let { present[p.manifest.id] = it }
                    }
                }) { Text(stringRes(R.string.settings__dictionary_packs__remove)) }
            },
            dismissButton = { TextButton(onClick = { confirmRemove = null }) { Text(stringRes(R.string.action__cancel)) } },
        )
    }
}

@Composable
private fun Row2(onRemove: () -> Unit, onCancel: () -> Unit) {
    androidx.compose.foundation.layout.Row {
        TextButton(onClick = onRemove) { Text(stringRes(R.string.settings__dictionary_packs__remove)) }
        TextButton(onClick = onCancel) { Text(stringRes(R.string.action__cancel)) }
    }
}
