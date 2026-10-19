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

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Typing and key presses an allowed hub app may ask for through the bridge. Pure Kotlin: the Android shell supplies a
 * [Port] and this object decides everything else (caller, switch, size, shape, field).
 *
 * Deliberately narrow: insert text at the caret, or press Enter, Tab, an arrow, or Ctrl plus a letter. There is no
 * operation that reads the editor and none that runs a toolbar button. Neither requests nor replies are logged, and a
 * reply never repeats the text or the key.
 */
object BridgeInput {
    /** Whole request, in characters. Larger requests are refused before they are parsed. */
    const val MAX_REQUEST_CHARS = 16_000

    /** Text per request. Six times this still fits [MAX_REQUEST_CHARS] when every character is JSON-escaped. */
    const val MAX_TEXT_CHARS = 2_000
    const val MAX_KEY_REPEAT = 20
    const val MAX_ID_CHARS = 64

    const val ERROR_TOO_LARGE = "request_too_large"
    const val ERROR_INVALID = "invalid_request"
    const val ERROR_UNSUPPORTED_OP = "unsupported_op"
    const val ERROR_UNAVAILABLE = "keyboard_unavailable"
    const val ERROR_NO_FIELD = "no_active_field"
    const val ERROR_SENSITIVE = "sensitive_field"
    const val ERROR_FAILED = "input_failed"

    enum class Key { ENTER, TAB, UP, DOWN, LEFT, RIGHT }

    sealed interface Action {
        data class Text(val text: String) : Action

        /** Exactly one of [key] and [letter] is set. [letter] is `a`..`z` and always comes with [ctrl]. */
        data class Press(
            val key: Key?,
            val letter: Char?,
            val ctrl: Boolean,
            val count: Int,
        ) : Action
    }

    /** What the keyboard can say about the editor without reading its contents. */
    data class FieldState(
        val keyboardUp: Boolean,
        val hasField: Boolean,
        /** Password-like field. */
        val sensitive: Boolean,
    )

    /** Bridge to the running keyboard. All three calls happen on the thread that owns the editor. */
    interface Port {
        fun imeRunning(): Boolean
        fun field(): FieldState
        fun perform(action: Action): Boolean
    }

    sealed interface Parsed {
        data class Ok(val action: Action) : Parsed
        data class Fail(val error: String) : Parsed
    }

    private val lenient = Json { ignoreUnknownKeys = true }
    private val strict = Json { ignoreUnknownKeys = false }

    private val keyNames = mapOf(
        "enter" to Key.ENTER,
        "tab" to Key.TAB,
        "up" to Key.UP,
        "down" to Key.DOWN,
        "left" to Key.LEFT,
        "right" to Key.RIGHT,
    )

    /**
     * Answers one bridge request. Order matters: caller and switch first (so a stranger learns nothing about request
     * shapes), then size, then shape, then the keyboard, the field, and only then the edit.
     */
    fun handle(raw: String?, authorized: Boolean, controlEnabled: Boolean, port: Port): String {
        val op = BridgeProtocol.op(raw)
        if (!authorized || !controlEnabled) {
            return BridgeProtocol.response(op, authorized, controlEnabled, imeRunning = false)
        }
        if (raw != null && raw.length > MAX_REQUEST_CHARS) {
            return BridgeProtocol.inputResponse(op, ERROR_TOO_LARGE, imeRunning = safeRunning(port))
        }
        val realOp = rawOp(raw)
        return when (realOp) {
            BridgeProtocol.OP_CAPABILITIES, BridgeProtocol.OP_STATUS ->
                BridgeProtocol.response(realOp, authorized = true, controlEnabled = true, imeRunning = safeRunning(port))
            BridgeProtocol.OP_INPUT_TEXT, BridgeProtocol.OP_INPUT_KEY -> input(realOp, raw, port)
            "" -> BridgeProtocol.inputResponse("", ERROR_INVALID, safeRunning(port))
            else -> BridgeProtocol.inputResponse("", ERROR_UNSUPPORTED_OP, safeRunning(port))
        }
    }

    private fun input(op: String, raw: String?, port: Port): String {
        val action = when (val parsed = parse(raw)) {
            is Parsed.Fail -> return BridgeProtocol.inputResponse(op, parsed.error, safeRunning(port))
            is Parsed.Ok -> parsed.action
        }
        val state = try {
            port.field()
        } catch (e: Exception) {
            return BridgeProtocol.inputResponse(op, ERROR_FAILED, imeRunning = false)
        }
        val error = when {
            !state.keyboardUp -> ERROR_UNAVAILABLE
            !state.hasField -> ERROR_NO_FIELD
            state.sensitive -> ERROR_SENSITIVE
            else -> {
                val done = try {
                    port.perform(action)
                } catch (e: Exception) {
                    false
                }
                if (done) "" else ERROR_FAILED
            }
        }
        return BridgeProtocol.inputResponse(op, error, imeRunning = state.keyboardUp)
    }

