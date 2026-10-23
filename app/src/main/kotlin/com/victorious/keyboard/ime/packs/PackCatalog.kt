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

package com.victorious.keyboard.ime.packs

import com.victorious.keyboard.app.ProjectUrls
import java.security.MessageDigest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * The catalog of downloadable packs: `packs/index.json` in this repository (format in `docs/PACKS.md`). The catalog is
 * untrusted input even though this repository serves it: it is bounded, strictly typed, refuses unknown fields, and only
 * lists downloads that live in this repository's own release assets. A catalog entry never makes anything trusted: a
 * download still has to match its SHA-256 and then pass the pack type's own strict reader, and a command-provider app is
 * still approved one certificate at a time on the Fleet link screen.
 *
 * Pure Kotlin. Nothing here opens a connection, writes a file or logs; the keyboard does not read the catalog yet.
 */
object PackCatalogLimits {
    const val MAX_INDEX_BYTES = 256 * 1024
    const val MAX_ENTRIES = 500
    const val MAX_PACK_BYTES = 64L * 1024 * 1024
    const val MAX_TITLE = 80
    const val MAX_DESCRIPTION = 400
    const val MAX_PUBLISHER = 60
    const val MAX_ID = 80
    const val MAX_DOWNLOAD_URL = 300
    const val FORMAT = "victorious-pack-catalog"
    const val VERSION = 1

    /** The only place a download may live: release assets of this repository. */
    val DOWNLOAD_PREFIX = ProjectUrls.REPO + "/releases/download/"

    /** Publisher name for packs this project ships itself. Its extension-style ids must start with [OWN_ID_PREFIX]. */
    const val OWN_PUBLISHER = "victorious"
    const val OWN_ID_PREFIX = "com.victorious."

    /**
     * Id prefixes of packs that ship inside the app: the current built-ins and the older FlorisBoard ids they keep as
     * aliases. They are never downloaded, so a catalog entry may not use them.
     */
    val BUILT_IN_ID_PREFIXES = listOf("org.florisboard.", "com.victorious.keyboard.")
}

enum class PackType(val wire: String, val downloadable: Boolean) {
    DICTIONARY_PACK("dictionary-pack", true),
    LANGUAGE_PACK("language-pack", true),
    THEME("theme", true),
    LAYOUT("layout", true),
    EXTENSION("extension", true),

    /** An installed app that offers fleet commands. Listed with its package and certificate, never downloaded. */
    COMMAND_PROVIDER("command-provider", false);

    companion object {
        fun fromWire(value: String): PackType? = entries.firstOrNull { it.wire == value }
    }
}

data class CatalogEntry(
    val id: String,
    val type: PackType,
    val version: String,
    val title: String,
    val description: String,
    val publisher: String,
    /** Download link, SHA-256 (lowercase hex) and size of the file. Set exactly when [type] is downloadable. */
    val download: String?,
    val sha256: String?,
    val sizeBytes: Long?,
    /** Android package and signing-certificate SHA-256 (colon-separated uppercase hex). Set only for command providers. */
    val packageName: String?,
    val certSha256: String?,
)

data class PackCatalog(val updated: String, val entries: List<CatalogEntry>)

enum class CatalogError {
    TOO_LARGE, NOT_JSON, BAD_SHAPE, WRONG_FORMAT, UNSUPPORTED_VERSION, BAD_DATE, TOO_MANY_ENTRIES, UNKNOWN_TYPE, BAD_ID,
    RESERVED_ID, DUPLICATE_ID, BAD_VERSION, BAD_TEXT, BAD_PUBLISHER, BAD_DOWNLOAD, BAD_HASH, BAD_SIZE, BAD_PACKAGE,
    BAD_CERT, UNEXPECTED_FIELD,
}

sealed interface CatalogResult {
    data class Ok(val catalog: PackCatalog) : CatalogResult

    /** [entry] is the zero-based position in `packs`, or -1 when the problem is in the document itself. */
    data class Failed(val error: CatalogError, val entry: Int = -1) : CatalogResult
}

object PackCatalogReader {
    private val strict = Json { ignoreUnknownKeys = false }
    private val DATE = Regex("""\d{4}-\d{2}-\d{2}""")
    private val VERSION = Regex("""\d{1,4}\.\d{1,4}\.\d{1,4}""")

    /** Same pattern the extension validator enforces for `extension.json` ids. */
    private val EXTENSION_ID = Regex("""[a-z][a-z0-9_]*(\.[a-z0-9][a-z0-9_]*)*""")

