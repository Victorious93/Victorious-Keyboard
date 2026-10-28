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

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/**
 * Frozen remote-control envelope v1 on the keyboard bridge.
 *
 * Authorization is decided by the caller UID, package, and certificate before this object runs.
 * Nothing in the envelope is an identity. These operations never type into the focused app and never
 * read typed text, the clipboard, or the vault. Requests and replies are not logged.
 */
object RemoteEnvelope {
    const val BRIDGE_PROTOCOL_MAJOR = 1
    const val BRIDGE_PROTOCOL_MINOR = 1
    const val SKEW_MS = 120_000L
    const val DEFAULT_TTL_MS = 120_000L
    const val MAX_TTL_MS = 300_000L
    const val NONCE_RETENTION_MS = 600_000L
    const val MAX_BODY = 16_000
    const val MAX_STEPS = 32
    const val KEY_PLAY_PAUSE = 85
    const val KEY_NEXT = 87
    const val KEY_PREVIOUS = 88
    const val KEY_REWIND = 89
    const val KEY_FAST_FORWARD = 90

    const val OP_CAPABILITIES = "capabilities.get"
    const val OP_COMMAND = "remote.command"
    const val OP_MACRO_STEP = "macro.step"
    const val OP_MACRO_RUN = "macro.run"
    const val OP_NODE_STATUS = "node.status.get"
    const val OP_CONFIG_EXPORT = "config.export"
    const val OP_CONFIG_IMPORT = "config.import"
    val OPS = setOf(
        OP_CAPABILITIES, OP_COMMAND, OP_MACRO_STEP, OP_MACRO_RUN, OP_NODE_STATUS,
        OP_CONFIG_EXPORT, OP_CONFIG_IMPORT,
    )

    const val ERR_INVALID = "invalid_parameters"
    const val ERR_NOT_PAIRED = "not_paired"
    const val ERR_NOT_FOUND = "not_found"
    const val ERR_INTERNAL = "internal"
    const val ERR_UNAUTHORIZED = "unauthorized"
    const val ERR_REPLAY = "replay_detected"
    const val ERR_EXPIRED = "expired"
    const val ERR_DISABLED = "external_control_disabled"
    const val ERR_CONFIRM = "confirmation_required"
    const val ERR_UNSUPPORTED = "unsupported_op"
    const val ERR_OFF = "capability_off"

    /** Actions that insert text or key events into the focused app. They stay on the old bridge ops. */
    val INPUT_OPS = setOf(BridgeProtocol.OP_INPUT_TEXT, BridgeProtocol.OP_INPUT_KEY)

    data class Capability(
        val name: String,
        val kind: String,
        val remoteAllowed: Boolean,
        val requiresConfirm: Boolean,
    )

    val CAPABILITIES = listOf(
        Capability("keyboard.status", "read", true, false),
        Capability("keyboard.mode", "action", true, false),
        Capability("keyboard.theme", "action", true, false),
        Capability("media.play", "action", true, false),
        Capability("media.pause", "action", true, false),
        Capability("media.toggle", "action", true, false),
        Capability("media.next", "action", true, false),
        Capability("media.prev", "action", true, false),
        Capability("media.rewind", "action", true, false),
        Capability("media.fast_forward", "action", true, false),
        Capability("media.volume", "action", true, false),
        Capability("media.seek", "action", true, false),
        Capability("media.seekBy", "action", true, false),
        Capability("media.state", "read", true, false),
        Capability(OP_NODE_STATUS, "read", true, false),
        Capability(OP_CONFIG_EXPORT, "action", false, false),
        Capability(OP_CONFIG_IMPORT, "action", false, true),
    )

    private val byName = CAPABILITIES.associateBy { it.name }
    private val FORBIDDEN_DETAIL = listOf(
        "password", "apikey", "apitoken", "token", "secret", "codeword", "whitelist",
        "signingkey", "privatekey", "clipboard", "vault",
    )
    private val sessionOnly = setOf("media.seek", "media.seekBy", "media.state")

    data class Status(val imeRunning: Boolean, val mode: String, val theme: String)

    /** Whether this process is up. The package name is the application id, never a certificate or user text. */
    data class NodePulse(val packageName: String, val imeRunning: Boolean)

    enum class Effect { DONE, NOT_PAIRED, NOT_FOUND, CAPABILITY_OFF, INTERNAL }

