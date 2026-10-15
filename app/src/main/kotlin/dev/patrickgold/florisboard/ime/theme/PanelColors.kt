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

package dev.patrickgold.florisboard.ime.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.toArgb
import dev.patrickgold.florisboard.ime.theme.colors.PanelPalette
import org.florisboard.lib.snygg.SnyggSelector
import org.florisboard.lib.snygg.ui.rememberSnyggThemeQuery

/** [PanelPalette] as Compose colors. */
data class PanelColors(
    val background: Color,
    val surface: Color,
    val ink: Color,
    val muted: Color,
    val line: Color,
    val selected: Color,
)

/** The panel colors for the active keyboard theme (window, key and pressed-key colors). Call inside the keyboard UI. */
@Composable
fun rememberPanelColors(): PanelColors {
    val window = rememberSnyggThemeQuery(FlorisImeUi.Window.elementName)
    val key = rememberSnyggThemeQuery(FlorisImeUi.Key.elementName)
    val pressed = rememberSnyggThemeQuery(FlorisImeUi.Key.elementName, selector = SnyggSelector.PRESSED)
    val windowColor = window.background()
    val keyColor = key.background()
    val keyText = key.foreground()
    val pressedColor = pressed.background()
    return remember(windowColor, keyColor, keyText, pressedColor) {
        val palette = PanelPalette.from(
            window = windowColor.argbOrNull(),
            key = keyColor.argbOrNull(),
            keyText = keyText.argbOrNull(),
            pressedKey = pressedColor.argbOrNull(),
        )
        PanelColors(
            background = Color(palette.background),
            surface = Color(palette.surface),
            ink = Color(palette.ink),
            muted = Color(palette.muted),
            line = Color(palette.line),
            selected = Color(palette.selected),
        )
    }
}

private fun Color.argbOrNull(): Int? = if (isSpecified) toArgb() else null
