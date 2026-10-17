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

package dev.patrickgold.florisboard.app.settings.fleet

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.fleetHub
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import dev.patrickgold.florisboard.lib.fleet.FleetAccess
import dev.patrickgold.jetpref.datastore.ui.ExperimentalJetPrefDatastoreUi
import dev.patrickgold.jetpref.datastore.ui.SwitchPreference
import org.florisboard.lib.compose.FlorisInfoCard
import org.florisboard.lib.compose.stringRes

@OptIn(ExperimentalJetPrefDatastoreUi::class)
@Composable
fun FleetScreen() = FlorisScreen {
    title = stringRes(R.string.settings__fleet__title)
    previewFieldVisible = true
    val prefs by FlorisPreferenceStore
    val context = LocalContext.current
    val hub by context.fleetHub()
    val offers by hub.offers.collectAsState()

    content {
        LaunchedEffect(Unit) { hub.start() }
        SwitchPreference(
            prefs.fleet.enabled,
            title = stringRes(R.string.settings__fleet__enabled),
            summary = stringRes(R.string.settings__fleet__enabled_summary),
        )
        FlorisInfoCard(text = stringRes(R.string.settings__fleet__info))
        if (offers.isEmpty()) {
            Text(
                text = stringRes(R.string.settings__fleet__none),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        offers.forEach { offer ->
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(text = offer.label, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = offer.certSha256.ifBlank { stringRes(R.string.settings__fleet__no_cert) },
                    style = MaterialTheme.typography.bodySmall,
                )
                if (offer.access == FleetAccess.NeedsApproval) {
                    TextButton(onClick = { hub.approve(offer.packageName) }) {
                        Text(stringRes(R.string.settings__fleet__approve))
                    }
                } else {
                    Switch(
                        checked = offer.access == FleetAccess.Active,
                        onCheckedChange = { hub.setEnabled(offer.packageName, it) },
                    )
                }
            }
        }
    }
}
