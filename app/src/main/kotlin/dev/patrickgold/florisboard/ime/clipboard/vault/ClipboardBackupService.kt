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

package dev.patrickgold.florisboard.ime.clipboard.vault

import dev.patrickgold.florisboard.ime.clipboard.backup.BackupCrypto
import dev.patrickgold.florisboard.ime.clipboard.backup.BackupException
import dev.patrickgold.florisboard.ime.clipboard.backup.BackupKind
import dev.patrickgold.florisboard.ime.clipboard.backup.BackupReader
import dev.patrickgold.florisboard.ime.clipboard.backup.BackupWriter
import dev.patrickgold.florisboard.ime.clipboard.backup.DropReason
import dev.patrickgold.florisboard.ime.clipboard.backup.ExportMeta
import dev.patrickgold.florisboard.ime.clipboard.backup.ImportMode
import dev.patrickgold.florisboard.ime.clipboard.backup.ImportPlan
import dev.patrickgold.florisboard.ime.clipboard.backup.ImportPlanner
import dev.patrickgold.florisboard.ime.clipboard.backup.LANE_REGULAR
import dev.patrickgold.florisboard.ime.clipboard.backup.LANE_VAULT
import dev.patrickgold.florisboard.ime.clipboard.backup.LaneIds
import dev.patrickgold.florisboard.ime.clipboard.backup.ParsedBackup
import dev.patrickgold.florisboard.ime.clipboard.backup.ZipExportPolicy
import dev.patrickgold.florisboard.ime.clipboard.backup.ZipScope
import dev.patrickgold.florisboard.ime.clipboard.history.ClipEntry
import dev.patrickgold.florisboard.ime.clipboard.lanes.VaultExportPolicy
import dev.patrickgold.florisboard.ime.clipboard.history.HistoryPolicy
import dev.patrickgold.florisboard.ime.clipboard.history.SensitiveDetector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.SecureRandom
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** Everything currently in history, as domain entries. Media rows appear with blank text and are in [mediaIds]. */
data class HistorySnapshot(val entries: List<ClipEntry>, val mediaIds: Set<String>) {
    val textEntries: List<ClipEntry> get() = entries.filter { it.id !in mediaIds }
}

/** Storage seam. The Room implementation lives next to the manager; tests use an in-memory fake. */
interface ClipboardEntryRepository {
    suspend fun snapshot(): HistorySnapshot

    /**
     * Applies [plan] atomically: all deletions and upserts happen in one transaction. If this throws, the stored
     * history must be exactly what it was before the call.
     */
    suspend fun apply(plan: ImportPlan)
}

/**
 * Sensitive-vault seam. [snapshot] and [apply] throw [BackupException.VaultLocked] while the vault is locked.
 * Plaintext from [snapshot] is only for an export the user just asked for.
 */
interface VaultEntryRepository {
    fun isLocked(): Boolean

    suspend fun snapshot(): List<ClipEntry>

    /** Applies [plan] atomically. A failure leaves the vault file as it was. */
    suspend fun apply(plan: ImportPlan)
}

enum class ExportFormat(val extension: String, val mime: String, val restorable: Boolean) {
    ENCRYPTED_ZIP("vkbk", BackupWriter.MIME_ENCRYPTED, true),
    ZIP("zip", BackupWriter.MIME_ZIP, true),
    JSON("json", BackupWriter.MIME_JSON, true),
    MARKDOWN("md", BackupWriter.MIME_MARKDOWN, false),
    TEXT("txt", BackupWriter.MIME_TEXT, false),
}

class ExportOptions(
    val format: ExportFormat,
    /** Required for [ExportFormat.ENCRYPTED_ZIP]; ignored otherwise. Callers should zero it after use. */
    val password: CharArray? = null,
    /** Entries Android marked sensitive, or that look like credentials, are left out unless this is true. */
    val includeSensitive: Boolean = false,
    /** Restrict the export to these entry ids (the "export selected items" action). Null = everything. */
    val onlyIds: Set<String>? = null,
    /**
     * Sensitive-vault rows. Included only for an encrypted export when this is true.
     * Other formats drop them and count them as skipped.
     */
    val includeVault: Boolean = false,
    val vaultEntries: List<ClipEntry> = emptyList(),
)

