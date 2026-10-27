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
import android.database.ContentObserver
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.content.pm.PackageManager
import com.victorious.keyboard.lib.fleet.CertDigest
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Reads `content://com.victorious.launch.theme/current`.
 * The only ContentResolver call is [query]. This class has no insert, update, or delete.
 * The provider package and its signing certificate are checked before a row is trusted.
 * A signature permission is not used as authorization.
 */
class FleetThemeClient(context: Context) {
    private val appContext = context.applicationContext
    private val contentUri: Uri = Uri.parse(FleetThemeContract.CONTENT_URI)
    private val worker = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "fleet-theme").apply { isDaemon = true }
    }
    private val observing = AtomicBoolean(false)
    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            enqueue(null)
        }
    }

    fun start() {
        ensureObserving()
        enqueue(null)
    }

    /** The broadcast is only a wake-up. Its extras are not the theme. */
    fun enqueueWake(pending: BroadcastReceiver.PendingResult) {
        ensureObserving()
        enqueue { pending.finish() }
    }

    private fun ensureObserving() {
        if (!observing.compareAndSet(false, true)) return
        try {
            appContext.contentResolver.registerContentObserver(contentUri, false, observer)
        } catch (_: Exception) {
            observing.set(false)
        }
    }

    private fun enqueue(after: (() -> Unit)?) {
        worker.execute {
            try {
                refresh()
            } finally {
                after?.invoke()
            }
        }
    }

    fun refresh() {
        try {
            FleetThemeRuntime.publish(read())
        } catch (_: SecurityException) {
            FleetThemeRuntime.publish(FleetThemeState.NotPinned)
        } catch (_: Exception) {
            FleetThemeRuntime.publish(FleetThemeState.Absent)
        }
    }

    private fun read(): FleetThemeState {
        val provider = appContext.packageManager.resolveContentProvider(FleetThemeContract.AUTHORITY, 0)
            ?: return FleetThemeState.Absent
        val certs = certificates(provider.packageName)
        val trust = FleetThemeAccess.trust(provider.packageName, certs)
        if (trust == FleetThemeTrust.Absent) return FleetThemeState.Absent
        if (trust == FleetThemeTrust.Untrusted) return FleetThemeState.NotPinned
        val cursor = appContext.contentResolver.query(contentUri, null, null, null, null)
            ?: return FleetThemeState.Absent
        cursor.use { rows ->
            if (!rows.moveToFirst()) return FleetThemeState.Absent
            return FleetThemeAccess.decide(
                packageName = provider.packageName,
                certs = certs,
                queryThrewSecurity = false,
                columns = columns(rows),
            )
        }
    }

    private fun columns(cursor: Cursor): Map<String, String?> {
        val out = LinkedHashMap<String, String?>()
        for (name in FleetThemeContract.COLUMNS) {
            val index = cursor.getColumnIndex(name)
            if (index < 0) continue
            out[name] = if (cursor.isNull(index)) null else cursor.getString(index)
        }
        return out
    }

    private fun certificates(packageName: String): List<String> {
        return try {
            val encoded = if (Build.VERSION.SDK_INT >= 28) {
                val info = appContext.packageManager.getPackageInfo(
                    packageName,
                    PackageManager.GET_SIGNING_CERTIFICATES,
                )
                info.signingInfo?.apkContentsSigners?.map { it.toByteArray() }.orEmpty()
            } else {
                @Suppress("DEPRECATION")
                val info = appContext.packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
                @Suppress("DEPRECATION")
                info.signatures?.map { it.toByteArray() }.orEmpty()
            }
            encoded.map { CertDigest.sha256(it) }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
