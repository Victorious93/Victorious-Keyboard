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

package com.victorious.keyboard.lib.fleet

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.Base64

class ConfigBackupTest : FunSpec({
    val pkg = "com.victorious.keyboard"
    val source = "local-test"
    val now = 1_760_920_000_000L
    val key = ByteArray(32) { (it + 3).toByte() }
    val toolbar = """
        {"pinned":["CLIPBOARD","UNDO"],"pinnedRow":true,"page":"quick","hidden":["COPY"],
         "order":["CLIPBOARD"],"bracketPairing":false,"shellHistoryEnabled":true,
         "commands":[{"text":"rm-marker-command"}],"recentCommands":["recent-marker"]}
    """.trim()

    fun prefs() = mapOf(
        "theme__mode" to "dark",
        "theme__day_theme_id" to "org.florisboard.themes:victorious_black",
        "keyboard__number_row" to "true",
        "smartbar__layout" to "suggestions",
        "devrow__key_order" to "esc,tab",
        "devrow__macros" to "rm-marker-command",
        "fleet__external_control" to "true",
        "clipboard__history" to "copied-marker",
        "vault__items" to "vault-marker",
        "snippet__body" to "snippet-marker",
    )

    test("export keeps themes, layouts, and the toolbar, and drops secrets and command text") {
        val bundle = ConfigBackup.export(pkg, source, now, prefs(), toolbar, clearPayload = true)
        bundle!!.shouldContain(""""wrapping":"none"""")
        bundle.shouldContain(""""packageName":"com.victorious.keyboard"""")
        bundle.shouldContain("CLIPBOARD")
        bundle.shouldContain("theme__mode")
        bundle.shouldContain("keyboard__number_row")
        bundle.shouldContain("org.florisboard.themes:victorious_black")
        bundle.shouldNotContain("rm-marker-command")
        bundle.shouldNotContain("recent-marker")
        bundle.shouldNotContain("copied-marker")
        bundle.shouldNotContain("vault-marker")
        bundle.shouldNotContain("snippet-marker")
        bundle.shouldNotContain("devrow__macros")
        bundle.shouldNotContain("fleet__external_control")
        bundle.shouldNotContain("ciphertext")
        val root = Json.parseToJsonElement(bundle) as JsonObject
        root["version"]!!.jsonPrimitive.content.toInt() shouldBe 1
        val entry = root["entries"]!!.jsonArray.single().jsonObject
        entry["schemaVersion"]!!.jsonPrimitive.content.toInt() shouldBe 1
        val payload = Json.parseToJsonElement(entry["clearPayload"]!!.jsonPrimitive.content) as JsonObject
        val rows = payload["rows"]!!.jsonArray.map { it.jsonObject }
        val layout = rows.first { it["id"]!!.jsonPrimitive.content == ConfigBackup.TOOLBAR_ID }
        layout["value"]!!.jsonPrimitive.content.shouldContain("CLIPBOARD")
        layout["value"]!!.jsonPrimitive.content.shouldNotContain("commands")
        ConfigBackup.export(pkg, source, now, prefs(), "{", clearPayload = true) shouldBe null
        ConfigBackup.export(pkg, source, now, prefs() + ("theme__mode" to "has a password"), toolbar, clearPayload = true) shouldBe null
    }

    test("a ciphertext bundle round-trips and a tampered record is internal") {
        val bundle = ConfigBackup.export(pkg, source, now, prefs(), toolbar, key = key)!!
        bundle.shouldContain(""""wrapping":"raw-aes-256-gcm"""")
        bundle.shouldNotContain("clearPayload")
        bundle.shouldNotContain("CLIPBOARD")
        val live = mutableMapOf("theme__mode" to "light", ConfigBackup.EXTERNAL_CONTROL to "true")
        val before = live.toMap()
        val dry = ConfigBackup.import(bundle, live, pkg, dryRun = true, conflictPolicy = "overwrite", key = key)
        dry.error shouldBe ""
        dry.wrote shouldBe false
        dry.rows[ConfigBackup.EXTERNAL_CONTROL] shouldBe "false"
        dry.rows["theme__mode"] shouldBe "dark"
        live shouldBe before

        val held = ConfigBackup.import(bundle, live, pkg, conflictPolicy = "overwrite", confirmed = false, key = key)
        held.error shouldBe "confirmation_required"
        held.wrote shouldBe false
        held.rows.isEmpty() shouldBe true
        live shouldBe before

        val applied = ConfigBackup.import(bundle, live, pkg, conflictPolicy = "overwrite", confirmed = true, key = key)
        applied.wrote shouldBe true
        applied.error shouldBe ""
        applied.rows[ConfigBackup.EXTERNAL_CONTROL] shouldBe "false"
        applied.rows["theme__mode"] shouldBe "dark"
        applied.rows["keyboard__number_row"] shouldBe "true"
        applied.rows[ConfigBackup.TOOLBAR_ID]!!.shouldContain("CLIPBOARD")
        applied.rows[ConfigBackup.TOOLBAR_ID]!!.shouldNotContain("rm-marker-command")
        applied.overwritten shouldBe listOf("theme__mode")
        live shouldBe before

        ConfigBackup.import(bundle, live, pkg, confirmed = true, key = ByteArray(32) { 7 })
            .error shouldBe "internal"
        val root = Json.parseToJsonElement(bundle) as JsonObject
        val cipher = root["entries"]!!.jsonArray.single().jsonObject["ciphertext"]!!.jsonPrimitive.content
        val parts = cipher.split('.')
        val body = Base64.getUrlDecoder().decode(parts[2])
        body[body.lastIndex] = (body[body.lastIndex].toInt() xor 0x01).toByte()
        val flipped = parts[0] + "." + parts[1] + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(body)
        val tampered = bundle.replace(cipher, flipped)
        ConfigBackup.import(tampered, live, pkg, confirmed = true, key = key).error shouldBe "internal"
    }

    test("conflict modes, confirmation, and a forced-off external switch") {
        val incoming = mapOf(
            "theme__mode" to "light",
            "keyboard__number_row" to "false",
            "smartbar__layout" to "actions",
            ConfigBackup.EXTERNAL_CONTROL to "true",
        )
        val exported = ConfigBackup.export(pkg, source, now, incoming, clearPayload = true)!!
        val bundle = exported.replace(
            """"rows":[""",
            """"rows":[{"id":"fleet__external_control","value":"true"},""",
        )
        val live = mapOf("theme__mode" to "dark", "keyboard__number_row" to "false")

        val skip = ConfigBackup.import(bundle, live, pkg, confirmed = true, conflictPolicy = "skip")
        skip.wrote shouldBe true
        skip.rows["theme__mode"] shouldBe "dark"
        skip.rows.containsKey("smartbar__layout") shouldBe false
        skip.skipped shouldBe listOf("theme__mode", "keyboard__number_row", "smartbar__layout")
        skip.rows[ConfigBackup.EXTERNAL_CONTROL] shouldBe "false"

        val merge = ConfigBackup.import(bundle, live, pkg, confirmed = true, conflictPolicy = "merge-by-id")
        merge.rows["theme__mode"] shouldBe "dark"
        merge.rows["keyboard__number_row"] shouldBe "false"
        merge.rows["smartbar__layout"] shouldBe "actions"
        merge.added shouldBe listOf("smartbar__layout")
        merge.skipped shouldBe listOf("theme__mode", "keyboard__number_row")
        merge.rows[ConfigBackup.EXTERNAL_CONTROL] shouldBe "false"

        val overwrite = ConfigBackup.import(bundle, live, pkg, confirmed = true, conflictPolicy = "overwrite")
        overwrite.rows["theme__mode"] shouldBe "light"
        overwrite.rows["smartbar__layout"] shouldBe "actions"
        overwrite.overwritten shouldBe listOf("theme__mode")
        overwrite.added shouldBe listOf("smartbar__layout")
        overwrite.rows[ConfigBackup.EXTERNAL_CONTROL] shouldBe "false"
    }

    test("bad bundles fail closed and a missing package is not found") {
        ConfigBackup.import("{", emptyMap(), pkg, confirmed = true).error shouldBe "invalid_parameters"
        ConfigBackup.import("""{"version":2}""", emptyMap(), pkg, confirmed = true).error shouldBe "invalid_parameters"
        ConfigBackup.import("{}", emptyMap(), pkg, conflictPolicy = "replace", confirmed = true).error shouldBe "invalid_parameters"
        val other = ConfigBackup.export("ai.droidcommand.app", source, now, mapOf("theme__mode" to "dark"), clearPayload = true)!!
        ConfigBackup.import(other, emptyMap(), pkg, dryRun = true).error shouldBe "not_found"
        val one = ConfigBackup.export(pkg, source, now, mapOf("theme__mode" to "dark"), clearPayload = true)!!
        val duplicate = one.replace(
            """"entries":[""",
            """"entries":[{"packageName":"$pkg","schemaVersion":1,"clearPayload":"{\"rows\":[]}"},""",
        )
        ConfigBackup.import(duplicate, emptyMap(), pkg, confirmed = true).error shouldBe "invalid_parameters"
        val keystore = one.replace(""""wrapping":"none"""", """"wrapping":"android-keystore"""")
        ConfigBackup.import(keystore, emptyMap(), pkg, confirmed = true).error shouldBe "invalid_parameters"
        val poisoned = ConfigBackup.export(
            pkg,
            source,
            now,
            mapOf("theme__mode" to "dark", "keyboard__api_token" to "present"),
            clearPayload = true,
        )
        poisoned shouldBe null
    }
})
