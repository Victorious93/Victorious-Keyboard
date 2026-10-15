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
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * Provisional payloads for [FleetHub.getSnippets] and the list-commands ask.
 * TODO-confirm-with-hub: replace this parser when the hub's real document is known.
 * A document that cannot be read becomes an empty list. It is never logged.
 */
object FleetCodec {
    private const val MAX_DOCUMENT = 65_536
    private val json = Json { ignoreUnknownKeys = true }
    private val namePattern = Regex("[a-z][a-z0-9_-]{0,31}")

    fun commands(raw: String?): List<FleetCommand> {
        val text = bounded(raw) ?: return emptyList()
        val parsed = try {
            json.decodeFromString<List<RawCommand>>(text)
        } catch (e: Throwable) {
            if (e is SerializationException || e is IllegalArgumentException || e is StackOverflowError) {
                return emptyList()
            }
            return emptyList()
        }
        val out = ArrayList<FleetCommand>()
        for (row in parsed) {
            val name = row.name.lowercase()
            if (!namePattern.matches(name)) continue
            out.add(FleetCommand(name, clean(row.description, 120, allowNewlines = false)))
            if (out.size >= 40) break
        }
        return out
    }

    fun snippets(raw: String?): List<FleetSnippet> {
        val text = bounded(raw) ?: return emptyList()
        val parsed = try {
            json.decodeFromString<List<RawSnippet>>(text)
        } catch (e: Throwable) {
            return emptyList()
        }
        val out = ArrayList<FleetSnippet>()
        for ((index, row) in parsed.withIndex()) {
            val label = clean(row.label, 48, allowNewlines = false)
            if (label.isEmpty()) continue
            val body = clean(row.text, 4_000, allowNewlines = true)
            val command = clean(row.command, 64, allowNewlines = false)
            if (body.isEmpty() && command.isEmpty()) continue
            val id = row.id.takeIf { it.matches(Regex("[A-Za-z0-9_-]{1,64}")) } ?: "s$index"
            out.add(
                FleetSnippet(
                    id = id,
                    label = label,
                    kind = kindOf(row.kind),
                    text = body,
                    command = command,
                    args = row.args.take(8).map { clean(it, 200, allowNewlines = false) },
                ),
            )
            if (out.size >= 40) break
        }
        return out
    }

    private fun bounded(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        if (raw.length > MAX_DOCUMENT) return null
        return raw
    }

    private fun kindOf(raw: String): FleetSnippetKind = when (raw.lowercase()) {
        "adb" -> FleetSnippetKind.ADB
        "fastboot" -> FleetSnippetKind.FASTBOOT
        "termux" -> FleetSnippetKind.TERMUX
        else -> FleetSnippetKind.OTHER
    }

    private fun clean(text: String, max: Int, allowNewlines: Boolean): String {
        val out = StringBuilder()
        for (ch in text) {
            val keep = ch == '\t' || (allowNewlines && ch == '\n') || !ch.isISOControl()
            if (keep) out.append(ch)
            if (out.length >= max) break
        }
        return out.toString().trim()
    }

    @Serializable
    private data class RawCommand(
        val name: String = "",
        val description: String = "",
    )

    @Serializable
    private data class RawSnippet(
        val id: String = "",
        val label: String = "",
        val kind: String = "",
        val text: String = "",
        val command: String = "",
        val args: List<String> = emptyList(),
    )
}

object FleetSession {
    /** Reads the hub. Any failure leaves the keyboard usable and sends nothing. */
    fun load(enabled: Boolean, installed: Boolean, hub: FleetHub?): FleetSnapshot {
        if (!enabled || !installed || hub == null) return FleetSnapshot.absent()
        val alive = try {
            hub.ping()
        } catch (e: Exception) {
            false
        }
        if (!alive) return FleetSnapshot.installed()
        val advertised = try {
            FleetCodec.commands(hub.askHub(FleetHubContract.ACTION_LIST_COMMANDS, ""))
        } catch (e: Exception) {
            emptyList()
        }
        val snippets = try {
            FleetCodec.snippets(hub.getSnippets())
        } catch (e: Exception) {
            emptyList()
        }
        return FleetSnapshot(FleetLink.CONNECTED, advertised, snippets)
    }
}
