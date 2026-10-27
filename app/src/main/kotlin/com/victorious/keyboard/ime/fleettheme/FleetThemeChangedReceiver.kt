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

package com.victorious.keyboard.ime.fleettheme

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.victorious.keyboard.FlorisApplication

/**
 * Exported because VictorLaunch sends the broadcast with `setPackage` to this app.
 * The extras are a wake-up. This receiver does not read them and does not write to the provider.
 */
class FleetThemeChangedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (!FleetThemeWake.isWake(intent?.action)) return
        val pending = goAsync()
        val app = context.applicationContext
        try {
            val client = (app as? FlorisApplication)?.fleetTheme?.value ?: FleetThemeClient(app)
            client.enqueueWake(pending)
        } catch (_: Exception) {
            pending.finish()
        }
    }
}
