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
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import com.victorious.fleet.FleetApiContract
import com.victorious.fleet.IFleetKeyboardProvider
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.lib.fleet.FleetDiscovery
import dev.patrickgold.florisboard.lib.fleet.FleetEndpoint
import dev.patrickgold.florisboard.lib.fleet.FleetOutcome
import dev.patrickgold.florisboard.lib.fleet.FleetProvider
import dev.patrickgold.florisboard.lib.fleet.FleetServiceCandidate
import dev.patrickgold.florisboard.lib.fleet.FleetServiceTarget
import dev.patrickgold.florisboard.lib.fleet.FleetSession
import dev.patrickgold.florisboard.lib.fleet.FleetSnippet
import dev.patrickgold.florisboard.lib.fleet.FleetSnapshot
import java.util.concurrent.ConcurrentHashMap
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
 * Discovers every app that exports [FleetApiContract.SERVICE_ACTION], holds the shared signature
 * permission, and is signed with this keyboard's certificate. There is no app list.
 *
 * TODO-confirm-with-hub: the action and permission are placeholders in `FleetApiContract`.
 *
 * Binder calls stay off the main thread. A dead provider drops only that app. Returned text is
 * not logged.
 */
class FleetHubClient(private val context: Context) {
    private val prefs by FlorisPreferenceStore
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val calls = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "fleet-hub").apply { isDaemon = true }
    }
    private val generation = AtomicInteger()
    private val bindings = ConcurrentHashMap<String, Binding>()

    private val _snapshot = MutableStateFlow(FleetSnapshot.absent())
    val snapshot: StateFlow<FleetSnapshot> = _snapshot

    @Volatile private var targets: List<FleetServiceTarget> = emptyList()
    private var started = false

    private class Binding(val className: String) {
        lateinit var connection: ServiceConnection
        @Volatile var provider: FleetProvider? = null
        @Volatile var bound: Boolean = false
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
            addAction(Intent.ACTION_PACKAGE_REPLACED)
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
            if (!enabled) {
                withContext(Dispatchers.Main) { unbindAll() }
                if (generation.get() == gen) {
                    targets = emptyList()
                    _snapshot.value = FleetSnapshot.absent()
                }
                return@launch
            }
            val found = try {
                discover()
            } catch (e: Exception) {
                emptyList()
            }
            withContext(Dispatchers.Main) {
                if (generation.get() != gen) return@withContext
                syncBindings(gen, found)
            }
        }
    }

    fun ask(packageName: String, action: String, text: String): String? {
        val provider = bindings[packageName]?.provider ?: return null
        return call(provider) { it.ask(action, text) }?.take(FleetOutcomeClamp)
    }

    fun insertSnippet(packageName: String, snippet: FleetSnippet): FleetOutcome? {
        val provider = bindings[packageName]?.provider ?: return null
        return call(provider) { snippet.insertText(it) }
    }

    private fun <T> call(provider: FleetProvider, block: (FleetProvider) -> T): T? {
        val future = calls.submit<T?> {
            try {
                block(provider)
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
        if (generation.get() != gen) return
        val endpoints = targets.map { target ->
            FleetEndpoint(target.packageName, bindings[target.packageName]?.provider)
        }
        val loaded = FleetSession.load(enabled = true, endpoints = endpoints)
        if (generation.get() != gen) return
        _snapshot.value = loaded
    }

    private fun discover(): List<FleetServiceTarget> {
        val pm = context.packageManager
        val intent = Intent(FleetApiContract.SERVICE_ACTION)
        val keyboardCerts = certificates(pm, context.packageName)
        val candidates = ArrayList<FleetServiceCandidate>()
        for (info in query(pm, intent)) {
            val service = info.serviceInfo ?: continue
            val pkg = service.packageName ?: continue
            val className = service.name ?: continue
            val granted = try {
                pm.checkPermission(FleetApiContract.PERMISSION, pkg) == PackageManager.PERMISSION_GRANTED
            } catch (e: Exception) {
                false
            }
            candidates.add(
                FleetServiceCandidate(
                    packageName = pkg,
                    className = className,
                    permissionGranted = granted,
                    certificates = certificates(pm, pkg),
                ),
            )
        }
        return FleetDiscovery.select(context.packageName, keyboardCerts, candidates)
    }

    private fun query(pm: PackageManager, intent: Intent) = if (Build.VERSION.SDK_INT >= 33) {
        pm.queryIntentServices(intent, PackageManager.ResolveInfoFlags.of(0L))
    } else {
        @Suppress("DEPRECATION")
        pm.queryIntentServices(intent, 0)
    }

    private fun certificates(pm: PackageManager, packageName: String): List<ByteArray> = try {
        if (Build.VERSION.SDK_INT >= 28) {
            val info = pm.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            info.signingInfo?.apkContentsSigners?.map { it.toByteArray() } ?: emptyList()
        } else {
            @Suppress("DEPRECATION")
            val info = pm.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
            @Suppress("DEPRECATION")
            info.signatures?.map { it.toByteArray() } ?: emptyList()
        }
    } catch (e: Exception) {
        emptyList()
    }

    private fun syncBindings(gen: Int, next: List<FleetServiceTarget>) {
        targets = next
        val keep = next.map { it.packageName }.toSet()
        for (pkg in bindings.keys.toList()) {
            if (pkg !in keep) unbind(pkg)
        }
        for (target in next) {
            val existing = bindings[target.packageName]
            if (existing != null && existing.bound && existing.provider != null && existing.className == target.className) {
                continue
            }
            if (existing != null) unbind(target.packageName)
            bind(target)
        }
        calls.execute { publish(gen) }
    }

    private fun bind(target: FleetServiceTarget) {
        val holder = Binding(target.className)
        holder.connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                val remote = try {
                    if (service == null) null else IFleetKeyboardProvider.Stub.asInterface(service)
                } catch (e: Exception) {
                    null
                }
                if (remote == null || service == null) {
                    holder.provider = null
                } else {
                    try {
                        service.linkToDeath({
                            if (bindings[target.packageName] !== holder) return@linkToDeath
                            holder.provider = null
                            val now = generation.get()
                            calls.execute { publish(now) }
                        }, 0)
                    } catch (e: Exception) {
                        holder.provider = null
                        val now = generation.get()
                        calls.execute { publish(now) }
                        return
                    }
                    holder.provider = AidlFleetProvider(remote)
                }
                val now = generation.get()
                calls.execute { publish(now) }
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                if (bindings[target.packageName] !== holder) return
                holder.provider = null
                holder.bound = false
                val now = generation.get()
                calls.execute { publish(now) }
            }
        }
        bindings[target.packageName] = holder
        val intent = Intent(FleetApiContract.SERVICE_ACTION).setComponent(
            ComponentName(target.packageName, target.className),
        )
        holder.bound = try {
            context.bindService(intent, holder.connection, Context.BIND_AUTO_CREATE)
        } catch (e: Exception) {
            false
        }
    }

    private fun unbind(packageName: String) {
        val binding = bindings.remove(packageName) ?: return
        binding.provider = null
        if (!binding.bound) return
        try {
            context.unbindService(binding.connection)
        } catch (e: Exception) {
            // Already unbound.
        }
        binding.bound = false
    }

    private fun unbindAll() {
        for (pkg in bindings.keys.toList()) unbind(pkg)
        targets = emptyList()
    }

    private companion object {
        const val FleetOutcomeClamp = 8_000
    }
}
