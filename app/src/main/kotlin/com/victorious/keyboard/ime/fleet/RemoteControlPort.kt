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

package com.victorious.keyboard.ime.fleet

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import android.view.KeyEvent
import com.victorious.keyboard.FlorisImeService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import com.victorious.keyboard.R
import com.victorious.keyboard.app.FlorisPreferenceStore
import com.victorious.keyboard.ime.mode.OperatingMode
import com.victorious.keyboard.ime.theme.extCoreTheme
import com.victorious.keyboard.lib.ext.ExtensionComponentName
import com.victorious.keyboard.lib.fleet.RemoteEnvelope

/**
 * Android side of the remote-control envelope. It changes the mode and the theme, and it sends media keys.
 * Node status reports the application id and whether the keyboard service is running.
 * It does not type into the focused app, and it does not read the field.
 */
class RemoteControlPort(context: Context) : RemoteEnvelope.Port {
    private val app = context.applicationContext
    private val prefs by FlorisPreferenceStore

    override fun status(): RemoteEnvelope.Status {
        return RemoteEnvelope.Status(
            imeRunning = FlorisImeService.isRunning(),
            mode = prefs.modes.defaultMode.get().name,
            theme = prefs.theme.dayThemeId.get().toString(),
        )
    }

    override fun setMode(mode: String): RemoteEnvelope.Effect {
        val selected = OperatingMode.entries.firstOrNull { it.name.equals(mode, ignoreCase = true) }
        if (selected == null || !selected.isSelectable) return RemoteEnvelope.Effect.NOT_FOUND
        return try {
            runBlocking(Dispatchers.IO) { prefs.modes.defaultMode.set(selected) }
            RemoteEnvelope.Effect.DONE
        } catch (_: Exception) {
            RemoteEnvelope.Effect.INTERNAL
        }
    }

    override fun setTheme(themeId: String): RemoteEnvelope.Effect {
        val name = try {
            ExtensionComponentName.from(themeId)
        } catch (_: Exception) {
            return RemoteEnvelope.Effect.NOT_FOUND
        }
        if (name.extensionId != extCoreTheme("day").extensionId) return RemoteEnvelope.Effect.NOT_FOUND
        if (!name.componentId.matches(Regex("[a-z0-9_]+"))) return RemoteEnvelope.Effect.NOT_FOUND
        return try {
            runBlocking(Dispatchers.IO) {
                prefs.theme.dayThemeId.set(name)
                prefs.theme.nightThemeId.set(name)
            }
            RemoteEnvelope.Effect.DONE
        } catch (_: Exception) {
            RemoteEnvelope.Effect.INTERNAL
        }
    }

    override fun mediaKey(keyCode: Int): RemoteEnvelope.Effect {
        return try {
            val audio = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
            audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
            RemoteEnvelope.Effect.DONE
        } catch (_: Exception) {
            RemoteEnvelope.Effect.INTERNAL
        }
    }

    override fun volume(direction: Int): RemoteEnvelope.Effect {
        val adjust = when (direction) {
            1 -> AudioManager.ADJUST_RAISE
            -1 -> AudioManager.ADJUST_LOWER
            else -> return RemoteEnvelope.Effect.INTERNAL
        }
        return try {
            val audio = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, adjust, AudioManager.FLAG_SHOW_UI)
            RemoteEnvelope.Effect.DONE
        } catch (_: Exception) {
            RemoteEnvelope.Effect.INTERNAL
        }
    }

    override fun nodePulse(): RemoteEnvelope.NodePulse {
        return RemoteEnvelope.NodePulse(
            packageName = app.packageName,
            imeRunning = FlorisImeService.isRunning(),
        )
    }

    override fun prompt() {
        try {
            val manager = app.getSystemService(NotificationManager::class.java) ?: return
            val channelId = "fleet_remote"
            manager.createNotificationChannel(
                NotificationChannel(channelId, app.getString(R.string.floris_app_name), NotificationManager.IMPORTANCE_DEFAULT),
            )
            val notification = Notification.Builder(app, channelId)
                .setSmallIcon(R.drawable.ic_keyboard_keys)
                .setContentTitle(app.getString(R.string.floris_app_name))
                .setContentText(app.getString(R.string.settings__fleet__remote_confirm))
                .setAutoCancel(true)
                .build()
            manager.notify(REMOTE_NOTICE, notification)
        } catch (_: Exception) {
            // The command is still refused. A missing notification must not approve it.
        }
    }

    private companion object {
        const val REMOTE_NOTICE = 0x5243
    }
}
