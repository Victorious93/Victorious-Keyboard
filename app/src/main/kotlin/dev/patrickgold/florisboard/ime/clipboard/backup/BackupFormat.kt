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

package dev.patrickgold.florisboard.ime.clipboard.backup

import dev.patrickgold.florisboard.ime.clipboard.history.ClipCategory
import dev.patrickgold.florisboard.ime.clipboard.history.ClipEntry
import dev.patrickgold.florisboard.ime.clipboard.history.ClipHash
import dev.patrickgold.florisboard.ime.clipboard.history.ClipTags
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.SecureRandom
import java.time.Instant
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Describes the export being created; kept separate from the entries so tests can pin the clock. */
data class ExportMeta(
    val createdAtMs: Long,
    val appVersion: String,
    val skippedUnsupported: Int = 0,
    val skippedSensitive: Int = 0,
)

/**
 * Upgrades older backup JSON to the current shape.
 * The v1 → v2 step marks every entry that has no `lane` as the regular clipboard.
 * The entries hash is checked on the original bytes before this runs.
 */
internal object BackupMigrations {
    /** Index = source version minus 1. Each step must be pure and map version N documents to version N+1. */
    private val steps: List<(JsonObject) -> JsonObject> = listOf { doc ->
        val entries = doc["entries"]?.asArrayOrNull() ?: return@listOf doc
        val upgraded = JsonArray(entries.map { element ->
            val obj = element as? JsonObject ?: return@map element
            if (obj.containsKey("lane")) obj
            else JsonObject(obj.toMap() + ("lane" to JsonPrimitive(LANE_REGULAR)))
        })
        JsonObject(doc.toMap() + ("entries" to upgraded))
    }

    fun requireReadable(version: Int) {
        if (version < 1) throw BackupException.Malformed("invalid format version $version")
        if (version > BACKUP_FORMAT_VERSION) throw BackupException.UnsupportedVersion(version)
        check(steps.size == BACKUP_FORMAT_VERSION - 1) { "migration chain does not cover all versions" }
    }

    fun upgradeEntries(formatVersion: Int, doc: JsonObject): JsonObject {
        var current = doc
        var version = formatVersion
        while (version < BACKUP_FORMAT_VERSION) {
            current = steps[version - 1](current)
            version++
        }
        return current
    }

    private fun JsonElement.asArrayOrNull(): JsonArray? = runCatching { jsonArray }.getOrNull()
}