class ExportResult(
    val bytes: ByteArray,
    val suggestedName: String,
    val mime: String,
    val itemCount: Int,
    val skippedSensitive: Int,
    val skippedUnsupported: Int,
)

/** A user-facing `.zip` of one or both clipboards. [encrypted] is true whenever a password was used. */
class ZipExportResult(
    val bytes: ByteArray,
    val suggestedName: String,
    val mime: String,
    val regularCount: Int,
    val vaultCount: Int,
    val encrypted: Boolean,
    val skippedUnsupported: Int,
)

class Inspection(val parsed: ParsedBackup, val kind: BackupKind)

/** The user's explicit consent, tied to exactly which entries a previewed plan would remove or discard. */
data class Confirmation(val removedIds: Set<String>, val droppedIds: Set<String>) {
    companion object {
        fun of(plan: ImportPlan) = Confirmation(
            removedIds = plan.removedExisting.mapTo(HashSet()) { it.id },
            droppedIds = plan.droppedIncoming.filter { it.reason == DropReason.OVER_CAPACITY }
                .mapTo(HashSet()) { it.entry.id },
        )

        fun of(regular: ImportPlan, vault: ImportPlan?): Confirmation {
            if (vault == null) return of(regular)
            val both = listOf(regular, vault)
            return Confirmation(
                removedIds = both.flatMapTo(HashSet()) { of(it).removedIds },
                droppedIds = both.flatMapTo(HashSet()) { of(it).droppedIds },
            )
        }
    }

    fun covers(plan: ImportPlan): Boolean =
        plan.removedExisting.all { it.id in removedIds } &&
            plan.droppedIncoming.filter { it.reason == DropReason.OVER_CAPACITY }.all { it.entry.id in droppedIds }

    fun covers(import: LaneImport): Boolean {
        val vaultPlan = import.vault
        return covers(import.regular) && (vaultPlan == null || covers(vaultPlan))
    }
}

/** Import plans for the two stores. A null [vault] plan means the file has no vault rows, so the vault is left alone. */
data class LaneImport(val regular: ImportPlan, val vault: ImportPlan?) {
    val requiresConfirmation: Boolean
        get() = regular.requiresConfirmation || vault?.requiresConfirmation == true
}

/** The plan needs consent that was not given, or the history changed since the user confirmed. */
class ConfirmationRequiredException(val plan: ImportPlan, val lanes: LaneImport? = null) :
    Exception("Applying this import would remove or discard entries; confirmation is required.")

enum class RestoreStage { PLANNING, APPLYING, DONE }

class RestoreResult(
    val added: Int,
    val merged: Int,
    val skippedDuplicates: Int,
    val removedExisting: Int,
    val droppedIncoming: Int,
    val finalCount: Int,
)

/**
 * Orchestrates export, inspection, preview and restore of clipboard backups. Pure Kotlin: file and URI access is the
 * caller's job (bytes in, bytes out), which keeps the logic JVM-testable.
 *
 * Safety properties:
 *  * Export never includes sensitive-looking entries unless asked.
 *  * [inspect] changes nothing. [preview] changes nothing.
 *  * [restore] re-plans against the **current** history, so what is applied matches what is verified now — and if
 *    that would delete more than the user confirmed, it asks again ([ConfirmationRequiredException]).
 *  * [restore] applies through [ClipboardEntryRepository.apply], which is atomic.
 */
