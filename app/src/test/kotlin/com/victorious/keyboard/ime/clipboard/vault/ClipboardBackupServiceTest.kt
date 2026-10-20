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

package com.victorious.keyboard.ime.clipboard.vault

import com.victorious.keyboard.ime.clipboard.backup.BackupException
import com.victorious.keyboard.ime.clipboard.backup.ImportMode
import com.victorious.keyboard.ime.clipboard.backup.ImportPlan
import com.victorious.keyboard.ime.clipboard.history.ClipEntry
import com.victorious.keyboard.ime.clipboard.history.HistoryPolicy
import com.victorious.keyboard.ime.clipboard.history.entry
import com.victorious.keyboard.ime.clipboard.history.fullHistory
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldMatch
import java.io.IOException

/** In-memory repository with transactional semantics: a failing [apply] leaves [entries] untouched. */
private class FakeRepo(initial: List<ClipEntry>, private val media: Set<String> = emptySet()) :
    ClipboardEntryRepository {
    var entries: List<ClipEntry> = initial
    var failOnApply: Boolean = false
    var applyCount = 0

    override suspend fun snapshot() = HistorySnapshot(entries, media)

    override suspend fun apply(plan: ImportPlan) {
        val next = LinkedHashMap<String, ClipEntry>()
        entries.forEach { next[it.id] = it }
        plan.deleteIds.forEach { next.remove(it) }
        plan.upserts.forEach { next[it.id] = it }
        if (failOnApply) throw IOException("simulated failure before commit")
        entries = next.values.toList()
        applyCount++
    }
}

private fun service(repo: ClipboardEntryRepository, now: Long = 1_800_000_000_000L) =
    ClipboardBackupService(repo, appVersion = "0.1.0-test", clock = { now })

private val pw = "a long enough password".toCharArray()

