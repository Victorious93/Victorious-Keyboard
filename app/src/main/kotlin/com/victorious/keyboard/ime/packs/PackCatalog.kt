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

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * The pack catalog is one JSON file, `packs/index.json` in this repository. It only *lists* packs: it names what
 * exists, where the file is, how big it is and its SHA-256. Reading the catalog never downloads or installs anything,
 * and the keyboard has no `INTERNET` permission yet (ROADMAP item 10 adds an opt-in check). A catalog is untrusted
 * input (CLAUDE.md invariant 5): its size is bounded before parsing, every entry is checked on its own, and an entry
 * that fails any check is dropped and counted, never repaired. Pure Kotlin; nothing is logged.
 */
object CatalogLimits {
    const val FORMAT = "victorious-pack-catalog"
    const val VERSION = 1
    const val MAX_BYTES = 256 * 1024
    const val MAX_ENTRIES = 500
    const val MAX_TITLE = 80
    const val MAX_DESCRIPTION = 400
    const val MAX_PUBLISHER = 80
    const val MAX_DOWNLOAD_BYTES = 16L * 1024 * 1024
    const val DOWNLOAD_PREFIX = "https://github.com/Victorious93/Victorious-Keyboard/releases/download/"
    const val PAGE_PREFIX = "https://github.com/Victorious93/Victorious-Keyboard/"
}

enum class PackType(val wire: String) {
    THEME("theme"),
    LAYOUT("layout"),
    LANGUAGE_PACK("language-pack"),
    DICTIONARY_PACK("dictionary-pack"),
    COMMAND_PROVIDER("command-provider"),
    ;

    /** A downloadable file (zip) as opposed to an app that is installed some other way. */
    val isDownload: Boolean get() = this != COMMAND_PROVIDER

    companion object {
        fun fromWire(value: String): PackType? = entries.firstOrNull { it.wire == value }
    }
}

data class CatalogEntry(
    val type: PackType,
    val id: String,
    val version: String,
    val title: String,
    val description: String,
    val publisher: String,
    /** Download link for a file pack; an optional information page for a command provider. */
    val url: String?,
    val sha256: String?,
    val sizeBytes: Long?,
    val packageName: String?,
    val certSha256: String?,
)

data class PackCatalog(val entries: List<CatalogEntry>, val rejectedEntries: Int)

enum class CatalogError { TOO_LARGE, BAD_JSON, UNSUPPORTED_FORMAT, UNSUPPORTED_VERSION, TOO_MANY_ENTRIES }

sealed interface CatalogResult {
    data class Ok(val catalog: PackCatalog) : CatalogResult
    data class Failed(val error: CatalogError) : CatalogResult
}

@Serializable
private data class RawCatalog(
    val format: String = "",
    val version: Int = 0,
    val packs: List<RawEntry> = emptyList(),
)

@Serializable
private data class RawEntry(
    val type: String = "",
    val id: String = "",
    val version: String = "",
    val title: String = "",
    val description: String = "",
    val publisher: String = "",
    val url: String? = null,
    val sha256: String? = null,
    val sizeBytes: Long? = null,
    val packageName: String? = null,
    val certSha256: String? = null,
)

object PackCatalogReader {
    // Unknown keys are ignored so a newer catalog can add fields; a breaking change bumps `version` instead.
    private val json = Json { ignoreUnknownKeys = true; isLenient = false }

    /** Own packs only: `com.victorious.` plus one or more lower-case segments. Built-ins (`org.florisboard.*`) are not listed. */
    private val ID = Regex("com\\.victorious(\\.[a-z0-9][a-z0-9-]{0,39}){1,4}")
    private val VERSION = Regex("(0|[1-9][0-9]{0,5})\\.(0|[1-9][0-9]{0,5})\\.(0|[1-9][0-9]{0,5})")
    private val SHA256 = Regex("[0-9a-f]{64}")
    private val PACKAGE = Regex("[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*){1,7}")
    private val DOWNLOAD = Regex(
        Regex.escape(CatalogLimits.DOWNLOAD_PREFIX) + "[A-Za-z0-9][A-Za-z0-9._-]{0,99}/[A-Za-z0-9][A-Za-z0-9._-]{0,99}",
    )
    private val PAGE = Regex(Regex.escape(CatalogLimits.PAGE_PREFIX) + "[A-Za-z0-9/._#-]{0,200}")

    fun read(bytes: ByteArray): CatalogResult {
        if (bytes.size > CatalogLimits.MAX_BYTES) return CatalogResult.Failed(CatalogError.TOO_LARGE)
        val raw = try {
            json.decodeFromString<RawCatalog>(bytes.toString(Charsets.UTF_8))
        } catch (_: SerializationException) {
            return CatalogResult.Failed(CatalogError.BAD_JSON)
        } catch (_: IllegalArgumentException) {
            return CatalogResult.Failed(CatalogError.BAD_JSON)
        }
        if (raw.format != CatalogLimits.FORMAT) return CatalogResult.Failed(CatalogError.UNSUPPORTED_FORMAT)
        if (raw.version != CatalogLimits.VERSION) return CatalogResult.Failed(CatalogError.UNSUPPORTED_VERSION)
        if (raw.packs.size > CatalogLimits.MAX_ENTRIES) return CatalogResult.Failed(CatalogError.TOO_MANY_ENTRIES)

        val seen = HashSet<String>()
        val entries = ArrayList<CatalogEntry>()
        var rejected = 0
        for (r in raw.packs) {
            val entry = validate(r)
            // An id names one pack, whatever its type; a second entry with the same id is dropped.
            if (entry == null || !seen.add(entry.id)) rejected++ else entries.add(entry)
        }
        return CatalogResult.Ok(PackCatalog(entries, rejected))
    }

    private fun text(value: String, max: Int): Boolean =
        value.isNotBlank() && value.length <= max && value.none { it.isISOControl() }

    private fun validate(r: RawEntry): CatalogEntry? {
        val type = PackType.fromWire(r.type) ?: return null
        if (!ID.matches(r.id) || !VERSION.matches(r.version)) return null
        if (!text(r.title, CatalogLimits.MAX_TITLE) || !text(r.publisher, CatalogLimits.MAX_PUBLISHER)) return null
        if (r.description.length > CatalogLimits.MAX_DESCRIPTION || r.description.any { it.isISOControl() && it != '\n' }) return null
        if (type.isDownload) {
            val url = r.url ?: return null
            val sha = r.sha256 ?: return null
            val size = r.sizeBytes ?: return null
            if (!DOWNLOAD.matches(url) || url.contains("..") || !SHA256.matches(sha)) return null
            if (size < 1 || size > CatalogLimits.MAX_DOWNLOAD_BYTES) return null
            if (r.packageName != null || r.certSha256 != null) return null
        } else {
            val pkg = r.packageName ?: return null
            val cert = r.certSha256 ?: return null
            if (!PACKAGE.matches(pkg) || !SHA256.matches(cert)) return null
            if (r.url != null && (!PAGE.matches(r.url) || r.url.contains(".."))) return null
            if (r.sha256 != null || r.sizeBytes != null) return null
        }
        return CatalogEntry(
            type, r.id, r.version, r.title, r.description, r.publisher,
            r.url, r.sha256, r.sizeBytes, r.packageName, r.certSha256,
        )
    }
}
