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

package dev.patrickgold.florisboard.ime.media

import dev.patrickgold.florisboard.lib.fleet.FleetAccess
import dev.patrickgold.florisboard.lib.fleet.FleetGrant
import dev.patrickgold.florisboard.lib.fleet.FleetGrants
import dev.patrickgold.florisboard.lib.fleet.FleetTrust
import dev.patrickgold.florisboard.lib.fleet.CertDigest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/**
 * Media buttons. The numbers match `KeyEvent` and `AudioManager` so the shell can pass them through.
 * Nothing here reads the editor, the clipboard, or the vault, and nothing is logged.
 */
enum class MediaCommand {
    PLAY_PAUSE,
    PREVIOUS,
    NEXT,
    REWIND,
    FAST_FORWARD,
    VOLUME_DOWN,
    VOLUME_UP,
}

object MediaCodes {
    const val PLAY_PAUSE = 85
    const val NEXT = 87
    const val PREVIOUS = 88
    const val REWIND = 89
    const val FAST_FORWARD = 90
    const val ACTION_DOWN = 0
    const val ACTION_UP = 1
    const val STREAM_MUSIC = 3
    const val ADJUST_LOWER = -1
    const val ADJUST_RAISE = 1
    const val FLAG_SHOW_UI = 1
    const val SEEK_STEP_MS = 10_000L

    fun keyCode(command: MediaCommand): Int? = when (command) {
        MediaCommand.PLAY_PAUSE -> PLAY_PAUSE
        MediaCommand.PREVIOUS -> PREVIOUS
        MediaCommand.NEXT -> NEXT
        MediaCommand.REWIND -> REWIND
        MediaCommand.FAST_FORWARD -> FAST_FORWARD
        MediaCommand.VOLUME_DOWN, MediaCommand.VOLUME_UP -> null
    }

    /** Down, then up. Volume is not a media key. */
    fun presses(keyCode: Int): List<Pair<Int, Int>> = listOf(ACTION_DOWN to keyCode, ACTION_UP to keyCode)
}

/**
 * Victorious File Manager's media bridge. The keyboard talks to it only after the user approves that
 * package's certificate and turns File Manager seek on. Otherwise every button is a media key.
 */
object FileManagerProtocol {
    const val PACKAGE = "ai.victorious.filemanager"
    const val PACKAGE_DEBUG = "ai.victorious.filemanager.debug"
    const val ACTION = "ai.victorious.filemanager.action.BRIDGE"
    const val NO_SESSION = "NO_SESSION"
    const val WHAT_REQUEST = 1
    const val WHAT_RESPONSE = 2
    const val KEY_REQUEST = "request"
    const val KEY_RESPONSE = "response"
    val OPS = listOf(
        "media.play",
        "media.pause",
        "media.toggle",
        "media.next",
        "media.prev",
        "media.seek",
        "media.seekBy",
        "media.volume",
        "media.state",
    )
    private const val MAX_TITLE = 120

    private val json = Json { ignoreUnknownKeys = true }

    fun allowed(packageName: String): Boolean = packageName == PACKAGE || packageName == PACKAGE_DEBUG

    /**
     * Seek may use the bridge only when the switch is on and this exact certificate was approved.
     * An empty release certificate never auto-trusts File Manager.
     */
    fun trusted(packageName: String, certSha256: String, grant: FleetGrant?, seekEnabled: Boolean): Boolean {
        if (!seekEnabled || !allowed(packageName)) return false
        return FleetTrust.access(certSha256, "", grant) == FleetAccess.Active
    }

    /** The first approved package, release before debug when both are in [installed] in that order. */
    fun bridgePackage(
        installed: List<Pair<String, String>>,
        grants: List<FleetGrant>,
        seekEnabled: Boolean,
    ): String? {
        if (!seekEnabled) return null
        return installed.firstOrNull { (pkg, cert) ->
            trusted(pkg, cert, FleetGrants.find(grants, pkg), seekEnabled = true)
        }?.first
    }

