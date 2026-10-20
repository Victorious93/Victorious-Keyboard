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

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Keyboard preferences backup. Themes, layouts, keyboard settings, and the toolbar layout only.
 * Clipboard history, typed text, the vault, and snippet bodies are not entries. A remote bridge
 * caller is refused by [RemoteEnvelope]; this object is the local codec.
 *
 * Import never turns external control on. A real import forces that switch off.
 */
object ConfigBackup {
    const val BUNDLE_VERSION = 1
    const val SCHEMA_VERSION = 1
    const val EXTERNAL_CONTROL = "fleet__external_control"
    const val TOOLBAR_ID = "toolbar.layout"
    private const val MAX_ROWS = 200
    private const val MAX_VALUE = 20_000

    private val json = Json { ignoreUnknownKeys = true }
    private val allowPrefixes = listOf("theme__", "keyboard__", "smartbar__", "localization__", "devrow__")
    private val denyKeys = setOf("devrow__macros")
    /** Preference names that are never a settings row, even under an allowed prefix. */
    private val excluded = listOf("clipboard", "vault", "snippet")

    /** Names that refuse the whole bundle. Clipboard history is excluded by key, not by this list. */
    private val secrets = listOf(
        "password", "secret", "token", "apikey", "privatekey", "signingkey",
        "whitelist", "codeword", "twilio", "cookie", "authtoken", "numberpool",
    )

    data class Outcome(
        val error: String = "",
        val wrote: Boolean = false,
        val rows: Map<String, String> = emptyMap(),
        val added: List<String> = emptyList(),
        val skipped: List<String> = emptyList(),
        val overwritten: List<String> = emptyList(),
    )

    /** Preferences that belong in a backup, plus a toolbar layout with command text removed. */
    fun select(prefs: Map<String, String>, toolbarJson: String?): Map<String, String>? {
        val out = LinkedHashMap<String, String>()
        for ((key, value) in prefs) {
            if (!exportable(key) || value.length > MAX_VALUE) continue
            if (!clean(key) || !clean(value)) return null
            out[key] = value
        }
        if (!toolbarJson.isNullOrBlank()) {
            val layout = toolbarLayout(toolbarJson) ?: return null
            out[TOOLBAR_ID] = layout
        }
        if (out.size > MAX_ROWS) return null
        out.remove(EXTERNAL_CONTROL)
        return out
    }

    /**
     * One bundle for this package. [clearPayload] is the local-dev form. Otherwise [key] is the
     * 32-byte AES-256-GCM data key and the bundle carries ciphertext only.
     */
    fun export(
        packageName: String,
        sourceNodeRef: String,
        now: Long,
        prefs: Map<String, String>,
        toolbarJson: String? = null,
        clearPayload: Boolean = false,
        key: ByteArray? = null,
    ): String? {
        if (packageName.isBlank() || sourceNodeRef.isBlank()) return null
        val rows = select(prefs, toolbarJson) ?: return null
        val payload = payloadJson(rows)
        val entry = if (clearPayload) {
            buildJsonObject {
                put("packageName", packageName)
                put("schemaVersion", SCHEMA_VERSION)
                put("clearPayload", payload)
            }
        } else {
            if (key == null || key.size != 32) return null
            val cipher = encrypt(payload, packageName, sourceNodeRef, key) ?: return null
            buildJsonObject {
                put("packageName", packageName)
                put("schemaVersion", SCHEMA_VERSION)
                put("ciphertext", cipher)
            }
        }
        val bundle = buildJsonObject {
            put("version", BUNDLE_VERSION)
            put("createdAtMs", now)
            put("sourceNodeRef", sourceNodeRef)
            put("wrapping", if (clearPayload) "none" else "raw-aes-256-gcm")
            put("entries", buildJsonArray { add(entry) })
        }
        return json.encodeToString(JsonObject.serializer(), bundle)
    }

