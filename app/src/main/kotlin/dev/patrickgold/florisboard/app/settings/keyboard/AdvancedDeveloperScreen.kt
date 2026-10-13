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

package dev.patrickgold.florisboard.app.settings.keyboard

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.FlorisPreferenceModel
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.app.enumDisplayEntriesOf
import dev.patrickgold.florisboard.ime.developer.CustomSymbols
import dev.patrickgold.florisboard.ime.developer.CustomSymbolsMode
import dev.patrickgold.florisboard.ime.developer.DevRowSize
import dev.patrickgold.florisboard.ime.developer.DeveloperRowTiming
import dev.patrickgold.florisboard.ime.mode.ModeOverrides
import dev.patrickgold.florisboard.ime.snippets.SnippetDateFormat
import dev.patrickgold.florisboard.ime.snippets.SnippetTimeFormat
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import dev.patrickgold.jetpref.datastore.model.observeAsState
import dev.patrickgold.jetpref.datastore.ui.DialogSliderPreference
import dev.patrickgold.jetpref.datastore.ui.ExperimentalJetPrefDatastoreUi
import dev.patrickgold.jetpref.datastore.ui.ListPreference
import dev.patrickgold.jetpref.datastore.ui.Preference
import dev.patrickgold.jetpref.datastore.ui.PreferenceGroup
import dev.patrickgold.jetpref.datastore.ui.SwitchPreference
import kotlinx.coroutines.launch
import org.florisboard.lib.compose.stringRes

private const val MAX_CUSTOM_SYMBOLS_CHARS = 2_000
private const val MAX_OVERRIDES_CHARS = 4_000

/**
 * Advanced developer settings: row layout and size, timing, feedback, snippet formats and the user's own app-to-mode
 * mappings. Free-text settings are validated live with the same pure parsers the keyboard uses, and nothing in them is
 * ever executed.
 */
@OptIn(ExperimentalJetPrefDatastoreUi::class)
@Composable
fun AdvancedDeveloperScreen() = FlorisScreen {
    title = stringRes(R.string.devadv__title)
    previewFieldVisible = true

    val prefs by FlorisPreferenceStore
    val scope = rememberCoroutineScope()
    var confirmReset by remember { mutableStateOf(false) }
    val customSymbols by prefs.devrow.customSymbols.observeAsState()
    val appOverrides by prefs.modes.appOverrides.observeAsState()

    content {
        PreferenceGroup(title = stringRes(R.string.devadv__group_layout)) {
            ListPreference(
                prefs.devrow.size,
                title = stringRes(R.string.devadv__size__label),
                entries = enumDisplayEntriesOf(DevRowSize::class),
            )
            SwitchPreference(
                prefs.devrow.navigationKeys,
                title = stringRes(R.string.devadv__navigation_keys__label),
                summary = stringRes(R.string.devadv__navigation_keys__summary),
            )
            SwitchPreference(
                prefs.devrow.symbols,
                title = stringRes(R.string.devadv__symbols__label),
                summary = stringRes(R.string.devadv__symbols__summary),
            )
            TextSettingRow(
                title = stringRes(R.string.devadv__custom_symbols__label),
                summary = if (customSymbols.isBlank()) stringRes(R.string.devadv__none) else customSymbols.take(60),
                help = stringRes(R.string.devadv__custom_symbols__help),
                value = customSymbols,
                maxChars = MAX_CUSTOM_SYMBOLS_CHARS,
                singleLine = true,
                feedback = {
                    val p = CustomSymbols.parse(it)
                    stringRes(R.string.devadv__custom_symbols__count, "n" to p.keys.size) +
                        if (p.rejected.isEmpty()) "" else " " + stringRes(R.string.devadv__rejected) + " " + p.rejected.take(5).joinToString(" ")
                },
                onSave = { scope.launch { prefs.devrow.customSymbols.set(it) } },
            )
            ListPreference(
                prefs.devrow.customSymbolsMode,
                title = stringRes(R.string.devadv__custom_symbols_mode__label),
                entries = enumDisplayEntriesOf(CustomSymbolsMode::class),
            )
        }
        PreferenceGroup(title = stringRes(R.string.devadv__group_timing)) {
            DialogSliderPreference(
                prefs.devrow.holdDelayMs,
                title = stringRes(R.string.devadv__hold_delay__label),
                valueLabel = { stringRes(R.string.unit__milliseconds__symbol, "v" to it) },
                min = DeveloperRowTiming.HOLD_MIN,
                max = DeveloperRowTiming.HOLD_MAX,
                stepIncrement = 50,
            )
            DialogSliderPreference(
                prefs.devrow.repeatIntervalMs,
                title = stringRes(R.string.devadv__repeat_interval__label),
                valueLabel = { stringRes(R.string.unit__milliseconds__symbol, "v" to it) },
                min = DeveloperRowTiming.REPEAT_MIN,
                max = DeveloperRowTiming.REPEAT_MAX,
                stepIncrement = 10,
            )
            DialogSliderPreference(
                prefs.devrow.lockWindowMs,
                title = stringRes(R.string.devadv__lock_window__label),
                valueLabel = { stringRes(R.string.unit__milliseconds__symbol, "v" to it) },
                min = DeveloperRowTiming.LOCK_WINDOW_MIN,
                max = DeveloperRowTiming.LOCK_WINDOW_MAX,
                stepIncrement = 50,
            )
            SwitchPreference(
                prefs.devrow.haptics,
                title = stringRes(R.string.devadv__haptics__label),
                summary = stringRes(R.string.devadv__haptics__summary),
            )
        }
        PreferenceGroup(title = stringRes(R.string.devadv__group_snippets)) {
            ListPreference(
                prefs.snippets.dateFormat,
                title = stringRes(R.string.devadv__date_format__label),
                entries = enumDisplayEntriesOf(SnippetDateFormat::class),
            )
            ListPreference(
                prefs.snippets.timeFormat,
                title = stringRes(R.string.devadv__time_format__label),
                entries = enumDisplayEntriesOf(SnippetTimeFormat::class),
            )
            SwitchPreference(
                prefs.snippets.keepSpace,
                title = stringRes(R.string.devadv__keep_space__label),
                summary = stringRes(R.string.devadv__keep_space__summary),
            )
            SwitchPreference(
                prefs.snippets.keepEnter,
                title = stringRes(R.string.devadv__keep_enter__label),
                summary = stringRes(R.string.devadv__keep_enter__summary),
            )
            SwitchPreference(
                prefs.snippets.keepTab,
                title = stringRes(R.string.devadv__keep_tab__label),
                summary = stringRes(R.string.devadv__keep_tab__summary),
            )
            SwitchPreference(
                prefs.snippets.expandInTerminalMode,
                title = stringRes(R.string.devadv__expand_terminal__label),
                summary = stringRes(R.string.devadv__expand_terminal__summary),
            )
        }
        PreferenceGroup(title = stringRes(R.string.devadv__group_modes)) {
            TextSettingRow(
                title = stringRes(R.string.devadv__overrides__label),
                summary = if (appOverrides.isBlank()) stringRes(R.string.devadv__none)
                else stringRes(R.string.devadv__overrides__count, "n" to ModeOverrides.parse(appOverrides).overrides.size),
                help = stringRes(R.string.devadv__overrides__help),
                value = appOverrides,
                maxChars = MAX_OVERRIDES_CHARS,
                singleLine = false,
                feedback = {
                    val p = ModeOverrides.parse(it)
                    stringRes(R.string.devadv__overrides__count, "n" to p.overrides.size) +
                        if (p.rejected.isEmpty()) "" else " " + stringRes(R.string.devadv__overrides__invalid, "n" to p.rejected.size)
                },
                onSave = { scope.launch { prefs.modes.appOverrides.set(it) } },
            )
        }
        Preference(
            title = stringRes(R.string.devadv__reset__label),
            summary = stringRes(R.string.devadv__reset__summary),
            onClick = { confirmReset = true },
        )
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text(stringRes(R.string.devadv__reset__title)) },
            text = { Text(stringRes(R.string.devadv__reset__text)) },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { resetAdvanced(prefs) }
                    confirmReset = false
                }) { Text(stringRes(R.string.devadv__reset__confirm)) }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text(stringRes(R.string.action__cancel)) } },
        )
    }
}