    fun anyTrusted(installed: List<Pair<String, String>>, grants: List<FleetGrant>, seekEnabled: Boolean): Boolean {
        return bridgePackage(installed, grants, seekEnabled) != null
    }

    /** Pins this certificate. Refuses any other package, and a fingerprint that is not 32 bytes. */
    fun pin(grants: List<FleetGrant>, packageName: String, certSha256: String): List<FleetGrant>? {
        if (!allowed(packageName)) return null
        if (CertDigest.normalize(certSha256).length != 64) return null
        return FleetGrants.approve(grants, packageName, certSha256)
    }

    /**
     * A request body, or null when the op or its argument is not in the contract.
     * `media.volume` accepts levels 0 through 100. `media.seek` needs a position at or after 0.
     */
    fun encode(
        op: String,
        id: String,
        positionMs: Long? = null,
        deltaMs: Long? = null,
        level: Int? = null,
    ): String? {
        if (op !in OPS || id.isBlank()) return null
        if (op == "media.seek" && (positionMs == null || positionMs < 0)) return null
        if (op != "media.seek" && positionMs != null) return null
        if (op == "media.seekBy" && deltaMs == null) return null
        if (op != "media.seekBy" && deltaMs != null) return null
        if (op == "media.volume" && (level == null || level !in 0..100)) return null
        if (op != "media.volume" && level != null) return null
        return body(op, id, positionMs, deltaMs, level)
    }

    fun body(
        op: String,
        id: String,
        positionMs: Long? = null,
        deltaMs: Long? = null,
        level: Int? = null,
    ): String {
        val obj = buildJsonObject {
            put("protocol_major", 1)
            put("op", op)
            put("id", id.take(64))
            if (positionMs != null) put("positionMs", positionMs)
            if (deltaMs != null) put("deltaMs", deltaMs)
            if (level != null) put("level", level)
        }
        return json.encodeToString(JsonObject.serializer(), obj)
    }

    fun opFor(command: MediaCommand): String? = when (command) {
        MediaCommand.PLAY_PAUSE -> "media.toggle"
        MediaCommand.PREVIOUS -> "media.prev"
        MediaCommand.NEXT -> "media.next"
        MediaCommand.REWIND -> "media.seekBy"
        MediaCommand.FAST_FORWARD -> "media.seekBy"
        MediaCommand.VOLUME_DOWN, MediaCommand.VOLUME_UP -> null
    }

    fun request(command: MediaCommand, id: String): String? {
        val op = opFor(command) ?: return null
        val delta = when (command) {
            MediaCommand.REWIND -> -MediaCodes.SEEK_STEP_MS
            MediaCommand.FAST_FORWARD -> MediaCodes.SEEK_STEP_MS
            else -> null
        }
        return encode(op, id, deltaMs = delta)
    }