class ClipboardBackupService(
    private val repo: ClipboardEntryRepository,
    private val appVersion: String,
    private val policy: () -> HistoryPolicy = { HistoryPolicy() },
    private val clock: () -> Long = System::currentTimeMillis,
    private val vault: VaultEntryRepository? = null,
) {
    /** The default file name offered in the system file picker. */
    fun suggestedFileName(format: ExportFormat): String =
        "victorious-clipboard-${FILE_STAMP.format(Instant.ofEpochMilli(clock()))}.${format.extension}"

    /** Default name for a clipboard ZIP. The extension is always `.zip`, encrypted or not. */
    fun suggestedZipName(): String =
        "victorious-clipboard-${FILE_STAMP.format(Instant.ofEpochMilli(clock()))}.zip"

    suspend fun export(options: ExportOptions): ExportResult = withContext(Dispatchers.Default) {
        val snap = repo.snapshot()
        val unsupported = snap.entries.size - snap.textEntries.size
        var candidates = snap.textEntries
        options.onlyIds?.let { ids -> candidates = candidates.filter { it.id in ids } }

        val (sensitive, plain) = candidates.partition { it.sensitive || SensitiveDetector.check(it.text) != null }
        val regular = if (options.includeSensitive) candidates else plain
        val skippedSensitive = if (options.includeSensitive) 0 else sensitive.size
        val includeVault = VaultExportPolicy.include(
            encryptedContainer = options.format == ExportFormat.ENCRYPTED_ZIP,
            explicitOptIn = options.includeVault,
        )
        val vaultRows = if (includeVault) options.vaultEntries else emptyList()
        val skippedVault = if (!includeVault && options.includeVault) options.vaultEntries.size else 0
        val (regularOut, vaultOut) = LaneIds.separate(regular, vaultRows)
        val chosen = regularOut + vaultOut
        val lanes = vaultOut.associate { it.id to LANE_VAULT }

        val now = clock()
        val meta = ExportMeta(now, appVersion, skippedUnsupported = unsupported, skippedSensitive = skippedSensitive)
        val bytes = when (options.format) {
            ExportFormat.ENCRYPTED_ZIP -> {
                val pw = options.password ?: throw BackupException.WeakPassword(BackupCrypto.MIN_PASSWORD_LENGTH)
                BackupWriter.writeEncrypted(chosen, meta, pw, lanes = lanes)
            }
            ExportFormat.ZIP -> BackupWriter.writeZip(chosen, meta, lanes)
            ExportFormat.JSON -> BackupWriter.writeJson(chosen, meta, lanes)
            ExportFormat.MARKDOWN -> BackupWriter.writeMarkdown(chosen, meta)
            ExportFormat.TEXT -> BackupWriter.writeText(chosen)
        }
        ExportResult(
            bytes = bytes,
            suggestedName = "victorious-clipboard-${FILE_STAMP.format(Instant.ofEpochMilli(now))}.${options.format.extension}",
            mime = options.format.mime,
            itemCount = chosen.size,
            skippedSensitive = skippedSensitive + skippedVault,
            skippedUnsupported = unsupported,
        )
    }

    /**
     * Writes a `.zip` the user can save with the system file picker.
     * Regular-only may be plain. Any scope that includes the vault requires a password and stores item text inside
     * `content.vkbk`. A locked vault is not read.
     */
    suspend fun exportZip(
        scope: ZipScope,
        password: CharArray?,
        iterations: Int = BackupCrypto.DEFAULT_ITERATIONS,
        random: SecureRandom = SecureRandom(),
    ): ZipExportResult = withContext(Dispatchers.Default) {
        val pw = password?.takeIf { it.isNotEmpty() }
        if (ZipExportPolicy.requiresPassword(scope) && pw == null) throw BackupException.EncryptionRequired()
        if (pw != null && pw.size < BackupCrypto.MIN_PASSWORD_LENGTH) {
            throw BackupException.WeakPassword(BackupCrypto.MIN_PASSWORD_LENGTH)
        }
        if (scope.includesVault()) {
            val store = vault ?: throw BackupException.VaultLocked()
            if (store.isLocked()) throw BackupException.VaultLocked()
        }
        val snap = if (scope.includesRegular()) repo.snapshot() else HistorySnapshot(emptyList(), emptySet())
        val unsupported = if (scope.includesRegular()) snap.entries.size - snap.textEntries.size else 0
        val regular = if (scope.includesRegular()) snap.textEntries else emptyList()
        val vaultRows = if (scope.includesVault()) {
            vault?.snapshot() ?: throw BackupException.VaultLocked()
        } else {
            emptyList()
        }
        val (regularOut, vaultOut) = LaneIds.separate(regular, vaultRows)
        val lanes = vaultOut.associate { it.id to LANE_VAULT }
        val now = clock()
        val meta = ExportMeta(now, appVersion, skippedUnsupported = unsupported)
        val encrypted = pw != null
        val bytes = if (pw == null) {
            BackupWriter.writeZip(regularOut + vaultOut, meta, lanes)
        } else {
            BackupWriter.writePasswordZip(regularOut + vaultOut, meta, lanes, pw, random, iterations)
        }
        ZipExportResult(
            bytes = bytes,
            suggestedName = "victorious-clipboard-${FILE_STAMP.format(Instant.ofEpochMilli(now))}.zip",
            mime = BackupWriter.MIME_ZIP,
            regularCount = regularOut.size,
            vaultCount = vaultOut.size,
            encrypted = encrypted,
            skippedUnsupported = unsupported,
        )
    }

    private fun splitLanes(parsed: ParsedBackup): Pair<List<ClipEntry>, List<ClipEntry>> {
        val regular = ArrayList<ClipEntry>()
        val vaultRows = ArrayList<ClipEntry>()
        for (entry in parsed.entries) {
            when (parsed.laneOf(entry.id)) {
                LANE_VAULT -> vaultRows.add(entry)
                LANE_REGULAR -> regular.add(entry)
                else -> throw BackupException.Malformed("unknown clipboard lane")
            }
        }
        return regular to vaultRows
    }

    /** Parses and validates a backup. May take a second or more for encrypted files (the KDF is slow on purpose). */
    suspend fun inspect(bytes: ByteArray, password: CharArray?): Inspection = withContext(Dispatchers.Default) {
        val kind = BackupReader.detect(bytes)
        Inspection(BackupReader.read(bytes, password), kind)
    }

    /** What importing [inspection] in [mode] would do to the regular history **right now**. Changes nothing. */
    suspend fun preview(inspection: Inspection, mode: ImportMode): ImportPlan =
        previewLanes(inspection, mode).regular

    /**
     * Plans each clipboard separately. Vault rows are never planned into the regular history.
     * A file with no vault rows leaves the vault untouched, even in replace mode.
     */
    suspend fun previewLanes(inspection: Inspection, mode: ImportMode): LaneImport = withContext(Dispatchers.Default) {
        val (regularIncoming, vaultIncoming) = splitLanes(inspection.parsed)
        if (vaultIncoming.isNotEmpty()) {
            val store = vault ?: throw BackupException.VaultLocked()
            if (store.isLocked()) throw BackupException.VaultLocked()
        }
        val regularPlan = ImportPlanner.plan(repo.snapshot().entries, regularIncoming, mode, policy())
        val vaultPlan = if (vaultIncoming.isEmpty()) {
            null
        } else {
            ImportPlanner.plan(vault!!.snapshot(), vaultIncoming, mode, policy())
        }
        LaneImport(regularPlan, vaultPlan)
    }

    suspend fun restore(
        inspection: Inspection,
        mode: ImportMode,
        confirmation: Confirmation?,
        onStage: (RestoreStage) -> Unit = {},
    ): RestoreResult {
        onStage(RestoreStage.PLANNING)
        val planned = previewLanes(inspection, mode)
        if (planned.requiresConfirmation && (confirmation == null || !confirmation.covers(planned))) {
            throw ConfirmationRequiredException(planned.regular, planned)
        }
        onStage(RestoreStage.APPLYING)
        // Each store commits on its own. The vault goes first so a failure there leaves regular history as it was.
        planned.vault?.let { vault!!.apply(it) }
        repo.apply(planned.regular)
        onStage(RestoreStage.DONE)
        val vaultPlan = planned.vault
        return RestoreResult(
            added = planned.regular.added.size + (vaultPlan?.added?.size ?: 0),
            merged = planned.regular.mergedExisting.size + (vaultPlan?.mergedExisting?.size ?: 0),
            skippedDuplicates = planned.regular.skippedDuplicates.size + (vaultPlan?.skippedDuplicates?.size ?: 0),
            removedExisting = planned.regular.removedExisting.size + (vaultPlan?.removedExisting?.size ?: 0),
            droppedIncoming = planned.regular.droppedIncoming.size + (vaultPlan?.droppedIncoming?.size ?: 0),
            finalCount = planned.regular.finalEntries.size + (vaultPlan?.finalEntries?.size ?: 0),
        )
    }

    private companion object {
        val FILE_STAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC)
    }
}
