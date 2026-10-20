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

package com.victorious.keyboard.ime.window

/**
 * How the system reports the bottom system bar. Used only to pick the default extra gap.
 * The padding itself always follows the insets, not this label.
 */
enum class NavigationStyle {
    GESTURE,
    BUTTONS,
    UNKNOWN,
}

/**
 * Bottom insets that can steal a tap from the space bar or the enter key.
 * Values are pixels and may be zero when the window did not receive them.
 */
data class BottomObstructionPx(
    val navigationBars: Int = 0,
    val mandatorySystemGestures: Int = 0,
    val tappableElement: Int = 0,
    val captionBar: Int = 0,
)

/**
 * How far the docked keyboard must sit above the system navigation area.
 *
 * [androidx.compose.foundation.layout.safeDrawingPadding] only includes system bars
 * (`navigationBars` and `captionBar`). On gesture navigation the home-gesture strip
 * ([mandatorySystemGestures], and on some devices [tappableElement]) is taller than the
 * drawn bar, so a key that is merely clear of the bar is still inside the gesture.
 * Padding is the largest of those insets, plus the user's extra gap.
 */
object KeyboardBottomClearance {
    const val GAP_MIN_DP = 0
    const val GAP_MAX_DP = 48
    const val GAP_GESTURE_DP = 16
    const val GAP_BUTTONS_DP = 4
    const val GAP_UNKNOWN_DP = 16

    /** Stored preference value meaning "use the recommendation for this navigation style". */
    const val GAP_AUTOMATIC = -1

    /** Used only when a phone reports no bottom inset at all. See [resolvedSystemInsetPx]. */
    const val MISSING_INSET_FALLBACK_DP = 48

    fun reportedInsetPx(obstruction: BottomObstructionPx): Int = maxOf(
        0,
        obstruction.navigationBars,
        obstruction.mandatorySystemGestures,
        obstruction.tappableElement,
        obstruction.captionBar,
    )

    /**
     * When every inset is zero the IME window was not told about the navigation area.
     * [fallbackPx] is applied only in that case, and only by the caller that knows the
     * window is a docked phone keyboard (edge-to-edge, targetSdk 36).
     */
    fun resolvedSystemInsetPx(obstruction: BottomObstructionPx, fallbackPx: Int = 0): Int {
        val reported = reportedInsetPx(obstruction)
        return if (reported > 0) reported else fallbackPx.coerceAtLeast(0)
    }

    fun navigationStyle(obstruction: BottomObstructionPx): NavigationStyle = when {
        obstruction.mandatorySystemGestures > 0 && obstruction.tappableElement == 0 -> NavigationStyle.GESTURE
        obstruction.tappableElement > 0 && obstruction.mandatorySystemGestures == 0 -> NavigationStyle.BUTTONS
        reportedInsetPx(obstruction) == 0 -> NavigationStyle.UNKNOWN
        obstruction.mandatorySystemGestures > obstruction.tappableElement -> NavigationStyle.GESTURE
        else -> NavigationStyle.BUTTONS
    }

    fun defaultGapDp(style: NavigationStyle): Int = when (style) {
        NavigationStyle.GESTURE -> GAP_GESTURE_DP
        NavigationStyle.BUTTONS -> GAP_BUTTONS_DP
        NavigationStyle.UNKNOWN -> GAP_UNKNOWN_DP
    }

    /** [storedDp] of [GAP_AUTOMATIC] follows [style]. Any other value is clamped to 0–48. */
    fun effectiveGapDp(storedDp: Int, style: NavigationStyle): Int = when {
        storedDp < 0 -> defaultGapDp(style)
        else -> storedDp.coerceIn(GAP_MIN_DP, GAP_MAX_DP)
    }

    /**
     * True when [pointerYInRootPx] is inside the system-owned strip at the bottom of the root
     * window. The user's extra gap is above this strip, so a release there still counts as a key.
     */
    fun pointerInSystemStrip(pointerYInRootPx: Float, rootHeightPx: Int, systemInsetPx: Int): Boolean {
        if (systemInsetPx <= 0 || rootHeightPx <= 0) return false
        return pointerYInRootPx >= rootHeightPx - systemInsetPx
    }
}