    /**
     * What the keyboard may do for a remote command. Implementations must not type, press editor keys,
     * or read the field, the clipboard, or the vault.
     */
    interface Port {
        fun status(): Status
        fun setMode(mode: String): Effect
        fun setTheme(themeId: String): Effect
        fun mediaKey(keyCode: Int): Effect
        fun volume(direction: Int): Effect
        fun nodePulse(): NodePulse
        fun prompt()
    }

    /**
     * A status detail may be shown. It refuses names that would carry a secret, and it refuses a dump.
     * The check is on the letters only, so spacing does not hide a banned word.
     */
    internal fun detailAllowed(detail: String): Boolean {
        if (detail.length > 200) return false
        val folded = detail.lowercase().filter { it.isLetter() }
        return FORBIDDEN_DETAIL.none { it in folded }
    }

    /** Seen nonces, keyed by the authenticated source and the nonce. Entries live at least ten minutes. */
    class NonceCache(private val retentionMs: Long = NONCE_RETENTION_MS) {
        private val until = LinkedHashMap<String, Long>()

        /** @return true when this pair was already seen and is still inside the retention window. */
        fun checkAndRemember(sourceNodeRef: String, nonce: String, now: Long): Boolean {
            val key = sourceNodeRef + "\n" + nonce
            val expires = until[key]
            if (expires != null && expires >= now) return true
            evict(now)
            until[key] = now + retentionMs
            return false
        }

        private fun evict(now: Long) {
            val iterator = until.entries.iterator()
            while (iterator.hasNext()) {
                if (iterator.next().value < now) iterator.remove()
            }
        }
    }

    fun peekOp(raw: String?): String {
        return try {
            val obj = objectOrNull(raw) ?: return ""
            val op = obj["op"] as? JsonPrimitive ?: return ""
            if (!op.isString) return ""
            op.content.takeIf { it in OPS }.orEmpty()
        } catch (_: Exception) {
            ""
        }
    }

    /**
     * Reply used when the bridge throws before it can answer. The op and request id are kept
     * so the caller can match the failure. The body of [raw] is not included.
     */
    fun failed(raw: String?): String {
        val obj = objectOrNull(raw)
        val requestId = obj?.string("requestId")?.take(64).orEmpty()
        return reply(peekOp(raw), requestId, false, ERR_INTERNAL)
    }

    fun handle(
        raw: String?,
        authorized: Boolean,
        controlEnabled: Boolean,
        sourceNodeRef: String,
        now: Long,
        cache: NonceCache,
        port: Port,
    ): String {
        val peeked = peekOp(raw)
        if (!authorized) return reply(peeked, "", false, ERR_UNAUTHORIZED)
        if (!controlEnabled) return reply(peeked, "", false, ERR_DISABLED)
        val request = when (val parsed = parse(raw)) {
            is Parsed.Fail -> return reply(parsed.op, parsed.requestId, false, parsed.error)
            is Parsed.Ok -> parsed.request
        }
        if (cache.checkAndRemember(sourceNodeRef, request.nonce, now)) {
            return reply(request.op, request.requestId, false, ERR_REPLAY)
        }
        if (expired(request.timestampMs, request.ttlMs, now)) {
            return reply(request.op, request.requestId, false, ERR_EXPIRED)
        }
        return try {
            when (request.op) {
                OP_CAPABILITIES -> capabilities(request)
                OP_NODE_STATUS -> nodeStatus(request, now, port)
                OP_CONFIG_EXPORT, OP_CONFIG_IMPORT -> configRemoteRefused(request)
                OP_COMMAND -> runAction(request, request.actionRef, request.params, request.destructive, port, dry = false)
                    .let { outcome -> reply(request.op, request.requestId, outcome.error.isEmpty(), outcome.error, result = outcome.result) }
                OP_MACRO_STEP -> macroStep(request, port)
                OP_MACRO_RUN -> macroRun(request, port)
                else -> reply(request.op, request.requestId, false, ERR_UNSUPPORTED)
            }
        } catch (_: Exception) {
            reply(request.op, request.requestId, false, ERR_INTERNAL)
        }
    }

    private fun capabilities(request: Request): String {
        val list = buildJsonArray {
            for (item in CAPABILITIES) {
                add(buildJsonObject {
                    put("name", item.name)
                    put("kind", item.kind)
                    put("remoteAllowed", item.remoteAllowed)
                    put("requiresConfirm", item.requiresConfirm)
                })
            }
        }
        return reply(request.op, request.requestId, true, "", capabilities = list)
    }

