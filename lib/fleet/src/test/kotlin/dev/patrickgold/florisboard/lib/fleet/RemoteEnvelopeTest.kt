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

package dev.patrickgold.florisboard.lib.fleet

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

class RemoteEnvelopeTest : FunSpec({
    test("the frozen version is major 1 minor 1 and matches the bridge major") {
        RemoteEnvelope.BRIDGE_PROTOCOL_MAJOR shouldBe 1
        RemoteEnvelope.BRIDGE_PROTOCOL_MINOR shouldBe 1
        RemoteEnvelope.BRIDGE_PROTOCOL_MAJOR shouldBe BridgeProtocol.PROTOCOL_MAJOR
    }

    test("a stranger and a switched-off hub learn nothing and do not consume a nonce") {
        val cache = RemoteEnvelope.NonceCache()
        val off = call(cache, request("capabilities.get"), authorized = false)
        off.str("error") shouldBe "unauthorized"
        off.bool("ok") shouldBe false
        call(cache, request("capabilities.get"), enabled = false).str("error") shouldBe "external_control_disabled"
        call(cache, request("capabilities.get")).bool("ok") shouldBe true
    }

    test("capabilities.get lists each safe action and does not list input") {
        val body = call(RemoteEnvelope.NonceCache(), request("capabilities.get"))
        body.int("protocol_major") shouldBe 1
        body.int("protocol_minor") shouldBe 1
        val names = body["capabilities"]!!.jsonArray.map { it.jsonObject.str("name") }
        names shouldBe RemoteEnvelope.CAPABILITIES.map { it.name }
        for (item in body["capabilities"]!!.jsonArray) {
            val kind = item.jsonObject.str("kind")
            (kind == "read" || kind == "action" || kind == "destructive") shouldBe true
            item.jsonObject.bool("remoteAllowed") shouldBe true
            item.jsonObject.bool("requiresConfirm") shouldBe false
        }
        (BridgeProtocol.OP_INPUT_TEXT in names) shouldBe false
        (BridgeProtocol.OP_INPUT_KEY in names) shouldBe false
        val status = body["capabilities"]!!.jsonArray.map { it.jsonObject }
            .first { it.str("name") == RemoteEnvelope.OP_NODE_STATUS }
        status.str("kind") shouldBe "read"
        status.bool("remoteAllowed") shouldBe true
        status.bool("requiresConfirm") shouldBe false
    }

    test("node.status.get returns node health only and ignores a destructive flag") {
        val port = RecordingPort()
        val now = 1_700_000_000_000L
        val body = call(
            RemoteEnvelope.NonceCache(),
            request(
                "node.status.get",
                now = now,
                destructive = true,
                params = mapOf("note" to "clipboard secret vault typed"),
            ),
            now = now,
            port = port,
        )
        body.bool("ok") shouldBe true
        body.str("error") shouldBe ""
        port.prompts shouldBe 0
        port.calls shouldBe listOf("node")
        val result = body["result"]!!.jsonObject
        result.str("nodeId") shouldBe "com.victorious.keyboard"
        result.str("packageName") shouldBe "com.victorious.keyboard"
        result.str("connectionClass") shouldBe "EXTERNAL_NODE"
        result["updatedAtMs"]!!.jsonPrimitive.content.toLong() shouldBe now
        val domain = result["domains"]!!.jsonArray.single().jsonObject
        domain.str("name") shouldBe "node_health"
        domain.str("state") shouldBe "ok"
        domain.str("detail") shouldBe "ime running"
        val raw = body.toString()
        raw.contains("clipboard") shouldBe false
        raw.contains("secret") shouldBe false
        raw.contains("vault") shouldBe false
        raw.contains("STANDARD") shouldBe false
        raw.contains("victorious_black") shouldBe false
    }

    test("node.status.get uses the same auth, replay, expiry, and node id rules") {
        val cache = RemoteEnvelope.NonceCache()
        val nonce = "00112233445566778899aabbccddee11"
        call(cache, request("node.status.get", nonce = nonce), authorized = false).str("error") shouldBe "unauthorized"
        call(cache, request("node.status.get", nonce = nonce)).bool("ok") shouldBe true
        call(cache, request("node.status.get", nonce = nonce)).str("error") shouldBe "replay_detected"
        val now = 2_000_000L
        call(
            RemoteEnvelope.NonceCache(),
            request("node.status.get", nonce = "22".repeat(16), now = now - 180_000),
            now = now,
        ).str("error") shouldBe "expired"
        call(
            RemoteEnvelope.NonceCache(),
            request("node.status.get", enabled = false),
            enabled = false,
        ).str("error") shouldBe "external_control_disabled"

        val stopped = RecordingPort(imeRunning = false)
        val down = call(RemoteEnvelope.NonceCache(), request("node.status.get"), port = stopped)
        down["result"]!!.jsonObject["domains"]!!.jsonArray.single().jsonObject.str("state") shouldBe "down"
        down["result"]!!.jsonObject["domains"]!!.jsonArray.single().jsonObject.str("detail") shouldBe "ime stopped"

        val match = call(
            RemoteEnvelope.NonceCache(),
            request("node.status.get", params = mapOf("nodeId" to "com.victorious.keyboard")),
        )
        match.bool("ok") shouldBe true
        val missing = call(
            RemoteEnvelope.NonceCache(),
            request("node.status.get", params = mapOf("nodeId" to "clipboard-other-node")),
        )
        missing.str("error") shouldBe "not_found"
        missing.toString().contains("clipboard") shouldBe false
        ("result" in missing) shouldBe false
        call(
            RemoteEnvelope.NonceCache(),
            request("node.status.get", params = mapOf("nodeId" to "")),
        ).str("error") shouldBe "invalid_parameters"
        call(
            RemoteEnvelope.NonceCache(),
            request("node.status.get", action = "node.status.get"),
        ).str("error") shouldBe "invalid_parameters"

        val command = RecordingPort()
        call(
            RemoteEnvelope.NonceCache(),
            request("remote.command", action = "node.status.get"),
            port = command,
        ).str("error") shouldBe "unsupported_op"
        command.calls.shouldBeEmpty()
        command.prompts shouldBe 0

        val blown = RecordingPort(blowUp = true)
        call(RemoteEnvelope.NonceCache(), request("node.status.get"), port = blown).str("error") shouldBe "internal"

        RemoteEnvelope.detailAllowed("ime running") shouldBe true
        RemoteEnvelope.detailAllowed("clip board") shouldBe false
        RemoteEnvelope.detailAllowed("x".repeat(201)) shouldBe false
        RemoteEnvelope.peekOp("""{"op":"node.status.get"}""") shouldBe "node.status.get"
    }

    test("replay is keyed by the authenticated source and the nonce, not by an envelope field") {
        val cache = RemoteEnvelope.NonceCache()
        val nonce = "00112233445566778899aabbccddeeff"
        call(cache, request("capabilities.get", nonce = nonce)).bool("ok") shouldBe true
        call(cache, request("capabilities.get", nonce = nonce)).str("error") shouldBe "replay_detected"
        call(cache, request("capabilities.get", nonce = nonce), source = "ai.droidcommand.app.debug")
            .bool("ok") shouldBe true
        call(cache, request("capabilities.get", nonce = nonce, extra = mapOf("sourceNodeRef" to "ai.droidcommand.app")))
            .str("error") shouldBe "invalid_parameters"
        call(cache, request("capabilities.get", nonce = "aabbccddeeff00112233445566778899")).bool("ok") shouldBe true
    }

    test("a nonce is retained for ten minutes and can be used again after that") {
        val cache = RemoteEnvelope.NonceCache()
        val nonce = "abcdefabcdefabcdefabcdefabcdefab"
        val start = 5_000_000L
        call(cache, request("capabilities.get", nonce = nonce, now = start), now = start).bool("ok") shouldBe true
        call(
            cache,
            request("capabilities.get", nonce = nonce, now = start + RemoteEnvelope.NONCE_RETENTION_MS),
            now = start + RemoteEnvelope.NONCE_RETENTION_MS,
        ).str("error") shouldBe "replay_detected"
        val later = start + RemoteEnvelope.NONCE_RETENTION_MS + 1
        call(cache, request("capabilities.get", nonce = nonce, now = later), now = later).bool("ok") shouldBe true
    }

    test("expired requests are rejected for ttl and for clock skew, and a repeat is a replay") {
        val cache = RemoteEnvelope.NonceCache()
        val now = 1_000_000L
        call(cache, request("capabilities.get", now = now - 60_000, ttl = 30_000), now = now).str("error") shouldBe "expired"
        call(cache, request("capabilities.get", now = now - 60_000, ttl = 30_000), now = now)
            .str("error") shouldBe "replay_detected"
        call(cache, request("capabilities.get", nonce = "22".repeat(16), now = now - 180_000), now = now)
            .str("error") shouldBe "expired"
        call(cache, request("capabilities.get", nonce = "33".repeat(16), now = now + 180_000), now = now)
            .str("error") shouldBe "expired"
        call(cache, request("capabilities.get", nonce = "44".repeat(16), now = now - 120_000), now = now).bool("ok") shouldBe true
        call(cache, request("capabilities.get", nonce = "55".repeat(16), ttl = 300_000), now = now).bool("ok") shouldBe true
        call(cache, request("capabilities.get", nonce = "66".repeat(16), ttl = 300_001), now = now)
            .str("error") shouldBe "invalid_parameters"
        call(cache, request("capabilities.get", nonce = "77".repeat(16), omitTtl = false, ttl = 0), now = now)
            .str("error") shouldBe "invalid_parameters"
    }

    test("remote commands never type, and input actions are unsupported") {
        val port = RecordingPort()
        val cache = RemoteEnvelope.NonceCache()
        call(cache, request("remote.command", action = "keyboard.input.text", params = mapOf("text" to "hello")), port = port)
            .str("error") shouldBe "unsupported_op"
        call(cache, request("remote.command", nonce = "12".repeat(16), action = "keyboard.input.key"), port = port)
            .str("error") shouldBe "unsupported_op"
        port.calls.shouldBeEmpty()
        port.prompts shouldBe 0
    }

    test("status, mode, theme, and media keys reach the port and session commands do not") {
        val port = RecordingPort()
        val cache = RemoteEnvelope.NonceCache()
        val status = call(cache, request("remote.command", action = "keyboard.status"), port = port)
        status.bool("ok") shouldBe true
        status["result"]!!.jsonObject.bool("imeRunning") shouldBe true
        status["result"]!!.jsonObject.str("mode") shouldBe "STANDARD"
        call(cache, request("remote.command", nonce = "a1".repeat(16), action = "keyboard.mode", params = mapOf("mode" to "developer")), port = port)
            .bool("ok") shouldBe true
        call(cache, request("remote.command", nonce = "a2".repeat(16), action = "keyboard.theme", params = mapOf("theme" to "org.florisboard.themes:victorious_black")), port = port)
            .bool("ok") shouldBe true
        call(cache, request("remote.command", nonce = "a3".repeat(16), action = "media.toggle"), port = port).bool("ok") shouldBe true
        call(cache, request("remote.command", nonce = "a4".repeat(16), action = "media.volume", params = mapOf("direction" to "up")), port = port)
            .bool("ok") shouldBe true
        call(cache, request("remote.command", nonce = "a5".repeat(16), action = "media.state"), port = port)
            .str("error") shouldBe "not_paired"
        call(cache, request("remote.command", nonce = "a6".repeat(16), action = "media.seek", params = mapOf("positionMs" to 10)), port = port)
            .str("error") shouldBe "not_paired"
        call(cache, request("remote.command", nonce = "a7".repeat(16), action = "media.volume", params = mapOf("level" to 40)), port = port)
            .str("error") shouldBe "not_paired"
        port.calls shouldBe listOf(
            "status",
            "mode:developer",
            "theme:org.florisboard.themes:victorious_black",
            "key:${RemoteEnvelope.KEY_PLAY_PAUSE}",
            "volume:1",
        )
        call(cache, request("remote.command", nonce = "a8".repeat(16), action = "keyboard.mode", params = mapOf("mode" to "nope")), port = port)
            .str("error") shouldBe "not_found"
        call(cache, request("remote.command", nonce = "a9".repeat(16), action = "media.next"), port = RecordingPort(effect = RemoteEnvelope.Effect.CAPABILITY_OFF))
            .str("error") shouldBe "capability_off"
        call(cache, request("remote.command", nonce = "b1".repeat(16), action = "media.next"), port = RecordingPort(blowUp = true))
            .str("error") shouldBe "internal"
    }

    test("a destructive request is never run and always asks on the device") {
        val port = RecordingPort()
        val cache = RemoteEnvelope.NonceCache()
        val denied = call(
            cache,
            request("remote.command", action = "keyboard.mode", params = mapOf("mode" to "standard"), destructive = true),
            port = port,
        )
        denied.str("error") shouldBe "confirmation_required"
        port.calls.shouldBeEmpty()
        port.prompts shouldBe 1
        val dry = RecordingPort()
        call(
            cache,
            request(
                "macro.run",
                nonce = "c1".repeat(16),
                macro = true,
                dry = true,
                steps = listOf(step("keyboard.mode", mapOf("mode" to "termux"), destructive = true)),
            ),
            port = dry,
        ).str("error") shouldBe "confirmation_required"
        dry.prompts shouldBe 0
        dry.calls.shouldBeEmpty()
    }

    test("macros abort, continue, and dry-run without sending input") {
        val port = RecordingPort()
        val cache = RemoteEnvelope.NonceCache()
        val aborted = call(
            cache,
            request(
                "macro.run",
                macro = true,
                steps = listOf(
                    step("keyboard.input.text"),
                    step("media.next"),
                ),
            ),
            port = port,
        )
        aborted.str("error") shouldBe "unsupported_op"
        aborted["steps"]!!.jsonArray.size shouldBe 1
        port.calls.shouldBeEmpty()

        val continued = call(
            cache,
            request(
                "macro.run",
                nonce = "d1".repeat(16),
                macro = true,
                steps = listOf(
                    step("keyboard.input.key", onError = "continue"),
                    step("media.prev"),
                ),
            ),
            port = port,
        )
        continued.str("error") shouldBe "unsupported_op"
        continued["steps"]!!.jsonArray.size shouldBe 2
        continued["steps"]!!.jsonArray[1].jsonObject.bool("ok") shouldBe true
        port.calls shouldBe listOf("key:${RemoteEnvelope.KEY_PREVIOUS}")

        val dry = RecordingPort()
        call(
            cache,
            request(
                "macro.run",
                nonce = "d2".repeat(16),
                macro = true,
                dry = true,
                steps = listOf(step("media.fast_forward"), step("keyboard.theme", mapOf("theme" to "org.florisboard.themes:floris_day"))),
            ),
            port = dry,
        ).bool("ok") shouldBe true
        dry.calls.shouldBeEmpty()

        val one = call(
            cache,
            request(
                "macro.step",
                nonce = "d3".repeat(16),
                action = "media.rewind",
                macro = true,
                stepIndex = 2,
                onError = "continue",
            ),
            port = port,
        )
        one.bool("ok") shouldBe true
        one["steps"]!!.jsonArray.single().jsonObject.int("index") shouldBe 2
        port.calls shouldBe listOf("key:${RemoteEnvelope.KEY_PREVIOUS}", "key:${RemoteEnvelope.KEY_REWIND}")
        call(
            cache,
            request(
                "macro.step",
                nonce = "d4".repeat(16),
                action = "media.next",
                macro = true,
                stepIndex = 0,
                onError = "skip",
            ),
        ).str("error") shouldBe "invalid_parameters"
    }

    test("a quoted number and a short nonce are invalid parameters") {
        val cache = RemoteEnvelope.NonceCache()
        val quoted = """{"op":"capabilities.get","requestId":"r1","nonce":"${"ab".repeat(16)}","timestampMs":"1000"}"""
        call(cache, quoted).str("error") shouldBe "invalid_parameters"
        call(cache, request("capabilities.get", nonce = "abcd")).str("error") shouldBe "invalid_parameters"
        call(cache, request("remote.command", nonce = "e1".repeat(16), action = "media.volume", params = mapOf("level" to 101)))
            .str("error") shouldBe "invalid_parameters"
        call(cache, request("nope")).str("error") shouldBe "unsupported_op"
    }

    test("an old bridge status request is not a remote-envelope op") {
        RemoteEnvelope.peekOp("""{"protocol_major":1,"op":"keyboard.status"}""") shouldBe ""
        RemoteEnvelope.peekOp("""{"op":"capabilities.get"}""") shouldBe "capabilities.get"
    }
})

