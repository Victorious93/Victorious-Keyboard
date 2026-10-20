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

package com.victorious.keyboard.ime.theme

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import com.victorious.keyboard.app.FlorisPreferenceStore
import com.victorious.keyboard.ime.theme.colors.KeyboardColorOverlay
import com.victorious.keyboard.ime.theme.colors.KeyboardPaletteCodec
import com.victorious.keyboard.ime.window.LocalWindowController
import com.victorious.keyboard.keyboardManager
import com.victorious.keyboard.themeManager
import dev.patrickgold.jetpref.datastore.model.observeAsState
import org.florisboard.lib.snygg.ui.ProvideSnyggTheme
import org.florisboard.lib.snygg.ui.rememberSnyggTheme

@Composable
fun FlorisImeTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val windowController = LocalWindowController.current

    val keyboardManager by context.keyboardManager()
    val themeManager by context.themeManager()

    val prefs by FlorisPreferenceStore
    val accentColor by prefs.theme.accentColor.observeAsState()

    val activeThemeInfo by themeManager.activeThemeInfo.collectAsState()
    val keyboardColorsRaw by prefs.theme.keyboardColors.observeAsState()
    val stylesheet = remember(activeThemeInfo, keyboardColorsRaw) {
        KeyboardColorOverlay.apply(
            stylesheet = activeThemeInfo.stylesheet,
            themeId = activeThemeInfo.name.componentId,
            palette = KeyboardPaletteCodec.decode(keyboardColorsRaw),
        )
    }

    val assetResolver = remember(activeThemeInfo) {
        FlorisAssetResolver(context, activeThemeInfo)
    }
    val snyggTheme = rememberSnyggTheme(stylesheet, assetResolver)
    val windowSpec by windowController.activeWindowSpec.collectAsState()
    val fontScale by remember { derivedStateOf { windowSpec.fontScale } }

    val state by keyboardManager.activeState.collectAsState()
    val attributes = mapOf(
        FlorisImeUi.Attr.Mode to state.keyboardMode.toString(),
        FlorisImeUi.Attr.ShiftState to state.inputShiftState.toString(),
    )

    MaterialTheme {
        CompositionLocalProvider(
            LocalTextStyle provides TextStyle.Default,
        ) {
            ProvideSnyggTheme(
                snyggTheme = snyggTheme,
                dynamicAccentColor = accentColor,
                fontSizeMultiplier = fontScale,
                assetResolver = assetResolver,
                rootAttributes = attributes,
                content = content,
                materialYouFlags = activeThemeInfo.config.materialYouFlags
            )
        }
    }
}