    private fun macroStep(request: Request, port: Port): String {
        val outcome = runAction(request, request.actionRef, request.params, request.destructive, port, dry = false)
        val steps = buildJsonArray {
            add(stepJson(request.stepIndex ?: 0, request.actionRef, outcome.error))
        }
        return reply(request.op, request.requestId, outcome.error.isEmpty(), outcome.error, result = outcome.result, steps = steps)
    }

    private fun macroRun(request: Request, port: Port): String {
        val reports = ArrayList<Pair<Int, Outcome>>()
        for ((index, step) in request.steps.withIndex()) {
            val outcome = runAction(request, step.actionRef, step.params, step.destructive, port, dry = request.dryRun)
            reports += index to outcome
            val stop = outcome.error == ERR_CONFIRM || (outcome.error.isNotEmpty() && step.onError == "abort")
            if (stop && !request.dryRun) break
        }
        val first = reports.firstOrNull { it.second.error.isNotEmpty() }?.second?.error.orEmpty()
        val steps = buildJsonArray {
            for ((index, outcome) in reports) {
                add(stepJson(index, request.steps[index].actionRef, outcome.error))
            }
        }
        return reply(request.op, request.requestId, first.isEmpty(), first, steps = steps)
    }

    private data class Outcome(val error: String, val result: JsonObject? = null)

    private fun runAction(
        request: Request,
        actionRef: String,
        params: Map<String, JsonPrimitive>,
        destructive: Boolean,
        port: Port,
        dry: Boolean,
    ): Outcome {
        if (actionRef == OP_NODE_STATUS || actionRef == OP_CONFIG_EXPORT || actionRef == OP_CONFIG_IMPORT ||
            actionRef in INPUT_OPS || actionRef !in byName
        ) {
            return Outcome(ERR_UNSUPPORTED)
        }
        val capability = byName.getValue(actionRef)
        if (!capability.remoteAllowed) return Outcome(ERR_UNSUPPORTED)
        if (destructive || capability.requiresConfirm || capability.kind == "destructive") {
            if (!dry) port.prompt()
            return Outcome(ERR_CONFIRM)
        }
        val shape = validateParams(actionRef, params) ?: return Outcome(ERR_INVALID)
        if (shape == ParamShape.SESSION) return Outcome(ERR_NOT_PAIRED)
        if (dry) return Outcome("")
        val effect = try {
            dispatch(actionRef, params, port)
        } catch (_: Exception) {
            return Outcome(ERR_INTERNAL)
        }
        return when (effect) {
            is Dispatch.Status -> Outcome("", effect.body)
            is Dispatch.Effect -> when (effect.effect) {
                Effect.DONE -> Outcome("")
                Effect.NOT_PAIRED -> Outcome(ERR_NOT_PAIRED)
                Effect.NOT_FOUND -> Outcome(ERR_NOT_FOUND)
                Effect.CAPABILITY_OFF -> Outcome(ERR_OFF)
                Effect.INTERNAL -> Outcome(ERR_INTERNAL)
            }
        }
    }

    private sealed interface Dispatch {
        data class Status(val body: JsonObject) : Dispatch
        data class Effect(val effect: RemoteEnvelope.Effect) : Dispatch
    }

    private fun dispatch(actionRef: String, params: Map<String, JsonPrimitive>, port: Port): Dispatch {
        return when (actionRef) {
            "keyboard.status" -> {
                val status = port.status()
                Dispatch.Status(buildJsonObject {
                    put("imeRunning", status.imeRunning)
                    put("mode", status.mode)
                    put("theme", status.theme)
                })
            }
            "keyboard.mode" -> Dispatch.Effect(port.setMode(params.getValue("mode").content))
            "keyboard.theme" -> Dispatch.Effect(port.setTheme(params.getValue("theme").content))
            "media.play", "media.pause", "media.toggle" -> Dispatch.Effect(port.mediaKey(KEY_PLAY_PAUSE))
            "media.next" -> Dispatch.Effect(port.mediaKey(KEY_NEXT))
            "media.prev" -> Dispatch.Effect(port.mediaKey(KEY_PREVIOUS))
            "media.rewind" -> Dispatch.Effect(port.mediaKey(KEY_REWIND))
            "media.fast_forward" -> Dispatch.Effect(port.mediaKey(KEY_FAST_FORWARD))
            "media.volume" -> Dispatch.Effect(port.volume(if (params.getValue("direction").content == "up") 1 else -1))
            else -> Dispatch.Effect(Effect.NOT_PAIRED)
        }
    }

