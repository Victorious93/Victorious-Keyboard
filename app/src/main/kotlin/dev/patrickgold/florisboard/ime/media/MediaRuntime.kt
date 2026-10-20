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

package dev.patrickgold.florisboard.ime.media

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.view.KeyEvent
import dev.patrickgold.florisboard.lib.fleet.CertDigest
import dev.patrickgold.florisboard.lib.fleet.FleetGrants
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Sends media keys, or File Manager's bridge when [useBridge] is true.
 * Call this off the main thread: the bridge waits for a reply. Titles are not logged.
 */
object MediaRuntime {
    private val ids = AtomicInteger(1)

    fun perform(context: Context, command: MediaCommand, bridgePackage: String?): MediaOutcome {
        val app = context.applicationContext
        val plan = MediaControl.plan(command, bridgePackage != null, "m" + ids.getAndIncrement())
        return MediaControl.run(plan, AndroidSink(app, bridgePackage))
    }

    fun nowPlaying(context: Context, bridgePackage: String?): FileManagerReply.State? {
        return bridgeReply(context, bridgePackage) as? FileManagerReply.State
    }

    /** One `media.state` reply. A missing bridge is unreadable. Titles are not logged. */
    fun bridgeReply(context: Context, bridgePackage: String?): FileManagerReply {
        if (bridgePackage == null) return FileManagerReply.Unreadable
        val body = FileManagerProtocol.encode("media.state", "m" + ids.getAndIncrement()) ?: return FileManagerReply.Unreadable
        val raw = FileManagerClient(context.applicationContext).exchange(body, bridgePackage)
        return FileManagerProtocol.parse(raw)
    }

    /**
     * Sets the player's own volume with `media.volume`. The system music stream is not adjusted.
     * [current] null starts at the midpoint. The returned level is the one that was requested.
     */
    fun performPlayerVolume(context: Context, louder: Boolean, current: Int?, bridgePackage: String?): Pair<MediaOutcome, Int> {
        val level = FileManagerProtocol.nextPlayerLevel(current, louder)
        val plan = MediaControl.planPlayerVolume(level, bridgePackage != null, "v" + ids.getAndIncrement())
        val outcome = MediaControl.run(plan, AndroidSink(context.applicationContext, bridgePackage))
        return outcome to level
    }
}

/**
 * File Manager packages found on this phone. Neither package has to be installed. A missing service is
 * skipped. The seek switch and the grant list come from preferences.
 */
object FileManagerInstall {
    fun certificates(context: Context): List<Pair<String, String>> {
        val seen = listOf(FileManagerProtocol.PACKAGE, FileManagerProtocol.PACKAGE_DEBUG).mapNotNull { name ->
            val action = FileManagerProtocol.actionFor(name) ?: return@mapNotNull null
            if (resolve(context, name) == null) return@mapNotNull null
            val cert = certificate(context, name) ?: return@mapNotNull null
            FileManagerProtocol.Seen(name, action, cert)
        }
        return FileManagerProtocol.discover(seen)
    }

    fun ready(context: Context, seekEnabled: Boolean, grantsRaw: String): Boolean {
        return bridgePackage(context, seekEnabled, grantsRaw) != null
    }

    /** The approved package the bridge should bind, or null. Release is listed before debug. */
    fun bridgePackage(context: Context, seekEnabled: Boolean, grantsRaw: String): String? {
        return FileManagerProtocol.bridgePackage(certificates(context), FleetGrants.parse(grantsRaw), seekEnabled)
    }

    fun certificate(context: Context, packageName: String): String? {
        if (!FileManagerProtocol.allowed(packageName)) return null
        return try {
            val encoded = if (Build.VERSION.SDK_INT >= 28) {
                val info = context.packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                info.signingInfo?.apkContentsSigners?.firstOrNull()?.toByteArray()
            } else {
                @Suppress("DEPRECATION")
                val info = context.packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
                @Suppress("DEPRECATION")
                info.signatures?.firstOrNull()?.toByteArray()
            } ?: return null
            CertDigest.sha256(encoded)
        } catch (_: Exception) {
            null
        }
    }
}

private class AndroidSink(private val context: Context, private val bridgePackage: String?) : MediaSink {
    private val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    override fun press(action: Int, keyCode: Int) {
        audio.dispatchMediaKeyEvent(KeyEvent(action, keyCode))
    }

    override fun volume(stream: Int, direction: Int, flags: Int) {
        audio.adjustStreamVolume(stream, direction, flags)
    }

    override fun exchange(body: String): String? {
        val name = bridgePackage ?: return null
        return FileManagerClient(context).exchange(body, name)
    }
}

/** One request to File Manager. A missing service or a timeout returns null and the caller uses media keys. */
internal class FileManagerClient(private val context: Context) {
    fun exchange(body: String, packageName: String? = null): String? {
        val component = component(packageName) ?: return null
        val latch = CountDownLatch(1)
        val reply = arrayOfNulls<String>(1)
        val incoming = Messenger(object : Handler(Looper.getMainLooper()) {
            override fun handleMessage(msg: Message) {
                if (msg.what == FileManagerProtocol.WHAT_RESPONSE) {
                    reply[0] = msg.data?.getString(FileManagerProtocol.KEY_RESPONSE)
                }
                latch.countDown()
            }
        })
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                val message = Message.obtain(null, FileManagerProtocol.WHAT_REQUEST)
                message.data = Bundle().apply { putString(FileManagerProtocol.KEY_REQUEST, body) }
                message.replyTo = incoming
                try {
                    Messenger(service).send(message)
                } catch (_: Exception) {
                    latch.countDown()
                }
            }

            override fun onServiceDisconnected(name: ComponentName) {
                latch.countDown()
            }
        }
        val intent = Intent().setComponent(component)
        val bound = try {
            context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        } catch (_: Exception) {
            false
        }
        if (!bound) return null
        try {
            latch.await(1_500, TimeUnit.MILLISECONDS)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        } finally {
            try {
                context.unbindService(connection)
            } catch (_: Exception) {
                // Already unbound.
            }
        }
        return reply[0]
    }

    private fun component(packageName: String?): ComponentName? = FileManagerInstall.resolve(context, packageName)
}

internal fun FileManagerInstall.resolve(context: Context, packageName: String?): ComponentName? {
    val name = packageName ?: return null
    val action = FileManagerProtocol.actionFor(name) ?: return null
    val intent = Intent(action).setPackage(name)
    val info = try {
        if (Build.VERSION.SDK_INT >= 33) {
            context.packageManager.queryIntentServices(intent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.queryIntentServices(intent, 0)
        }
    } catch (_: Exception) {
        emptyList()
    }.firstOrNull() ?: return null
    val service = info.serviceInfo ?: return null
    if (service.packageName != name) return null
    return ComponentName(service.packageName, service.name)
}
