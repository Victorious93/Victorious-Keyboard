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
 * JSON documents a provider returns. A document that cannot be read becomes an
 * empty list. It is never logged.
 */
object FleetCodec {
    private const val MAX_DOCUMENT = 65_536
    private val json = Json { ignoreUnknownKeys = true }
    private val namePattern = Regex("[a-z][a-z0-9_-]{0,31}")

    fun commands(raw: String?): List<FleetAdvertised> {
        val parsed = decode<RawCommand>(raw) ?: return emptyList()
        val out = ArrayList<FleetAdvertised>()
        for (row in parsed) {
            val name = row.name.lowercase()
            if (!namePattern.matches(name)) continue
            out.add(FleetAdvertised(name, plain(row.description, 120, allowNewlines = false)))
            if (out.size >= 40) break
        }
        return out
    }

    fun actions(raw: String?): List<FleetAction> {
        val parsed = decode<RawAction>(raw) ?: return emptyList()
        val out = ArrayList<FleetAction>()
        val seen = HashSet<String>()
        for (row in parsed) {
            val name = row.name.lowercase()
            if (!namePattern.matches(name) || !seen.add(name)) continue
            val label = plain(row.label, 48, allowNewlines = false).ifEmpty { name }
            out.add(FleetAction(name, label))
            if (out.size >= 40) break
        }
        return out
    }

    fun snippets(raw: String?): List<FleetSnippet> {
        val parsed = decode<RawSnippet>(raw) ?: return emptyList()
        val out = ArrayList<FleetSnippet>()
        for ((index, row) in parsed.withIndex()) {
            val label = plain(row.label, 48, allowNewlines = false)
            if (label.isEmpty()) continue
            val body = plain(row.text, 4_000, allowNewlines = true)
            val command = plain(row.command, 64, allowNewlines = false)
            if (body.isEmpty() && command.isEmpty()) continue
            val id = row.id.takeIf { it.matches(Regex("[A-Za-z0-9_-]{1,64}")) } ?: "s$index"
            out.add(
                FleetSnippet(
                    id = id,
                    label = label,
                    kind = kindOf(row.kind),
                    text = body,
                    command = command,
                    args = row.args.take(8).map { plain(it, 200, allowNewlines = false) },
                ),
            )
            if (out.size >= 40) break
        }
        return out
    }

    fun plain(text: String, max: Int, allowNewlines: Boolean): String {
        val out = StringBuilder()
        for (ch in text) {
            val keep = ch == '\t' || (allowNewlines && ch == '\n') || !ch.isISOControl()
            if (keep) out.append(ch)
            if (out.length >= max) break
        }
        return out.toString().trim()
    }

    private inline fun <reified T> decode(raw: String?): List<T>? {
        if (raw.isNullOrBlank() || raw.length > MAX_DOCUMENT) return null
        return try {
            json.decodeFromString<List<T>>(raw)
        } catch (e: Throwable) {
            null
        }
    }

    private fun kindOf(raw: String): FleetSnippetKind = when (raw.lowercase()) {
        "adb" -> FleetSnippetKind.ADB
        "fastboot" -> FleetSnippetKind.FASTBOOT
        "termux" -> FleetSnippetKind.TERMUX
        else -> FleetSnippetKind.OTHER
    }

    @Serializable
    private data class RawCommand(
        val name: String = "",
        val description: String = "",
    )

    @Serializable
    private data class RawAction(
        val name: String = "",
        val label: String = "",
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
    /** Reads every endpoint. Any failure leaves that app disconnected and sends nothing. */
    fun load(enabled: Boolean, endpoints: List<FleetEndpoint>): FleetSnapshot {
        if (!enabled || endpoints.isEmpty()) return FleetSnapshot.absent()
        val rows = endpoints.take(FleetDiscovery.MAX_PROVIDERS).map { read(it) }
        return FleetSnapshot(assign(rows))
    }

    private data class Read(
        val packageName: String,
        val title: String,
        val declaredNamespace: String,
        val connected: Boolean,
        val iconPng: ByteArray?,
        val commands: List<FleetAdvertised>,
        val snippets: List<FleetSnippet>,
        val actions: List<FleetAction>,
    )

    private fun read(endpoint: FleetEndpoint): Read {
        val provider = endpoint.provider ?: return disconnected(endpoint.packageName)
        val alive = try {
            provider.ping()
        } catch (e: Exception) {
            false
        }
        if (!alive) return disconnected(endpoint.packageName)
        val title = try {
            FleetCodec.plain(provider.providerName(), 48, allowNewlines = false).ifEmpty { tail(endpoint.packageName) }
        } catch (e: Exception) {
            tail(endpoint.packageName)
        }
        val namespace = try {
            provider.namespace()
        } catch (e: Exception) {
            ""
        }
        val commands = try {
            FleetCodec.commands(provider.slashCommands())
        } catch (e: Exception) {
            emptyList()
        }
        val snippets = try {
            FleetCodec.snippets(provider.snippets())
        } catch (e: Exception) {
            emptyList()
        }
        val actions = try {
            FleetCodec.actions(provider.actions())
        } catch (e: Exception) {
            emptyList()
        }
        val icon = try {
            FleetIcons.keep(provider.icon())
        } catch (e: Exception) {
            null
        }
        return Read(endpoint.packageName, title, namespace, true, icon, commands, snippets, actions)
    }

    private fun disconnected(packageName: String): Read = Read(
        packageName = packageName,
        title = tail(packageName),
        declaredNamespace = "",
        connected = false,
        iconPng = null,
        commands = emptyList(),
        snippets = emptyList(),
        actions = emptyList(),
    )

    private fun tail(packageName: String): String {
        val raw = packageName.substringAfterLast('.').ifEmpty { packageName }
        return FleetCodec.plain(raw, 32, allowNewlines = false).ifEmpty { "App" }
    }

    private fun assign(rows: List<Read>): List<FleetApp> {
        val taken = HashSet<String>()
        return rows.map { row ->
            val namespace = if (row.connected) {
                val token = FleetNamespaces.tokenFor(row.declaredNamespace, row.title, row.packageName, taken)
                taken.add(token)
                token
            } else {
                ""
            }
            FleetApp(
                packageName = row.packageName,
                title = row.title,
                namespace = namespace,
                connected = row.connected,
                iconPng = row.iconPng,
                commands = row.commands,
                snippets = row.snippets,
                actions = row.actions,
            )
        }
    }
}