    /** Same pattern the dictionary pack reader enforces for `pack.json` ids. */
    private val DICTIONARY_ID = Regex("""[a-z0-9][a-z0-9-]{0,39}""")
    private val PUBLISHER = Regex("""[a-z0-9][a-z0-9 ._-]{0,59}""")
    private val SHA256_HEX = Regex("""[0-9a-f]{64}""")
    private val CERT = Regex("""[0-9A-F]{2}(:[0-9A-F]{2}){31}""")
    private val PACKAGE = Regex("""[a-z][a-z0-9_]*(\.[a-z][a-z0-9_]*)+""")
    private val STRING_FIELDS = setOf(
        "format", "updated", "id", "type", "version", "title", "description", "publisher", "download", "sha256",
        "package", "cert_sha256",
    )
    private val ROOT_FIELDS = setOf("format", "version", "updated", "packs")
    private val ENTRY_FIELDS = STRING_FIELDS - setOf("format", "updated") + "size_bytes"

    /**
     * @param reservedIds ids of packs that ship in the app, such as the bundled dictionary packs. An entry may not reuse
     *  one, so a catalog can never claim to update something it cannot replace.
     */
    fun read(bytes: ByteArray, reservedIds: Set<String> = emptySet()): CatalogResult {
        if (bytes.size > PackCatalogLimits.MAX_INDEX_BYTES) return CatalogResult.Failed(CatalogError.TOO_LARGE)
        val root = try {
            strict.parseToJsonElement(bytes.toString(Charsets.UTF_8))
        } catch (_: Exception) {
            return CatalogResult.Failed(CatalogError.NOT_JSON)
        } as? JsonObject ?: return CatalogResult.Failed(CatalogError.BAD_SHAPE)
        if (!root.keys.all { it in ROOT_FIELDS }) return CatalogResult.Failed(CatalogError.UNEXPECTED_FIELD)
        if (str(root["format"]) != PackCatalogLimits.FORMAT) return CatalogResult.Failed(CatalogError.WRONG_FORMAT)
        val version = (root["version"] as? JsonPrimitive)?.takeIf { !it.isString }?.content?.toIntOrNull()
            ?: return CatalogResult.Failed(CatalogError.BAD_SHAPE)
        if (version != PackCatalogLimits.VERSION) return CatalogResult.Failed(CatalogError.UNSUPPORTED_VERSION)
        val updated = str(root["updated"])
        if (updated == null || !DATE.matches(updated)) return CatalogResult.Failed(CatalogError.BAD_DATE)
        val packs = root["packs"] as? JsonArray ?: return CatalogResult.Failed(CatalogError.BAD_SHAPE)
        if (packs.size > PackCatalogLimits.MAX_ENTRIES) return CatalogResult.Failed(CatalogError.TOO_MANY_ENTRIES)

        val seen = HashSet<String>()
        val entries = ArrayList<CatalogEntry>(packs.size)
        for ((i, element) in packs.withIndex()) {
            val entry = when (val parsed = entry(element, i, reservedIds)) {
                is Parsed.Bad -> return CatalogResult.Failed(parsed.error, i)
                is Parsed.Good -> parsed.entry
            }
            if (!seen.add(entry.id)) return CatalogResult.Failed(CatalogError.DUPLICATE_ID, i)
            entries += entry
        }
        return CatalogResult.Ok(PackCatalog(updated, entries))
    }

    private sealed interface Parsed {
        data class Good(val entry: CatalogEntry) : Parsed
        data class Bad(val error: CatalogError) : Parsed
    }