private const val NONCE = "00112233445566778899aabbccddeeff"

private fun request(
    op: String,
    nonce: String = NONCE,
    now: Long = 1_000_000L,
    ttl: Long? = null,
    omitTtl: Boolean = ttl == null,
    action: Any? = null,
    params: Map<String, Any?> = emptyMap(),
    destructive: Boolean = false,
    extra: Map<String, String> = emptyMap(),
    macro: Boolean = false,
    dry: Boolean = false,
    steps: List<JsonObject> = emptyList(),
    stepIndex: Int? = null,
    onError: String? = null,
): String {
    val obj = buildJsonObject {
        put("op", op)
        put("requestId", "r1")
        put("nonce", nonce)
        put("timestampMs", now)
        if (!omitTtl && ttl != null) put("ttlMs", ttl)
        if (action is String) put("actionRef", action)
        if (params.isNotEmpty()) {
            put("params", buildJsonObject {
                for ((key, value) in params) {
                    when (value) {
                        is String -> put(key, value)
                        is Int -> put(key, value)
                        is Long -> put(key, value)
                    }
                }
            })
        }
        if (destructive) put("destructive", true)
        for ((key, value) in extra) put(key, value)
        if (macro) put("macroId", "m1")
        if (dry) put("dryRun", true)
        if (steps.isNotEmpty()) put("steps", kotlinx.serialization.json.JsonArray(steps))
        if (stepIndex != null) put("stepIndex", stepIndex)
        if (onError != null) put("onError", onError)
    }
    return Json.encodeToString(JsonObject.serializer(), obj)
}

