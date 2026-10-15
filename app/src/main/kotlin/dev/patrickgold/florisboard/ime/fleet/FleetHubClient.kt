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

package dev.patrickgold.florisboard.ime.fleet

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.os.Build
import android.os.IBinder
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.lib.fleet.FleetHub
import dev.patrickgold.florisboard.lib.fleet.FleetHubContract
import dev.patrickgold.florisboard.lib.fleet.FleetOutcome
import dev.patrickgold.florisboard.lib.fleet.FleetSession
import dev.patrickgold.florisboard.lib.fleet.FleetSnippet
import dev.patrickgold.florisboard.lib.fleet.FleetSnapshot
import dev.patrickgold.florisboard.lib.fleet.IFleetHub
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Binds to the hub when its package is installed and the fleet switch is on.
 * Binder calls stay off the main thread. Failures leave [snapshot] disconnected
 * and never throw to the IME. Returned text is not logged.
 */
class FleetHubClient(private val context: Context) {
    private val prefs by FlorisPreferenceStore
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val calls = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "fleet-hub").apply { isDaemon = true }
    }
    private val generation = AtomicInteger()

    private val _snapshot = MutableStateFlow(FleetSnapshot.absent())
    val snapshot: StateFlow<FleetSnapshot> = _snapshot

    @Volatile private var hub: FleetHub? = null
    @Volatile private var bound = false
    private var started = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val remote = try {
                if (service == null) null else IFleetHub.Stub.asInterface(service)
            } catch (e: Exception) {
                null
            }
            if (remote == null) {
                hub = null
                _snapshot.value = FleetSnapshot.installed()
                return
            }
            try {
                service?.linkToDeath({
                    hub = null
                    bound = false
                    _snapshot.value = FleetSnapshot.installed()
                }, 0)
            } catch (e: Exception) {
                hub = null
                _snapshot.value = FleetSnapshot.installed()
                return
            }
            hub = AidlFleetHub(remote)
            val gen = generation.get()
            calls.execute { publish(gen) }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            hub = null
            bound = false
            _snapshot.value = if (isInstalled()) FleetSnapshot.installed() else FleetSnapshot.absent()
        }
    }

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            refresh()
        }
    }

    fun start() {
        if (started) return
        started = true
        scope.launch {
            var failures = 0
            while (failures < 20) {
                try {
                    prefs.fleet.enabled.asFlow().collect { refresh() }
                    return@launch
                } catch (e: Exception) {
                    failures++
                    _snapshot.value = FleetSnapshot.absent()
                    delay(250)
                }
            }
        }
        val filter = IntentFilter(Intent.ACTION_PACKAGE_ADDED).apply {
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addDataScheme("package")
        }
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                context.registerReceiver(packageReceiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                context.registerReceiver(packageReceiver, filter)
            }
        } catch (e: Exception) {
            // Presence is checked again when the tools panel opens.
        }
        refresh()
    }

    fun refresh() {
        val gen = generation.incrementAndGet()
        scope.launch {
            val enabled = try {
                prefs.fleet.enabled.get()
            } catch (e: Exception) {
                false
            }
            val installed = enabled && isInstalled()
            if (!installed) {
                withContext(Dispatchers.Main) { unbindOnMain() }
                if (generation.get() == gen) _snapshot.value = FleetSnapshot.absent()
                return@launch
            }
            withContext(Dispatchers.Main) { bindOnMain(gen) }
        }
    }

    fun ask(action: String, text: String): String? = call { it.askHub(action, text) }?.take(FleetOutcomeClamp)

    fun insertSnippet(snippet: FleetSnippet): FleetOutcome? = call { snippet.insertText(it) }

    private fun <T> call(block: (FleetHub) -> T): T? {
        val current = hub ?: return null
        val future = calls.submit<T?> {
            try {
                block(current)
            } catch (e: Exception) {
                null
            }
        }
        return try {
            future.get(2, TimeUnit.SECONDS)
        } catch (e: Exception) {
            future.cancel(true)
            null
        }
    }

    private fun publish(gen: Int) {
        val current = hub
        val loaded = FleetSession.load(enabled = true, installed = true, hub = current)
        if (generation.get() != gen) return
        _snapshot.value = loaded
    }

    private fun bindOnMain(gen: Int) {
        val current = hub
        if (bound && current != null) {
            calls.execute { publish(gen) }
            return
        }
        val intent = Intent(FleetHubContract.SERVICE_ACTION).setPackage(FleetHubContract.PACKAGE_NAME)
        val ok = try {
            context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        } catch (e: Exception) {
            false
        }
        bound = ok
        if (!ok && generation.get() == gen) {
            _snapshot.value = FleetSnapshot.installed()
        }
    }

    private fun unbindOnMain() {
        hub = null
        if (!bound) return
        try {
            context.unbindService(connection)
        } catch (e: Exception) {
            // Already unbound.
        }
        bound = false
    }

    private fun isInstalled(): Boolean = try {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo(FleetHubContract.PACKAGE_NAME, 0)
        true
    } catch (e: Exception) {
        false
    }

    private companion object {
        const val FleetOutcomeClamp = 8_000
    }
}
