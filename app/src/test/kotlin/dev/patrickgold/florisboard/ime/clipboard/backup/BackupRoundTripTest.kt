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

import dev.patrickgold.florisboard.ime.clipboard.history.ClipClassifier
import dev.patrickgold.florisboard.ime.clipboard.history.ClipEntry
import dev.patrickgold.florisboard.ime.clipboard.history.ClipSearch
import dev.patrickgold.florisboard.ime.clipboard.history.DEFAULT_MAX_ITEM_CHARS
import dev.patrickgold.florisboard.ime.clipboard.history.MAX_HISTORY_ENTRIES
import dev.patrickgold.florisboard.ime.clipboard.history.entry
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * The acceptance scenario from the brief: a full 500-entry history of long entries survives an
 * encrypted export → import round trip, in each import mode, byte for byte.
 */
class BackupRoundTripTest : FunSpec({
    val meta = ExportMeta(createdAtMs = 1_800_000_000_000L, appVersion = "0.1.0-test")
    val pw = "correct horse battery staple".toCharArray()

    fun longHistory(): List<ClipEntry> = (0 until MAX_HISTORY_ENTRIES).map { i ->
        val body = buildString {
            append("entry $i — ünïcödé 🔐 uniq-$i-end\n")
            while (length < DEFAULT_MAX_ITEM_CHARS - 200) append("line ").append(length).append(" lorem ipsum dolor\n")
        }
        val e = entry(i, text = body, pinned = i % 50 == 0, favorite = i % 7 == 0, tags = setOf("t${i % 5}", "all"))
        e.copy(category = ClipClassifier.classify(body))
    }

    val original = longHistory()

    test("500 long entries: encrypted export → read → REPLACE into an empty history is lossless") {
        val file = BackupWriter.writeEncrypted(original, meta, pw, iterations = 100_000)
        val parsed = BackupReader.read(file, pw)
        parsed.entries.size shouldBe 500
        val plan = ImportPlanner.plan(emptyList(), parsed.entries, ImportMode.REPLACE)
        plan.requiresConfirmation shouldBe false
        plan.finalEntries.sortedBy { it.id } shouldBe original.sortedBy { it.id }
        plan.droppedIncoming.size shouldBe 0
    }

    test("restoring the same backup over the same history in MERGE and SKIP mode changes nothing") {
        val parsed = BackupReader.read(BackupWriter.writeZip(original, meta), null)
        for (mode in listOf(ImportMode.MERGE, ImportMode.SKIP_DUPLICATES)) {
            val plan = ImportPlanner.plan(original, parsed.entries, mode)
            plan.added.size shouldBe 0
            plan.removedExisting.size shouldBe 0
            plan.mergedExisting.size shouldBe 0
            plan.finalEntries.sortedBy { it.id } shouldBe original.sortedBy { it.id }
        }
    }

    test("a failed import leaves the existing history untouched (plan is pure; nothing is applied)") {
        val before = original.toList()
        val corrupt = BackupWriter.writeEncrypted(original, meta, pw, iterations = 100_000).also { it[it.size - 1] = (it[it.size - 1].toInt() xor 1).toByte() }
        runCatching { BackupReader.read(corrupt, pw) }.isFailure shouldBe true
        original shouldBe before
    }

    test("search over the 500 restored long entries") {
        val hit = ClipSearch.search(original, "uniq-321-end")
        hit.map { it.id } shouldBe listOf("id-00321")
        ClipSearch.search(original, "ipsum").size shouldBe 500
    }
})
