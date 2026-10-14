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

package dev.patrickgold.florisboard.ime.clipboard.lanes

import dev.patrickgold.florisboard.ime.clipboard.backup.ImportPlan
import dev.patrickgold.florisboard.ime.clipboard.history.ClipEntry
import dev.patrickgold.florisboard.ime.clipboard.history.HistoryPolicy
import dev.patrickgold.florisboard.ime.clipboard.history.InsertPlan
import dev.patrickgold.florisboard.ime.clipboard.history.planInsert
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

/** Encrypts one vault value. [context] is bound so a payload moved to another row does not decrypt. */
interface VaultTextCipher {
    fun encrypt(plain: String, context: String): String

    fun decrypt(stored: String, context: String): String
}

/** The vault file is not a version this reader understands. It is left on disk untouched. */
class VaultFileUnreadableException : Exception("The sensitive clipboard file could not be read.")

/**
 * One stored vault row. [payload] is ciphertext. This type's string form does not include it.
 */
data class VaultRecord(
    val id: String,
    val createdAtMs: Long,
    val pinned: Boolean,
    val reason: VaultReason,
    val payload: String,
) {
    override fun toString(): String = "VaultRecord(id=$id, pinned=$pinned, reason=$reason)"
}

/** Decrypted view used only while the vault is unlocked. Its string form does not include [text]. */
data class VaultPlain(
    val id: String,
    val text: String,
    val createdAtMs: Long,
    val pinned: Boolean,
    val reason: VaultReason,
) {
    fun toEntry(): ClipEntry = ClipEntry(id = id, text = text, createdAtMs = createdAtMs, pinned = pinned)

    override fun toString(): String = "VaultPlain(id=$id, chars=${text.length}, pinned=$pinned, reason=$reason)"
}

object VaultFileCodec {
    const val HEADER = "vk-vault 1"
    const val MAX_BYTES = 2_000_000

    fun encode(records: List<VaultRecord>): String = buildString {
        append(HEADER)
        append('\n')
        for (record in records) {
            require(!record.payload.contains('\n') && !record.payload.contains('\t'))
            append(record.id)
            append('\t')
            append(record.createdAtMs)
            append('\t')
            append(if (record.pinned) '1' else '0')
            append('\t')
            append(record.reason.name)
            append('\t')
            append(record.payload)
            append('\n')
        }
    }

    /** Null when the header is missing or the file is too large. Bad rows are skipped, not decrypted. */
    fun decode(raw: String): List<VaultRecord>? {
        if (raw.length > MAX_BYTES) return null
        val lines = raw.split('\n')
        if (lines.firstOrNull()?.trim() != HEADER) return null
        val out = ArrayList<VaultRecord>()
        for (line in lines.drop(1)) {
            if (line.isBlank()) continue
            val parts = line.split('\t', limit = 5)
            if (parts.size != 5) return null
            val created = parts[1].toLongOrNull() ?: return null
            val pinned = when (parts[2]) {
                "1" -> true
                "0" -> false
                else -> return null
            }
            val reason = VaultReason.entries.firstOrNull { it.name == parts[3] } ?: return null
            if (parts[0].isBlank() || parts[4].isBlank()) return null
            out.add(VaultRecord(parts[0], created, pinned, reason, parts[4]))
        }
        return out
    }
}

/**
 * Separate on-disk store for the sensitive clipboard. Plaintext is encrypted before it is written
 * and is not kept in the file. A damaged file is not overwritten.
 */