    private enum class ParamShape { NONE, SESSION }

    private fun validateParams(actionRef: String, params: Map<String, JsonPrimitive>): ParamShape? {
        fun only(vararg names: String) = params.keys == names.toSet()
        return when (actionRef) {
            "keyboard.status", "media.play", "media.pause", "media.toggle", "media.next", "media.prev",
            "media.rewind", "media.fast_forward", "media.state",
            -> if (params.isEmpty()) {
                if (actionRef == "media.state") ParamShape.SESSION else ParamShape.NONE
            } else {
                null
            }
            "keyboard.mode" -> {
                val mode = params["mode"]?.contentOrNull
                if (only("mode") && mode != null && mode.matches(Regex("[a-z_]{1,32}"))) ParamShape.NONE else null
            }
            "keyboard.theme" -> {
                val theme = params["theme"]?.contentOrNull
                if (only("theme") && theme != null && theme.length in 3..200 && theme.matches(Regex("[A-Za-z0-9._:-]+"))) {
                    ParamShape.NONE
                } else {
                    null
                }
            }
            "media.volume" -> when {
                only("direction") && params.getValue("direction").content in setOf("up", "down") -> ParamShape.NONE
                only("level") && params.getValue("level").longOrNull in 0L..100L -> ParamShape.SESSION
                else -> null
            }
            "media.seek" -> {
                val position = params["positionMs"]?.longOrNull
                if (only("positionMs") && position != null && position >= 0L) ParamShape.SESSION else null
            }
            "media.seekBy" -> if (only("deltaMs") && params["deltaMs"]?.longOrNull != null) ParamShape.SESSION else null
            else -> null
        }
    }

    /** A remote caller cannot export or import. The local codec is [ConfigBackup]. */
    private fun configRemoteRefused(request: Request): String {
        if (request.actionRef.isNotEmpty()) return reply(request.op, request.requestId, false, ERR_INVALID)
        return reply(request.op, request.requestId, false, ERR_OFF)
    }

    /**
     * `node.status.get` is its own op, not an actionRef. `destructive` is ignored, so this read is never
     * confirmation-gated. The snapshot is node health only.
     */
    private fun nodeStatus(request: Request, now: Long, port: Port): String {
        if (request.actionRef.isNotEmpty()) return reply(request.op, request.requestId, false, ERR_INVALID)
        val asked = request.params["nodeId"]
        if (asked != null && (!asked.isString || asked.content.isBlank() || asked.content.length > 200)) {
            return reply(request.op, request.requestId, false, ERR_INVALID)
        }
        val pulse = try {
            port.nodePulse()
        } catch (_: Exception) {
            return reply(request.op, request.requestId, false, ERR_INTERNAL)
        }
        val nodeId = pulse.packageName
        if (nodeId.isBlank() || nodeId.length > 200 || nodeId.any { it.isISOControl() || it.isWhitespace() } || !detailAllowed(nodeId)) {
            return reply(request.op, request.requestId, false, ERR_INVALID)
        }
        if (asked != null && asked.content != nodeId) {
            return reply(request.op, request.requestId, false, ERR_NOT_FOUND)
        }
        val detail = if (pulse.imeRunning) "ime running" else "ime stopped"
        if (!detailAllowed(detail)) return reply(request.op, request.requestId, false, ERR_INVALID)
        val result = buildJsonObject {
            put("nodeId", nodeId)
            put("packageName", nodeId)
            put("connectionClass", "EXTERNAL_NODE")
            put("updatedAtMs", now)
            put("domains", buildJsonArray {
                add(buildJsonObject {
                    put("name", "node_health")
                    put("state", if (pulse.imeRunning) "ok" else "down")
                    put("detail", detail)
                })
            })
        }
        return reply(request.op, request.requestId, true, "", result = result)
    }

    private fun expired(timestampMs: Long, ttlMs: Long, now: Long): Boolean {
        val delta = now - timestampMs
        val abs = if (delta < 0) -delta else delta
        val pastTtl = timestampMs <= now && now - timestampMs > ttlMs
        return pastTtl || abs > SKEW_MS
    }

    private data class Step(
        val actionRef: String,
        val params: Map<String, JsonPrimitive>,
        val destructive: Boolean,
        val onError: String,
    )

