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
import dev.patrickgold.florisboard.ime.clipboard.backup.ParsedBackup
import dev.patrickgold.florisboard.ime.clipboard.history.ClipEntry
import dev.patrickgold.florisboard.ime.clipboard.history.HistoryPolicy
import dev.patrickgold.florisboard.ime.clipboard.history.SensitiveDetector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
)

class ExportResult(
    val bytes: ByteArray,
    val suggestedName: String,
    val mime: String,
    val itemCount: Int,
    val skippedSensitive: Int,
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
    }

    fun covers(plan: ImportPlan): Boolean =
        plan.removedExisting.all { it.id in removedIds } &&
            plan.droppedIncoming.filter { it.reason == DropReason.OVER_CAPACITY }.all { it.entry.id in droppedIds }
}

/** The plan needs consent that was not given, or the history changed since the user confirmed. */
class ConfirmationRequiredException(val plan: ImportPlan) :
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
) {
    /** The default file name offered in the system file picker. */
    fun suggestedFileName(format: ExportFormat): String =
        "victorious-clipboard-${FILE_STAMP.format(Instant.ofEpochMilli(clock()))}.${format.extension}"

    suspend fun export(options: ExportOptions): ExportResult = withContext(Dispatchers.Default) {
        val snap = repo.snapshot()
        val unsupported = snap.entries.size - snap.textEntries.size
        var candidates = snap.textEntries
        options.onlyIds?.let { ids -> candidates = candidates.filter { it.id in ids } }

        val (sensitive, plain) = candidates.partition { it.sensitive || SensitiveDetector.check(it.text) != null }
        val chosen = if (options.includeSensitive) candidates else plain
        val skippedSensitive = if (options.includeSensitive) 0 else sensitive.size

        val now = clock()
        val meta = ExportMeta(now, appVersion, skippedUnsupported = unsupported, skippedSensitive = skippedSensitive)
        val bytes = when (options.format) {
            ExportFormat.ENCRYPTED_ZIP -> {
                val pw = options.password ?: throw BackupException.WeakPassword(BackupCrypto.MIN_PASSWORD_LENGTH)
                BackupWriter.writeEncrypted(chosen, meta, pw)
            }
            ExportFormat.ZIP -> BackupWriter.writeZip(chosen, meta)
            ExportFormat.JSON -> BackupWriter.writeJson(chosen, meta)
            ExportFormat.MARKDOWN -> BackupWriter.writeMarkdown(chosen, meta)
            ExportFormat.TEXT -> BackupWriter.writeText(chosen)
        }
        ExportResult(
            bytes = bytes,
            suggestedName = "victorious-clipboard-${FILE_STAMP.format(Instant.ofEpochMilli(now))}.${options.format.extension}",
            mime = options.format.mime,
            itemCount = chosen.size,
            skippedSensitive = skippedSensitive,
            skippedUnsupported = unsupported,
        )
    }

    /** Parses and validates a backup. May take a second or more for encrypted files (the KDF is slow on purpose). */
    suspend fun inspect(bytes: ByteArray, password: CharArray?): Inspection = withContext(Dispatchers.Default) {
        val kind = BackupReader.detect(bytes)
        Inspection(BackupReader.read(bytes, password), kind)
    }

    /** What importing [inspection] in [mode] would do to the history **right now**. Changes nothing. */
    suspend fun preview(inspection: Inspection, mode: ImportMode): ImportPlan = withContext(Dispatchers.Default) {
        ImportPlanner.plan(repo.snapshot().entries, inspection.parsed.entries, mode, policy())
    }

    suspend fun restore(
        inspection: Inspection,
        mode: ImportMode,
        confirmation: Confirmation?,
        onStage: (RestoreStage) -> Unit = {},
    ): RestoreResult {
        onStage(RestoreStage.PLANNING)
        val plan = preview(inspection, mode)
        if (plan.requiresConfirmation && (confirmation == null || !confirmation.covers(plan))) {
            throw ConfirmationRequiredException(plan)
        }
        onStage(RestoreStage.APPLYING)
        repo.apply(plan)
        onStage(RestoreStage.DONE)
        return RestoreResult(
            added = plan.added.size,
            merged = plan.mergedExisting.size,
            skippedDuplicates = plan.skippedDuplicates.size,
            removedExisting = plan.removedExisting.size,
            droppedIncoming = plan.droppedIncoming.size,
            finalCount = plan.finalEntries.size,
        )
    }

    private companion object {
        val FILE_STAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC)
    }
}
