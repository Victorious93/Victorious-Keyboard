/* Copyright (C) 2026 Victorious Keyboard Contributors
 * Licensed under the Apache License, Version 2.0. See LICENSE.
 */
package com.victorious.keyboard.ime.usb

/** Report ID 2, three buttons, signed relative X/Y/wheel. HID Logical Minimum/Maximum -127..127. */
object UsbHidMouse {
    const val LEFT = 1
    const val RIGHT = 2
    const val MIDDLE = 4

    fun report(dx: Int = 0, dy: Int = 0, wheel: Int = 0, buttons: Int = 0): ByteArray {
        require(buttons in 0..7)
        require(dx in -127..127 && dy in -127..127 && wheel in -127..127)
        return byteArrayOf(2, buttons.toByte(), dx.toByte(), dy.toByte(), wheel.toByte())
    }

    fun click(button: Int): List<ByteArray> {
        require(button in listOf(LEFT, RIGHT, MIDDLE))
        return listOf(report(buttons = button), report())
    }
}
