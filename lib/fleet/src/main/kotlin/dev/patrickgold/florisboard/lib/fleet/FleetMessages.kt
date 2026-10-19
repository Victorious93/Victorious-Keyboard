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

import java.security.MessageDigest
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Messenger protocol owned by the keyboard. A provider exports
 * [ACTION]. Requests use [WHAT_REQUEST] and Bundle key [KEY_REQUEST].
 * Replies use [WHAT_RESPONSE] and Bundle key [KEY_RESPONSE].
 * Both bodies are JSON with [PROTOCOL_MAJOR].
 */
object FleetMessages {
    const val PROTOCOL_MAJOR = 1
    const val ACTION = "com.victorious.keyboard.action.COMMAND_PROVIDER"
    const val WHAT_REQUEST = 1
    const val WHAT_RESPONSE = 2
    const val KEY_REQUEST = "request"
    const val KEY_RESPONSE = "response"
    const val OP_LIST = "commands.list"
    const val OP_RUN = "command.run"

    private const val MAX_DOCUMENT = 65_536
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun listRequest(id: String): String {
        return json.encodeToString(Request(protocol_major = PROTOCOL_MAJOR, op = OP_LIST, id = id))
    }

    fun runRequest(id: String, name: String, argument: String): String {
        return json.encodeToString(
            Request(
                protocol_major = PROTOCOL_MAJOR,
                op = OP_RUN,
                id = id,
                name = FleetCodec.plain(name, 64, allowNewlines = false),
                argument = argument.take(FleetPolicy.MAX_SEND),
            ),
        )
    }

    /** Empty [ok] means the document was refused. Nothing from it is logged. */
    fun parseList(raw: String?): Listed {
        val row = decode(raw) ?: return Listed.Empty
        if (row.protocol_major != PROTOCOL_MAJOR || !row.ok || row.op != OP_LIST) return Listed.Empty
        val commands = ArrayList<FleetAdvertised>()
        val actions = ArrayList<FleetAction>()
        val seen = HashSet<String>()
        for (command in row.commands) {
            val name = command.name.lowercase()
            if (!Regex("[a-z][a-z0-9_-]{0,31}").matches(name) || !seen.add(name)) continue
            val label = FleetCodec.plain(command.label.ifEmpty { command.description }, 48, allowNewlines = false)
                .ifEmpty { name }
            commands.add(FleetAdvertised(name, label))
            actions.add(FleetAction(name, label))
            if (commands.size >= 40) break
        }
        val snippets = ArrayList<FleetSnippet>()
        for ((index, snippet) in row.snippets.withIndex()) {
            val label = FleetCodec.plain(snippet.label.ifEmpty { snippet.name }, 48, allowNewlines = false)
            if (label.isEmpty()) continue
            val text = FleetCodec.plain(snippet.text, 4_000, allowNewlines = true)
            val command = FleetCodec.plain(
                snippet.command.ifEmpty { if (text.isEmpty()) snippet.name else "" },
                64,
                allowNewlines = false,
            )
            if (text.isEmpty() && command.isEmpty()) continue
            val id = snippet.id.takeIf { it.matches(Regex("[A-Za-z0-9_-]{1,64}")) }
                ?: snippet.name.takeIf { it.matches(Regex("[A-Za-z0-9_-]{1,64}")) }
                ?: "s$index"
            snippets.add(
                FleetSnippet(
                    id = id,
                    label = label,
                    kind = FleetSnippetKind.OTHER,
                    text = text,
                    command = command,
                    args = emptyList(),
                ),
            )
            if (snippets.size >= 40) break
        }
        return Listed(
            ok = true,
            title = FleetCodec.plain(row.title, 48, allowNewlines = false),
            namespace = FleetCodec.plain(row.namespace, 16, allowNewlines = false).lowercase(),
            commands = commands,
            snippets = snippets,
            actions = actions,
        )
    }

    fun legacyCommands(commands: List<FleetAdvertised>): String {
        return json.encodeToString(commands.map { LegacyCommand(it.name, it.description) })
    }

