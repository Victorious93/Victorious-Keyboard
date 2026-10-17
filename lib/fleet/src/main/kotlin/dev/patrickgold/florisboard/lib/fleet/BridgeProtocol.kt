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

/**
 * Status bridge the keyboard hosts for the Droidcommand hub.
 * Replies never include typed text or keystrokes.
 */
object BridgeProtocol {
    const val PROTOCOL_MAJOR = 1
    const val ACTION = "com.victorious.keyboard.action.BRIDGE"
    const val WHAT_REQUEST = 1
    const val WHAT_RESPONSE = 2
    const val KEY_REQUEST = "request"
    const val KEY_RESPONSE = "response"
    const val OP_CAPABILITIES = "keyboard.capabilities.get"
    const val OP_STATUS = "keyboard.status"
    const val ERROR_DISABLED = "external_control_disabled"
    const val ERROR_UNAUTHORIZED = "unauthorized_caller"
    const val FLEET_CLASS = "external_node"

    val allowedPackages = setOf("ai.droidcommand.app", "ai.droidcommand.app.debug")

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun op(raw: String?): String {
        if (raw.isNullOrBlank() || raw.length > 8_000) return ""
        return try {
            json.decodeFromString<Request>(raw).op
        } catch (e: Throwable) {
            ""
        }
    }

    /** Every package on the calling UID must be an allowed hub package, and each must have a certificate. */
    fun authorized(packages: List<String>, certificatesPresent: Boolean): Boolean {
        if (!certificatesPresent || packages.isEmpty()) return false
        return packages.all { it in allowedPackages }
    }

    fun response(op: String, authorized: Boolean, controlEnabled: Boolean, imeRunning: Boolean): String {
        val live = authorized && controlEnabled && imeRunning
        val mode = if (live) "live" else "stub"
        val error = when {
            !authorized -> ERROR_UNAUTHORIZED
            !controlEnabled -> ERROR_DISABLED
            op != OP_CAPABILITIES && op != OP_STATUS -> ""
            else -> ""
        }
        val ok = error.isEmpty() && (op == OP_CAPABILITIES || op == OP_STATUS)
        val body = Response(
            protocol_major = PROTOCOL_MAJOR,
            live = live,
            mode = mode,
            dca_required = false,
            fleet_class = FLEET_CLASS,
            op = op,
            ok = ok,
            error = error,
            capabilities = if (ok && op == OP_CAPABILITIES) listOf(OP_CAPABILITIES, OP_STATUS) else emptyList(),
            external_control = controlEnabled && authorized,
            ime_running = if (ok && op == OP_STATUS) imeRunning else false,
        )
        return json.encodeToString(body)
    }

    @Serializable
    private data class Request(val protocol_major: Int = 0, val op: String = "")

    @Serializable
    private data class Response(
        val protocol_major: Int,
        val live: Boolean,
        val mode: String,
        val dca_required: Boolean,
        val fleet_class: String,
        val op: String,
        val ok: Boolean,
        val error: String,
        val capabilities: List<String>,
        val external_control: Boolean,
        val ime_running: Boolean,
    )
}
