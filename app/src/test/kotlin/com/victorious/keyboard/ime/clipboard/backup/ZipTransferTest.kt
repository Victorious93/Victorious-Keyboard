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

package com.victorious.keyboard.ime.clipboard.backup

import com.victorious.keyboard.ime.clipboard.history.ClipEntry
import com.victorious.keyboard.ime.clipboard.vault.ClipboardBackupService
import com.victorious.keyboard.ime.clipboard.vault.ClipboardEntryRepository
import com.victorious.keyboard.ime.clipboard.vault.Confirmation
import com.victorious.keyboard.ime.clipboard.vault.HistorySnapshot
import com.victorious.keyboard.ime.clipboard.vault.VaultEntryRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain

private const val ITERS = 100_000
private const val SECRET = "VAULTPLAINTEXTTOKEN"
private val password = "correct horse".toCharArray()

private class MemoryHistory(initial: List<ClipEntry> = emptyList(), private val media: Set<String> = emptySet()) :
    ClipboardEntryRepository {
    var entries: List<ClipEntry> = initial
    override suspend fun snapshot() = HistorySnapshot(entries, media)
    override suspend fun apply(plan: ImportPlan) {
        val next = LinkedHashMap<String, ClipEntry>()
        entries.forEach { next[it.id] = it }
        plan.deleteIds.forEach { next.remove(it) }
        plan.upserts.forEach { next[it.id] = it }
        entries = next.values.toList()
    }
}

private class MemoryVault(initial: List<ClipEntry> = emptyList(), var locked: Boolean = false) : VaultEntryRepository {
    var entries: List<ClipEntry> = initial
    override fun isLocked() = locked
    override suspend fun snapshot(): List<ClipEntry> {
        if (locked) throw BackupException.VaultLocked()
        return entries
    }
    override suspend fun apply(plan: ImportPlan) {
        if (locked) throw BackupException.VaultLocked()
        val next = LinkedHashMap<String, ClipEntry>()
        entries.forEach { next[it.id] = it }
        plan.deleteIds.forEach { next.remove(it) }
        plan.upserts.forEach { next[it.id] = it }
        entries = next.values.toList()
    }
}

private fun service(history: MemoryHistory, vault: MemoryVault? = null) =
    ClipboardBackupService(history, appVersion = "0.1.0-test", clock = { 1_700_000_000_000L }, vault = vault)

