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

package dev.patrickgold.florisboard.ime.smartbar

/**
 * Picks a suggestion-chip font size that fits the chip's **inner** height.
 *
 * The smartbar row is a fixed dp height. Chip margin and padding are also dp. The type size is sp,
 * so it grows with the system font scale (and the keyboard's own multiplier, which the theme has
 * already applied to [requestedSp]). Compose then clips a `Text` to the max height it was given,
 * from the top, which is why a 15sp word in a short pill showed only the upper half of the glyphs.
 * Horizontal overflow is a separate problem and is ellipsized by the text composable.
 *
 * The returned value is an sp size. Drawn at [fontScale], its line box (`size * fontScale *
 * [LINE_BOX]`) is at most [availableHeightDp].
 */
object CandidateChipFit {
    /** Ascent + descent relative to the font size, with a little room for the pill. */
    const val LINE_BOX = 1.35f

    fun fontSp(availableHeightDp: Float, requestedSp: Float, fontScale: Float): Float {
        val scale = fontScale.takeIf { it.isFinite() && it > 0f } ?: 1f
        val requested = requestedSp.takeIf { it.isFinite() && it > 0f } ?: 1f
        val inner = availableHeightDp.takeIf { it.isFinite() }?.coerceAtLeast(0f) ?: 0f
        if (inner <= 0f) return 1f
        val maxSp = inner / (LINE_BOX * scale)
        return minOf(requested, maxSp).coerceAtLeast(1f)
    }
}
