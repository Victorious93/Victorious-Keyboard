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

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.annotation.StringRes
import dev.patrickgold.florisboard.FlorisImeService
import dev.patrickgold.florisboard.R
import java.time.ZoneId
import java.util.concurrent.Executors

/**
 * Tile tap. Hides the keyboard, waits, then takes one screenshot and saves it. A second tap replaces a wait that
 * has not fired yet. Image bytes are not logged.
 *
 * [AccessibilityService.takeScreenshot] is the path that returns pixels we can store. If that call fails on
 * Android 11 or newer, [GLOBAL_ACTION_TAKE_SCREENSHOT] is the fallback: the system keeps the file, and we cannot
 * copy it.
 */
object ScreenshotCapture {
    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "vk-screenshot").apply { isDaemon = true }
    }
    private var pending: Runnable? = null

    fun onTile(context: Context) {
        val app = context.applicationContext
        when (ScreenshotPolicy.decide(Build.VERSION.SDK_INT, ScreenshotAccessibility.isEnabled(app))) {
            ScreenshotTap.NeedsAndroid11 -> toast(app, R.string.screenshot__needs_android_11)
            ScreenshotTap.NeedsHelper -> FlorisImeService.launchSettingsAt(ScreenshotPolicy.SETTINGS_PATH)
            ScreenshotTap.Capture -> schedule(app)
        }
    }

    private fun schedule(app: Context) {
        pending?.let { main.removeCallbacks(it) }
        FlorisImeService.hideUi()
        val task = Runnable {
            pending = null
            capture(app)
        }
        pending = task
        main.postDelayed(task, ScreenshotPolicy.HIDE_DELAY_MS)
    }

    private fun capture(app: Context) {
        val service = ScreenshotAccessibilityService.current()
        if (service == null) {
            FlorisImeService.launchSettingsAt(ScreenshotPolicy.SETTINGS_PATH)
            return
        }
        service.capture(FlorisImeService.currentDisplayId()) { bitmap ->
            if (bitmap == null) {
                val tookSystemShot = service.systemScreenshot()
                val message = if (tookSystemShot) {
                    R.string.screenshot__system_fallback
                } else {
                    R.string.screenshot__failed
                }
                toast(app, message)
                return@capture
            }
            io.execute {
                val name = ScreenshotPolicy.filename(System.currentTimeMillis(), ZoneId.systemDefault())
                val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ScreenshotStore.savePng(app, bitmap, name)
                } else {
                    null
                }
                bitmap.recycle()
                main.post {
                    if (uri == null) {
                        toast(app, R.string.screenshot__failed)
                    } else {
                        openSaved(app, uri)
                    }
                }
            }
        }
    }

    private fun openSaved(app: Context, uri: android.net.Uri) {
        val intent = Intent(app, ScreenshotSavedActivity::class.java).apply {
            data = uri
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
        }
        try {
            app.startActivity(intent)
        } catch (_: Exception) {
            toast(app, R.string.screenshot__saved_body)
        }
    }

    private fun toast(context: Context, @StringRes id: Int) {
        Toast.makeText(context, id, Toast.LENGTH_SHORT).show()
    }
}
