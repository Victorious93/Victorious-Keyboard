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

package dev.patrickgold.florisboard.ime.window

import android.content.Context
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.captionBar
import androidx.compose.foundation.layout.mandatorySystemGestures
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.tappableElement
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.jetpref.datastore.model.observeAsState

/**
 * Height of the system-owned strip at the bottom of the IME root, in px.
 * A touch that enters this strip must not commit the key it started on.
 * Zero when the caller is outside the IME window.
 */
val LocalImeSystemBottomStrip = staticCompositionLocalOf { 0 }

data class ImeBottomClearance(
    /** Extra padding to add under [WindowInsets.safeDrawing], so the two are not summed twice. */
    val extraBottom: Dp,
    /** System strip only. Does not include the user's extra gap. */
    val systemStripPx: Int,
    val effectiveGapDp: Int,
    val automatic: Boolean,
)

object NavigationStyleProbe {
    /**
     * Prefer the insets. When they are all zero, fall back to the framework navigation-mode
     * resource (0 = 3-button, 1 = 2-button, 2 = gesture). That resource is not a public API;
     * a missing id leaves the style unknown and the gap at 16 dp.
     */
    fun resolve(obstruction: BottomObstructionPx, context: Context): NavigationStyle {
        val fromInsets = KeyboardBottomClearance.navigationStyle(obstruction)
        if (fromInsets != NavigationStyle.UNKNOWN) return fromInsets
        val id = context.resources.getIdentifier("config_navBarInteractionMode", "integer", "android")
        if (id == 0) return NavigationStyle.UNKNOWN
        return when (context.resources.getInteger(id)) {
            2 -> NavigationStyle.GESTURE
            0, 1 -> NavigationStyle.BUTTONS
            else -> NavigationStyle.UNKNOWN
        }
    }
}

@Composable
fun rememberBottomObstruction(): BottomObstructionPx {
    val density = LocalDensity.current
    return BottomObstructionPx(
        navigationBars = WindowInsets.navigationBars.getBottom(density),
        mandatorySystemGestures = WindowInsets.mandatorySystemGestures.getBottom(density),
        tappableElement = WindowInsets.tappableElement.getBottom(density),
        captionBar = WindowInsets.captionBar.getBottom(density),
    )
}

/**
 * @param allowFallback when true and every inset is 0, assume a 48 dp navigation area.
 *  Callers pass true only for a docked phone keyboard. Floating mode and tablets pass false.
 */
@Composable
fun rememberImeBottomClearance(allowFallback: Boolean): ImeBottomClearance {
    val context = LocalContext.current
    val density = LocalDensity.current
    val prefs by FlorisPreferenceStore
    val stored by prefs.keyboard.bottomGapDp.observeAsState()
    val obstruction = rememberBottomObstruction()
    val style = NavigationStyleProbe.resolve(obstruction, context)
    val gapDp = KeyboardBottomClearance.effectiveGapDp(stored, style)
    val fallbackPx = if (allowFallback) {
        with(density) { KeyboardBottomClearance.MISSING_INSET_FALLBACK_DP.dp.roundToPx() }
    } else {
        0
    }
    val systemPx = KeyboardBottomClearance.resolvedSystemInsetPx(obstruction, fallbackPx)
    val wantedPx = systemPx + with(density) { gapDp.dp.roundToPx() }
    val safePx = WindowInsets.safeDrawing.getBottom(density)
    val extraPx = (wantedPx - safePx).coerceAtLeast(0)
    return ImeBottomClearance(
        extraBottom = with(density) { extraPx.toDp() },
        systemStripPx = systemPx,
        effectiveGapDp = gapDp,
        automatic = stored < 0,
    )
}