class SensitiveVault(
    private val file: File,
    private val cipher: VaultTextCipher,
    private val policy: () -> HistoryPolicy = { HistoryPolicy() },
    private val newId: () -> String = { UUID.randomUUID().toString() },
    private val clock: () -> Long = System::currentTimeMillis,
) {
    @Synchronized
    fun count(): Int = readRecords().size

    @Synchronized
    fun loadPlain(): List<VaultPlain> = readRecords().mapNotNull { it.decryptOrNull() }

    @Synchronized
    fun offer(text: String, reason: VaultReason): InsertPlan {
        val records = readRecords()
        val plains = records.mapNotNull { it.decryptOrNull() }
        val candidate = ClipEntry(id = newId(), text = text, createdAtMs = clock())
        val plan = policy().planInsert(plains.map { it.toEntry() }, candidate)
        when (plan) {
            is InsertPlan.Add -> {
                val kept = records.filter { it.id !in plan.evictIds }
                val created = VaultRecord(
                    id = candidate.id,
                    createdAtMs = candidate.createdAtMs,
                    pinned = false,
                    reason = reason,
                    payload = cipher.encrypt(text, candidate.id),
                )
                write(kept + created)
            }
            is InsertPlan.MoveToTop -> {
                write(records.map { record ->
                    if (record.id == plan.existingId) record.copy(createdAtMs = clock()) else record
                })
            }
            is InsertPlan.IgnoreDuplicate,
            InsertPlan.RejectedBlank,
            is InsertPlan.RejectedTooLarge,
            is InsertPlan.RejectedHistoryFullOfPinned,
            -> Unit
        }
        return plan
    }

    /** Rewrites one row in place. The id, pin, and reason stay, so the text remains a vault item. */
    @Synchronized
    fun replaceText(id: String, text: String): Boolean {
        if (text.isBlank() || text.length > policy().maxItemChars) return false
        val records = readRecords().toMutableList()
        val index = records.indexOfFirst { it.id == id }
        if (index < 0) return false
        records[index] = records[index].copy(payload = cipher.encrypt(text, id))
        write(records)
        return true
    }

    @Synchronized
    fun setPinned(id: String, pinned: Boolean) {
        val records = readRecords()
        if (records.none { it.id == id }) return
        write(records.map { if (it.id == id) it.copy(pinned = pinned) else it })
    }

    /** Removes the row and returns its plaintext, or null when the id is absent. */
    @Synchronized
    fun remove(id: String): VaultPlain? {
        val records = readRecords()
        val target = records.firstOrNull { it.id == id } ?: return null
        write(records.filter { it.id != id })
        return target.decryptOrNull()
    }

    /**
     * Applies a backup import. Existing rows keep their [VaultReason]; new rows are [VaultReason.IMPORTED].
     * The file is replaced in one atomic move. Item text is encrypted and is not logged.
     */
    @Synchronized
    fun applyImport(plan: ImportPlan) {
        val records = readRecords()
        val reasons = records.associate { it.id to it.reason }
        val byId = records.associateBy { it.id }.toMutableMap()
        for (id in plan.deleteIds) byId.remove(id)
        for (entry in plan.upserts) {
            byId[entry.id] = VaultRecord(
                id = entry.id,
                createdAtMs = entry.createdAtMs,
                pinned = entry.pinned,
                reason = reasons[entry.id] ?: VaultReason.IMPORTED,
                payload = cipher.encrypt(entry.text, entry.id),
            )
        }
        val ordered = ArrayList<VaultRecord>(byId.size)
        val seen = HashSet<String>()
        for (record in records) {
            val next = byId[record.id] ?: continue
            if (seen.add(next.id)) ordered.add(next)
        }
        for (entry in plan.upserts) {
            val next = byId[entry.id] ?: continue
            if (seen.add(next.id)) ordered.add(next)
        }
        write(ordered)
    }

    /** Deletes unpinned items whose age has reached [ttlMs]. Returns how many were removed. */
    @Synchronized
    fun deleteExpired(nowMs: Long, ttlMs: Long): Int {
        val records = readRecords()
        val kept = records.filterNot { VaultExpiry.isDue(it.createdAtMs, nowMs, ttlMs, it.pinned) }
        val removed = records.size - kept.size
        if (removed > 0) write(kept)
        return removed
    }

    private fun readRecords(): List<VaultRecord> {
        if (!file.exists() || file.length() == 0L) return emptyList()
        if (file.length() > VaultFileCodec.MAX_BYTES) throw VaultFileUnreadableException()
        return VaultFileCodec.decode(file.readText()) ?: throw VaultFileUnreadableException()
    }

    private fun write(records: List<VaultRecord>) {
        val text = VaultFileCodec.encode(records)
        file.parentFile?.mkdirs()
        val tmp = File(file.path + ".tmp")
        tmp.writeText(text)
        Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    }

    private fun VaultRecord.decryptOrNull(): VaultPlain? = try {
        VaultPlain(id, cipher.decrypt(payload, id), createdAtMs, pinned, reason)
    } catch (_: Exception) {
        null
    }
}
