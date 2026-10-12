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

package dev.patrickgold.florisboard.ime.developer.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.ime.keyboard.FlorisImeSizing
import dev.patrickgold.florisboard.ime.mode.DeveloperRowPrefs
import dev.patrickgold.florisboard.ime.mode.ModePresets
import dev.patrickgold.florisboard.keyboardManager
import dev.patrickgold.jetpref.datastore.model.observeAsState

/**
 * The height the developer row adds to the text view, or 0 when it is not shown there (same condition as in
 * `TextInputLayout`). `FlorisImeSizing.imeUiHeight()` does not include the row, so a full-height panel that replaces the
 * keyboard adds this to keep the window from shrinking when it opens (which would also move the host app's layout).
 * The result matches the text view's height. Opened from the quick-action overflow panel (which hides the row and is one
 * row shorter) the window grows once by this amount; that is accepted rather than tracked.
 */
@Composable
fun rememberDeveloperRowExtraHeight(): Dp {
    val context = LocalContext.current
    val keyboardManager by context.keyboardManager()
    val prefs by FlorisPreferenceStore
    val state by keyboardManager.activeState.collectAsState()
    val mode by keyboardManager.activeMode.collectAsState()
    val enabled by prefs.devrow.enabled.observeAsState()
    val profile by prefs.devrow.profile.observeAsState()
    val terminalKeys by prefs.devrow.terminalKeys.observeAsState()
    val navigationKeys by prefs.devrow.navigationKeys.observeAsState()
    val symbols by prefs.devrow.symbols.observeAsState()
    val size by prefs.devrow.size.observeAsState()
    val row = ModePresets.developerRow(mode, DeveloperRowPrefs(enabled, profile, terminalKeys, navigationKeys, symbols))
    return if (row.visible && !state.isActionsOverflowVisible) FlorisImeSizing.smartbarHeight * size.scale else 0.dp
}
