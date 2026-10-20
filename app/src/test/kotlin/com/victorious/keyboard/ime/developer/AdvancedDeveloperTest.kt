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

package com.victorious.keyboard.ime.developer

import com.victorious.keyboard.ime.terminal.SpecialKey
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

private fun labels(keys: List<DevKey>) = keys.map { it.label }

class CustomSymbolsTest : FunSpec({
    test("whitespace separated tokens become insert keys, in order") {
        val p = CustomSymbols.parse("=> -> :: ||")
        labels(p.keys) shouldBe listOf("=>", "->", "::", "||")
        p.rejected shouldBe emptyList()
        p.keys[0].action shouldBe DevKeyAction.Insert("=>", 0)
    }

    test("empty and blank input give no keys and no errors") {
        for (t in listOf("", "   ", "\n\t \n")) CustomSymbols.parse(t) shouldBe CustomSymbols.Parsed(emptyList(), emptyList())
    }

    test("bracket and quote pairs put the caret between the halves") {
        for (pair in listOf("()", "[]", "{}", "<>", "\"\"", "''", "``")) {
            CustomSymbols.parse(pair).keys.single().action shouldBe DevKeyAction.Insert(pair, 1)
        }
        CustomSymbols.parse("(").keys.single().action shouldBe DevKeyAction.Insert("(", 0)
    }

    test("duplicates are dropped silently, first one wins") {
        labels(CustomSymbols.parse("a b a c b").keys) shouldBe listOf("a", "b", "c")
    }

    test("tokens longer than 8 code points are rejected and reported, not truncated") {
        val p = CustomSymbols.parse("ok " + "x".repeat(9) + " 12345678")
        labels(p.keys) shouldBe listOf("ok", "12345678")
        p.rejected shouldBe listOf("x".repeat(9))
    }

    test("length is counted in code points, so emoji count once") {
        CustomSymbols.parse("😀😀😀😀😀😀😀😀").keys.size shouldBe 1 // 8 code points
        CustomSymbols.parse("😀😀😀😀😀😀😀😀😀").rejected.size shouldBe 1 // 9
    }

    test("control characters are rejected") {
        // whitespace-class controls split tokens; others (NUL, ESC, DEL, bell) are rejected
        val p = CustomSymbols.parse("a\u0000b \u001b[A x\u007fy ok")
        labels(p.keys) shouldBe listOf("ok")
        p.rejected.size shouldBe 3
    }

    test("at most 40 keys; the overflow is reported") {
        val text = (1..45).joinToString(" ") { "k$it" }
        val p = CustomSymbols.parse(text)
        p.keys.size shouldBe CustomSymbols.MAX_KEYS
        p.rejected.size shouldBe 5
    }

    test("a huge input is handled without trouble") {
        val p = CustomSymbols.parse("z ".repeat(200_000))
        labels(p.keys) shouldBe listOf("z")
    }
})

