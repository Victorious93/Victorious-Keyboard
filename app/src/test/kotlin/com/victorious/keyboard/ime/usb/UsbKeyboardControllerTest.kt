/* Copyright (C) 2026 Victorious Keyboard Contributors
 * Licensed under the Apache License, Version 2.0. See LICENSE.
 */
package com.victorious.keyboard.ime.usb

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.nio.file.Files
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout

class UsbKeyboardControllerTest : FunSpec({
    val shell: (String) -> Process = { ProcessBuilder("sh", "-c", it).redirectErrorStream(true).start() }
    fun controller(script: String) = UsbKeyboardController(script) { helper, active ->
        RootUsbTransport.connect(helper, shell, active)
    }
    test("canceling setup preserves a restoration failure instead of displaying idle") {
        val marker = Files.createTempDirectory("usb-cancel").resolve("started").toFile()
        val helper = """
            echo VK_AVAILABLE
            read -r start
            [ "${'$'}start" = START ] || exit 1
            trap 'echo VK_RESTORE_FAILED' EXIT
            touch '${marker.path}'
            read -r cancelled
        """.trimIndent()
        val controller = controller(helper)
        try {
            controller.start()
            withTimeout(5000) { while (!marker.exists()) delay(10) }
            controller.stop()
            withTimeout(5000) { controller.state.first { it.phase == UsbKeyboardController.Phase.ERROR } }
            controller.state.value.error shouldBe "RESTORE_FAILED"
            controller.start() // Failed restoration requires recovery, not an invisible fresh session.
            controller.state.value.error shouldBe "RESTORE_FAILED"
        } finally {
            controller.stop(dispose = true)
            marker.delete()
            marker.parentFile.delete()
        }
    }
    test("canceling an in-flight send cannot hide a failed restoration") {
        val marker = Files.createTempDirectory("usb-send").resolve("sending").toFile()
        val helper = """
            echo VK_AVAILABLE
            read -r start
            [ "${'$'}start" = START ] || exit 1
            trap 'echo VK_RESTORE_FAILED' EXIT
            echo VK_READY
            read -r report
            touch '${marker.path}'
            read -r cancelled
        """.trimIndent()
        val controller = controller(helper)
        try {
            controller.start()
            withTimeout(5000) { controller.state.first { it.phase == UsbKeyboardController.Phase.ACTIVE } }
            controller.key(UsbKey.A)
            withTimeout(5000) { while (!marker.exists()) delay(10) }
            controller.stop()
            withTimeout(5000) { controller.state.first { it.phase == UsbKeyboardController.Phase.ERROR } }
            controller.state.value.error shouldBe "RESTORE_FAILED"
        } finally {
            controller.stop(dispose = true)
            marker.delete()
            marker.parentFile.delete()
        }
    }
    test("nothing is accepted, and nothing can be marked sent, before a session is active") {
        val controller = controller("echo VK_ERROR:ROOT_REQUIRED")
        try {
            controller.sendText("hello") shouldBe false
            controller.key(UsbKey.A) shouldBe false
            controller.click(UsbHidMouse.LEFT) shouldBe false
            controller.state.value.phase shouldBe UsbKeyboardController.Phase.IDLE
            controller.sendText("caf\u00e9") shouldBe false // unsupported text is refused whole
        } finally {
            controller.stop(dispose = true)
        }
    }
})
