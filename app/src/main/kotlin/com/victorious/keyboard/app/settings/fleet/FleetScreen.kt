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

package com.victorious.keyboard.app.settings.fleet

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
import com.victorious.keyboard.R
import com.victorious.keyboard.app.FlorisPreferenceStore
import com.victorious.keyboard.app.ProjectUrls
import com.victorious.keyboard.fleetHub
import com.victorious.keyboard.ime.media.FileManagerInstall
import com.victorious.keyboard.ime.media.FileManagerProtocol
import com.victorious.keyboard.lib.compose.FlorisScreen
import com.victorious.keyboard.lib.fleet.FleetAccess
import com.victorious.keyboard.lib.fleet.FleetGrants
import com.victorious.keyboard.lib.util.launchUrl
import dev.patrickgold.jetpref.datastore.model.observeAsState
import dev.patrickgold.jetpref.datastore.ui.ExperimentalJetPrefDatastoreUi
import dev.patrickgold.jetpref.datastore.ui.Preference
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
        SwitchPreference(
            prefs.fleet.externalControl,
            title = stringRes(R.string.settings__fleet__external_control),
            summary = stringRes(R.string.settings__fleet__external_control_summary),
        )
        SwitchPreference(
            prefs.fleet.fileManagerSeek,
            title = stringRes(R.string.settings__fleet__file_manager_seek),
            summary = stringRes(R.string.settings__fleet__file_manager_seek_summary),
        )
        FileManagerApproval()
        FlorisInfoCard(text = stringRes(R.string.settings__fleet__info))
        Preference(
            title = stringRes(R.string.packs__get_more),
            summary = stringRes(R.string.packs__get_more_summary),
            onClick = { context.launchUrl(ProjectUrls.COMMAND_PROVIDERS) },
        )
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

@Composable
private fun FileManagerApproval() {
    val context = LocalContext.current
    val prefs by FlorisPreferenceStore
    val hub by context.fleetHub()
    val grantsRaw by prefs.fleet.grants.observeAsState()
    val installed = FileManagerInstall.certificates(context)
    if (installed.isEmpty()) {
        Text(
            text = stringRes(R.string.settings__fleet__file_manager_missing),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
        return
    }
    val grants = FleetGrants.parse(grantsRaw)
    installed.forEach { (packageName, cert) ->
        val approved = FileManagerProtocol.trusted(
            packageName,
            cert,
            FleetGrants.find(grants, packageName),
            seekEnabled = true,
        )
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(text = packageName, style = MaterialTheme.typography.titleMedium)
            Text(text = cert, style = MaterialTheme.typography.bodySmall)
            if (approved) {
                Text(
                    text = stringRes(R.string.settings__fleet__file_manager_approved),
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                TextButton(onClick = { hub.approvePinned(packageName, cert) }) {
                    Text(stringRes(R.string.settings__fleet__file_manager_approve))
                }
            }
        }
    }
}