private fun step(
    action: String,
    params: Map<String, Any?> = emptyMap(),
    destructive: Boolean = false,
    onError: String? = null,
): JsonObject = buildJsonObject {
    put("actionRef", action)
    if (params.isNotEmpty()) {
        put("params", buildJsonObject {
            for ((key, value) in params) {
                when (value) {
                    is String -> put(key, value)
                    is Int -> put(key, value)
                }
            }
        })
    }
    if (destructive) put("destructive", true)
    if (onError != null) put("onError", onError)
}

private fun call(
    cache: RemoteEnvelope.NonceCache,
    raw: String,
    now: Long = 1_000_000L,
    source: String = "ai.droidcommand.app",
    authorized: Boolean = true,
    enabled: Boolean = true,
    port: RemoteEnvelope.Port = RecordingPort(),
): JsonObject {
    val reply = RemoteEnvelope.handle(raw, authorized, enabled, source, now, cache, port)
    return Json.parseToJsonElement(reply) as JsonObject
}

private fun JsonObject.str(name: String) = this[name]!!.jsonPrimitive.content
private fun JsonObject.bool(name: String) = this[name]!!.jsonPrimitive.content.toBoolean()
private fun JsonObject.int(name: String) = this[name]!!.jsonPrimitive.content.toInt()

