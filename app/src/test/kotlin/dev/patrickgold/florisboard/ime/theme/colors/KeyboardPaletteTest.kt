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

package dev.patrickgold.florisboard.ime.theme.colors

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.io.File
import kotlin.math.roundToInt
import org.florisboard.lib.snygg.SnyggAnnotationRule
import org.florisboard.lib.snygg.SnyggElementRule
import org.florisboard.lib.snygg.SnyggSelector
import org.florisboard.lib.snygg.SnyggSinglePropertySet
import org.florisboard.lib.snygg.SnyggStylesheet
import org.florisboard.lib.snygg.value.SnyggStaticColorValue

class KeyboardPaletteTest : FunSpec({
    test("every preset round-trips through the stored text and keeps readable text") {
        KeyboardPalettes.presets.forEach { preset ->
            val back = KeyboardPaletteCodec.decode(KeyboardPaletteCodec.encode(preset.palette))
            back shouldBe preset.palette
            val tokens = KeyboardColorTokens.from(preset.palette)
            (KeyboardColorMath.contrast(tokens.defines.getValue("--on-primary"), preset.palette.primary) >= 4.5) shouldBe true
            (KeyboardColorMath.contrast(tokens.defines.getValue("--on-secondary"), preset.palette.secondary) >= 4.5) shouldBe true
            (KeyboardColorMath.contrast(tokens.suggestionText, preset.palette.suggestion) >= 4.5) shouldBe true
            (KeyboardColorMath.contrast(preset.palette.resolved().keyText, preset.palette.key) >= 4.5) shouldBe true
            (KeyboardColorMath.contrast(tokens.pressedKeyText, tokens.pressedKey) >= 4.5) shouldBe true
        }
    }

    test("a corrupt, empty, or oversized value falls back to Victorious Neon") {
        KeyboardPaletteCodec.decode(null) shouldBe KeyboardPalettes.Neon
        KeyboardPaletteCodec.decode("") shouldBe KeyboardPalettes.Neon
        KeyboardPaletteCodec.decode("not json") shouldBe KeyboardPalettes.Neon
        KeyboardPaletteCodec.decode("{\"v\":1,\"background\":\"#ffffff\"}") shouldBe KeyboardPalettes.Neon
        KeyboardPaletteCodec.decode("x".repeat(KeyboardPaletteCodec.MAX_CHARS + 1)) shouldBe KeyboardPalettes.Neon
        KeyboardPaletteCodec.decode("{\"v\":2,\"background\":\"#ffffff\",\"key\":\"#000000\",\"keyText\":\"#ffffff\",\"primary\":\"#ff0000\",\"secondary\":\"#00ff00\",\"suggestion\":\"#0000ff\"}") shouldBe KeyboardPalettes.Neon
    }

    test("unreadable key text is replaced and a readable choice is kept") {
        val paleOnPale = KeyboardPalettes.Neon.copy(key = 0xFFFFF3C4.toInt(), keyText = 0xFFFFE082.toInt())
        paleOnPale.resolved().keyText shouldNotBe paleOnPale.keyText
        (KeyboardColorMath.contrast(paleOnPale.resolved().keyText, paleOnPale.key) >= 4.5) shouldBe true
        KeyboardPalettes.Neon.resolved().keyText shouldBe KeyboardPalettes.Neon.keyText
    }

    test("neon tokens keep the brand background, amber, and pink") {
        val tokens = KeyboardColorTokens.from(KeyboardPalettes.Neon)
        KeyboardColorMath.hex(tokens.defines.getValue("--background")) shouldBe "#010619"
        KeyboardColorMath.hex(tokens.defines.getValue("--primary")) shouldBe "#ffc107"
        KeyboardColorMath.hex(tokens.defines.getValue("--secondary")) shouldBe "#ff4d8d"
        KeyboardColorMath.hex(tokens.defines.getValue("--glow")) shouldBe "#ffc107"
        KeyboardColorMath.hex(tokens.defines.getValue("--surface")) shouldBe "#141c33"
    }

    test("hex accepts short and long forms and rejects junk") {
        KeyboardColorMath.parseHex("#abc") shouldBe 0xFFAABBCC.toInt()
        KeyboardColorMath.parseHex("#00E5FF") shouldBe 0xFF00E5FF.toInt()
        KeyboardColorMath.parseHex("ffc107") shouldBe 0xFFFFC107.toInt()
        KeyboardColorMath.parseHex("#ffc10780") shouldBe 0xFFC10780.toInt()
        KeyboardColorMath.parseHex("nope") shouldBe null
        KeyboardColorMath.parseHex("#12345") shouldBe null
    }

    test("the overlay recolors neon and leaves a Floris theme alone") {
        val assets = File("src/main/assets/ime/theme/org.florisboard.themes/stylesheets")
        val neon = SnyggStylesheet.fromJson(assets.resolve("victorious_neon.json").readText()).getOrThrow()
        val night = SnyggStylesheet.fromJson(assets.resolve("floris_night.json").readText()).getOrThrow()
        KeyboardColorOverlay.apply(night, "floris_night", KeyboardPalettes.CyberBlue) shouldBe night

        val painted = KeyboardColorOverlay.apply(neon, "victorious_neon", KeyboardPalettes.CyberBlue)
        val defines = painted.rules.getValue(SnyggAnnotationRule.Defines) as SnyggSinglePropertySet
        hexOf(defines, "--background") shouldBe "#020814"
        hexOf(defines, "--primary") shouldBe "#00e5ff"
        hexOf(defines, "--secondary") shouldBe "#3d7eff"
        hexOf(defines, "--surface") shouldBe "#0b1e33"

        val pill = painted.rules.entries.first { (rule, _) ->
            rule is SnyggElementRule &&
                rule.elementName == "smartbar-candidate-word" &&
                rule.selector == SnyggSelector.NONE &&
                rule.attributes.isEmpty()
        }.value as SnyggSinglePropertySet
        hexOf(pill.properties.getValue("background")) shouldBe "#102a44"

        val pressed = painted.rules.entries.first { (rule, _) ->
            rule is SnyggElementRule && rule.elementName == "key" && rule.selector == SnyggSelector.PRESSED && rule.attributes.isEmpty()
        }.value as SnyggSinglePropertySet
        hexOf(pressed.properties.getValue("background")) shouldNotBe "#2a2410"
        KeyboardColorOverlay.appliesTo("victorious_neon_light") shouldBe true
        KeyboardColorOverlay.appliesTo("floris_day") shouldBe false
    }
})

private fun hexOf(set: SnyggSinglePropertySet, name: String): String = hexOf(set.properties.getValue(name))

private fun hexOf(value: org.florisboard.lib.snygg.value.SnyggValue): String {
    val color = (value as SnyggStaticColorValue).color
    val rgb = (color.red * 255f).roundToInt().coerceIn(0, 255) shl 16 or
        ((color.green * 255f).roundToInt().coerceIn(0, 255) shl 8) or
        (color.blue * 255f).roundToInt().coerceIn(0, 255)
    return "#%06x".format(rgb)
}
