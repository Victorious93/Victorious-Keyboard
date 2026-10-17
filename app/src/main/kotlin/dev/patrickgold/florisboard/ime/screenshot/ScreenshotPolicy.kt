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

package dev.patrickgold.florisboard.ime.screenshot

import dev.patrickgold.florisboard.ime.toolbar.ToolbarActionId
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** What a tap on the Screenshot tile should do. The tile itself is omitted when the setting is off. */
enum class ScreenshotTap {
    /** Android 10 and below cannot take a screenshot we can save. */
    NeedsAndroid11,
    /** The accessibility helper is not turned on. */
    NeedsHelper,
    /** Hide the keyboard, then take the screenshot. */
    Capture,
}

/**
 * Decisions for the one-tap screenshot. Pure: no Android types, no image bytes, and no user text in the file name.
 */
object ScreenshotPolicy {
    /** [android.accessibilityservice.AccessibilityService.takeScreenshot] exists from API 30 (Android 11). */
    const val MIN_SDK = 30

    /** How long to wait after hiding the keyboard so the keyboard is not in the picture. */
    const val HIDE_DELAY_MS = 400L

    /** MediaStore relative directory. The trailing slash is what the platform asks for. */
    const val RELATIVE_PATH = "Pictures/Victorious Keyboard/"

    const val MIME = "image/png"

    /** Deep-link path of the setup screen (`Routes.Settings.Screenshot`). */
    const val SETTINGS_PATH = "settings/screenshot"

    private val safeName = Regex("screenshot-\\d{8}-\\d{6}-\\d{3}\\.png")
    private val stamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.US)

    fun decide(sdkInt: Int, helperEnabled: Boolean): ScreenshotTap = when {
        sdkInt < MIN_SDK -> ScreenshotTap.NeedsAndroid11
        !helperEnabled -> ScreenshotTap.NeedsHelper
        else -> ScreenshotTap.Capture
    }

    /**
     * Hidden names for the tools panel. The user's own hidden set is kept. When the Screenshot setting is off, the
     * tile is hidden as well, without writing that into the user's button order.
     */
    fun panelHidden(userHidden: Set<String>, tileShown: Boolean): Set<String> =
        if (tileShown) userHidden else userHidden + ToolbarActionId.SCREENSHOT.name

    /**
     * True only when the master accessibility switch is on and the enabled-services list contains this component.
     * Entries are matched whole, so a longer name that merely starts with ours does not count.
     */
    fun helperEnabled(
        accessibilityOn: Boolean,
        enabledServices: String?,
        componentFlat: String,
        componentShort: String,
    ): Boolean {
        if (!accessibilityOn || componentFlat.isBlank()) return false
        val listed = enabledServices?.split(':')?.map { it.trim() }.orEmpty()
        return listed.any { entry ->
            entry == componentFlat || (componentShort.isNotBlank() && entry == componentShort)
        }
    }

    /** `screenshot-yyyyMMdd-HHmmss-SSS.png` in [zone]. The clock is the only input. */
    fun filename(epochMillis: Long, zone: ZoneId): String {
        val zoned = Instant.ofEpochMilli(epochMillis).atZone(zone)
        val milli = (zoned.nano / 1_000_000).toString().padStart(3, '0')
        return "screenshot-${stamp.format(zoned)}-$milli.png"
    }

    fun isSafeFilename(name: String): Boolean = safeName.matches(name)
}