/** Puts every setting on this screen back to its default. The basic Developer mode settings are left alone. */
private suspend fun resetAdvanced(prefs: FlorisPreferenceModel) {
    prefs.devrow.size.set(DevRowSize.NORMAL)
    prefs.devrow.navigationKeys.set(true)
    prefs.devrow.symbols.set(true)
    prefs.devrow.customSymbols.set("")
    prefs.devrow.customSymbolsMode.set(CustomSymbolsMode.APPEND)
    prefs.devrow.holdDelayMs.set(DeveloperRowTiming.HOLD_DEFAULT)
    prefs.devrow.repeatIntervalMs.set(DeveloperRowTiming.REPEAT_DEFAULT)
    prefs.devrow.lockWindowMs.set(DeveloperRowTiming.LOCK_WINDOW_DEFAULT)
    prefs.devrow.haptics.set(true)
    prefs.snippets.dateFormat.set(SnippetDateFormat.ISO)
    prefs.snippets.timeFormat.set(SnippetTimeFormat.H24)
    prefs.snippets.keepSpace.set(true)
    prefs.snippets.keepEnter.set(true)
    prefs.snippets.keepTab.set(true)
    prefs.snippets.expandInTerminalMode.set(false)
    prefs.modes.appOverrides.set("")
}

/**
 * A text setting edited in a dialog. [feedback] is computed from the text being typed (it is the pure parser's result),
 * [maxChars] bounds the input, and nothing is saved until the user presses Save. The text is never passed through the
 * `{name}` string formatter (a value containing its own placeholder name would loop forever there).
 */
@OptIn(ExperimentalJetPrefDatastoreUi::class)
@Composable
private fun TextSettingRow(
    title: String,
    summary: String,
    help: String,
    value: String,
    maxChars: Int,
    singleLine: Boolean,
    feedback: @Composable (String) -> String,
    onSave: (String) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Preference(title = title, summary = summary, onClick = { open = true })
    if (open) {
        var text by rememberSaveable(value) { mutableStateOf(value) }
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(title) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(style = MaterialTheme.typography.bodySmall, text = help)
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        value = text,
                        onValueChange = { if (it.length <= maxChars) text = it },
                        singleLine = singleLine,
                        minLines = if (singleLine) 1 else 4,
                        maxLines = if (singleLine) 1 else 10,
                    )
                    Text(
                        modifier = Modifier.padding(top = 6.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        text = feedback(text),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { onSave(text); open = false }) { Text(stringRes(R.string.action__save)) }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { text = "" }) { Text(stringRes(R.string.devadv__clear)) }
                    TextButton(onClick = { open = false }) { Text(stringRes(R.string.action__cancel)) }
                }
            },
        )
    }
}
