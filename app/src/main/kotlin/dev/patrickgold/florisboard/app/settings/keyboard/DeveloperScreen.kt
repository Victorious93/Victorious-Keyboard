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

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.LocalNavController
import dev.patrickgold.florisboard.app.Routes
import dev.patrickgold.florisboard.app.enumDisplayEntriesOf
import dev.patrickgold.florisboard.ime.developer.LanguageProfile
import dev.patrickgold.florisboard.ime.mode.OperatingMode
import dev.patrickgold.florisboard.ime.mode.TerminalKeyRow
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import dev.patrickgold.jetpref.datastore.ui.ExperimentalJetPrefDatastoreUi
import dev.patrickgold.jetpref.datastore.ui.ListPreference
import dev.patrickgold.jetpref.datastore.ui.Preference
import dev.patrickgold.jetpref.datastore.ui.PreferenceGroup
import dev.patrickgold.jetpref.datastore.ui.SwitchPreference
import org.florisboard.lib.compose.stringRes

/**
 * Operating modes and the terminal key row, moved out of the Keyboard screen so they are reachable from Home. The row's
 * switches here are the ones `TerminalRowPolicy.plan` reads; none of them depends on the retired `devrow.enabled`.
 */
@OptIn(ExperimentalJetPrefDatastoreUi::class)
@Composable
fun DeveloperScreen() = FlorisScreen {
    title = stringRes(R.string.settings__developer__title)
    previewFieldVisible = true

    val navController = LocalNavController.current

    content {
        PreferenceGroup(title = stringRes(R.string.settings__developer__group_mode)) {
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
        }
        PreferenceGroup(title = stringRes(R.string.settings__developer__group_row)) {
            Text(
                text = stringRes(R.string.settings__developer__row_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            ListPreference(
                prefs.devrow.terminalKeyRow,
                title = stringRes(R.string.pref__devrow__terminal_key_row__label),
                entries = enumDisplayEntriesOf(TerminalKeyRow::class),
            )
            SwitchPreference(
                prefs.devrow.terminalKeys,
                title = stringRes(R.string.pref__devrow__terminal_keys__label),
                summary = stringRes(R.string.pref__devrow__terminal_keys__summary),
                visibleIf = { prefs.devrow.terminalKeyRow isNotEqualTo TerminalKeyRow.NEVER },
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
        }
    }
}
