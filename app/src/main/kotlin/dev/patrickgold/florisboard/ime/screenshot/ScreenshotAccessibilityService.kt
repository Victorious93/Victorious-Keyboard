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

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.graphics.Bitmap
import android.graphics.ColorSpace
import android.hardware.HardwareBuffer
import android.os.Build
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import androidx.annotation.RequiresApi

/**
 * Turns on only so the keyboard can call [takeScreenshot]. It does not subscribe to events and does not read
 * window content. The picture is handed to the caller and is not stored in this service.
 */
class ScreenshotAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        val info = serviceInfo ?: return
        info.eventTypes = 0
        info.packageNames = emptyArray()
        info.flags = info.flags and
            AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS.inv() and
            AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE.inv() and
            AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS.inv()
        serviceInfo = info
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        clear(this)
        return false
    }

    override fun onDestroy() {
        clear(this)
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Unused on purpose. Event types are cleared in onServiceConnected.
    }

    override fun onInterrupt() = Unit

    /**
     * Asks the platform for one screenshot of [displayId]. The callback receives a software bitmap, or null.
     * The hardware buffer is closed here. Nothing is written to a log.
     */
    fun capture(displayId: Int, onBitmap: (Bitmap?) -> Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            onBitmap(null)
            return
        }
        val id = if (displayId == Display.INVALID_DISPLAY) Display.DEFAULT_DISPLAY else displayId
        takeScreenshot(
            id,
            mainExecutor,
            object : TakeScreenshotCallback {
                override fun onSuccess(screenshot: ScreenshotResult) {
                    onBitmap(ScreenshotImages.softwareCopy(screenshot.hardwareBuffer, screenshot.colorSpace))
                }

                override fun onFailure(errorCode: Int) {
                    onBitmap(null)
                }
            },
        )
    }

    /**
     * System screenshot (API 28+). It does not return the picture, so the caller cannot save or copy that file.
     * Used only when [capture] fails on Android 11 or newer.
     */
    fun systemScreenshot(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return false
        return performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT)
    }

    companion object {
        @Volatile
        private var instance: ScreenshotAccessibilityService? = null

        fun current(): ScreenshotAccessibilityService? = instance

        private fun clear(service: ScreenshotAccessibilityService) {
            if (instance === service) instance = null
        }
    }
}

internal object ScreenshotImages {
    @RequiresApi(Build.VERSION_CODES.R)
    fun softwareCopy(buffer: HardwareBuffer, colorSpace: ColorSpace?): Bitmap? {
        try {
            val hardware = Bitmap.wrapHardwareBuffer(buffer, colorSpace) ?: return null
            return try {
                hardware.copy(Bitmap.Config.ARGB_8888, false)
            } finally {
                hardware.recycle()
            }
        } finally {
            buffer.close()
        }
    }
}
