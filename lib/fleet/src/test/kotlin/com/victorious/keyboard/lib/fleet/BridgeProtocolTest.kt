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
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class BridgeProtocolTest : FunSpec({
    val hub = listOf("ai.droidcommand.app")
    val debugHub = listOf("ai.droidcommand.app.debug")

    test("a messenger stamp is the caller, and a missing stamp is not this process") {
        BridgeProtocol.callerUid(10_123) shouldBe 10_123
        BridgeProtocol.callerUid(0) shouldBe 0
        BridgeProtocol.callerUid(-1) shouldBe null
    }

    test("only the two hub packages with a certificate are allowed") {
        BridgeProtocol.authorized(hub, certificatesPresent = true) shouldBe true
        BridgeProtocol.authorized(debugHub, certificatesPresent = true) shouldBe true
        BridgeProtocol.authorized(hub, certificatesPresent = false) shouldBe false
        BridgeProtocol.authorized(listOf("com.victorious.keyboard"), true) shouldBe false
        BridgeProtocol.authorized(listOf("ai.droidcommand.app", "com.other.app"), true) shouldBe false
        BridgeProtocol.authorized(emptyList(), true) shouldBe false
    }

    test("a disabled switch refuses an authorized caller and hides status") {
        val raw = BridgeProtocol.response(
            op = BridgeProtocol.OP_STATUS,
            authorized = true,
            controlEnabled = false,
            imeRunning = true,
        )
        val json = Json.parseToJsonElement(raw).jsonObject
        json["ok"]!!.jsonPrimitive.boolean shouldBe false
        json["error"]!!.jsonPrimitive.content shouldBe BridgeProtocol.ERROR_DISABLED
        json["dca_required"]!!.jsonPrimitive.boolean shouldBe false
        json["fleet_class"]!!.jsonPrimitive.content shouldBe "external_node"
        json["protocol_major"]!!.jsonPrimitive.content shouldBe "1"
        json["live"]!!.jsonPrimitive.boolean shouldBe false
        json["mode"]!!.jsonPrimitive.content shouldBe "stub"
        raw shouldNotContain "keystroke"
        raw shouldNotContain "selection"
    }

    test("an unauthorized caller is refused even when the switch is on") {
        val raw = BridgeProtocol.response(BridgeProtocol.OP_CAPABILITIES, authorized = false, controlEnabled = true, imeRunning = true)
        val json = Json.parseToJsonElement(raw).jsonObject
        json["error"]!!.jsonPrimitive.content shouldBe BridgeProtocol.ERROR_UNAUTHORIZED
        json["ok"]!!.jsonPrimitive.boolean shouldBe false
        json["capabilities"]!!.jsonArray.size shouldBe 0
    }

    test("capabilities and status stay free of typed text") {
        val caps = BridgeProtocol.response(BridgeProtocol.OP_CAPABILITIES, true, true, true)
        val status = BridgeProtocol.response(BridgeProtocol.OP_STATUS, true, true, false)
        caps shouldContain "keyboard.capabilities.get"
        caps shouldContain "keyboard.status"
        val statusJson = Json.parseToJsonElement(status).jsonObject
        statusJson["ok"]!!.jsonPrimitive.boolean shouldBe true
        statusJson["live"]!!.jsonPrimitive.boolean shouldBe false
        statusJson["mode"]!!.jsonPrimitive.content shouldBe "stub"
        statusJson["ime_running"]!!.jsonPrimitive.boolean shouldBe false
        status shouldNotContain "commitText"
        val live = Json.parseToJsonElement(
            BridgeProtocol.response(BridgeProtocol.OP_STATUS, true, true, true),
        ).jsonObject
        live["live"]!!.jsonPrimitive.boolean shouldBe true
        live["mode"]!!.jsonPrimitive.content shouldBe "live"
        live["ime_running"]!!.jsonPrimitive.boolean shouldBe true
    }
})