class ClipboardBackupServiceTest : FunSpec({
    context("export") {
        test("filename and counts") {
            val repo = FakeRepo(fullHistory(5))
            val r = service(repo).export(ExportOptions(ExportFormat.JSON))
            r.itemCount shouldBe 5
            r.suggestedName shouldMatch Regex("victorious-clipboard-\\d{8}-\\d{6}\\.json")
            r.mime shouldBe "application/json"
        }

        test("entries Android marked sensitive and credential-looking entries are left out by default") {
            val pwText = "db_password = hunter2hunter2"
            val repo = FakeRepo(listOf(
                entry(1, "ordinary note"),
                entry(2, "flagged by android").copy(sensitive = true),
                entry(3, pwText),
            ))
            val r = service(repo).export(ExportOptions(ExportFormat.JSON))
            r.itemCount shouldBe 1
            r.skippedSensitive shouldBe 2
            String(r.bytes).contains("hunter2") shouldBe false

            val all = service(repo).export(ExportOptions(ExportFormat.JSON, includeSensitive = true))
            all.itemCount shouldBe 3
            all.skippedSensitive shouldBe 0
        }

        test("only selected ids are exported") {
            val repo = FakeRepo(fullHistory(10))
            val r = service(repo).export(ExportOptions(ExportFormat.ZIP, onlyIds = setOf("id-00002", "id-00003")))
            r.itemCount shouldBe 2
            service(FakeRepo(emptyList())).inspect(r.bytes, null).parsed.entries.map { it.id }.toSet() shouldBe
                setOf("id-00002", "id-00003")
        }

        test("image/video rows are not exported and are reported as skipped") {
            val repo = FakeRepo(fullHistory(4) + entry(99, text = ""), media = setOf("id-00099"))
            val r = service(repo).export(ExportOptions(ExportFormat.JSON))
            r.itemCount shouldBe 4
            r.skippedUnsupported shouldBe 1
        }

        test("an encrypted export requires a strong enough password") {
            val repo = FakeRepo(fullHistory(2))
            shouldThrow<BackupException.WeakPassword> { service(repo).export(ExportOptions(ExportFormat.ENCRYPTED_ZIP)) }
            shouldThrow<BackupException.WeakPassword> {
                service(repo).export(ExportOptions(ExportFormat.ENCRYPTED_ZIP, "short".toCharArray()))
            }
        }

        test("markdown and text exports are not restorable formats") {
            ExportFormat.MARKDOWN.restorable shouldBe false
            ExportFormat.TEXT.restorable shouldBe false
            ExportFormat.ENCRYPTED_ZIP.restorable shouldBe true
        }
    }

    context("restore") {
        test("encrypted export → fresh install → REPLACE restores everything") {
            val source = FakeRepo(fullHistory(50, pinnedEvery = 10))
            val file = service(source).export(ExportOptions(ExportFormat.ENCRYPTED_ZIP, pw)).bytes

            val target = FakeRepo(emptyList())
            val svc = service(target)
            val inspection = svc.inspect(file, pw)
            val stages = mutableListOf<RestoreStage>()
            val result = svc.restore(inspection, ImportMode.REPLACE, null) { stages += it }
            target.entries.sortedBy { it.id } shouldBe source.entries.sortedBy { it.id }
            result.added shouldBe 50
            result.finalCount shouldBe 50
            stages shouldBe listOf(RestoreStage.PLANNING, RestoreStage.APPLYING, RestoreStage.DONE)
        }

        test("inspecting and previewing never modify the history") {
            val source = FakeRepo(fullHistory(5))
            val file = service(source).export(ExportOptions(ExportFormat.ZIP)).bytes
            val target = FakeRepo(listOf(entry(900, "mine")))
            val svc = service(target)
            val inspection = svc.inspect(file, null)
            svc.preview(inspection, ImportMode.REPLACE)
            svc.preview(inspection, ImportMode.MERGE)
            target.entries.map { it.id } shouldBe listOf("id-00900")
            target.applyCount shouldBe 0
        }

        test("REPLACE over existing data needs confirmation, and nothing changes without it") {
            val file = service(FakeRepo(fullHistory(3))).export(ExportOptions(ExportFormat.JSON)).bytes
            val target = FakeRepo(listOf(entry(900, "keep me", pinned = true)))
            val svc = service(target)
            val inspection = svc.inspect(file, null)
            shouldThrow<ConfirmationRequiredException> { svc.restore(inspection, ImportMode.REPLACE, null) }
            target.entries.map { it.id } shouldBe listOf("id-00900")

            val plan = svc.preview(inspection, ImportMode.REPLACE)
            plan.requiresConfirmation shouldBe true
            svc.restore(inspection, ImportMode.REPLACE, Confirmation.of(plan))
            target.entries.map { it.id }.toSet() shouldBe setOf("id-00000", "id-00001", "id-00002")
        }

        test("confirmation is tied to what was previewed: if more would be deleted now, it asks again") {
            val backup = (1000 until 1003).map { entry(it, createdAtMs = 900_000L + it) }
            val file = service(FakeRepo(backup)).export(ExportOptions(ExportFormat.JSON)).bytes

            val target = FakeRepo((0 until 500).map { entry(it, createdAtMs = 1_000L + it) })
            val svc = service(target)
            val inspection = svc.inspect(file, null)
            val previewed = svc.preview(inspection, ImportMode.MERGE)
            previewed.removedExisting shouldHaveSize 3
            val confirmation = Confirmation.of(previewed)

            // Between preview and confirm, a pinned-free 'newer' state appears: the oldest 3 are now different.
            target.entries = target.entries.filter { it.id !in setOf("id-00000", "id-00001", "id-00002") } +
                (600 until 603).map { entry(it, createdAtMs = 50L + it) } // older entries that would now be evicted
            shouldThrow<ConfirmationRequiredException> { svc.restore(inspection, ImportMode.MERGE, confirmation) }
            target.applyCount shouldBe 0
        }

        test("a failing apply leaves the history exactly as it was (rollback)") {
            val file = service(FakeRepo(fullHistory(10))).export(ExportOptions(ExportFormat.JSON)).bytes
            val existing = listOf(entry(900, "mine"), entry(901, "also mine", pinned = true))
            val target = FakeRepo(existing)
            target.failOnApply = true
            val svc = service(target)
            val inspection = svc.inspect(file, null)
            val plan = svc.preview(inspection, ImportMode.MERGE)
            shouldThrow<IOException> { svc.restore(inspection, ImportMode.MERGE, Confirmation.of(plan)) }
            target.entries shouldBe existing
        }

        test("malformed or tampered files are rejected before anything is planned") {
            val target = FakeRepo(fullHistory(3))
            val svc = service(target)
            shouldThrow<BackupException.NotABackup> { svc.inspect("hello".toByteArray(), null) }
            val enc = service(FakeRepo(fullHistory(3))).export(ExportOptions(ExportFormat.ENCRYPTED_ZIP, pw)).bytes
            enc[enc.size - 1] = (enc[enc.size - 1].toInt() xor 1).toByte()
            shouldThrow<BackupException.WrongPasswordOrTampered> { svc.inspect(enc, pw) }
            shouldThrow<BackupException.PasswordRequired> { svc.inspect(enc, null) }
            target.applyCount shouldBe 0
        }

        test("a backup over the cap previews the exact loss, and only restores after consent") {
            val big = FakeRepo(fullHistory(500))
            val file = service(big).export(ExportOptions(ExportFormat.ZIP)).bytes
            val target = FakeRepo(emptyList())
            val svc = ClipboardBackupService(target, "t", policy = { HistoryPolicy(maxEntries = 100) })
            val inspection = svc.inspect(file, null)
            val plan = svc.preview(inspection, ImportMode.REPLACE)
            plan.droppedIncoming shouldHaveSize 400
            plan.finalEntries shouldHaveSize 100
            shouldThrow<ConfirmationRequiredException> { svc.restore(inspection, ImportMode.REPLACE, null) }
            svc.restore(inspection, ImportMode.REPLACE, Confirmation.of(plan))
            target.entries shouldHaveSize 100
        }
    }
})