class RowOptionsTest : FunSpec({
    val custom = CustomSymbols.parse("=> ->").keys

    test("default options reproduce the original full row") {
        for (p in LanguageProfile.entries) {
            DeveloperRows.row(p, RowOptions()) shouldBe DeveloperRows.terminalRow + DeveloperRows.symbolRow(p)
        }
    }

    test("the two terminal groups together are the original terminal row, in the original order") {
        DeveloperRows.terminalRow shouldBe DeveloperRows.modifierRow + DeveloperRows.navigationRow
        labels(DeveloperRows.modifierRow) shouldBe listOf("Esc", "Tab", "Ctrl", "Alt")
        DeveloperRows.navigationRow.map { it.action } shouldBe listOf(
            SpecialKey.LEFT, SpecialKey.DOWN, SpecialKey.UP, SpecialKey.RIGHT, SpecialKey.HOME, SpecialKey.END,
            SpecialKey.PAGE_UP, SpecialKey.PAGE_DOWN,
        ).map { DevKeyAction.Special(it) }
    }

    test("each group can be switched off on its own") {
        val p = LanguageProfile.PYTHON
        DeveloperRows.row(p, RowOptions(terminalKeys = false)) shouldBe DeveloperRows.navigationRow + DeveloperRows.symbolRow(p)
        DeveloperRows.row(p, RowOptions(navigationKeys = false)) shouldBe DeveloperRows.modifierRow + DeveloperRows.symbolRow(p)
        DeveloperRows.row(p, RowOptions(symbols = false)) shouldBe DeveloperRows.terminalRow
        DeveloperRows.row(p, RowOptions(false, false, false)) shouldBe emptyList()
    }

    test("custom keys are appended to the profile symbols by default") {
        DeveloperRows.row(LanguageProfile.JSON, RowOptions(custom = custom)) shouldBe
            DeveloperRows.terminalRow + DeveloperRows.symbolRow(LanguageProfile.JSON) + custom
    }

    test("REPLACE swaps the profile symbols for the custom ones") {
        DeveloperRows.row(LanguageProfile.JSON, RowOptions(custom = custom, customMode = CustomSymbolsMode.REPLACE)) shouldBe
            DeveloperRows.terminalRow + custom
    }

    test("REPLACE with no custom keys never blanks the symbols") {
        DeveloperRows.row(LanguageProfile.JSON, RowOptions(customMode = CustomSymbolsMode.REPLACE)) shouldBe
            DeveloperRows.terminalRow + DeveloperRows.symbolRow(LanguageProfile.JSON)
    }

    test("custom keys still show when the profile symbols are switched off") {
        DeveloperRows.row(LanguageProfile.JSON, RowOptions(symbols = false, custom = custom)) shouldBe
            DeveloperRows.terminalRow + custom
    }

    test("the older two-argument form behaves as before") {
        for (p in LanguageProfile.entries) {
            DeveloperRows.row(p, true) shouldBe DeveloperRows.terminalRow + DeveloperRows.symbolRow(p)
            DeveloperRows.row(p, false) shouldBe DeveloperRows.symbolRow(p)
        }
    }

    test("size names are persisted and scales are sane") {
        DevRowSize.entries.map { it.name } shouldBe listOf("COMPACT", "NORMAL", "LARGE")
        DevRowSize.entries.all { it.scale in 0.5f..2f } shouldBe true
        DevRowSize.NORMAL.scale shouldBe 1.0f
        CustomSymbolsMode.entries.map { it.name } shouldBe listOf("APPEND", "REPLACE")
    }

    test("timing values are clamped into their allowed range") {
        DeveloperRowTiming.hold(0) shouldBe DeveloperRowTiming.HOLD_MIN.toLong()
        DeveloperRowTiming.hold(10_000) shouldBe DeveloperRowTiming.HOLD_MAX.toLong()
        DeveloperRowTiming.hold(350) shouldBe 350L
        DeveloperRowTiming.repeat(-5) shouldBe DeveloperRowTiming.REPEAT_MIN.toLong()
        DeveloperRowTiming.repeat(Int.MAX_VALUE) shouldBe DeveloperRowTiming.REPEAT_MAX.toLong()
        DeveloperRowTiming.lockWindow(1) shouldBe DeveloperRowTiming.LOCK_WINDOW_MIN.toLong()
        DeveloperRowTiming.lockWindow(Int.MIN_VALUE) shouldBe DeveloperRowTiming.LOCK_WINDOW_MIN.toLong()
        for ((d, lo, hi) in listOf(
            Triple(DeveloperRowTiming.HOLD_DEFAULT, DeveloperRowTiming.HOLD_MIN, DeveloperRowTiming.HOLD_MAX),
            Triple(DeveloperRowTiming.REPEAT_DEFAULT, DeveloperRowTiming.REPEAT_MIN, DeveloperRowTiming.REPEAT_MAX),
            Triple(DeveloperRowTiming.LOCK_WINDOW_DEFAULT, DeveloperRowTiming.LOCK_WINDOW_MIN, DeveloperRowTiming.LOCK_WINDOW_MAX),
        )) (d in lo..hi) shouldBe true
    }
})