    fun parse(raw: String?): FileManagerReply {
        if (raw.isNullOrBlank() || raw.length > 8_000) return FileManagerReply.Unreadable
        val obj = try {
            json.parseToJsonElement(raw) as? JsonObject
        } catch (_: Exception) {
            null
        } ?: return FileManagerReply.Unreadable
        val error = obj["error"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
        val ok = obj["ok"]?.jsonPrimitive?.booleanOrNull
        if (error != null || ok == false) return FileManagerReply.Failed(error ?: "error")
        if (obj["playing"] != null || obj["title"] != null || obj["positionMs"] != null) {
            return FileManagerReply.State(
                playing = obj["playing"]?.jsonPrimitive?.booleanOrNull ?: false,
                positionMs = obj["positionMs"]?.jsonPrimitive?.longOrNull ?: 0L,
                durationMs = obj["durationMs"]?.jsonPrimitive?.longOrNull ?: 0L,
                title = cleanTitle(obj["title"]?.jsonPrimitive?.contentOrNull.orEmpty()),
                source = cleanTitle(obj["source"]?.jsonPrimitive?.contentOrNull.orEmpty()),
            )
        }
        return if (ok == true) FileManagerReply.Ok else FileManagerReply.Unreadable
    }

    private fun cleanTitle(raw: String): String =
        raw.filter { it == '\n' || it == '\t' || !it.isISOControl() }.trim().take(MAX_TITLE)
}

sealed interface FileManagerReply {
    data object Ok : FileManagerReply
    data object Unreadable : FileManagerReply
    data class Failed(val error: String) : FileManagerReply
    data class State(
        val playing: Boolean,
        val positionMs: Long,
        val durationMs: Long,
        val title: String,
        val source: String,
    ) : FileManagerReply
}

sealed interface MediaPlan {
    data class Keys(val keyCode: Int) : MediaPlan
    data class Volume(val direction: Int) : MediaPlan
    data class Bridge(val body: String, val fallback: MediaPlan?) : MediaPlan
}

sealed interface MediaOutcome {
    data object Keys : MediaOutcome
    data object Volume : MediaOutcome
    data class Bridged(val state: FileManagerReply.State?) : MediaOutcome
    data class FellBack(val error: String) : MediaOutcome
    data class NoSession(val error: String) : MediaOutcome
    data object Unavailable : MediaOutcome
}

/** Records what the shell would send. Tests use this; the Android shell is a different implementation of the same calls. */
interface MediaSink {
    fun press(action: Int, keyCode: Int)
    fun volume(stream: Int, direction: Int, flags: Int)
    fun exchange(body: String): String?
}

object MediaControl {
    /**
     * Volume buttons always adjust the music stream. Transport buttons use File Manager only when
     * seek is on and the certificate is approved. A bridge command that has a media key falls back to that key.
     */
    fun plan(command: MediaCommand, useBridge: Boolean, id: String): MediaPlan {
        val direction = when (command) {
            MediaCommand.VOLUME_DOWN -> MediaCodes.ADJUST_LOWER
            MediaCommand.VOLUME_UP -> MediaCodes.ADJUST_RAISE
            else -> null
        }
        if (direction != null) return MediaPlan.Volume(direction)
        val key = MediaCodes.keyCode(command)?.let { MediaPlan.Keys(it) }
        if (!useBridge) return key ?: MediaPlan.Volume(0)
        val body = FileManagerProtocol.request(command, id) ?: return key ?: MediaPlan.Volume(0)
        return MediaPlan.Bridge(body, key)
    }

    fun run(plan: MediaPlan, sink: MediaSink): MediaOutcome = when (plan) {
        is MediaPlan.Keys -> {
            sendKey(plan.keyCode, sink)
            MediaOutcome.Keys
        }
        is MediaPlan.Volume -> {
            sink.volume(MediaCodes.STREAM_MUSIC, plan.direction, MediaCodes.FLAG_SHOW_UI)
            MediaOutcome.Volume
        }
        is MediaPlan.Bridge -> {
            val reply = try {
                FileManagerProtocol.parse(sink.exchange(plan.body))
            } catch (_: Exception) {
                FileManagerReply.Unreadable
            }
            when (reply) {
                is FileManagerReply.State -> MediaOutcome.Bridged(reply)
                FileManagerReply.Ok -> MediaOutcome.Bridged(null)
                is FileManagerReply.Failed -> fallback(plan.fallback, reply.error, sink)
                FileManagerReply.Unreadable -> fallback(plan.fallback, "unreadable", sink)
            }
        }
    }

    private fun fallback(plan: MediaPlan?, error: String, sink: MediaSink): MediaOutcome {
        if (plan == null) {
            return if (error == FileManagerProtocol.NO_SESSION) MediaOutcome.NoSession(error) else MediaOutcome.Unavailable
        }
        run(plan, sink)
        return if (error == FileManagerProtocol.NO_SESSION) MediaOutcome.FellBack(error) else MediaOutcome.FellBack(error)
    }

    private fun sendKey(keyCode: Int, sink: MediaSink) {
        for ((action, code) in MediaCodes.presses(keyCode)) sink.press(action, code)
    }
}
