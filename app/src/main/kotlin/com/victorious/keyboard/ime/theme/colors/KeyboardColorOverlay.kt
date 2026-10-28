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

package com.victorious.keyboard.ime.theme.colors

import androidx.compose.ui.graphics.Color
import org.florisboard.lib.snygg.SnyggAnnotationRule
import org.florisboard.lib.snygg.SnyggElementRule
import org.florisboard.lib.snygg.SnyggSelector
import org.florisboard.lib.snygg.SnyggSinglePropertySetEditor
import org.florisboard.lib.snygg.SnyggStylesheet
import org.florisboard.lib.snygg.value.SnyggStaticColorValue

/**
 * Paints a [KeyboardPalette] over Victorious Neon by replacing its color tokens.
 * Other themes are returned unchanged. Shapes, spacing, and the rest of the stylesheet stay.
 */
object KeyboardColorOverlay {
    val neonThemeIds = setOf("victorious_black", "victorious_neon", "victorious_neon_light")

    private val suggestionRules = setOf("smartbar-candidate-word", "smartbar-candidate-clip")
    private val pressedRules = setOf("key", "media-emoji-key")

    fun appliesTo(themeId: String): Boolean = themeId in neonThemeIds

    fun apply(
        stylesheet: SnyggStylesheet,
        themeId: String,
        palette: KeyboardPalette,
        force: Boolean = false,
    ): SnyggStylesheet {
        if (!force && !appliesTo(themeId)) return stylesheet
        val editor = stylesheet.edit()
        val defines = editor.rules[SnyggAnnotationRule.Defines] as? SnyggSinglePropertySetEditor
            ?: return stylesheet
        // Fleet colors are painted onto whatever stylesheet is loaded. A theme without --glow
        // would otherwise keep its old colors while the black squared stylesheet is still loading.
        if (!force && !defines.properties.containsKey("--glow")) return stylesheet
        val tokens = KeyboardColorTokens.from(palette)
        for ((name, color) in tokens.defines) {
            defines.properties[name] = snygg(color)
        }
        for ((rule, set) in editor.rules) {
            if (rule !is SnyggElementRule || set !is SnyggSinglePropertySetEditor) continue
            when {
                rule.elementName in suggestionRules && rule.selector == SnyggSelector.NONE && rule.attributes.isEmpty() -> {
                    set.properties["background"] = snygg(palette.suggestion)
                    set.properties["foreground"] = snygg(tokens.suggestionText)
                }
                rule.elementName in pressedRules && rule.selector == SnyggSelector.PRESSED && rule.attributes.isEmpty() -> {
                    set.properties["background"] = snygg(tokens.pressedKey)
                    set.properties["foreground"] = snygg(tokens.pressedKeyText)
                }
            }
        }
        return editor.build()
    }

    private fun snygg(argb: Int): SnyggStaticColorValue {
        val a = ((argb ushr 24) and 0xFF) / 255f
        val r = ((argb ushr 16) and 0xFF) / 255f
        val g = ((argb ushr 8) and 0xFF) / 255f
        val b = (argb and 0xFF) / 255f
        return SnyggStaticColorValue(Color(r, g, b, a))
    }
}
