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

package com.victorious.keyboard.ime.keyboard

import androidx.compose.ui.unit.LayoutDirection
import com.victorious.keyboard.ime.ImeUiMode
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class ImeUiModeStateTest : FunSpec({
    test("every mode survives a round trip through the int form and has a unique value") {
        for (m in ImeUiMode.entries) ImeUiMode.fromInt(m.toInt()) shouldBe m
        ImeUiMode.entries.map { it.value }.toSet().size shouldBe ImeUiMode.entries.size
    }

    test("existing values keep their numbers (they are stored in the keyboard state bits)") {
        ImeUiMode.TEXT.value shouldBe 0
        ImeUiMode.MEDIA.value shouldBe 1
        ImeUiMode.CLIPBOARD.value shouldBe 2
        ImeUiMode.SNIPPETS.value shouldBe 3
    }

    test("an unknown value falls back to the text view instead of crashing") {
        ImeUiMode.fromInt(4) shouldBe ImeUiMode.TEXT
        ImeUiMode.fromInt(7) shouldBe ImeUiMode.TEXT
        ImeUiMode.fromInt(-1) shouldBe ImeUiMode.TEXT
    }

    test("the snippet mode fits the state field and touches no neighbouring bit") {
        val state = KeyboardState.new()
        state.layoutDirection = LayoutDirection.Rtl
        state.isKanaKata = true
        val before = state.rawValue
        state.imeUiMode = ImeUiMode.SNIPPETS
        state.imeUiMode shouldBe ImeUiMode.SNIPPETS
        state.layoutDirection shouldBe LayoutDirection.Rtl
        state.isKanaKata shouldBe true
        // Only bits 24 and 25 may differ from the state before.
        (state.rawValue xor before) shouldBe (3uL shl 24)
        state.imeUiMode = ImeUiMode.TEXT
        state.rawValue shouldBe before
    }

    test("every mode fits the state field and reads back unchanged") {
        for (m in ImeUiMode.entries) {
            val state = KeyboardState.new()
            state.imeUiMode = m
            state.imeUiMode shouldBe m
        }
        (ImeUiMode.entries.maxOf { it.value }.toULong() <= KeyboardState.M_IME_UI_MODE) shouldBe true
    }

    test("switching between panels leaves the other fields alone") {
        val state = KeyboardState.new()
        state.imeUiMode = ImeUiMode.CLIPBOARD
        state.imeUiMode = ImeUiMode.SNIPPETS
        state.imeUiMode shouldBe ImeUiMode.SNIPPETS
        state.imeUiMode = ImeUiMode.MEDIA
        state.imeUiMode shouldBe ImeUiMode.MEDIA
    }
})
