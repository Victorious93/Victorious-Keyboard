/* Copyright (C) 2026 Victorious Keyboard Contributors
 * Licensed under the Apache License, Version 2.0. See LICENSE.
 */
package com.victorious.keyboard.ime.usb

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class UsbHidKeyboardTest : FunSpec({
    test("ASCII text sends a boot keyboard press followed by all keys released") {
        UsbHidKeyboard.text("aA!").map { it.toList() } shouldBe listOf(
            listOf<Byte>(0, 0, 4, 0, 0, 0, 0, 0), List<Byte>(8) { 0 },
            listOf<Byte>(2, 0, 4, 0, 0, 0, 0, 0), List<Byte>(8) { 0 },
            listOf<Byte>(2, 0, 30, 0, 0, 0, 0, 0), List<Byte>(8) { 0 },
        )
    }
    test("every printable US ASCII character has an eight byte report and release") {
        val reports = UsbHidKeyboard.text((' '..'~').joinToString(""))
        reports.size shouldBe 190
        reports.chunked(2).forEach { (press, release) ->
            press.size shouldBe 8
            (press[2].toInt() > 0) shouldBe true
            release.toList() shouldBe List<Byte>(8) { 0 }
        }
    }
    test("unsupported text is rejected before any report is returned") {
        shouldThrow<IllegalArgumentException> { UsbHidKeyboard.text("hello\uD83D\uDE00") }
        shouldThrow<IllegalArgumentException> { UsbHidKeyboard.text("é") }
        shouldThrow<IllegalArgumentException> { UsbHidKeyboard.text("x".repeat(257)) }
        shouldThrow<IllegalArgumentException> { UsbHidKeyboard.text("\u0000") }
    }
    test("US punctuation skips the reserved non-US key usage") {
        UsbHidKeyboard.text(";:'\"`,.<>/?").filterIndexed { i, _ -> i % 2 == 0 }
            .map { it[2].toInt() } shouldBe listOf(51, 51, 52, 52, 53, 54, 55, 54, 55, 56, 56)
    }
    test("CRLF is one enter and tabs are real keys") {
        UsbHidKeyboard.text("\r\n\t").filterIndexed { i, _ -> i % 2 == 0 }
            .map { it[2].toInt() } shouldBe listOf(40, 43)
    }
    test("shortcuts use USB modifier bits and release the modifier too") {
        UsbHidKeyboard.stroke(UsbKey.C, 5).map { it.toList() } shouldBe listOf(
            listOf<Byte>(5, 0, 6, 0, 0, 0, 0, 0), List<Byte>(8) { 0 },
        )
        UsbKey.F12.usage shouldBe 69
        UsbKey.LEFT.usage shouldBe 80
        shouldThrow<IllegalArgumentException> { UsbHidKeyboard.stroke(UsbKey.A, 256) }
    }
    test("wire encoding contains report ID and eight keyboard octal bytes and no user text") {
        UsbHidKeyboard.octal(byteArrayOf(1, 0, 2, 4, 127, -128, -1, 0, 0)) shouldBe
            "\\001\\000\\002\\004\\177\\200\\377\\000\\000"
        shouldThrow<IllegalArgumentException> { UsbHidKeyboard.octal(byteArrayOf(0)) }
    }
    test("chord sends one key with exactly the selected modifiers, case-insensitively") {
        UsbHidKeyboard.chord('t', UsbHidKeyboard.CTRL or UsbHidKeyboard.SHIFT).map { it.toList() } shouldBe listOf(
            listOf<Byte>(3, 0, 23, 0, 0, 0, 0, 0), List<Byte>(8) { 0 },
        )
        UsbHidKeyboard.chord('T', 0).first().toList() shouldBe listOf<Byte>(0, 0, 23, 0, 0, 0, 0, 0)
        UsbHidKeyboard.chord('0', UsbHidKeyboard.ALT).first().toList() shouldBe listOf<Byte>(4, 0, 39, 0, 0, 0, 0, 0)
        UsbHidKeyboard.chord('/', UsbHidKeyboard.SUPER).first().toList() shouldBe listOf<Byte>(8, 0, 56, 0, 0, 0, 0, 0)
    }
    test("chord rejects shifted symbols, non-ASCII and unknown modifier bits instead of guessing") {
        for (bad in listOf('!', '?', 'é', '\n', ' ')) shouldThrow<IllegalArgumentException> { UsbHidKeyboard.chord(bad, 0) }
        shouldThrow<IllegalArgumentException> { UsbHidKeyboard.chord('a', 16) }
        shouldThrow<IllegalArgumentException> { UsbHidKeyboard.chord('a', -1) }
    }
    test("a modifier-only tap presses and releases the modifier with no key") {
        UsbHidKeyboard.modifierTap(UsbHidKeyboard.SUPER).map { it.toList() } shouldBe listOf(
            listOf<Byte>(8, 0, 0, 0, 0, 0, 0, 0), List<Byte>(8) { 0 },
        )
        shouldThrow<IllegalArgumentException> { UsbHidKeyboard.modifierTap(0) }
        shouldThrow<IllegalArgumentException> { UsbHidKeyboard.modifierTap(16) }
    }
    test("isSupported rejects the whole draft when any character or the length is unsupported") {
        UsbHidKeyboard.isSupported("Hello, World!\n\tok") shouldBe true
        UsbHidKeyboard.isSupported("caf\u00e9") shouldBe false
        UsbHidKeyboard.isSupported("hi \uD83D\uDE00") shouldBe false
        UsbHidKeyboard.isSupported("a".repeat(UsbHidKeyboard.MAX_TEXT)) shouldBe true
        UsbHidKeyboard.isSupported("a".repeat(UsbHidKeyboard.MAX_TEXT + 1)) shouldBe false
    }
    test("every usage the keyboard can emit is inside the descriptor's declared range 0x00..0x65") {
        val all = UsbKey.entries.map { it.usage } +
            UsbHidKeyboard.text("abcxyzABCXYZ0123456789 -=[]\\;'`,./_+{}|:\"~<>?!@#$%^&*()\n\t")
                .map { it[2].toInt() and 255 }
        all.all { it in 0..0x65 } shouldBe true
    }
})
