/*
 * Copyright (C) 2021-2025 The FlorisBoard Contributors
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

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Settings colors for the Victorious brand. Navy is the dark ground, amber is the primary,
 * hot pink is the accent. The light scheme uses a darker gold as [primary] so text buttons
 * stay readable on the cream background; bright amber stays the filled-chip color.
 */
val Navy = Color(0xFF010619)
val Amber = Color(0xFFFFC107)
val HotPink = Color(0xFFFF4D8D)

val brandDarkScheme = darkColorScheme(
    primary = Amber,
    onPrimary = Color(0xFF1A1200),
    primaryContainer = Color(0xFF3D3200),
    onPrimaryContainer = Color(0xFFFFE082),
    secondary = HotPink,
    onSecondary = Color(0xFF2A0614),
    secondaryContainer = Color(0xFF5C1834),
    onSecondaryContainer = Color(0xFFFFD0E0),
    tertiary = Color(0xFFFFB74D),
    onTertiary = Color(0xFF1A1200),
    tertiaryContainer = Color(0xFF4A3208),
    onTertiaryContainer = Color(0xFFFFE0B2),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Navy,
    onBackground = Color(0xFFF6F1E4),
    surface = Color(0xFF0E1730),
    onSurface = Color(0xFFF6F1E4),
    surfaceVariant = Color(0xFF1A2444),
    onSurfaceVariant = Color(0xFFD9C7A2),
    outline = Color(0xFFC9A227),
    outlineVariant = Color(0xFF2C3A5C),
    scrim = Color(0xFF000000),
    inverseSurface = Color(0xFFF6F1E4),
    inverseOnSurface = Navy,
    inversePrimary = Color(0xFF8A6200),
    surfaceDim = Color(0xFF010619),
    surfaceBright = Color(0xFF243056),
    surfaceContainerLowest = Color(0xFF00030C),
    surfaceContainerLow = Color(0xFF0A1224),
    surfaceContainer = Color(0xFF101A33),
    surfaceContainerHigh = Color(0xFF182240),
    surfaceContainerHighest = Color(0xFF222C4C),
)

val brandLightScheme = lightColorScheme(
    primary = Color(0xFF8A6200),
    onPrimary = Color(0xFFFFF8E7),
    primaryContainer = Color(0xFFFFE082),
    onPrimaryContainer = Color(0xFF1A1200),
    secondary = Color(0xFFC2185B),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFD9E4),
    onSecondaryContainer = Color(0xFF3E0016),
    tertiary = Color(0xFF6D4C00),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFE082),
    onTertiaryContainer = Color(0xFF241A00),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF6F1E6),
    onBackground = Navy,
    surface = Color(0xFFFFFDF8),
    onSurface = Navy,
    surfaceVariant = Color(0xFFEFE6D4),
    onSurfaceVariant = Color(0xFF4A4336),
    outline = Color(0xFF8A7A62),
    outlineVariant = Color(0xFFD9CBB2),
    scrim = Color(0xFF000000),
    inverseSurface = Navy,
    inverseOnSurface = Color(0xFFF6F1E4),
    inversePrimary = Amber,
    surfaceDim = Color(0xFFE6DFD2),
    surfaceBright = Color(0xFFFFFDF8),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFBF7F0),
    surfaceContainer = Color(0xFFF3EEE4),
    surfaceContainerHigh = Color(0xFFEBE4D6),
    surfaceContainerHighest = Color(0xFFE3DACC),
)
