/* Copyright (C) 2026 Victorious Keyboard Contributors
 * Licensed under the Apache License, Version 2.0. See LICENSE.
 */
package com.victorious.keyboard.ime.usb

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class UsbHidMouseTest : FunSpec({
    test("mouse uses report ID 2 and signed relative movement with scroll") {
        UsbHidMouse.report(-127, 127, -1).toList() shouldBe listOf<Byte>(2, 0, -127, 127, -1)
    }
    test("each click releases every mouse button") {
        for (button in listOf(1, 2, 4)) {
            UsbHidMouse.click(button).map { it.toList() } shouldBe listOf(
                listOf<Byte>(2, button.toByte(), 0, 0, 0), listOf<Byte>(2, 0, 0, 0, 0),
            )
        }
    }
    test("out of range movement cannot wrap or introduce undefined buttons") {
        shouldThrow<IllegalArgumentException> { UsbHidMouse.report(dx = 128) }
        shouldThrow<IllegalArgumentException> { UsbHidMouse.report(dy = -128) }
        shouldThrow<IllegalArgumentException> { UsbHidMouse.report(wheel = 128) }
        shouldThrow<IllegalArgumentException> { UsbHidMouse.report(buttons = 8) }
        shouldThrow<IllegalArgumentException> { UsbHidMouse.click(3) }
    }
})