class ZipTransferTest : FunSpec({
    test("a regular ZIP round-trips pins and timestamps without a password") {
        val pinned = ClipEntry(id = "note", text = "shopping list", createdAtMs = 50, pinned = true, editedAtMs = 80)
        val history = MemoryHistory(listOf(pinned))
        val exported = service(history).exportZip(ZipScope.REGULAR, password = null)
        exported.encrypted shouldBe false
        exported.regularCount shouldBe 1
        exported.vaultCount shouldBe 0
        exported.suggestedName.endsWith(".zip") shouldBe true
        val parsed = BackupReader.read(exported.bytes, null)
        parsed.passwordProtected shouldBe false
        parsed.laneOf("note") shouldBe LANE_REGULAR
        parsed.entries.single().pinned shouldBe true
        parsed.entries.single().createdAtMs shouldBe 50
        parsed.entries.single().editedAtMs shouldBe 80
        parsed.manifest.regularCount shouldBe 1
        parsed.manifest.vaultCount shouldBe 0
    }

    test("vault and both require a password and hide item text") {
        val history = MemoryHistory(listOf(ClipEntry(id = "reg", text = "shopping list", createdAtMs = 1)))
        val vault = MemoryVault(listOf(ClipEntry(id = "sec", text = SECRET, createdAtMs = 2, pinned = true)))
        val svc = service(history, vault)
        shouldThrow<BackupException.EncryptionRequired> { svc.exportZip(ZipScope.VAULT, null) }
        shouldThrow<BackupException.EncryptionRequired> { svc.exportZip(ZipScope.BOTH, charArrayOf()) }
        val emptyVault = MemoryVault()
        shouldThrow<BackupException.EncryptionRequired> { service(history, emptyVault).exportZip(ZipScope.VAULT, null) }
        shouldThrow<BackupException.WeakPassword> { svc.exportZip(ZipScope.BOTH, "short".toCharArray(), iterations = ITERS) }

        val exported = svc.exportZip(ZipScope.BOTH, password.copyOf(), iterations = ITERS)
        exported.encrypted shouldBe true
        exported.regularCount shouldBe 1
        exported.vaultCount shouldBe 1
        val raw = exported.bytes.toString(Charsets.ISO_8859_1)
        raw shouldNotContain SECRET
        raw shouldNotContain "shopping list"
        raw shouldNotContain "correct horse"
        val parsed = BackupReader.read(exported.bytes, password.copyOf())
        parsed.passwordProtected shouldBe true
        parsed.laneOf("sec") shouldBe LANE_VAULT
        parsed.laneOf("reg") shouldBe LANE_REGULAR
        parsed.entries.single { it.id == "sec" }.pinned shouldBe true
        parsed.entries.single { it.id == "sec" }.createdAtMs shouldBe 2
        parsed.manifest.format shouldBe BACKUP_FORMAT_NAME
        parsed.manifest.formatVersion shouldBe BACKUP_FORMAT_VERSION
    }

    test("a wrong password and a corrupt zip are rejected") {
        val vault = MemoryVault(listOf(ClipEntry(id = "sec", text = SECRET, createdAtMs = 2)))
        val exported = service(MemoryHistory(), vault).exportZip(ZipScope.VAULT, password.copyOf(), iterations = ITERS)
        shouldThrow<BackupException.PasswordRequired> { BackupReader.read(exported.bytes, null) }
        shouldThrow<BackupException.WrongPasswordOrTampered> {
            BackupReader.read(exported.bytes, "other horse".toCharArray(), )
        }
        val flipped = exported.bytes.copyOf()
        val at = flipped.size / 2
        flipped[at] = (flipped[at].toInt() xor 0xFF).toByte()
        shouldThrow<BackupException> { BackupReader.read(flipped, password.copyOf()) }
        shouldThrow<BackupException> { BackupReader.read(exported.bytes.copyOf(exported.bytes.size / 2), password.copyOf()) }
        shouldThrow<BackupException> { BackupReader.read(byteArrayOf(0x50, 0x4B, 3, 4, 1, 2, 3), null) }
    }

    test("merge and replace keep each clipboard separate") {
        val history = MemoryHistory(listOf(ClipEntry(id = "reg", text = "shopping list", createdAtMs = 1)))
        val vault = MemoryVault(listOf(ClipEntry(id = "sec", text = SECRET, createdAtMs = 2, pinned = true)))
        val svc = service(history, vault)
        val file = svc.exportZip(ZipScope.BOTH, password.copyOf(), iterations = ITERS)
        history.entries = listOf(ClipEntry(id = "keep", text = "shopping list", createdAtMs = 1, pinned = true))
        vault.entries = listOf(ClipEntry(id = "old", text = "old secret", createdAtMs = 1, pinned = true))
        val inspection = svc.inspect(file.bytes, password.copyOf())
        val merged = svc.previewLanes(inspection, ImportMode.MERGE)
        merged.regular.added.map { it.id } shouldBe emptyList()
        merged.vault?.added?.map { it.id } shouldBe listOf("sec")
        svc.restore(inspection, ImportMode.MERGE, Confirmation.of(merged.regular, merged.vault))
        history.entries.map { it.text } shouldContainExactlyInAnyOrder listOf("shopping list")
        vault.entries.map { it.id } shouldContainExactlyInAnyOrder listOf("old", "sec")
        history.entries.none { it.text == SECRET } shouldBe true

        val replaced = svc.previewLanes(inspection, ImportMode.REPLACE)
        svc.restore(inspection, ImportMode.REPLACE, Confirmation.of(replaced.regular, replaced.vault))
        history.entries.map { it.id } shouldBe listOf("reg")
        vault.entries.map { it.text } shouldBe listOf(SECRET)
    }

    test("a vault id that collides with a regular id is renamed") {
        val (regular, vaultRows) = LaneIds.separate(
            listOf(ClipEntry(id = "same", text = "shopping list", createdAtMs = 1)),
            listOf(ClipEntry(id = "same", text = SECRET, createdAtMs = 2)),
        )
        regular.single().id shouldBe "same"
        vaultRows.single().id shouldBe "same-v"
        vaultRows.single().text shouldBe SECRET
    }

    test("a locked vault is not exported or imported, and images are skipped") {
        val history = MemoryHistory(
            listOf(ClipEntry(id = "note", text = "shopping list", createdAtMs = 1)),
            media = setOf("pic"),
        )
        history.entries = history.entries + ClipEntry(id = "pic", text = "", createdAtMs = 2)
        val vault = MemoryVault(listOf(ClipEntry(id = "sec", text = SECRET, createdAtMs = 3)), locked = true)
        val svc = service(history, vault)
        shouldThrow<BackupException.VaultLocked> {
            svc.exportZip(ZipScope.VAULT, password.copyOf(), iterations = ITERS)
        }
        val regular = svc.exportZip(ZipScope.REGULAR, null)
        regular.skippedUnsupported shouldBe 1
        regular.bytes.toString(Charsets.UTF_8) shouldNotContain SECRET
        BackupReader.read(regular.bytes, null).entries.map { it.id } shouldBe listOf("note")

        vault.locked = false
        val both = svc.exportZip(ZipScope.BOTH, password.copyOf(), iterations = ITERS)
        vault.locked = true
        val inspection = svc.inspect(both.bytes, password.copyOf())
        val before = history.entries.map { it.id }
        shouldThrow<BackupException.VaultLocked> { svc.restore(inspection, ImportMode.REPLACE, null) }
        history.entries.map { it.id } shouldBe before
    }
})