    private data class Request(
        val op: String,
        val requestId: String,
        val actionRef: String,
        val params: Map<String, JsonPrimitive>,
        val nonce: String,
        val timestampMs: Long,
        val ttlMs: Long,
        val destructive: Boolean,
        val macroId: String,
        val stepIndex: Int?,
        val onError: String,
        val steps: List<Step>,
        val dryRun: Boolean,
    )

    private sealed interface Parsed {
        data class Ok(val request: Request) : Parsed
        data class Fail(val op: String, val requestId: String, val error: String) : Parsed
    }

    private fun parse(raw: String?): Parsed {
        val obj = objectOrNull(raw) ?: return Parsed.Fail("", "", ERR_INVALID)
        val op = obj.string("op") ?: return Parsed.Fail("", "", ERR_INVALID)
        if (op !in OPS) return Parsed.Fail(op, "", ERR_UNSUPPORTED)
        val requestId = obj.string("requestId")?.takeIf { it.length in 1..64 } ?: return Parsed.Fail(op, "", ERR_INVALID)
        val allowed = allowedKeys(op)
        if (obj.keys.any { it !in allowed }) return Parsed.Fail(op, requestId, ERR_INVALID)
        if (obj.long("protocol_major")?.let { it != BRIDGE_PROTOCOL_MAJOR.toLong() } == true) {
            return Parsed.Fail(op, requestId, ERR_INVALID)
        }
        if (obj.long("protocol_minor")?.let { it != BRIDGE_PROTOCOL_MINOR.toLong() } == true) {
            return Parsed.Fail(op, requestId, ERR_INVALID)
        }
        val nonce = normalizeNonce(obj.string("nonce")) ?: return Parsed.Fail(op, requestId, ERR_INVALID)
        val timestamp = obj.long("timestampMs") ?: return Parsed.Fail(op, requestId, ERR_INVALID)
        if (timestamp < 0) return Parsed.Fail(op, requestId, ERR_INVALID)
        val ttl = if ("ttlMs" in obj) {
            obj.long("ttlMs")?.takeIf { it in 1..MAX_TTL_MS } ?: return Parsed.Fail(op, requestId, ERR_INVALID)
        } else {
            DEFAULT_TTL_MS
        }
        val destructive = if ("destructive" in obj) {
            obj.bool("destructive") ?: return Parsed.Fail(op, requestId, ERR_INVALID)
        } else {
            false
        }
        if ("trigger" in obj && obj["trigger"] !is JsonObject) return Parsed.Fail(op, requestId, ERR_INVALID)
        if ("targetNodeRef" in obj) {
            val target = obj.string("targetNodeRef") ?: return Parsed.Fail(op, requestId, ERR_INVALID)
            if (target.length > 200) return Parsed.Fail(op, requestId, ERR_INVALID)
        }
        val params = readParams(obj["params"]) ?: return Parsed.Fail(op, requestId, ERR_INVALID)
        val actionRef = if ("actionRef" in obj) {
            obj.string("actionRef")?.takeIf { it.length in 1..80 } ?: return Parsed.Fail(op, requestId, ERR_INVALID)
        } else {
            ""
        }
        val macroId = if (op == OP_MACRO_STEP || op == OP_MACRO_RUN) {
            obj.string("macroId")?.takeIf { it.matches(Regex("[A-Za-z0-9._:-]{1,64}")) }
                ?: return Parsed.Fail(op, requestId, ERR_INVALID)
        } else {
            ""
        }
        val stepIndex = if (op == OP_MACRO_STEP) {
            obj.long("stepIndex")?.takeIf { it in 0..10_000 }?.toInt() ?: return Parsed.Fail(op, requestId, ERR_INVALID)
        } else {
            null
        }
        val onError = if (op == OP_MACRO_STEP) {
            if ("onError" in obj) {
                obj.string("onError")?.takeIf { it == "abort" || it == "continue" }
                    ?: return Parsed.Fail(op, requestId, ERR_INVALID)
            } else {
                "abort"
            }
        } else {
            "abort"
        }
        val dryRun = if (op == OP_MACRO_RUN && "dryRun" in obj) {
            obj.bool("dryRun") ?: return Parsed.Fail(op, requestId, ERR_INVALID)
        } else {
            false
        }
        val steps = if (op == OP_MACRO_RUN) {
            readSteps(obj["steps"]) ?: return Parsed.Fail(op, requestId, ERR_INVALID)
        } else {
            emptyList()
        }
        if (op == OP_COMMAND && actionRef.isEmpty()) return Parsed.Fail(op, requestId, ERR_INVALID)
        if (op == OP_MACRO_STEP && actionRef.isEmpty()) return Parsed.Fail(op, requestId, ERR_INVALID)
        return Parsed.Ok(
            Request(
                op, requestId, actionRef, params, nonce, timestamp, ttl, destructive,
                macroId, stepIndex, onError, steps, dryRun,
            ),
        )
    }

