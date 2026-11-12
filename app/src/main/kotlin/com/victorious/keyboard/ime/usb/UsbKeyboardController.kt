/* Copyright (C) 2026 Victorious Keyboard Contributors
 * Licensed under the Apache License, Version 2.0. See LICENSE.
 */
package com.victorious.keyboard.ime.usb

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Visible-screen only. No IME hook, persistence, clipboard access or bridge entry point. */
class UsbKeyboardController(
    private val script: String,
    private val connect: (String, () -> Boolean) -> RootUsbTransport = { helper, active ->
        RootUsbTransport.connect(helper, stillActive = active)
    },
) {
    enum class Phase { IDLE, STARTING, ACTIVE, SENDING, STOPPING, ERROR }
    data class State(val phase: Phase = Phase.IDLE, val error: String? = null)
    private val mutableState = MutableStateFlow(State())
    val state = mutableState.asStateFlow()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Mutex()
    private val stateLock = Any()
    private val generation = AtomicInteger()
    private var transport: RootUsbTransport? = null
    @Volatile private var restorationFailed = false
    private val moving = AtomicBoolean()
    private val pendingX = AtomicInteger()
    private val pendingY = AtomicInteger()
    private val pendingWheel = AtomicInteger()

    fun start() {
        if (restorationFailed || mutableState.value.phase !in listOf(Phase.IDLE, Phase.ERROR)) return
        val session = generation.incrementAndGet()
        mutableState.value = State(Phase.STARTING)
        scope.launch {
            lock.withLock {
                try {
                    val connected = connect(script) { generation.get() == session }
                    transport = connected
                    if (generation.get() == session) mutableState.value = State(Phase.ACTIVE)
                    else closeSession()
                } catch (error: Exception) {
                    if (error.message == "RESTORE_FAILED") restorationFailed = true
                    if (restorationFailed || generation.get() == session) mutableState.value = State(
                        Phase.ERROR, if (restorationFailed) "RESTORE_FAILED" else error.message,
                    )
                }
            }
        }
    }

    /**
     * Returns true only when the whole draft was validated AND handed to the helper. False means nothing was
     * sent (unsupported character, or no active session), so the caller must keep the draft.
     */
    fun sendText(text: String): Boolean {
        val reports = try { UsbHidKeyboard.text(text) } catch (_: IllegalArgumentException) { return false }
        return send(reports.map { byteArrayOf(1) + it })
    }

    fun key(key: UsbKey, modifiers: Int = 0) = send(UsbHidKeyboard.stroke(key, modifiers).map { byteArrayOf(1) + it })
    fun modifierTap(modifiers: Int) = send(UsbHidKeyboard.modifierTap(modifiers).map { byteArrayOf(1) + it })
    fun chord(key: Char, modifiers: Int) = send(UsbHidKeyboard.chord(key, modifiers).map { byteArrayOf(1) + it })
    fun release() = send(listOf(byteArrayOf(1) + UsbHidKeyboard.release(), UsbHidMouse.report()))
    fun click(button: Int) = send(UsbHidMouse.click(button))

    /** Coalesce pointer input while the helper is busy, instead of queuing unbounded gesture events. */
    fun move(dx: Int = 0, dy: Int = 0, wheel: Int = 0) {
        if (mutableState.value.phase != Phase.ACTIVE) return
        pendingX.updateAndGet { (it + dx.coerceIn(-4096, 4096)).coerceIn(-4096, 4096) }
        pendingY.updateAndGet { (it + dy.coerceIn(-4096, 4096)).coerceIn(-4096, 4096) }
        pendingWheel.updateAndGet { (it + wheel.coerceIn(-127, 127)).coerceIn(-127, 127) }
        drainMotion()
    }

    private fun drainMotion() {
        if (!moving.compareAndSet(false, true)) return
        val session = generation.get()
        scope.launch {
            try {
                lock.withLock {
                    while (generation.get() == session && mutableState.value.phase == Phase.ACTIVE) {
                        val x = pendingX.getAndUpdate { it - it.coerceIn(-127, 127) }.coerceIn(-127, 127)
                        val y = pendingY.getAndUpdate { it - it.coerceIn(-127, 127) }.coerceIn(-127, 127)
                        val wheel = pendingWheel.getAndUpdate { it - it.coerceIn(-127, 127) }.coerceIn(-127, 127)
                        if (x == 0 && y == 0 && wheel == 0) break
                        transport?.send(listOf(UsbHidMouse.report(x, y, wheel))) { generation.get() == session }
                    }
                }
            } catch (error: Exception) {
                lock.withLock {
                    // A late failure from an old session must never close a replacement session.
                    if (generation.get() == session) {
                        val restored = closeSession()
                        mutableState.value = State(Phase.ERROR, if (restored) error.message else "RESTORE_FAILED")
                    }
                }
            } finally {
                moving.set(false)
                if (mutableState.value.phase == Phase.ACTIVE &&
                    (pendingX.get() != 0 || pendingY.get() != 0 || pendingWheel.get() != 0)) drainMotion()
            }
        }
    }

    private fun send(reports: List<ByteArray>): Boolean {
        // Claim the helper atomically: two taps in the same frame must not both start a transmission.
        val claimed = synchronized(stateLock) {
            if (mutableState.value.phase != Phase.ACTIVE) false
            else { mutableState.value = State(Phase.SENDING); true }
        }
        if (!claimed) return false
        val session = generation.get()
        pendingX.set(0)
        pendingY.set(0)
        pendingWheel.set(0)
        scope.launch {
            lock.withLock {
                if (generation.get() != session) return@withLock
                try {
                    transport?.send(reports) { generation.get() == session }
                        ?: error("SESSION_ENDED")
                    if (generation.get() == session) mutableState.value = State(Phase.ACTIVE)
                } catch (error: Exception) {
                    val restored = closeSession()
                    if (generation.get() == session) mutableState.value = State(
                        Phase.ERROR, if (restored) error.message else "RESTORE_FAILED",
                    )
                }
            }
        }
        return true
    }

    fun stop(dispose: Boolean = false) {
        generation.incrementAndGet() // Invalidate a pending root prompt and queued sends immediately.
        mutableState.value = State(Phase.STOPPING)
        scope.launch {
            lock.withLock {
                val restored = closeSession()
                mutableState.value = if (restored) State() else State(Phase.ERROR, "RESTORE_FAILED")
            }
            if (dispose) scope.cancel()
        }
    }

    private fun closeSession(): Boolean {
        pendingX.set(0)
        pendingY.set(0)
        pendingWheel.set(0)
        val connected = transport
        transport = null
        if (runCatching { connected?.close() }.isFailure) restorationFailed = true
        return !restorationFailed
    }
}
