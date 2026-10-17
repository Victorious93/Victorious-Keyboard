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

package dev.patrickgold.florisboard.app.settings.screenshot

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccessibilityNew
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.ime.screenshot.ScreenshotAccessibility
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import dev.patrickgold.jetpref.datastore.ui.ExperimentalJetPrefDatastoreUi
import dev.patrickgold.jetpref.datastore.ui.Preference
import dev.patrickgold.jetpref.datastore.ui.SwitchPreference
import org.florisboard.lib.compose.stringRes

/**
 * Settings → Tools → Screenshot. Explains the helper in plain words and can show or hide the tile.
 * On Android 10 and below it only says the feature needs Android 11.
 */
@OptIn(ExperimentalJetPrefDatastoreUi::class)
@Composable
fun ScreenshotScreen() = FlorisScreen {
    title = stringRes(R.string.settings__screenshot__title)
    previewFieldVisible = false

    val context = LocalContext.current
    val needsAndroid11 = Build.VERSION.SDK_INT < Build.VERSION_CODES.R
    var helperOn by remember { mutableStateOf(false) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner, needsAndroid11) {
        if (!needsAndroid11) {
            helperOn = ScreenshotAccessibility.isEnabled(context)
        }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && !needsAndroid11) {
                helperOn = ScreenshotAccessibility.isEnabled(context)
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }

    content {
        SwitchPreference(
            prefs.tools.screenshotTile,
            title = stringRes(R.string.settings__screenshot__tile_title),
            summary = stringRes(R.string.settings__screenshot__tile_summary),
        )
        if (needsAndroid11) {
            Text(
                text = stringRes(R.string.screenshot__needs_android_11),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        } else {
            Text(
                text = stringRes(R.string.settings__screenshot__why),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            Text(
                text = stringRes(
                    if (helperOn) {
                        R.string.settings__screenshot__helper_on
                    } else {
                        R.string.settings__screenshot__helper_off
                    },
                ),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            Preference(
                icon = Icons.Outlined.AccessibilityNew,
                title = stringRes(R.string.settings__screenshot__open_settings),
                onClick = {
                    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                },
            )
        }
    }
}