    fun legacyActions(actions: List<FleetAction>): String {
        return json.encodeToString(actions.map { LegacyAction(it.name, it.label) })
    }

    fun legacySnippets(snippets: List<FleetSnippet>): String {
        return json.encodeToString(
            snippets.map { LegacySnippet(it.id, it.label, it.kind.name.lowercase(), it.text, it.command, it.args) },
        )
    }

    /**
     * The `id` on a provider reply, or null when the document is refused or has none.
     * The keyboard uses it so a late reply cannot complete a newer request.
     */
    fun replyId(raw: String?): String? {
        val id = decode(raw)?.id ?: return null
        if (id.isEmpty() || id.length > 64) return null
        return id
    }

    /** Insertable text, or blank when the reply is refused. */
    fun parseRun(raw: String?): String {
        val row = decode(raw) ?: return ""
        if (row.protocol_major != PROTOCOL_MAJOR || !row.ok || row.op != OP_RUN) return ""
        return FleetCodec.plain(row.text, FleetPolicy.MAX_INSERT, allowNewlines = true)
    }

    private fun decode(raw: String?): Envelope? {
        if (raw.isNullOrBlank() || raw.length > MAX_DOCUMENT) return null
        return try {
            json.decodeFromString<Envelope>(raw)
        } catch (e: Throwable) {
            null
        }
    }

    @Serializable
    private data class LegacyCommand(val name: String, val description: String)

    @Serializable
    private data class LegacyAction(val name: String, val label: String)

    @Serializable
    private data class LegacySnippet(
        val id: String,
        val label: String,
        val kind: String,
        val text: String,
        val command: String,
        val args: List<String>,
    )

    @Serializable
    private data class Request(
        val protocol_major: Int,
        val op: String,
        val id: String,
        val name: String = "",
        val argument: String = "",
    )

    @Serializable
    private data class Envelope(
        val protocol_major: Int = 0,
        val op: String = "",
        val id: String = "",
        val ok: Boolean = false,
        val title: String = "",
        val namespace: String = "",
        val text: String = "",
        val commands: List<RawCommand> = emptyList(),
        val snippets: List<RawSnippet> = emptyList(),
    )

    @Serializable
    private data class RawCommand(
        val name: String = "",
        val label: String = "",
        val description: String = "",
    )

    @Serializable
    private data class RawSnippet(
        val id: String = "",
        val name: String = "",
        val label: String = "",
        val text: String = "",
        val command: String = "",
    )
}

data class Listed(
    val ok: Boolean,
    val title: String,
    val namespace: String,
    val commands: List<FleetAdvertised>,
    val snippets: List<FleetSnippet>,
    val actions: List<FleetAction>,
) {
    companion object {
        val Empty = Listed(false, "", "", emptyList(), emptyList(), emptyList())
    }
}

/** SHA-256 of a signing certificate, printed as colon-separated hex. */
object CertDigest {
    fun sha256(encoded: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(encoded)
        return digest.joinToString(":") { "%02X".format(it) }
    }

    fun normalize(printed: String): String {
        return printed.uppercase().filter { it in '0'..'9' || it in 'A'..'F' }
    }
}

/**
 * SHA-256 of the keyboard release signing certificate.
 * Empty until release signing exists. Do not put the debug key here.
 */
object FleetReleaseCert {
    const val SHA256 = ""
}

enum class FleetAccess {
    Active,
    Off,
    NeedsApproval,
}

data class FleetGrant(
    val packageName: String,
    val certSha256: String,
    val approved: Boolean,
    val enabled: Boolean,
)

data class FleetOffer(
    val packageName: String,
    val className: String,
    val label: String,
    val certSha256: String,
    val access: FleetAccess,
)