    /**
     * Apply one bundle to [live]. Dry-run reports the diff and writes nothing. Any other import
     * needs [confirmed]. The returned rows force [EXTERNAL_CONTROL] off when they are written.
     */
    fun import(
        bundle: String,
        live: Map<String, String>,
        packageName: String,
        dryRun: Boolean = false,
        conflictPolicy: String = "skip",
        confirmed: Boolean = false,
        key: ByteArray? = null,
    ): Outcome {
        if (conflictPolicy !in setOf("skip", "overwrite", "merge-by-id")) return Outcome(RemoteEnvelope.ERR_INVALID)
        val incoming = when (val parsed = decode(bundle, packageName, key)) {
            null -> return Outcome(RemoteEnvelope.ERR_INVALID)
            Missing -> return Outcome(RemoteEnvelope.ERR_NOT_FOUND)
            Tamper -> return Outcome(RemoteEnvelope.ERR_INTERNAL)
            is Decoded -> parsed
        }
        val diff = plan(live, incoming.rows, conflictPolicy)
        if (dryRun) {
            val planned = diff.rows.toMutableMap()
            planned[EXTERNAL_CONTROL] = "false"
            return diff.copy(wrote = false, rows = planned)
        }
        if (!confirmed) return Outcome(RemoteEnvelope.ERR_CONFIRM)
        val next = diff.rows.toMutableMap()
        next[EXTERNAL_CONTROL] = "false"
        return diff.copy(wrote = true, rows = next)
    }

    private fun exportable(key: String): Boolean {
        if (key in denyKeys || key == EXTERNAL_CONTROL) return false
        if (allowPrefixes.none { key.startsWith(it) }) return false
        val folded = letters(key)
        return excluded.none { it in folded }
    }

    private fun clean(value: String): Boolean = secrets.none { it in letters(value) }

    private fun letters(value: String) = value.lowercase().filter { it.isLetter() }

    /** Layout fields only. Command text and recent commands are dropped. Null means the input was unsafe. */
    fun toolbarLayout(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val obj = try {
            json.parseToJsonElement(raw) as? JsonObject
        } catch (_: Exception) {
            return null
        } ?: return null
        val page = obj["page"]?.jsonPrimitive?.contentOrNull
        if (page != null && !clean(page)) return null
        val kept = buildJsonObject {
            for (name in listOf("pinned", "pinnedRow", "page", "hidden", "order", "bracketPairing", "shellHistoryEnabled")) {
                obj[name]?.let { put(name, it) }
            }
        }
        val text = json.encodeToString(JsonObject.serializer(), kept)
        if (!clean(text)) return null
        return text
    }

    private fun payloadJson(rows: Map<String, String>): String {
        val body = buildJsonObject {
            put("rows", buildJsonArray {
                for ((id, value) in rows) {
                    add(buildJsonObject {
                        put("id", id)
                        put("value", value)
                    })
                }
            })
        }
        return json.encodeToString(JsonObject.serializer(), body)
    }

    private sealed interface Parsed
    private data object Missing : Parsed
    private data object Tamper : Parsed
    private data class Decoded(val rows: Map<String, String>) : Parsed