    /** Parses and validates an input request. Unknown JSON fields are refused, not ignored. */
    fun parse(raw: String?): Parsed {
        if (raw.isNullOrBlank()) return Parsed.Fail(ERROR_INVALID)
        if (raw.length > MAX_REQUEST_CHARS) return Parsed.Fail(ERROR_TOO_LARGE)
        val request = try {
            if (!typesExact(strict.parseToJsonElement(raw))) return Parsed.Fail(ERROR_INVALID)
            strict.decodeFromString<InputRequest>(raw)
        } catch (e: Throwable) {
            return Parsed.Fail(ERROR_INVALID)
        }
        if (request.protocol_major != BridgeProtocol.PROTOCOL_MAJOR) return Parsed.Fail(ERROR_INVALID)
        if (request.id != null && request.id.length > MAX_ID_CHARS) return Parsed.Fail(ERROR_INVALID)
        return when (request.op) {
            BridgeProtocol.OP_INPUT_TEXT -> parseText(request)
            BridgeProtocol.OP_INPUT_KEY -> parseKey(request)
            else -> Parsed.Fail(ERROR_UNSUPPORTED_OP)
        }
    }

    private fun parseText(request: InputRequest): Parsed {
        val text = request.text ?: return Parsed.Fail(ERROR_INVALID)
        if (request.key != null || request.ctrl != null || request.count != null) return Parsed.Fail(ERROR_INVALID)
        if (text.isEmpty()) return Parsed.Fail(ERROR_INVALID)
        if (text.length > MAX_TEXT_CHARS) return Parsed.Fail(ERROR_TOO_LARGE)
        if (!validText(text)) return Parsed.Fail(ERROR_INVALID)
        return Parsed.Ok(Action.Text(text))
    }

    private fun parseKey(request: InputRequest): Parsed {
        if (request.text != null) return Parsed.Fail(ERROR_INVALID)
        val name = request.key?.lowercase() ?: return Parsed.Fail(ERROR_INVALID)
        val ctrl = request.ctrl ?: false
        val count = request.count ?: 1
        if (count < 1 || count > MAX_KEY_REPEAT) return Parsed.Fail(ERROR_INVALID)
        val named = keyNames[name]
        if (named != null) return Parsed.Ok(Action.Press(named, null, ctrl, count))
        // A bare letter is text; only Ctrl turns it into a key press.
        val letter = name.singleOrNull()
        if (letter != null && letter in 'a'..'z' && ctrl) return Parsed.Ok(Action.Press(null, letter, true, count))
        return Parsed.Fail(ERROR_INVALID)
    }

    /** The decoder coerces quoted numbers and bare words; the bridge does not. Strings must be strings, the rest not. */
    private fun typesExact(element: JsonElement): Boolean {
        val obj = element as? JsonObject ?: return false
        return obj.all { (name, value) ->
            val p = value as? JsonPrimitive ?: return@all false
            when (name) {
                "op", "id", "text", "key" -> p.isString
                else -> !p.isString
            }
        }
    }

    /** Printable text plus newline and tab. No NUL or other control characters, no unpaired surrogates. */
    internal fun validText(text: String): Boolean {
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                c == '\n' || c == '\t' -> Unit
                c.isISOControl() -> return false
                Character.isHighSurrogate(c) -> {
                    if (i + 1 >= text.length || !Character.isLowSurrogate(text[i + 1])) return false
                    i++
                }
                Character.isLowSurrogate(c) -> return false
            }
            i++
        }
        return true
    }

    private fun rawOp(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        return try {
            lenient.decodeFromString<OpOnly>(raw).op
        } catch (e: Throwable) {
            ""
        }
    }

    private fun safeRunning(port: Port): Boolean = try {
        port.imeRunning()
    } catch (e: Exception) {
        false
    }

    @Serializable
    private data class OpOnly(val op: String = "")

    @Serializable
    private data class InputRequest(
        val protocol_major: Int = 0,
        val op: String = "",
        val id: String? = null,
        val text: String? = null,
        val key: String? = null,
        val ctrl: Boolean? = null,
        val count: Int? = null,
    )
}