    private fun allowedKeys(op: String): Set<String> {
        val common = setOf(
            "op", "requestId", "targetNodeRef", "actionRef", "params", "nonce", "timestampMs", "ttlMs",
            "destructive", "trigger", "protocol_major", "protocol_minor",
        )
        return when (op) {
            OP_MACRO_STEP -> common + setOf("macroId", "stepIndex", "onError")
            OP_MACRO_RUN -> common + setOf("macroId", "steps", "dryRun")
            else -> common
        }
    }

    private fun readParams(element: kotlinx.serialization.json.JsonElement?): Map<String, JsonPrimitive>? {
        if (element == null) return emptyMap()
        val obj = element as? JsonObject ?: return null
        if (obj.size > 8) return null
        val out = LinkedHashMap<String, JsonPrimitive>()
        for ((key, value) in obj) {
            val primitive = value as? JsonPrimitive ?: return null
            if (key.length !in 1..32) return null
            out[key] = primitive
        }
        return out
    }

    private fun readSteps(element: kotlinx.serialization.json.JsonElement?): List<Step>? {
        val array = element as? JsonArray ?: return null
        if (array.isEmpty() || array.size > MAX_STEPS) return null
        return array.map { item ->
            val obj = item as? JsonObject ?: return null
            if (obj.keys.any { it !in setOf("actionRef", "params", "destructive", "onError") }) return null
            val action = obj.string("actionRef")?.takeIf { it.length in 1..80 } ?: return null
            val params = readParams(obj["params"]) ?: return null
            val destructive = if ("destructive" in obj) obj.bool("destructive") ?: return null else false
            val onError = if ("onError" in obj) {
                obj.string("onError")?.takeIf { it == "abort" || it == "continue" } ?: return null
            } else {
                "abort"
            }
            Step(action, params, destructive, onError)
        }
    }

    private fun objectOrNull(raw: String?): JsonObject? {
        if (raw.isNullOrBlank() || raw.length > MAX_BODY) return null
        return try {
            val element = kotlinx.serialization.json.Json.parseToJsonElement(raw)
            element as? JsonObject
        } catch (_: Exception) {
            null
        }
    }

    private fun normalizeNonce(raw: String?): String? {
        if (raw.isNullOrBlank() || raw.length > 36) return null
        val hex = raw.uppercase().filter { it in '0'..'9' || it in 'A'..'F' }
        if (hex.length != 32 || hex.length + raw.count { it == '-' } != raw.length) return null
        return hex
    }

    private fun JsonObject.string(name: String): String? {
        val value = this[name] as? JsonPrimitive ?: return null
        if (!value.isString) return null
        return value.content
    }

    private fun JsonObject.long(name: String): Long? {
        val value = this[name] as? JsonPrimitive ?: return null
        if (value.isString) return null
        return value.longOrNull
    }

    private fun JsonObject.bool(name: String): Boolean? {
        val value = this[name] as? JsonPrimitive ?: return null
        if (value.isString) return null
        return value.booleanOrNull
    }

    private fun stepJson(index: Int, actionRef: String, error: String) = buildJsonObject {
        put("index", index)
        put("actionRef", actionRef)
        put("ok", error.isEmpty())
        put("error", error)
    }

    private fun reply(
        op: String,
        requestId: String,
        ok: Boolean,
        error: String,
        capabilities: JsonArray? = null,
        result: JsonObject? = null,
        steps: JsonArray? = null,
    ): String {
        val body = buildJsonObject {
            put("protocol_major", BRIDGE_PROTOCOL_MAJOR)
            put("protocol_minor", BRIDGE_PROTOCOL_MINOR)
            put("op", op)
            put("requestId", requestId)
            put("ok", ok)
            put("error", error)
            if (capabilities != null) put("capabilities", capabilities)
            if (result != null) put("result", result)
            if (steps != null) put("steps", steps)
        }
        return kotlinx.serialization.json.Json.encodeToString(JsonObject.serializer(), body)
    }
}
