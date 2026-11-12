/* Copyright (C) 2026 Victorious Keyboard Contributors
 * Licensed under the Apache License, Version 2.0. See LICENSE.
 */
package com.victorious.keyboard.ime.usb

/** USB HID Usage Tables, keyboard page 0x07. These are NOT Android key codes. */
enum class UsbKey(val usage: Int) {
    A(4), C(6), V(25), X(27), Z(29),
    ENTER(40), ESC(41), BACKSPACE(42), TAB(43), SPACE(44),
    F1(58), F2(59), F3(60), F4(61), F5(62), F6(63),
    F7(64), F8(65), F9(66), F10(67), F11(68), F12(69),
    INSERT(73), HOME(74), PAGE_UP(75), DELETE(76), END(77), PAGE_DOWN(78),
    RIGHT(79), LEFT(80), DOWN(81), UP(82),
}

/** US layout. Eight-byte keyboard payload; transport prepends report ID 1. Validate the ENTIRE draft before returning anything to the transport. */
object UsbHidKeyboard {
    const val MAX_TEXT = 256
    const val CTRL = 1
    const val SHIFT = 2
    const val ALT = 4
    const val SUPER = 8

    fun release(): ByteArray = ByteArray(8)

    const val ALL_MODIFIERS = CTRL or SHIFT or ALT or SUPER

    fun stroke(key: UsbKey, modifiers: Int = 0): List<ByteArray> {
        require(modifiers in 0..ALL_MODIFIERS) { "Unsupported modifier." }
        return listOf(report(key.usage, modifiers), release())
    }

    /** Press and release the modifiers alone, e.g. Super to open a start menu. At least one is required. */
    fun modifierTap(modifiers: Int): List<ByteArray> {
        require(modifiers in 1..ALL_MODIFIERS) { "Choose at least one modifier." }
        return listOf(report(0, modifiers), release())
    }

    /**
     * A shortcut such as Ctrl+Shift+T: one US-layout printable key plus the chosen modifiers. Letters are
     * case-insensitive (Shift only when selected), and shifted symbols are rejected rather than guessed.
     */
    fun chord(key: Char, modifiers: Int): List<ByteArray> {
        require(modifiers in 0..ALL_MODIFIERS) { "Unsupported modifier." }
        val usage = when (key) {
            in 'a'..'z' -> 4 + key.code - 'a'.code
            in 'A'..'Z' -> 4 + key.code - 'A'.code
            in '1'..'9' -> 30 + key.code - '1'.code
            '0' -> 39
            else -> {
                val i = UNSHIFTED_PUNCTUATION.indexOf(key)
                require(i >= 0) { "Use a US English letter, digit or unshifted punctuation key." }
                PUNCTUATION_USAGES[i]
            }
        }
        return listOf(report(usage, modifiers), release())
    }

    /** True when [text] can be sent in full. [text] itself throws for the same inputs. */
    fun isSupported(text: String): Boolean =
        text.length <= MAX_TEXT && runCatching { text(text) }.isSuccess

    private const val UNSHIFTED_PUNCTUATION = "-=[]\\;\u0027`,./"
    private val PUNCTUATION_USAGES = listOf(45, 46, 47, 48, 49, 51, 52, 53, 54, 55, 56)

    fun text(text: String): List<ByteArray> {
        require(text.length <= MAX_TEXT) { "Text exceeds 256 characters." }
        return text.replace("\r\n", "\n").map { char ->
            val (usage, modifiers) = when (char) {
                in 'a'..'z' -> (4 + char.code - 'a'.code) to 0
                in 'A'..'Z' -> (4 + char.code - 'A'.code) to SHIFT
                in '1'..'9' -> (30 + char.code - '1'.code) to 0
                '0' -> 39 to 0
                '\n', '\r' -> 40 to 0
                '\t' -> 43 to 0
                ' ' -> 44 to 0
                else -> {
                    val shifted = "_+{}|:\"~<>?"
                    val usages = PUNCTUATION_USAGES
                    val punctuation = UNSHIFTED_PUNCTUATION.indexOf(char)
                    val shiftedPunctuation = shifted.indexOf(char)
                    val digit = "!@#$%^&*()".indexOf(char)
                    when {
                        punctuation >= 0 -> usages[punctuation] to 0
                        shiftedPunctuation >= 0 -> usages[shiftedPunctuation] to SHIFT
                        digit >= 0 -> (if (digit == 9) 39 else 30 + digit) to SHIFT
                        else -> throw IllegalArgumentException("Use US English letters, numbers and punctuation only.")
                    }
                }
            }
            report(usage, modifiers)
        }.flatMap { listOf(it, release()) }
    }

    private fun report(usage: Int, modifiers: Int) =
        byteArrayOf(modifiers.toByte(), 0, usage.toByte(), 0, 0, 0, 0, 0)

    /** Backslash-octal bytes used as printf format text by the helper, never shell source. */
    fun octal(report: ByteArray): String {
        require(report.size in listOf(5, 9))
        return report.joinToString("") { "\\" + (it.toInt() and 255).toString(8).padStart(3, '0') }
    }
}
