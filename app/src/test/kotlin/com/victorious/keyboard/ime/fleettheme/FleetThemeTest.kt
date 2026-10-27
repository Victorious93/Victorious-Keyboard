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

package com.victorious.keyboard.ime.fleettheme

import com.victorious.keyboard.ime.theme.colors.KeyboardPalettes
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.types.shouldBeInstanceOf
import java.io.File

class FleetThemeTest : FunSpec({
    val pin = FleetThemeContract.PROVIDER_CERT_SHA256
    val neon = """
        {
          "schemaVersion": 1,
          "mode": "NEON_FLEET",
          "iconStyle": "MONOCHROME",
          "iconPackPackage": "",
          "updatedAtMs": 1700000000000,
          "palette": {
            "background": "#FF0B0C12",
            "surface": "#FF161821",
            "primary": "#FF00E5FF",
            "secondary": "#FFE840FF",
            "onBackground": "#FFF5F7FF",
            "muted": "#FF9AA3B5",
            "danger": "#FFFF6B8A",
            "stale": "#FFFFC14D",
            "tile": "#CC1A1D2B"
          },
          "unrelated": true
        }
    """.trimIndent()

    fun row(json: String = neon, schema: String = "1"): Map<String, String?> {
        return mapOf(
            FleetThemeContract.COL_SCHEMA to schema,
            FleetThemeContract.COL_MODE to "NEON_FLEET",
            FleetThemeContract.COL_ICON_STYLE to "SYSTEM",
            FleetThemeContract.COL_JSON to json,
            FleetThemeContract.COL_UPDATED to "1700000000000",
            "attacker" to "#FFFF0000",
        )
    }

    test("schema 1 json keeps every palette color, including tile alpha, and ignores unknown keys") {
        val snapshot = FleetThemeCodec.decode(row())
        snapshot shouldNotBe null
        snapshot!!.schemaVersion shouldBe 1
        snapshot.mode shouldBe "NEON_FLEET"
        snapshot.iconStyle shouldBe "MONOCHROME"
        snapshot.palette.background shouldBe 0xFF0B0C12.toInt()
        snapshot.palette.surface shouldBe 0xFF161821.toInt()
        snapshot.palette.primary shouldBe 0xFF00E5FF.toInt()
        snapshot.palette.secondary shouldBe 0xFFE840FF.toInt()
        snapshot.palette.onBackground shouldBe 0xFFF5F7FF.toInt()
        snapshot.palette.muted shouldBe 0xFF9AA3B5.toInt()
        snapshot.palette.danger shouldBe 0xFFFF6B8A.toInt()
        snapshot.palette.stale shouldBe 0xFFFFC14D.toInt()
        snapshot.palette.tile shouldBe 0xCC1A1D2B.toInt()
        snapshot.keyboard.background shouldBe snapshot.palette.background
        snapshot.keyboard.key shouldBe snapshot.palette.surface
        snapshot.keyboard.primary shouldBe snapshot.palette.primary
        snapshot.keyboard.secondary shouldBe snapshot.palette.secondary
        snapshot.keyboard.suggestion shouldBe snapshot.palette.tile
        snapshot.toString() shouldNotContain "0B0C12"
        snapshot.toString() shouldNotContain "#FF"
    }

    test("a six-digit hex is opaque and a short or broken hex is rejected") {
        FleetThemeCodec.parseHex("#00e5ff") shouldBe 0xFF00E5FF.toInt()
        FleetThemeCodec.parseHex("#CC1A1D2B") shouldBe 0xCC1A1D2B.toInt()
        FleetThemeCodec.parseHex("#0B0") shouldBe null
        FleetThemeCodec.parseHex("FF0B0C12") shouldBe null
        FleetThemeCodec.parseHex("#GG0000") shouldBe null
        FleetThemeCodec.parseHex("#FF00E5") shouldBe null
    }

    test("schema other than 1 is rejected, including a json body that disagrees with the column") {
        FleetThemeCodec.decode(row(schema = "2")) shouldBe null
        FleetThemeCodec.decode(row(schema = "0")) shouldBe null
        FleetThemeCodec.decode(row().minus(FleetThemeContract.COL_SCHEMA)) shouldBe null
        val wrongJson = neon.replace("\"schemaVersion\": 1", "\"schemaVersion\": 2")
        FleetThemeCodec.decode(row(json = wrongJson)) shouldBe null
        FleetThemeCodec.decodeJson("""{"mode":"DARK"}""") shouldBe null
        val broken = neon.replace("#FF00E5FF", "#00E5")
        FleetThemeCodec.decode(row(json = broken)) shouldBe null
    }

    test("columns alone are enough, and a missing color is not replaced") {
        val columns = mapOf(
            FleetThemeContract.COL_SCHEMA to "1",
            FleetThemeContract.COL_MODE to "DARK",
            FleetThemeContract.COL_ICON_STYLE to "SYSTEM",
            FleetThemeContract.COL_ICON_PACK to "",
            FleetThemeContract.COL_UPDATED to "9",
            "background" to "#111111",
            "surface" to "#222222",
            "primary" to "#333333",
            "secondary" to "#444444",
            "onBackground" to "#EEEEEE",
            "muted" to "#888888",
            "danger" to "#AA0000",
            "stale" to "#CC8800",
            "tile" to "#33333333",
            "notAColor" to "#FFFFFFFF",
        )
        val snapshot = FleetThemeCodec.decode(columns)
        snapshot shouldNotBe null
        snapshot!!.palette.background shouldBe 0xFF111111.toInt()
        snapshot.palette.tile shouldBe 0x33333333
        snapshot.mode shouldBe "DARK"
        FleetThemeCodec.decode(columns.minus("danger")) shouldBe null
    }

    test("the provider pin matches the published debug certificate only") {
        val lower = pin.lowercase().replace(":", "")
        FleetThemeAccess.trust(FleetThemeContract.PROVIDER_PACKAGE, listOf(lower)) shouldBe FleetThemeTrust.Trusted
        FleetThemeAccess.trust(FleetThemeContract.PROVIDER_PACKAGE, listOf("  $pin  ")) shouldBe FleetThemeTrust.Trusted
        FleetThemeAccess.trust(FleetThemeContract.PROVIDER_PACKAGE, listOf("00:11", pin)) shouldBe FleetThemeTrust.Trusted
        FleetThemeAccess.trust("com.victorious.launch.debug", listOf(pin)) shouldBe FleetThemeTrust.Untrusted
        FleetThemeAccess.trust(FleetThemeContract.PROVIDER_PACKAGE, listOf("00:11:22")) shouldBe FleetThemeTrust.Untrusted
        FleetThemeAccess.trust(FleetThemeContract.PROVIDER_PACKAGE, listOf("")) shouldBe FleetThemeTrust.Untrusted
        FleetThemeAccess.trust(FleetThemeContract.PROVIDER_PACKAGE, listOf("TBD")) shouldBe FleetThemeTrust.Untrusted
        FleetThemeAccess.trust(FleetThemeContract.PROVIDER_PACKAGE, emptyList()) shouldBe FleetThemeTrust.Untrusted
        FleetThemeAccess.trust(null, listOf(pin)) shouldBe FleetThemeTrust.Absent
        FleetThemeAccess.trust("  ", listOf(pin)) shouldBe FleetThemeTrust.Absent
    }

    test("a payload does not authorize itself, and a refused query falls back to black") {
        val good = row()
        val applied = FleetThemeAccess.decide(FleetThemeContract.PROVIDER_PACKAGE, listOf(pin), false, good)
        applied.shouldBeInstanceOf<FleetThemeState.Applied>()
        applied.snapshot.palette.primary shouldBe 0xFF00E5FF.toInt()
        applied.keyboardPalette()!!.primary shouldBe 0xFF00E5FF.toInt()
        applied.settingsPaint()!!.danger shouldBe 0xFFFF6B8A.toInt()
        applied.settingsPaint()!!.light shouldBe false

        val spoofed = FleetThemeAccess.decide("com.example.theme", listOf(pin), false, good)
        spoofed shouldBe FleetThemeState.NotPinned
        spoofed.keyboardPalette() shouldBe KeyboardPalettes.Black
        spoofed.settingsPaint()!!.background shouldBe KeyboardPalettes.Black.background
        spoofed.forcesBlackSquared() shouldBe true

        val wrongCert = FleetThemeAccess.decide(FleetThemeContract.PROVIDER_PACKAGE, listOf("TBD"), false, good)
        wrongCert shouldBe FleetThemeState.NotPinned
        wrongCert.keyboardPalette() shouldBe KeyboardPalettes.Black

        val refused = FleetThemeAccess.decide(FleetThemeContract.PROVIDER_PACKAGE, listOf(pin), true, good)
        refused shouldBe FleetThemeState.NotPinned
        refused.keyboardPalette()!!.background shouldBe KeyboardPalettes.Black.background
        refused.settingsPaint()!!.primary shouldBe KeyboardPalettes.Black.primary

        val missing = FleetThemeAccess.decide(null, emptyList(), false, good)
        missing shouldBe FleetThemeState.Absent
        missing.keyboardPalette() shouldBe null
        missing.settingsPaint() shouldBe null
        missing.forcesBlackSquared() shouldBe false

        val emptyRow = FleetThemeAccess.decide(FleetThemeContract.PROVIDER_PACKAGE, listOf(pin), false, null)
        emptyRow shouldBe FleetThemeState.Absent

        val badSchema = FleetThemeAccess.decide(FleetThemeContract.PROVIDER_PACKAGE, listOf(pin), false, row(schema = "2"))
        badSchema shouldBe FleetThemeState.NotPinned
    }

    test("broadcast extras are a wake-up and are not a palette") {
        FleetThemeWake.isWake(FleetThemeContract.ACTION_CHANGED) shouldBe true
        FleetThemeWake.isWake("android.intent.action.BOOT_COMPLETED") shouldBe false
        val extras = mapOf(
            FleetThemeContract.COL_SCHEMA to "1",
            FleetThemeContract.COL_MODE to "LIGHT",
            FleetThemeContract.COL_ICON_STYLE to "TINTED",
            FleetThemeContract.COL_UPDATED to "5",
            "background" to "#FFFFFFFF",
        )
        FleetThemeCodec.decode(extras) shouldBe null
        val light = FleetThemeCodec.decode(row(json = neon.replace("NEON_FLEET", "LIGHT")))
        light!!.mode shouldBe "LIGHT"
        val applied = FleetThemeState.Applied(light)
        applied.settingsPaint()!!.light shouldBe true
        applied.settingsPaint()!!.background shouldBe light.palette.background
        val material = FleetThemeCodec.decode(row(json = neon.replace("NEON_FLEET", "MATERIAL_YOU")))
        material!!.mode shouldBe "MATERIAL_YOU"
        FleetThemeState.Applied(material).settingsPaint()!!.light shouldBe false
        material.keyboard.primary shouldBe light.keyboard.primary
    }

    test("the client only queries, the receiver is exported, and versionCode moved to 150") {
        val sources = File("src/main/kotlin/com/victorious/keyboard/ime/fleettheme")
            .walkTopDown()
            .filter { it.extension == "kt" }
            .toList()
        sources.isNotEmpty() shouldBe true
        val client = sources.first { it.name == "FleetThemeClient.kt" }.readText()
        client shouldContain "contentResolver.query"
        for (call in listOf(".insert(", ".update(", ".delete(", "applyBatch", "bulkInsert")) {
            for (file in sources) {
                file.readText().contains(call) shouldBe false
            }
        }
        val manifest = File("src/main/AndroidManifest.xml").readText()
        val receiver = Regex("""<receiver[\s\S]*?</receiver>""").find(manifest)
        receiver shouldNotBe null
        receiver!!.value shouldContain "FleetThemeChangedReceiver"
        receiver.value shouldContain "android:exported=\"true\""
        receiver.value shouldContain FleetThemeContract.ACTION_CHANGED
        manifest shouldContain "android:authorities=\"com.victorious.launch.theme\""
        manifest shouldNotContain "READ_FLEET_THEME"
        File("../gradle.properties").readText() shouldContain "projectVersionCode=150"
    }
})
