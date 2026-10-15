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

import androidx.compose.runtime.Composable
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.enumDisplayEntriesOf
import dev.patrickgold.florisboard.ime.snippets.SnippetPrefix
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import dev.patrickgold.jetpref.datastore.ui.ExperimentalJetPrefDatastoreUi
import dev.patrickgold.jetpref.datastore.ui.ListPreference
import dev.patrickgold.jetpref.datastore.ui.SwitchPreference
import org.florisboard.lib.compose.stringRes

/** Snippet expansion as you type. Moved here from the Keyboard screen's developer group, where it did not belong. */
@OptIn(ExperimentalJetPrefDatastoreUi::class)
@Composable
fun SnippetExpansionScreen() = FlorisScreen {
    title = stringRes(R.string.snippets__expansion__title)
    previewFieldVisible = true

    content {
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
    }
}