    private fun decode(bundle: String, packageName: String, key: ByteArray?): Parsed? {
        if (bundle.length > RemoteEnvelope.MAX_BODY) return null
        val obj = try {
            json.parseToJsonElement(bundle) as? JsonObject
        } catch (_: Exception) {
            return null
        } ?: return null
        if (obj["version"]?.jsonPrimitive?.longOrNull != BUNDLE_VERSION.toLong()) return null
        val source = obj["sourceNodeRef"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: return null
        val wrapping = obj["wrapping"]?.jsonPrimitive?.contentOrNull ?: return null
        val entries = obj["entries"] as? JsonArray ?: return null
        val names = entries.mapNotNull { (it as? JsonObject)?.get("packageName")?.jsonPrimitive?.contentOrNull }
        if (names.size != names.toSet().size) return null
        val entry = entries.mapNotNull { it as? JsonObject }.firstOrNull {
            it["packageName"]?.jsonPrimitive?.contentOrNull == packageName
        } ?: return Missing
        val schema = entry["schemaVersion"]?.jsonPrimitive?.longOrNull ?: return null
        val clear = entry["clearPayload"]?.jsonPrimitive?.contentOrNull
        val cipher = entry["ciphertext"]?.jsonPrimitive?.contentOrNull
        if ((clear == null) == (cipher == null)) return null
        val payload = when {
            wrapping == "none" && clear != null -> clear
            wrapping == "raw-aes-256-gcm" && cipher != null && key != null && key.size == 32 ->
                when (val opened = decrypt(cipher, packageName, schema, source, key)) {
                    is Opened.Plain -> opened.text
                    Opened.Tamper -> return Tamper
                    Opened.Bad -> return null
                }
            else -> return null
        }
        return rowsOf(payload)?.let { Decoded(it) }
    }

    private fun rowsOf(payload: String): Map<String, String>? {
        val obj = try {
            json.parseToJsonElement(payload) as? JsonObject
        } catch (_: Exception) {
            return null
        } ?: return null
        val rows = obj["rows"] as? JsonArray ?: return null
        if (rows.size > MAX_ROWS) return null
        val out = LinkedHashMap<String, String>()
        for (item in rows) {
            val row = item as? JsonObject ?: return null
            val id = row["id"]?.jsonPrimitive?.contentOrNull ?: return null
            val value = (row["value"] as? JsonPrimitive)?.contentOrNull ?: return null
            if (id == EXTERNAL_CONTROL) continue
            if (value.length > MAX_VALUE) return null
            if (id == TOOLBAR_ID) {
                val layout = toolbarLayout(value) ?: return null
                out[id] = layout
                continue
            }
            if (!exportable(id) || !clean(id) || !clean(value)) return null
            out[id] = value
        }
        return out
    }

    private fun plan(live: Map<String, String>, incoming: Map<String, String>, policy: String): Outcome {
        val next = LinkedHashMap(live)
        next.remove(EXTERNAL_CONTROL)
        val added = ArrayList<String>()
        val skipped = ArrayList<String>()
        val overwritten = ArrayList<String>()
        for ((id, value) in incoming) {
            if (id == EXTERNAL_CONTROL) continue
            val current = next[id]
            when {
                current == null && policy == "skip" -> skipped += id
                current == null -> {
                    next[id] = value
                    added += id
                }
                policy == "overwrite" && current != value -> {
                    next[id] = value
                    overwritten += id
                }
                else -> skipped += id
            }
        }
        return Outcome(rows = next, added = added, skipped = skipped, overwritten = overwritten)
    }

    private fun encrypt(payload: String, packageName: String, source: String, key: ByteArray): String? {
        return try {
            val iv = ByteArray(12)
            SecureRandom().nextBytes(iv)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
            cipher.updateAAD(aad(packageName, SCHEMA_VERSION.toLong(), source))
            val out = cipher.doFinal(payload.toByteArray(Charsets.UTF_8))
            "v1." + b64(iv) + "." + b64(out)
        } catch (_: Exception) {
            null
        }
    }

    private sealed interface Opened {
        data class Plain(val text: String) : Opened
        data object Tamper : Opened
        data object Bad : Opened
    }

    private fun decrypt(record: String, packageName: String, schema: Long, source: String, key: ByteArray): Opened {
        val parts = record.split('.')
        if (parts.size != 3 || parts[0] != "v1") return Opened.Bad
        val iv = b64d(parts[1]) ?: return Opened.Bad
        val body = b64d(parts[2]) ?: return Opened.Bad
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
            cipher.updateAAD(aad(packageName, schema, source))
            Opened.Plain(String(cipher.doFinal(body), Charsets.UTF_8))
        } catch (_: Exception) {
            Opened.Tamper
        }
    }

    private fun aad(packageName: String, schema: Long, source: String): ByteArray {
        return "$packageName|$schema|$source".toByteArray(Charsets.UTF_8)
    }

    private fun b64(bytes: ByteArray) = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

    private fun b64d(text: String) = try {
        Base64.getUrlDecoder().decode(text)
    } catch (_: Exception) {
        null
    }
}