object FleetGrants {
    fun parse(raw: String?): List<FleetGrant> {
        if (raw.isNullOrBlank()) return emptyList()
        val out = ArrayList<FleetGrant>()
        for (line in raw.lineSequence()) {
            if (line.isBlank() || out.size >= 64) continue
            val parts = line.split('\t')
            if (parts.size != 4) continue
            val pkg = parts[0]
            val cert = CertDigest.normalize(parts[1])
            if (pkg.isBlank() || pkg.length > 200 || cert.length != 64) continue
            if (pkg.any { it.isISOControl() || it.isWhitespace() }) continue
            out.add(
                FleetGrant(
                    packageName = pkg,
                    certSha256 = cert,
                    approved = parts[2] == "1",
                    enabled = parts[3] == "1",
                ),
            )
        }
        return out
    }

    fun render(grants: List<FleetGrant>): String {
        return grants.take(64).joinToString("\n") {
            val cert = CertDigest.normalize(it.certSha256)
            "${it.packageName}\t$cert\t${if (it.approved) 1 else 0}\t${if (it.enabled) 1 else 0}"
        }
    }

    fun find(grants: List<FleetGrant>, packageName: String): FleetGrant? {
        return grants.lastOrNull { it.packageName == packageName }
    }

    fun approve(grants: List<FleetGrant>, packageName: String, certSha256: String): List<FleetGrant> {
        return put(grants, FleetGrant(packageName, CertDigest.normalize(certSha256), approved = true, enabled = true))
    }

    fun setEnabled(grants: List<FleetGrant>, packageName: String, certSha256: String, enabled: Boolean): List<FleetGrant> {
        val current = find(grants, packageName)
        val approved = current?.approved == true && CertDigest.normalize(current.certSha256) == CertDigest.normalize(certSha256)
        return put(
            grants,
            FleetGrant(packageName, CertDigest.normalize(certSha256), approved = approved, enabled = enabled),
        )
    }

    private fun put(grants: List<FleetGrant>, grant: FleetGrant): List<FleetGrant> {
        return grants.filterNot { it.packageName == grant.packageName } + grant
    }
}

object FleetTrust {
    /**
     * A provider whose certificate matches [releaseSha256] is on until the user switches it off.
     * Any other certificate stays off until the user approves that exact certificate.
     * A stored grant for a different certificate is ignored.
     */
    fun access(certSha256: String, releaseSha256: String, grant: FleetGrant?): FleetAccess {
        val cert = CertDigest.normalize(certSha256)
        if (cert.length != 64) return FleetAccess.NeedsApproval
        val release = CertDigest.normalize(releaseSha256)
        val pinned = grant != null && CertDigest.normalize(grant.certSha256) == cert
        val auto = release.length == 64 && cert == release
        if (pinned && !grant.enabled) return FleetAccess.Off
        if (auto || (pinned && grant.approved)) return FleetAccess.Active
        return FleetAccess.NeedsApproval
    }
}

object FleetDiscovery {
    const val MAX_PROVIDERS = 16

    fun classify(
        ownPackage: String,
        releaseSha256: String,
        grants: List<FleetGrant>,
        candidates: List<FleetServiceCandidate>,
    ): List<FleetOffer> {
        return candidates
            .asSequence()
            .filter { it.packageName.isNotBlank() && it.className.isNotBlank() && it.packageName != ownPackage }
            .sortedWith(compareBy({ it.packageName }, { it.className }))
            .distinctBy { it.packageName }
            .take(MAX_PROVIDERS)
            .map { candidate ->
                val grant = FleetGrants.find(grants, candidate.packageName)
                FleetOffer(
                    packageName = candidate.packageName,
                    className = candidate.className,
                    label = candidate.label.ifBlank { candidate.packageName.substringAfterLast('.') },
                    certSha256 = candidate.certSha256,
                    access = FleetTrust.access(candidate.certSha256, releaseSha256, grant),
                )
            }
            .toList()
    }

    fun active(offers: List<FleetOffer>): List<FleetServiceTarget> {
        return offers.filter { it.access == FleetAccess.Active }.map {
            FleetServiceTarget(it.packageName, it.className)
        }
    }
}
