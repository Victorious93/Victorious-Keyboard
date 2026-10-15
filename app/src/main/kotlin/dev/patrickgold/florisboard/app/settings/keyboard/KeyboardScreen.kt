/*
 * Copyright (C) 2021-2025 The FlorisBoard Contributors
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.app.LocalNavController
import dev.patrickgold.florisboard.app.Routes
import dev.patrickgold.florisboard.app.enumDisplayEntriesOf
import dev.patrickgold.florisboard.ime.developer.LanguageProfile
import dev.patrickgold.florisboard.ime.mode.OperatingMode
import dev.patrickgold.florisboard.ime.mode.TerminalKeyRow
import dev.patrickgold.florisboard.ime.snippets.SnippetPrefix
import dev.patrickgold.florisboard.ime.input.CapitalizationBehavior
import dev.patrickgold.florisboard.ime.keyboard.SpaceBarMode
import dev.patrickgold.florisboard.ime.landscapeinput.LandscapeInputUiMode
import dev.patrickgold.florisboard.ime.smartbar.IncognitoDisplayMode
import dev.patrickgold.florisboard.ime.text.key.KeyHintMode
import dev.patrickgold.florisboard.ime.text.key.UtilityKeyAction
import dev.patrickgold.florisboard.ime.window.KeyboardBottomClearance
import dev.patrickgold.florisboard.ime.window.NavigationStyleProbe
import dev.patrickgold.florisboard.ime.window.rememberBottomObstruction
import dev.patrickgold.florisboard.ime.window.rememberImeBottomClearance
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import dev.patrickgold.jetpref.datastore.ui.DialogSliderPreference
import dev.patrickgold.jetpref.datastore.ui.ExperimentalJetPrefDatastoreUi
import dev.patrickgold.jetpref.datastore.ui.ListPreference
import dev.patrickgold.jetpref.datastore.ui.Preference
import dev.patrickgold.jetpref.datastore.ui.PreferenceGroup
import dev.patrickgold.jetpref.datastore.ui.SwitchPreference
import kotlinx.coroutines.launch
import org.florisboard.lib.compose.stringRes

@OptIn(ExperimentalJetPrefDatastoreUi::class)
@Composable
fun KeyboardScreen() = FlorisScreen {
    title = stringRes(R.string.settings__keyboard__title)
    previewFieldVisible = true

    val navController = LocalNavController.current

    content {
        SwitchPreference(
            prefs.keyboard.numberRow,
            title = stringRes(R.string.pref__keyboard__number_row__label),
            summary = stringRes(R.string.pref__keyboard__number_row__summary),
        )
        ListPreference(
            listPref = prefs.keyboard.hintedNumberRowMode,
            switchPref = prefs.keyboard.hintedNumberRowEnabled,
            title = stringRes(R.string.pref__keyboard__hinted_number_row_mode__label),
            summarySwitchDisabled = stringRes(R.string.state__disabled),
            entries = enumDisplayEntriesOf(KeyHintMode::class),
            enabledIf = { prefs.keyboard.numberRow.isFalse() }
        )
        ListPreference(
            listPref = prefs.keyboard.hintedSymbolsMode,
            switchPref = prefs.keyboard.hintedSymbolsEnabled,
            title = stringRes(R.string.pref__keyboard__hinted_symbols_mode__label),
            summarySwitchDisabled = stringRes(R.string.state__disabled),
            entries = enumDisplayEntriesOf(KeyHintMode::class),
        )
        SwitchPreference(
            prefs.keyboard.utilityKeyEnabled,
            title = stringRes(R.string.pref__keyboard__utility_key_enabled__label),
            summary = stringRes(R.string.pref__keyboard__utility_key_enabled__summary),
        )
        ListPreference(
            prefs.keyboard.utilityKeyAction,
            title = stringRes(R.string.pref__keyboard__utility_key_action__label),
            entries = enumDisplayEntriesOf(UtilityKeyAction::class),
            visibleIf = { prefs.keyboard.utilityKeyEnabled isEqualTo true },
        )
        ListPreference(
            prefs.keyboard.spaceBarMode,
            title = stringRes(R.string.pref__keyboard__space_bar_mode__label),
            entries = enumDisplayEntriesOf(SpaceBarMode::class),
        )
        ListPreference(
            prefs.keyboard.capitalizationBehavior,
            title = stringRes(R.string.pref__keyboard__capitalization_behavior__label),
            entries = enumDisplayEntriesOf(CapitalizationBehavior::class),
        )
        DialogSliderPreference(
            primaryPref = prefs.keyboard.fontSizeMultiplierPortrait,
            secondaryPref = prefs.keyboard.fontSizeMultiplierLandscape,
            title = stringRes(R.string.pref__keyboard__font_size_multiplier__label),
            primaryLabel = stringRes(R.string.screen_orientation__portrait),
            secondaryLabel = stringRes(R.string.screen_orientation__landscape),
            valueLabel = { stringRes(R.string.unit__percent__symbol, "v" to it) },
            min = 50,
            max = 150,
            stepIncrement = 5,
        )
        BottomGapPreference()
        ListPreference(
            listPref = prefs.keyboard.incognitoDisplayMode,
            title = stringRes(R.string.pref__keyboard__incognito_indicator__label),
            entries = enumDisplayEntriesOf(IncognitoDisplayMode::class),
        )

        PreferenceGroup(title = stringRes(R.string.pref__devrow__group_label)) {
            ListPreference(
                prefs.modes.defaultMode,
                title = stringRes(R.string.pref__modes__default__label),
                entries = enumDisplayEntriesOf(OperatingMode::class),
            )
            SwitchPreference(
                prefs.modes.autoDetect,
                title = stringRes(R.string.pref__modes__auto_detect__label),
                summary = stringRes(R.string.pref__modes__auto_detect__summary),
            )
            ListPreference(
                prefs.devrow.terminalKeyRow,
                title = stringRes(R.string.pref__devrow__terminal_key_row__label),
                entries = enumDisplayEntriesOf(TerminalKeyRow::class),
            )
            ListPreference(
                prefs.devrow.profile,
                title = stringRes(R.string.pref__devrow__profile__label),
                entries = enumDisplayEntriesOf(LanguageProfile::class),
                visibleIf = { prefs.devrow.terminalKeyRow isNotEqualTo TerminalKeyRow.NEVER },
            )
            Preference(
                title = stringRes(R.string.devadv__title),
                summary = stringRes(R.string.devadv__link_summary),
                onClick = { navController.navigate(Routes.Settings.AdvancedDeveloper) },
            )
            Preference(
                title = stringRes(R.string.snippets__title),
                summary = stringRes(R.string.snippets__link_summary),
                onClick = { navController.navigate(Routes.Settings.Snippets) },
            )
            SwitchPreference(
                prefs.snippets.expansionEnabled,
                title = stringRes(R.string.pref__snippets__expansion__label),
                summary = stringRes(R.string.pref__snippets__expansion__summary),
            )
            ListPreference(
                prefs.snippets.prefix,
                title = stringRes(R.string.pref__snippets__prefix__label),
                entries = enumDisplayEntriesOf(SnippetPrefix::class),
                visibleIf = { prefs.snippets.expansionEnabled isEqualTo true },
            )
            SwitchPreference(
                prefs.snippets.caseSensitive,
                title = stringRes(R.string.pref__snippets__case_sensitive__label),
                visibleIf = { prefs.snippets.expansionEnabled isEqualTo true },
            )
            SwitchPreference(
                prefs.snippets.expandOnEnter,
                title = stringRes(R.string.pref__snippets__expand_on_enter__label),
                summary = stringRes(R.string.pref__snippets__expand_on_enter__summary),
                visibleIf = { prefs.snippets.expansionEnabled isEqualTo true },
            )
            SwitchPreference(
                prefs.snippets.expandOnTab,
                title = stringRes(R.string.pref__snippets__expand_on_tab__label),
                summary = stringRes(R.string.pref__snippets__expand_on_tab__summary),
                visibleIf = { prefs.snippets.expansionEnabled isEqualTo true },
            )
            SwitchPreference(
                prefs.devrow.terminalKeys,
                title = stringRes(R.string.pref__devrow__terminal_keys__label),
                summary = stringRes(R.string.pref__devrow__terminal_keys__summary),
                visibleIf = { prefs.devrow.enabled isEqualTo true },
            )
        }
        PreferenceGroup(title = stringRes(R.string.pref__keyboard__group_layout__label)) {
            ListPreference(
                prefs.keyboard.landscapeInputUiMode,
                title = stringRes(R.string.pref__keyboard__landscape_input_ui_mode__label),
                entries = enumDisplayEntriesOf(LandscapeInputUiMode::class),
            )
            DialogSliderPreference(
                primaryPref = prefs.keyboard.keySpacingVertical,
                secondaryPref = prefs.keyboard.keySpacingHorizontal,
                title = stringRes(R.string.pref__keyboard__key_spacing__label),
                primaryLabel = stringRes(R.string.screen_orientation__vertical),
                secondaryLabel = stringRes(R.string.screen_orientation__horizontal),
                valueLabel = { stringRes(R.string.unit__percent__symbol, "v" to it) },
                min = 50,
                max = 150,
                stepIncrement = 5,
            )
        }

        PreferenceGroup(title = stringRes(R.string.pref__keyboard__group_keypress__label)) {
            Preference(
                title = stringRes(R.string.settings__input_feedback__title),
                onClick = { navController.navigate(Routes.Settings.InputFeedback) },
            )
            SwitchPreference(
                prefs.keyboard.popupEnabled,
                title = stringRes(R.string.pref__keyboard__popup_enabled__label),
                summary = stringRes(R.string.pref__keyboard__popup_enabled__summary),
            )
            SwitchPreference(
                prefs.keyboard.mergeHintPopupsEnabled,
                title = stringRes(R.string.pref__keyboard__merge_hint_popups_enabled__label),
                summary = stringRes(R.string.pref__keyboard__merge_hint_popups_enabled__summary),
            )
            DialogSliderPreference(
                prefs.keyboard.longPressDelay,
                title = stringRes(R.string.pref__keyboard__long_press_delay__label),
                valueLabel = { stringRes(R.string.unit__milliseconds__symbol, "v" to it) },
                min = 100,
                max = 700,
                stepIncrement = 10,
            )
            SwitchPreference(
                prefs.keyboard.spaceBarSwitchesToCharacters,
                title = stringRes(R.string.pref__keyboard__space_bar_switches_to_characters__label),
                summary = stringRes(R.string.pref__keyboard__space_bar_switches_to_characters__summary),
            )
        }
    }
}

@Composable
private fun BottomGapPreference() {
    val context = LocalContext.current
    val prefs by FlorisPreferenceStore
    val scope = rememberCoroutineScope()
    val clearance = rememberImeBottomClearance(allowFallback = false)
    val recommended = KeyboardBottomClearance.defaultGapDp(
        NavigationStyleProbe.resolve(rememberBottomObstruction(), context),
    )
    var open by remember { mutableStateOf(false) }
    var draft by remember { mutableFloatStateOf(clearance.effectiveGapDp.toFloat()) }
    val summary = if (clearance.automatic) {
        stringRes(R.string.pref__keyboard__bottom_gap__summary_auto, "v" to clearance.effectiveGapDp)
    } else {
        stringRes(R.string.pref__keyboard__bottom_gap__summary, "v" to clearance.effectiveGapDp)
    }

    Preference(
        title = stringRes(R.string.pref__keyboard__bottom_gap__label),
        summary = summary,
        onClick = {
            draft = clearance.effectiveGapDp.toFloat()
            open = true
        },
    )
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(stringRes(R.string.pref__keyboard__bottom_gap__label)) },
            text = {
                Column {
                    Text(stringRes(R.string.pref__keyboard__bottom_gap__value, "v" to draft.toInt()))
                    Slider(
                        value = draft,
                        onValueChange = { draft = it },
                        valueRange = KeyboardBottomClearance.GAP_MIN_DP.toFloat()..KeyboardBottomClearance.GAP_MAX_DP.toFloat(),
                        steps = KeyboardBottomClearance.GAP_MAX_DP - 1,
                    )
                    Text(summary)
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch { prefs.keyboard.bottomGapDp.set(draft.toInt()) }
                        open = false
                    },
                ) {
                    Text(stringRes(R.string.action__ok))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        scope.launch { prefs.keyboard.bottomGapDp.set(KeyboardBottomClearance.GAP_AUTOMATIC) }
                        open = false
                    },
                ) {
                    Text(stringRes(R.string.pref__keyboard__bottom_gap__use_recommended, "v" to recommended))
                }
            },
        )
    }
}
