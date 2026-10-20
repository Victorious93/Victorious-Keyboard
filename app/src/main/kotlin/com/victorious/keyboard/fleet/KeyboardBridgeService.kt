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

package com.victorious.keyboard.fleet

import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Binder
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.ime.fleet.ExternalInputPort
import dev.patrickgold.florisboard.ime.fleet.RemoteControlPort
import dev.patrickgold.florisboard.lib.fleet.BridgeInput
import dev.patrickgold.florisboard.lib.fleet.BridgeProtocol
import dev.patrickgold.florisboard.lib.fleet.CertDigest
import dev.patrickgold.florisboard.lib.fleet.RemoteEnvelope

/**
 * Bridge for the Droidcommand hub. External control is off until the user turns it on. The old operations may type
 * through [BridgeInput]. The remote-control envelope v1 ([RemoteEnvelope]) never does: it only reports status,
 * changes mode or theme, or sends media keys. The reply never includes what the user typed. The caller's UID is
 * the one Messenger stamped on the message. Reading it later on this handler would see this process instead of the caller.
 */
class KeyboardBridgeService : Service() {
    private val prefs by FlorisPreferenceStore
    private val messenger = Messenger(Incoming())
    private val port by lazy { ExternalInputPort(applicationContext) }
    private val remote by lazy { RemoteControlPort(applicationContext) }
    private val nonces = RemoteEnvelope.NonceCache()

    override fun onBind(intent: Intent?): IBinder? {
        if (intent?.action != BridgeProtocol.ACTION) return null
        return messenger.binder
    }

    private inner class Incoming : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            val replyTo = msg.replyTo ?: return
            val callerUid = BridgeProtocol.callerUid(msg.sendingUid)
            val request = if (msg.what == BridgeProtocol.WHAT_REQUEST) {
                msg.data?.getString(BridgeProtocol.KEY_REQUEST)
            } else {
                null
            }
            val response = try {
                if (callerUid == null) {
                    if (RemoteEnvelope.peekOp(request) in RemoteEnvelope.OPS) {
                        RemoteEnvelope.handle(request, false, false, "", 0L, nonces, remote)
                    } else {
                        BridgeProtocol.response("", authorized = false, controlEnabled = false, imeRunning = false)
                    }
                } else {
                    answer(request, callerUid)
                }
            } catch (e: Exception) {
                BridgeProtocol.response("", authorized = false, controlEnabled = false, imeRunning = false)
            }
            val outgoing = Message.obtain(null, BridgeProtocol.WHAT_RESPONSE)
            outgoing.data = Bundle().apply { putString(BridgeProtocol.KEY_RESPONSE, response) }
            try {
                replyTo.send(outgoing)
            } catch (e: Exception) {
                // The caller is already gone.
            }
        }
    }

    private fun answer(request: String?, callingUid: Int): String {
        val identity = Binder.clearCallingIdentity()
        try {
            val packages = packageManager.getPackagesForUid(callingUid)?.toList().orEmpty()
            val certificatesPresent = packages.isNotEmpty() && packages.all { certificatePresent(it) }
            val authorized = BridgeProtocol.authorized(packages, certificatesPresent)
            val enabled = if (authorized) {
                try {
                    prefs.fleet.externalControl.get()
                } catch (e: Exception) {
                    false
                }
            } else {
                false
            }
            val source = packages.filter { it in BridgeProtocol.allowedPackages }.minOrNull().orEmpty()
            if (RemoteEnvelope.peekOp(request) in RemoteEnvelope.OPS) {
                return RemoteEnvelope.handle(
                    request,
                    authorized,
                    enabled,
                    source,
                    System.currentTimeMillis(),
                    nonces,
                    remote,
                )
            }
            return BridgeInput.handle(request, authorized, enabled, port)
        } finally {
            Binder.restoreCallingIdentity(identity)
        }
    }

    private fun certificatePresent(packageName: String): Boolean {
        return try {
            val encoded = if (Build.VERSION.SDK_INT >= 28) {
                val info = packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                info.signingInfo?.apkContentsSigners?.firstOrNull()?.toByteArray()
            } else {
                @Suppress("DEPRECATION")
                val info = packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
                @Suppress("DEPRECATION")
                info.signatures?.firstOrNull()?.toByteArray()
            }
            encoded != null && CertDigest.sha256(encoded).isNotBlank()
        } catch (e: Exception) {
            false
        }
    }
}
