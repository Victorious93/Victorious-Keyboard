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

package com.victorious.keyboard.app.apptheme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.victorious.keyboard.ime.fleettheme.FleetPaint
import com.victorious.keyboard.ime.theme.colors.KeyboardColorMath

/** Settings colors taken from a fleet palette. Roles that the palette does not name stay on those colors. */
fun fleetColorScheme(paint: FleetPaint): ColorScheme {
    val background = Color(paint.background)
    val surface = Color(paint.surface)
    val primary = Color(paint.primary)
    val secondary = Color(paint.secondary)
    val onBackground = Color(paint.onBackground)
    val muted = Color(paint.muted)
    val danger = Color(paint.danger)
    val stale = Color(paint.stale)
    val tile = Color(paint.tile)
    val onPrimary = Color(KeyboardColorMath.readableOn(paint.primary))
    val onSecondary = Color(KeyboardColorMath.readableOn(paint.secondary))
    val onError = Color(KeyboardColorMath.readableOn(paint.danger))
    val onTertiary = Color(KeyboardColorMath.readableOn(paint.stale))
    return if (paint.light) {
        lightColorScheme(
            primary = primary,
            onPrimary = onPrimary,
            primaryContainer = surface,
            onPrimaryContainer = onBackground,
            secondary = secondary,
            onSecondary = onSecondary,
            secondaryContainer = tile,
            onSecondaryContainer = onBackground,
            tertiary = stale,
            onTertiary = onTertiary,
            error = danger,
            onError = onError,
            errorContainer = danger,
            onErrorContainer = onError,
            background = background,
            onBackground = onBackground,
            surface = surface,
            onSurface = onBackground,
            surfaceVariant = tile,
            onSurfaceVariant = muted,
            outline = muted,
        )
    } else {
        darkColorScheme(
            primary = primary,
            onPrimary = onPrimary,
            primaryContainer = surface,
            onPrimaryContainer = onBackground,
            secondary = secondary,
            onSecondary = onSecondary,
            secondaryContainer = tile,
            onSecondaryContainer = onBackground,
            tertiary = stale,
            onTertiary = onTertiary,
            error = danger,
            onError = onError,
            errorContainer = danger,
            onErrorContainer = onError,
            background = background,
            onBackground = onBackground,
            surface = surface,
            onSurface = onBackground,
            surfaceVariant = tile,
            onSurfaceVariant = muted,
            outline = muted,
        )
    }
}
