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

package com.victorious.keyboard.app.settings.packs

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.victorious.keyboard.R
import com.victorious.keyboard.app.FlorisPreferenceStore
import com.victorious.keyboard.extensionManager
import com.victorious.keyboard.fleetHub
import com.victorious.keyboard.ime.dictionary.packs.DictionaryPackInstaller
import com.victorious.keyboard.ime.packs.InstalledPacks
import com.victorious.keyboard.ime.packs.OfferStatus
import com.victorious.keyboard.ime.packs.PackPages
import com.victorious.keyboard.ime.packs.PackUpdateChecker
import com.victorious.keyboard.ime.packs.UpdateCheckResult
import com.victorious.keyboard.ime.packs.UpdateFailure
import com.victorious.keyboard.ime.packs.UrlConnectionTransport
import com.victorious.keyboard.lib.compose.FlorisScreen
import com.victorious.keyboard.lib.util.launchUrl
import dev.patrickgold.jetpref.datastore.model.observeAsState
import dev.patrickgold.jetpref.datastore.ui.ExperimentalJetPrefDatastoreUi
import dev.patrickgold.jetpref.datastore.ui.Preference
import dev.patrickgold.jetpref.datastore.ui.SwitchPreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.florisboard.lib.compose.FlorisInfoCard
import org.florisboard.lib.compose.stringRes

/**
 * Opt-in pack update check. The switch only allows the check; the request is made when the button is tapped, on a
 * background thread, to one fixed address. Nothing is downloaded except the catalog, nothing is installed, and a result
 * is kept only in memory. Compiled; never rendered or run on a device.
 */
@OptIn(ExperimentalJetPrefDatastoreUi::class)
@Composable
fun PackUpdatesScreen() = FlorisScreen {
    title = stringRes(R.string.settings__pack_updates__title)
    previewFieldVisible = false

    val prefs by FlorisPreferenceStore
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val allowed by prefs.packs.updateCheckEnabled.observeAsState()
    var running by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<UpdateCheckResult?>(null) }

    content {
        FlorisInfoCard(text = stringRes(R.string.settings__pack_updates__info))
        SwitchPreference(
            prefs.packs.updateCheckEnabled,
            title = stringRes(R.string.settings__pack_updates__switch),
            summary = stringRes(R.string.settings__pack_updates__switch_summary),
        )
        Preference(
            title = stringRes(R.string.settings__pack_updates__check_now),
            summary = stringRes(
                if (running) R.string.settings__pack_updates__checking else R.string.settings__pack_updates__check_now_summary,
            ),
            enabledIf = { prefs.packs.updateCheckEnabled isEqualTo true },
            onClick = {
                // Read the switch again at the tap. Nothing is contacted unless it is on.
                if (running || !prefs.packs.updateCheckEnabled.get()) return@Preference
                running = true
                result = null
                scope.launch {
                    val outcome = withContext(Dispatchers.IO) {
                        val installed = collectInstalled(context)
                        PackUpdateChecker.check(
                            enabled = prefs.packs.updateCheckEnabled.get(),
                            transport = UrlConnectionTransport(),
                            installed = installed,
                        )
                    }
                    result = outcome
                    running = false
                }
            },
        )
        Preference(
            title = stringRes(R.string.settings__pack_updates__open_page),
            summary = stringRes(R.string.settings__pack_updates__open_page_summary),
            onClick = { context.launchUrl(com.victorious.keyboard.app.ProjectUrls.PACKS) },
        )
        when (val r = result) {
            null -> Unit
            is UpdateCheckResult.Failed -> FlorisInfoCard(text = failureText(r.reason, r.httpStatus))
            is UpdateCheckResult.Done -> {
                Preference(
                    title = stringRes(R.string.settings__pack_updates__result_title),
                    // The date comes from the catalog and is concatenated, never passed as a format argument.
                    summary = stringRes(R.string.settings__pack_updates__result_dated) + " " + r.updated,
                    onClick = {},
                )
                if (r.offers.isEmpty()) {
                    FlorisInfoCard(text = stringRes(R.string.settings__pack_updates__none))
                }
                for (offer in r.offers) {
                    val e = offer.entry
                    // Everything from the catalog is concatenated as plain text, never passed through the formatter.
                    val summary = statusText(offer.status) + " · " + e.version + " · " + e.publisher +
                        (if (e.description.isNotBlank()) "\n" + e.description else "")
                    Preference(
                        title = e.title,
                        summary = summary,
                        onClick = { context.launchUrl(PackPages.urlFor(e.type)) },
                    )
                }
            }
        }
    }
}

/** Status label for an offer. */
@Composable
private fun statusText(status: OfferStatus): String = stringRes(
    when (status) {
        OfferStatus.UPDATE -> R.string.settings__pack_updates__status_update
        OfferStatus.NEW -> R.string.settings__pack_updates__status_new
        OfferStatus.CANNOT_COMPARE -> R.string.settings__pack_updates__status_unknown
        OfferStatus.CURRENT -> R.string.settings__pack_updates__status_current
    },
)

@Composable
private fun failureText(reason: UpdateFailure, httpStatus: Int): String = when (reason) {
    UpdateFailure.DISABLED -> stringRes(R.string.settings__pack_updates__fail_disabled)
    UpdateFailure.NETWORK -> stringRes(R.string.settings__pack_updates__fail_network)
    UpdateFailure.REDIRECT_REFUSED -> stringRes(R.string.settings__pack_updates__fail_redirect)
    UpdateFailure.HTTP_STATUS -> stringRes(R.string.settings__pack_updates__fail_http) + " " + httpStatus
    UpdateFailure.TOO_LARGE -> stringRes(R.string.settings__pack_updates__fail_too_large)
    UpdateFailure.BAD_CATALOG -> stringRes(R.string.settings__pack_updates__fail_bad_catalog)
}

/** What is installed, as far as the keyboard knows without opening any pack file. Runs off the main thread. */
private suspend fun collectInstalled(context: Context): InstalledPacks {
    val manager by context.extensionManager()
    val versions = HashMap<String, String>()
    val reserved = HashSet<String>()
    val all = manager.themes.value + manager.keyboardExtensions.value + manager.languagePacks.value
    for (ext in all) {
        if (manager.canDelete(ext)) {
            versions[ext.meta.id] = ext.meta.version
        } else {
            reserved += ext.meta.id
            reserved += ext.meta.aliases
        }
    }
    for (pack in DictionaryPackInstaller.bundled(context)) reserved += pack.manifest.id
    val hub by context.fleetHub()
    val packages = hub.offers.value.map { it.packageName }.toSet()
    return InstalledPacks(versionsById = versions, packages = packages, reservedIds = reserved)
}
