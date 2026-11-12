/* Copyright (C) 2026 Victorious Keyboard Contributors
 * Licensed under the Apache License, Version 2.0. See LICENSE.
 */
package com.victorious.keyboard.ime.usb

import java.io.Closeable
import java.io.IOException
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit

/** One privileged helper per visible screen session. Never logs helper output or typed bytes. */
class RootUsbTransport private constructor(private val process: Process) : Closeable {
    private val replies = ArrayBlockingQueue<String>(32)
    @Volatile private var cleanupResult: Boolean? = null
    private var startSent = false
    private val input = process.outputStream.bufferedWriter(Charsets.US_ASCII)
    private val reader = Thread({
        try {
            // Limit both line size and queued output even if a third-party su emits unexpected text.
            process.inputStream.bufferedReader().use { stream ->
                val line = StringBuilder()
                while (true) {
                    val c = stream.read()
                    if (c == -1) break
                    if (c == 10) {
                        val value = line.toString()
                        if (value == "VK_STOPPED") cleanupResult = true
                        if (value == "VK_RESTORE_FAILED") cleanupResult = false
                        if (value.startsWith("VK_")) replies.offer(value)
                        line.setLength(0)
                    } else if (line.length < 128) line.append(c.toChar())
                }
            }
        } catch (_: IOException) {
            // EOF is reported without including root-manager or input content in diagnostics.
        } finally {
            replies.offer("VK_EOF")
        }
    }, "usb-keyboard-replies").apply { isDaemon = true; start() }

    private fun expectReady(stillActive: () -> Boolean) {
        expect("VK_AVAILABLE", 45, stillActive)
        if (!stillActive()) throw IOException("SESSION_ENDED")
        startSent = true
        input.write("START\n")
        input.flush()
        expect("VK_READY", 10, stillActive)
    }

    fun send(reports: List<ByteArray>, stillActive: () -> Boolean) {
        for (report in reports) {
            if (!stillActive()) throw IOException("SESSION_ENDED")
            input.write(UsbHidKeyboard.octal(report))
            input.newLine()
            input.flush()
            expect("VK_OK", 4, stillActive)
        }
    }

    private fun expect(expected: String, seconds: Long, stillActive: () -> Boolean = { true }) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds)
        var reply: String? = null
        while (reply == null) {
            if (!stillActive()) throw IOException("SESSION_ENDED")
            if (System.nanoTime() >= deadline) throw IOException("TIMEOUT")
            reply = replies.poll(100, TimeUnit.MILLISECONDS)
        }
        if (reply != expected) throw IOException(
            if (reply.startsWith("VK_ERROR:")) reply.removePrefix("VK_ERROR:") else "HELPER_EXITED",
        )
    }

    /** EOF invokes the shell's cleanup trap, including after Android kills the app process. */
    override fun close() {
        runCatching { input.close() }
        if (!process.waitFor(8, TimeUnit.SECONDS)) {
            process.destroy() // TERM invokes the cleanup trap; don't SIGKILL away restoration.
            if (!process.waitFor(5, TimeUnit.SECONDS)) throw IOException("RESTORE_FAILED")
        }
        reader.join(1000)
        if (cleanupResult == false || (startSent && cleanupResult != true)) {
            throw IOException("RESTORE_FAILED")
        }
    }

    companion object {
        fun connect(
            script: String,
            launch: (String) -> Process = { ProcessBuilder("su", "-c", it).redirectErrorStream(true).start() },
            stillActive: () -> Boolean,
        ): RootUsbTransport {
            val transport = RootUsbTransport(launch(script))
            try {
                transport.expectReady(stillActive)
                return transport
            } catch (error: Exception) {
                try {
                    transport.close()
                } catch (restore: Exception) {
                    // Before mutation a root denial has no cleanup record. Otherwise keep a restoration failure visible.
                    if (transport.cleanupResult == false || transport.startSent) throw restore
                }
                throw error
            }
        }
    }
}
