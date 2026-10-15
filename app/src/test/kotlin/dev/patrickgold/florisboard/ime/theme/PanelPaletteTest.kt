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

package dev.patrickgold.florisboard.ime.theme

import dev.patrickgold.florisboard.ime.theme.colors.KeyboardColorMath
import dev.patrickgold.florisboard.ime.theme.colors.PanelPalette
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.doubles.shouldBeGreaterThanOrEqual
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

private fun argb(value: Long) = value.toInt()

class PanelPaletteTest : FunSpec({
    val black = argb(0xFF050505)
    val charcoal = argb(0xFF161616)
    val light = argb(0xFFE8E8E8)

    test("Victorious Black gives the colors the panels had before") {
        val p = PanelPalette.from(black, charcoal, light, argb(0xFF3A3A3A))
        p.background shouldBe black
        p.surface shouldBe charcoal
        p.ink shouldBe light
        p.selected shouldBe argb(0xFF3A3A3A)
    }

    test("a light theme gives a light panel with dark text") {
        val p = PanelPalette.from(argb(0xFFF2F2F2), argb(0xFFFFFFFF), argb(0xFF202020), argb(0xFFDDDDDD))
        p.background shouldBe argb(0xFFF2F2F2)
        p.surface shouldBe argb(0xFFFFFFFF)
        p.ink shouldBe argb(0xFF202020)
    }

    test("missing or fully transparent theme colors fall back one by one") {
        val p = PanelPalette.from(null, argb(0x00FFFFFF), null, null)
        p.background shouldBe PanelPalette.Fallback.background
        p.surface shouldBe PanelPalette.Fallback.surface
        p.ink shouldBe PanelPalette.Fallback.ink
    }

    test("translucent theme colors become opaque so text never sits on the app behind") {
        val p = PanelPalette.from(argb(0x80102030), argb(0x40FFFFFF), null, null)
        (p.background ushr 24) shouldBe 0xFF
        (p.surface ushr 24) shouldBe 0xFF
    }

    test("unreadable key text is replaced by a readable ink") {
        val p = PanelPalette.from(black, charcoal, argb(0xFF1C1C1C), null)
        p.ink shouldNotBe argb(0xFF1C1C1C)
        KeyboardColorMath.contrast(p.ink, p.surface) shouldBeGreaterThanOrEqual PanelPalette.MIN_CONTRAST
    }

    test("a pressed color equal to the key color still gives a visible selection") {
        val p = PanelPalette.from(black, charcoal, light, charcoal)
        p.selected shouldNotBe p.surface
    }

    test("muted text and lines sit between ink and surface") {
        val p = PanelPalette.from(black, charcoal, light, null)
        p.muted shouldNotBe p.ink
        p.line shouldNotBe p.surface
        KeyboardColorMath.contrast(p.muted, p.surface) shouldBeGreaterThanOrEqual 3.0
    }
})
