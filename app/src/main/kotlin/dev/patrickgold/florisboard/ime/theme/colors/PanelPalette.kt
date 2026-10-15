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

package dev.patrickgold.florisboard.ime.theme.colors

/**
 * Colors for the keyboard's own panels that are not Snygg elements (the AI panel and the clip editor), derived from the
 * active keyboard theme so light themes and the Keyboard colors screen reach them too. Opaque ARGB ints; pure.
 */
data class PanelPalette(
    val background: Int,
    val surface: Int,
    val ink: Int,
    val muted: Int,
    val line: Int,
    val selected: Int,
) {
    companion object {
        /** The values the panels used before they followed the theme (Victorious Black). */
        val Fallback = PanelPalette(
            background = 0xFF050505.toInt(),
            surface = 0xFF161616.toInt(),
            ink = 0xFFE8E8E8.toInt(),
            muted = 0xFFBDBDBD.toInt(),
            line = 0xFF3A3A3A.toInt(),
            selected = 0xFF3A3A3A.toInt(),
        )

        /** Below this, text on the panel is replaced by a readable ink (WCAG AA for normal text). */
        const val MIN_CONTRAST = 4.5

        /**
         * Builds the palette from what the theme says. Each argument is null when the theme leaves it unset or
         * transparent (an image background, for example); the matching [Fallback] value is used instead. Text that
         * would be hard to read on the key color is replaced, so a theme can never make the panel unreadable.
         */
        fun from(window: Int?, key: Int?, keyText: Int?, pressedKey: Int?): PanelPalette {
            val background = window.opaqueOr(Fallback.background)
            val surface = key.opaqueOr(Fallback.surface)
            val wanted = keyText.opaqueOr(Fallback.ink)
            val ink = if (KeyboardColorMath.contrast(wanted, surface) >= MIN_CONTRAST &&
                KeyboardColorMath.contrast(wanted, background) >= MIN_CONTRAST
            ) {
                wanted
            } else {
                KeyboardColorMath.readableOn(surface)
            }
            val selected = pressedKey.opaqueOr(KeyboardColorMath.mix(surface, ink, 0.22f))
            return PanelPalette(
                background = background,
                surface = surface,
                ink = ink,
                muted = KeyboardColorMath.mix(ink, surface, 0.28f),
                line = KeyboardColorMath.mix(surface, ink, 0.22f),
                selected = if (selected == surface) KeyboardColorMath.mix(surface, ink, 0.22f) else selected,
            )
        }

        private fun Int?.opaqueOr(default: Int): Int {
            if (this == null || (this ushr 24) == 0) return default
            return this or 0xFF000000.toInt()
        }
    }
}