object BackupWriter {
    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
    }

    const val MIME_JSON = "application/json"
    const val MIME_ZIP = "application/zip"
    const val MIME_ENCRYPTED = "application/octet-stream"
    const val MIME_MARKDOWN = "text/markdown"
    const val MIME_TEXT = "text/plain"

    private val order = compareByDescending<ClipEntry> { it.createdAtMs }.thenBy { it.id }

    private fun laneName(id: String, lanes: Map<String, String>): String {
        val lane = lanes[id] ?: LANE_REGULAR
        if (lane != LANE_REGULAR && lane != LANE_VAULT) throw BackupException.Malformed("unknown clipboard lane")
        return lane
    }

    private fun toBackup(e: ClipEntry, lane: String) = BackupEntry(
        id = e.id,
        text = e.text,
        createdAtMs = e.createdAtMs,
        editedAtMs = e.editedAtMs,
        pinned = e.pinned,
        favorite = e.favorite,
        tags = e.tags.sorted(),
        category = e.category.name,
        contentHash = e.contentHash,
        lane = lane,
    )

    private fun manifest(
        count: Int,
        meta: ExportMeta,
        entriesSha: String?,
        regularCount: Int,
        vaultCount: Int,
        passwordProtected: Boolean = false,
    ) = BackupManifest(
        createdAtMs = meta.createdAtMs,
        appVersion = meta.appVersion,
        itemCount = count,
        entriesSha256 = entriesSha,
        skippedUnsupported = meta.skippedUnsupported,
        skippedSensitive = meta.skippedSensitive,
        regularCount = regularCount,
        vaultCount = vaultCount,
        passwordProtected = passwordProtected,
    )

    private fun packed(entries: List<ClipEntry>, lanes: Map<String, String>): List<BackupEntry> =
        entries.sortedWith(order).map { toBackup(it, laneName(it.id, lanes)) }

    /** Single-file, human-readable JSON. */
    fun writeJson(entries: List<ClipEntry>, meta: ExportMeta, lanes: Map<String, String> = emptyMap()): ByteArray {
        val sorted = packed(entries, lanes)
        val doc = BackupDocument(
            manifest(sorted.size, meta, null, sorted.regularCount(), sorted.vaultCount()),
            sorted,
        )
        return json.encodeToString(BackupDocument.serializer(), doc).toByteArray(Charsets.UTF_8)
    }

    /** ZIP with `manifest.json` + `entries.json`; the manifest carries the SHA-256 of `entries.json`. */
    fun writeZip(entries: List<ClipEntry>, meta: ExportMeta, lanes: Map<String, String> = emptyMap()): ByteArray {
        val sorted = packed(entries, lanes)
        val entriesBytes = json.encodeToString(BackupEntries.serializer(), BackupEntries(sorted))
            .toByteArray(Charsets.UTF_8)
        val manifestBytes = json.encodeToString(
            BackupManifest.serializer(),
            manifest(
                sorted.size,
                meta,
                ClipHash.sha256Hex(entriesBytes),
                sorted.regularCount(),
                sorted.vaultCount(),
            ),
        ).toByteArray(Charsets.UTF_8)
        return zipMembers(MANIFEST_NAME to manifestBytes, ENTRIES_NAME to entriesBytes)
    }

    /**
     * User-facing ZIP whose item text lives in `content.vkbk` (the password container around a plain ZIP).
     * `manifest.json` records counts and the hash of that container. It does not contain item text.
     */
    fun writePasswordZip(
        entries: List<ClipEntry>,
        meta: ExportMeta,
        lanes: Map<String, String> = emptyMap(),
        password: CharArray,
        random: SecureRandom = SecureRandom(),
        iterations: Int = BackupCrypto.DEFAULT_ITERATIONS,
    ): ByteArray {
        val sorted = packed(entries, lanes)
        val cipher = BackupCrypto.encrypt(writeZip(entries, meta, lanes), password, random, iterations)
        val manifestBytes = json.encodeToString(
            BackupManifest.serializer(),
            manifest(
                sorted.size,
                meta,
                ClipHash.sha256Hex(cipher),
                sorted.regularCount(),
                sorted.vaultCount(),
                passwordProtected = true,
            ),
        ).toByteArray(Charsets.UTF_8)
        return zipMembers(MANIFEST_NAME to manifestBytes, CONTENT_NAME to cipher)
    }

    /** ZIP wrapped in the password-based authenticated container. See [BackupCrypto]. */
    fun writeEncrypted(
        entries: List<ClipEntry>,
        meta: ExportMeta,
        password: CharArray,
        random: SecureRandom = SecureRandom(),
        iterations: Int = BackupCrypto.DEFAULT_ITERATIONS,
        lanes: Map<String, String> = emptyMap(),
    ): ByteArray = BackupCrypto.encrypt(writeZip(entries, meta, lanes), password, random, iterations)

    private fun List<BackupEntry>.regularCount(): Int = count { it.lane != LANE_VAULT }

    private fun List<BackupEntry>.vaultCount(): Int = count { it.lane == LANE_VAULT }

    private fun zipMembers(vararg members: Pair<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            // Fixed timestamps keep output deterministic for a given input.
            for ((name, bytes) in members) {
                val ze = ZipEntry(name).apply { time = 0L }
                zip.putNextEntry(ze)
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    /**
     * One-way, human-readable archive. Cannot be re-imported (no integrity data); use JSON/ZIP for restores.
     * Each entry is rendered inside a fence long enough that its own backticks cannot break out.
     */
    fun writeMarkdown(entries: List<ClipEntry>, meta: ExportMeta): ByteArray {
        val sb = StringBuilder()
        sb.append("# Victorious Keyboard clipboard export\n\n")
        sb.append("Exported ").append(Instant.ofEpochMilli(meta.createdAtMs)).append(" · ")
            .append(entries.size).append(" items\n\n")
        entries.sortedWith(order).forEachIndexed { i, e ->
            sb.append("## ").append(i + 1).append(" · ").append(Instant.ofEpochMilli(e.createdAtMs))
                .append(" · ").append(e.category.name)
            if (e.pinned) sb.append(" · pinned")
            if (e.favorite) sb.append(" · favourite")
            if (e.tags.isNotEmpty()) sb.append(" · ").append(e.tags.sorted().joinToString(" ") { "#$it" })
            sb.append("\n\n")
            val fence = "`".repeat(maxOf(3, longestBacktickRun(e.text) + 1))
            sb.append(fence).append('\n').append(e.text)
            if (!e.text.endsWith("\n")) sb.append('\n')
            sb.append(fence).append("\n\n")
        }
        return sb.toString().toByteArray(Charsets.UTF_8)
    }

    /** One-way plain text: entries separated by a line of dashes. */
    fun writeText(entries: List<ClipEntry>): ByteArray =
        entries.sortedWith(order).joinToString("\n----------------------------------------\n") { it.text }
            .toByteArray(Charsets.UTF_8)

    private fun longestBacktickRun(s: String): Int {
        var best = 0
        var run = 0
        for (c in s) {
            if (c == '`') {
                run++
                if (run > best) best = run
            } else {
                run = 0
            }
        }
        return best
    }

    internal const val MANIFEST_NAME = "manifest.json"
    internal const val ENTRIES_NAME = "entries.json"
    /** Password container (see [BackupCrypto]) stored inside a user-facing ZIP. */
    internal const val CONTENT_NAME = "content.vkbk"
}

/**
 * Reads backups. **Everything in the file is untrusted input.** The reader:
 *  * enforces [BackupLimits] on file size, uncompressed size, entry counts and text sizes,
 *  * only ever reads whitelisted ZIP members into memory (nothing is extracted to disk, so path traversal is
 *    impossible; hostile member names are still rejected),
 *  * verifies manifest/entry hashes and rejects duplicates and invalid ids,
 *  * reports failures as [BackupException] with messages that never include clip text.
 */
object BackupReader {
    private val json = Json { ignoreUnknownKeys = true }
    private val idPattern = Regex("^[A-Za-z0-9_-]{1,64}$")

    fun detect(bytes: ByteArray): BackupKind = when {
        BackupCrypto.isEncrypted(bytes) -> BackupKind.ENCRYPTED
        bytes.size >= 4 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte() -> BackupKind.ZIP
        firstNonSpace(bytes) == '{'.code.toByte() -> BackupKind.JSON
        else -> BackupKind.UNKNOWN
    }

    fun read(bytes: ByteArray, password: CharArray?, limits: BackupLimits = BackupLimits()): ParsedBackup {
        if (bytes.size > limits.maxFileBytes) throw BackupException.TooLarge("file is larger than the allowed size")
        return when (detect(bytes)) {
            BackupKind.ENCRYPTED -> {
                if (password == null) throw BackupException.PasswordRequired()
                readZip(BackupCrypto.decrypt(bytes, password, limits), limits, password).copy(passwordProtected = true)
            }
            BackupKind.ZIP -> readZip(bytes, limits, password)
            BackupKind.JSON -> readJson(bytes, limits)
            BackupKind.UNKNOWN -> throw BackupException.NotABackup()
        }
    }

    private fun readJson(bytes: ByteArray, limits: BackupLimits): ParsedBackup {
        val root = parseObject(bytes)
        val manifestEl = root["manifest"] ?: throw BackupException.Malformed("invalid JSON structure")
        val manifest = decodeElement(BackupManifest.serializer(), manifestEl)
        validateManifest(manifest)
        if (manifest.entriesSha256 != null) {
            // A JSON document cannot self-hash; a hash here would be meaningless, so treat it as malformed.
            throw BackupException.Malformed("unexpected entries hash in single-file backup")
        }
        if (manifest.passwordProtected) throw BackupException.Malformed("marked encrypted but no container")
        val entriesEl = root["entries"] ?: throw BackupException.Malformed("invalid JSON structure")
        val upgraded = BackupMigrations.upgradeEntries(manifest.formatVersion, JsonObject(mapOf("entries" to entriesEl)))
        return finish(manifest, decodeElement(BackupEntries.serializer(), upgraded).entries, limits)
    }

    private fun readZip(bytes: ByteArray, limits: BackupLimits, password: CharArray?, depth: Int = 0): ParsedBackup {
        val members = HashMap<String, ByteArray>()
        var total = 0L
        var count = 0
        try {
            ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
                while (true) {
                    val ze = zip.nextEntry ?: break
                    count++
                    if (count > limits.maxZipEntries) throw BackupException.TooLarge("too many archive members")
                    val name = ze.name
                    if (isUnsafeName(name)) throw BackupException.Malformed("unsafe archive member name")
                    if (ze.isDirectory) continue
                    if (name !in WANTED) continue // Unknown members are ignored and never extracted.
                    if (members.containsKey(name)) throw BackupException.Malformed("duplicate archive member")
                    val buf = ByteArrayOutputStream()
                    val chunk = ByteArray(16 * 1024)
                    while (true) {
                        val n = zip.read(chunk)
                        if (n < 0) break
                        total += n
                        if (total > limits.maxUncompressedBytes) {
                            throw BackupException.TooLarge("archive expands beyond the allowed size")
                        }
                        buf.write(chunk, 0, n)
                    }
                    members[name] = buf.toByteArray()
                }
            }
        } catch (e: ZipException) {
            throw BackupException.Malformed("not a valid ZIP archive")
        } catch (e: java.io.IOException) {
            throw BackupException.Malformed("archive could not be read")
        }
        val manifestBytes = members[BackupWriter.MANIFEST_NAME] ?: throw BackupException.Malformed("manifest missing")
        val manifest = decode(BackupManifest.serializer(), manifestBytes)
        validateManifest(manifest)
        val cipher = members[BackupWriter.CONTENT_NAME]
        val entriesBytes = members[BackupWriter.ENTRIES_NAME]
        if (cipher != null && entriesBytes != null) {
            throw BackupException.Malformed("archive mixes plaintext entries with an encrypted container")
        }
        if (cipher != null) {
            if (depth > 0) throw BackupException.Malformed("nested encrypted container")
            if (!manifest.passwordProtected) {
                throw BackupException.Malformed("encrypted container is not marked password-protected")
            }
            if (password == null) throw BackupException.PasswordRequired()
            val expected = manifest.entriesSha256 ?: throw BackupException.Corrupt("manifest has no entries hash")
            if (!constantTimeEquals(expected, ClipHash.sha256Hex(cipher))) {
                throw BackupException.Corrupt("entries do not match the manifest hash")
            }
            val inner = readZip(BackupCrypto.decrypt(cipher, password, limits), limits, password, depth + 1)
            if (inner.manifest.itemCount != manifest.itemCount) {
                throw BackupException.Corrupt("item count does not match the manifest")
            }
            return inner.copy(passwordProtected = true)
        }
        if (manifest.passwordProtected) throw BackupException.Malformed("marked encrypted but no container")
        val plain = entriesBytes ?: throw BackupException.Malformed("entries missing")
        val expected = manifest.entriesSha256 ?: throw BackupException.Corrupt("manifest has no entries hash")
        if (!constantTimeEquals(expected, ClipHash.sha256Hex(plain))) {
            throw BackupException.Corrupt("entries do not match the manifest hash")
        }
        val upgraded = BackupMigrations.upgradeEntries(manifest.formatVersion, parseObject(plain))
        return finish(manifest, decodeElement(BackupEntries.serializer(), upgraded).entries, limits)
    }

    private fun validateManifest(m: BackupManifest) {
        if (m.format != BACKUP_FORMAT_NAME) throw BackupException.NotABackup()
        BackupMigrations.requireReadable(m.formatVersion)
    }

    private fun finish(manifest: BackupManifest, raw: List<BackupEntry>, limits: BackupLimits): ParsedBackup {
        if (raw.size > limits.maxEntries) throw BackupException.TooLarge("too many entries")
        if (raw.size != manifest.itemCount) throw BackupException.Corrupt("item count does not match the manifest")

        val seen = HashSet<String>(raw.size * 2)
        var unsupported = 0
        var regularCount = 0
        var vaultCount = 0
        val out = ArrayList<ClipEntry>(raw.size)
        val lanes = HashMap<String, String>(raw.size * 2)
        for (e in raw) {
            if (!idPattern.matches(e.id)) throw BackupException.Malformed("invalid entry id")
            if (!seen.add(e.id)) throw BackupException.Malformed("duplicate entry id")
            val lane = when (e.lane) {
                LANE_REGULAR -> LANE_REGULAR
                LANE_VAULT -> LANE_VAULT
                else -> throw BackupException.Malformed("unknown clipboard lane")
            }
            if (lane == LANE_VAULT) vaultCount++ else regularCount++
            if (e.type != ENTRY_TYPE_TEXT) {
                unsupported++
                continue
            }
            if (e.text.length > limits.maxTextChars) throw BackupException.TooLarge("an entry is too long")
            if (e.createdAtMs < 0 || (e.editedAtMs ?: 0) < 0) throw BackupException.Malformed("invalid timestamp")
            if (e.tags.size > limits.maxTags) throw BackupException.Malformed("too many tags on an entry")
            if (!constantTimeEquals(e.contentHash, ClipHash.sha256Hex(e.text))) {
                throw BackupException.Corrupt("an entry does not match its hash")
            }
            lanes[e.id] = lane
            out.add(
                ClipEntry(
                    id = e.id,
                    text = e.text,
                    createdAtMs = e.createdAtMs,
                    pinned = e.pinned,
                    favorite = e.favorite,
                    tags = ClipTags.normalizeAll(e.tags),
                    category = ClipCategory.parse(e.category),
                    editedAtMs = e.editedAtMs,
                )
            )
        }
        if (manifest.formatVersion >= 2 &&
            (manifest.regularCount != regularCount || manifest.vaultCount != vaultCount)
        ) {
            throw BackupException.Corrupt("lane counts do not match the manifest")
        }
        return ParsedBackup(manifest, out, unsupported + manifest.skippedUnsupported.coerceAtLeast(0), lanes)
    }

    private fun parseObject(bytes: ByteArray): JsonObject {
        val element = try {
            json.parseToJsonElement(bytes.toString(Charsets.UTF_8))
        } catch (e: BackupException) {
            throw e
        } catch (e: Exception) {
            // Deeply nested or otherwise hostile JSON can throw outside SerializationException.
            throw BackupException.Malformed("invalid JSON structure")
        }
        return element as? JsonObject ?: throw BackupException.Malformed("invalid JSON structure")
    }

    private fun <T> decodeElement(s: kotlinx.serialization.KSerializer<T>, element: JsonElement): T = try {
        json.decodeFromJsonElement(s, element)
    } catch (e: BackupException) {
        throw e
    } catch (e: Exception) {
        throw BackupException.Malformed("invalid JSON structure")
    }

    private fun <T> decode(s: kotlinx.serialization.KSerializer<T>, bytes: ByteArray): T = try {
        json.decodeFromString(s, bytes.toString(Charsets.UTF_8))
    } catch (e: SerializationException) {
        throw BackupException.Malformed("invalid JSON structure")
    } catch (e: IllegalArgumentException) {
        throw BackupException.Malformed("invalid JSON structure")
    }

    private val WANTED = setOf(BackupWriter.MANIFEST_NAME, BackupWriter.ENTRIES_NAME, BackupWriter.CONTENT_NAME)

    private fun isUnsafeName(n: String): Boolean =
        n.isEmpty() || n.startsWith("/") || n.contains('\\') || n.contains(':') || n.contains('\u0000') ||
            n.split('/').any { it == ".." }

    private fun firstNonSpace(b: ByteArray): Byte? {
        for (x in b) {
            if (x.toInt().toChar().isWhitespace()) continue
            return x
        }
        return null
    }

    private fun constantTimeEquals(a: String, b: String): Boolean =
        java.security.MessageDigest.isEqual(a.toByteArray(Charsets.UTF_8), b.toByteArray(Charsets.UTF_8))
}