    private fun entry(element: JsonElement, index: Int, reservedIds: Set<String>): Parsed {
        val obj = element as? JsonObject ?: return Parsed.Bad(CatalogError.BAD_SHAPE)
        if (!obj.keys.all { it in ENTRY_FIELDS }) return Parsed.Bad(CatalogError.UNEXPECTED_FIELD)
        // Strings must be strings and numbers must be numbers: the decoder would coerce a quoted size, this does not.
        for ((name, value) in obj) {
            val p = value as? JsonPrimitive ?: return Parsed.Bad(CatalogError.BAD_SHAPE)
            if (name in STRING_FIELDS != p.isString) return Parsed.Bad(CatalogError.BAD_SHAPE)
        }
        val type = str(obj["type"])?.let { PackType.fromWire(it) } ?: return Parsed.Bad(CatalogError.UNKNOWN_TYPE)
        val id = str(obj["id"]) ?: return Parsed.Bad(CatalogError.BAD_ID)
        val publisher = str(obj["publisher"])
        if (publisher == null || !PUBLISHER.matches(publisher) || publisher.length > PackCatalogLimits.MAX_PUBLISHER) {
            return Parsed.Bad(CatalogError.BAD_PUBLISHER)
        }
        val idPattern = if (type == PackType.DICTIONARY_PACK) DICTIONARY_ID else EXTENSION_ID
        if (id.length > PackCatalogLimits.MAX_ID || !idPattern.matches(id)) return Parsed.Bad(CatalogError.BAD_ID)
        if (PackCatalogLimits.BUILT_IN_ID_PREFIXES.any { "$id.".startsWith(it) } || id in reservedIds) {
            return Parsed.Bad(CatalogError.RESERVED_ID)
        }
        // Our own extension-style packs live under com.victorious.*, and nobody else may claim that namespace. A
        // dictionary pack is a plain slug and a provider is identified by its package, so neither is held to a prefix.
        val ownPublisher = publisher == PackCatalogLimits.OWN_PUBLISHER
        val extensionStyle = type.downloadable && type != PackType.DICTIONARY_PACK
        if (extensionStyle && id.startsWith(PackCatalogLimits.OWN_ID_PREFIX) != ownPublisher) {
            return Parsed.Bad(CatalogError.BAD_PUBLISHER)
        }
        val version = str(obj["version"])
        if (version == null || !VERSION.matches(version)) return Parsed.Bad(CatalogError.BAD_VERSION)
        val title = str(obj["title"])
        val description = str(obj["description"]) ?: ""
        if (title.isNullOrBlank() || title.length > PackCatalogLimits.MAX_TITLE || title.any { it.isISOControl() } ||
            description.length > PackCatalogLimits.MAX_DESCRIPTION ||
            description.any { it.isISOControl() && it != '\n' }
        ) return Parsed.Bad(CatalogError.BAD_TEXT)

        val download = str(obj["download"])
        val sha256 = str(obj["sha256"])
        val size = (obj["size_bytes"] as? JsonPrimitive)?.content
        val packageName = str(obj["package"])
        val cert = str(obj["cert_sha256"])
        if (type.downloadable) {
            if (packageName != null || cert != null) return Parsed.Bad(CatalogError.UNEXPECTED_FIELD)
            if (download == null || !downloadOk(download)) return Parsed.Bad(CatalogError.BAD_DOWNLOAD)
            if (sha256 == null || !SHA256_HEX.matches(sha256)) return Parsed.Bad(CatalogError.BAD_HASH)
            val bytes = size?.toLongOrNull()
            if (bytes == null || bytes < 1 || bytes > PackCatalogLimits.MAX_PACK_BYTES) {
                return Parsed.Bad(CatalogError.BAD_SIZE)
            }
            return Parsed.Good(
                CatalogEntry(id, type, version, title, description, publisher, download, sha256, bytes, null, null),
            )
        }
        if (download != null || sha256 != null || size != null) return Parsed.Bad(CatalogError.UNEXPECTED_FIELD)
        if (packageName == null || !PACKAGE.matches(packageName) || packageName.length > PackCatalogLimits.MAX_ID) {
            return Parsed.Bad(CatalogError.BAD_PACKAGE)
        }
        if (cert == null || !CERT.matches(cert)) return Parsed.Bad(CatalogError.BAD_CERT)
        return Parsed.Good(
            CatalogEntry(id, type, version, title, description, publisher, null, null, null, packageName, cert),
        )
    }

    /** A release asset of this repository, as a plain https link: no query, no fragment, no credentials, no `..`. */
    private fun downloadOk(url: String): Boolean {
        if (url.length > PackCatalogLimits.MAX_DOWNLOAD_URL || !url.startsWith(PackCatalogLimits.DOWNLOAD_PREFIX)) return false
        val rest = url.removePrefix(PackCatalogLimits.DOWNLOAD_PREFIX)
        if (rest.any { it.isISOControl() || it == ' ' || it == '?' || it == '#' || it == '\\' || it == '@' || it == '%' }) {
            return false
        }
        val parts = rest.split('/')
        return parts.size == 2 && parts.all { it.isNotEmpty() && it != "." && it != ".." } &&
            (parts[1].endsWith(".zip") || parts[1].endsWith(".flex"))
    }

    private fun str(element: JsonElement?): String? = (element as? JsonPrimitive)?.takeIf { it.isString }?.content
}

/** Checks a downloaded file against the SHA-256 the catalog lists. The comparison does not stop at the first difference. */
object PackHash {
    fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    fun matches(bytes: ByteArray, expectedHex: String): Boolean {
        val actual = sha256Hex(bytes)
        if (expectedHex.length != actual.length) return false
        var diff = 0
        for (i in actual.indices) diff = diff or (actual[i].code xor expectedHex[i].lowercaseChar().code)
        return diff == 0
    }
}
