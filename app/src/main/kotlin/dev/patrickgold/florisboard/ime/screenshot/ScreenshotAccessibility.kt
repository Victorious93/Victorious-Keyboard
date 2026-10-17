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

import android.content.ComponentName
import android.content.Context
import android.provider.Settings

/** Reads whether the user has turned this app's screenshot helper on. Does not look at the screen. */
object ScreenshotAccessibility {
    fun isEnabled(context: Context): Boolean {
        val resolver = context.contentResolver
        val master = Settings.Secure.getInt(resolver, Settings.Secure.ACCESSIBILITY_ENABLED, 0) == 1
        val listed = Settings.Secure.getString(resolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        val component = ComponentName(context, ScreenshotAccessibilityService::class.java)
        return ScreenshotPolicy.helperEnabled(
            accessibilityOn = master,
            enabledServices = listed,
            componentFlat = component.flattenToString(),
            componentShort = component.flattenToShortString(),
        )
    }
}
