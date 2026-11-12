/* Copyright (C) 2026 Victorious Keyboard Contributors
 * Licensed under the Apache License, Version 2.0. See LICENSE.
 */
package com.victorious.keyboard.ime.usb

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.io.IOException
import java.nio.file.Files

class RootUsbTransportTest : FunSpec({
    val shell: (String) -> Process = { ProcessBuilder("sh", "-c", it).redirectErrorStream(true).start() }
    val helper = """
        echo VK_AVAILABLE
        read -r start
        [ "${'$'}start" = START ] || exit 1
        echo VK_READY
        while IFS= read -r report; do echo VK_OK; done
        echo VK_STOPPED
    """.trimIndent()

    test("ready and acknowledgement handshake sends keyboard and mouse bytes through a real process") {
        val file = Files.createTempFile("usb-wire", ".txt").toFile()
        try {
            // Only a test-created fixed path enters this fixture, not typed content.
            val recording = helper.replace("do echo VK_OK;", "do printf '%s\\n' \"${'$'}report\" >> '${file.path}'; echo VK_OK;")
            RootUsbTransport.connect(recording, shell) { true }.use {
                it.send(listOf(byteArrayOf(1) + UsbHidKeyboard.release(), UsbHidMouse.report())) { true }
            }
            file.readLines() shouldBe listOf(
                "\\001\\000\\000\\000\\000\\000\\000\\000\\000", "\\002\\000\\000\\000\\000",
            )
        } finally { file.delete() }
    }
    test("a helper restoration failure is visible to its caller") {
        val transport = RootUsbTransport.connect(helper.replace("VK_STOPPED", "VK_RESTORE_FAILED"), shell) { true }
        shouldThrow<IOException> { transport.close() }.message shouldBe "RESTORE_FAILED"
    }
    test("an unexpected helper exit cannot be reported as successful restoration") {
        val transport = RootUsbTransport.connect(helper.replace("echo VK_STOPPED", "exit 0"), shell) { true }
        shouldThrow<IOException> { transport.close() }.message shouldBe "RESTORE_FAILED"
    }
    test("a canceled start cannot send the START instruction") {
        val file = Files.createTempFile("usb-start", ".txt").toFile()
        try {
            val script = "echo VK_AVAILABLE; read -r start; [ \"${'$'}start\" != START ] || echo changed > '${file.path}'"
            shouldThrow<IOException> { RootUsbTransport.connect(script, shell) { false } }.message shouldBe "SESSION_ENDED"
            file.readText() shouldBe ""
        } finally { file.delete() }
    }
    test("write failures are errors rather than acknowledgements") {
        val transport = RootUsbTransport.connect(helper.replace("echo VK_OK", "echo VK_ERROR:WRITE_FAILED"), shell) { true }
        try {
            shouldThrow<IOException> { transport.send(listOf(UsbHidMouse.report())) { true } }.message shouldBe "WRITE_FAILED"
        } finally { transport.close() }
    }
})