private class RecordingPort(
    private val effect: RemoteEnvelope.Effect = RemoteEnvelope.Effect.DONE,
    private val blowUp: Boolean = false,
    private val imeRunning: Boolean = true,
    private val packageName: String = "com.victorious.keyboard",
) : RemoteEnvelope.Port {
    val calls = mutableListOf<String>()
    var prompts = 0

    override fun status(): RemoteEnvelope.Status {
        calls += "status"
        if (blowUp) throw IllegalStateException("down")
        return RemoteEnvelope.Status(true, "STANDARD", "org.florisboard.themes:victorious_black")
    }

    override fun setMode(mode: String): RemoteEnvelope.Effect {
        calls += "mode:$mode"
        if (blowUp) throw IllegalStateException("down")
        return if (mode == "nope") RemoteEnvelope.Effect.NOT_FOUND else effect
    }

    override fun setTheme(themeId: String): RemoteEnvelope.Effect {
        calls += "theme:$themeId"
        return effect
    }

    override fun mediaKey(keyCode: Int): RemoteEnvelope.Effect {
        if (blowUp) throw IllegalStateException("down")
        calls += "key:$keyCode"
        return effect
    }

    override fun volume(direction: Int): RemoteEnvelope.Effect {
        calls += "volume:$direction"
        return effect
    }

    override fun nodePulse(): RemoteEnvelope.NodePulse {
        calls += "node"
        if (blowUp) throw IllegalStateException("down")
        return RemoteEnvelope.NodePulse(packageName, imeRunning)
    }

    override fun prompt() {
        prompts += 1
    }
}
