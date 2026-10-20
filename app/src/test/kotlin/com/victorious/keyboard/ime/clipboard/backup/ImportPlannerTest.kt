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

import com.victorious.keyboard.ime.clipboard.history.ClipCategory
import com.victorious.keyboard.ime.clipboard.history.ClipEntry
import com.victorious.keyboard.ime.clipboard.history.DEFAULT_MAX_ITEM_CHARS
import com.victorious.keyboard.ime.clipboard.history.HistoryPolicy
import com.victorious.keyboard.ime.clipboard.history.MAX_HISTORY_ENTRIES
import com.victorious.keyboard.ime.clipboard.history.entry
import com.victorious.keyboard.ime.clipboard.history.fullHistory
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.ints.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import kotlin.random.Random

/** Applies a plan the way the database layer does: one transaction, deletes then upserts. */
private fun apply(existing: List<ClipEntry>, plan: ImportPlan): List<ClipEntry> {
    val byId = LinkedHashMap<String, ClipEntry>()
    existing.forEach { byId[it.id] = it }
    plan.deleteIds.forEach { byId.remove(it) }
    plan.upserts.forEach { byId[it.id] = it }
    return byId.values.toList()
}

private fun ids(l: List<ClipEntry>) = l.map { it.id }.toSet()

class ImportPlannerTest : FunSpec({
    val policy = HistoryPolicy()

    context("REPLACE") {
        test("into an empty history needs no confirmation") {
            val incoming = fullHistory(10)
            val plan = ImportPlanner.plan(emptyList(), incoming, ImportMode.REPLACE)
            ids(plan.finalEntries) shouldBe ids(incoming)
            plan.requiresConfirmation shouldBe false
            plan.removedExisting.shouldBeEmpty()
        }

        test("over existing history deletes everything, pinned included, and demands confirmation") {
            val existing = listOf(entry(1, pinned = true), entry(2))
            val incoming = listOf(entry(10), entry(11))
            val plan = ImportPlanner.plan(existing, incoming, ImportMode.REPLACE)
            ids(plan.finalEntries) shouldBe setOf("id-00010", "id-00011")
            plan.requiresConfirmation shouldBe true
            plan.removedPinned shouldBe 1
            ids(apply(existing, plan)) shouldBe ids(plan.finalEntries)
        }

        test("a same-id entry in the backup is an upsert, not a delete") {
            val existing = listOf(entry(1, text = "old"))
            val incoming = listOf(entry(1, text = "new"))
            val plan = ImportPlanner.plan(existing, incoming, ImportMode.REPLACE)
            plan.deleteIds.shouldBeEmpty()
            apply(existing, plan).single().text shouldBe "new"
        }

        test("a backup larger than the cap keeps the newest 500 and reports exactly what is dropped") {
            val incoming = fullHistory(620)
            val plan = ImportPlanner.plan(emptyList(), incoming, ImportMode.REPLACE)
            plan.finalEntries shouldHaveSize 500
            plan.droppedIncoming shouldHaveSize 120
            plan.droppedIncoming.all { it.reason == DropReason.OVER_CAPACITY } shouldBe true
            // The dropped ones are the 120 oldest.
            ids(plan.droppedIncoming.map { it.entry }) shouldBe (0 until 120).map { "id-%05d".format(it) }.toSet()
            plan.requiresConfirmation shouldBe true
        }

        test("pinned incoming entries survive the cap in preference to unpinned ones") {
            val incoming = (0 until 600).map { entry(it, pinned = it < 10) } // the 10 oldest are pinned
            val plan = ImportPlanner.plan(emptyList(), incoming, ImportMode.REPLACE)
            plan.finalEntries shouldHaveSize 500
            plan.finalEntries.count { it.pinned } shouldBe 10
        }
    }

    context("MERGE") {
        test("adds new entries and keeps existing ones") {
            val existing = fullHistory(5)
            val incoming = listOf(entry(100), entry(101))
            val plan = ImportPlanner.plan(existing, incoming, ImportMode.MERGE)
            plan.finalEntries shouldHaveSize 7
            plan.added shouldHaveSize 2
            plan.requiresConfirmation shouldBe false
        }

        test("duplicates by id or by identical text are not added twice") {
            val existing = listOf(entry(1, text = "same text"), entry(2, text = "other"))
            val incoming = listOf(entry(1, text = "same text"), entry(77, text = "other"), entry(78, text = "fresh"))
            val plan = ImportPlanner.plan(existing, incoming, ImportMode.MERGE)
            plan.finalEntries shouldHaveSize 3
            plan.added.map { it.text } shouldBe listOf("fresh")
            plan.skippedDuplicates shouldHaveSize 2
        }

        test("tags are unioned and pin/favourite are OR-ed for duplicates") {
            val existing = listOf(entry(1, text = "x", tags = setOf("a"), pinned = false))
            val incoming = listOf(entry(9, text = "x", tags = setOf("b"), pinned = true, favorite = true))
            val plan = ImportPlanner.plan(existing, incoming, ImportMode.MERGE)
            val m = plan.finalEntries.single()
            m.id shouldBe "id-00001" // the existing identity is kept
            m.tags shouldBe setOf("a", "b")
            m.pinned shouldBe true
            m.favorite shouldBe true
            plan.mergedExisting shouldHaveSize 1
        }

        test("same id with different text: the more recently modified version wins") {
            val existing = listOf(entry(1, text = "mine", createdAtMs = 10))
            val newer = listOf(entry(1, text = "theirs", createdAtMs = 10, editedAtMs = 50))
            val older = listOf(entry(1, text = "stale", createdAtMs = 10, editedAtMs = 5))
            ImportPlanner.plan(existing, newer, ImportMode.MERGE).finalEntries.single().text shouldBe "theirs"
            ImportPlanner.plan(existing, newer, ImportMode.MERGE).conflicts shouldBe 1
            ImportPlanner.plan(existing, older, ImportMode.MERGE).finalEntries.single().text shouldBe "mine"
        }

        test("importing the same backup twice is idempotent") {
            val backup = fullHistory(50)
            val once = apply(emptyList(), ImportPlanner.plan(emptyList(), backup, ImportMode.MERGE))
            val twicePlan = ImportPlanner.plan(once, backup, ImportMode.MERGE)
            twicePlan.added.shouldBeEmpty()
            twicePlan.mergedExisting.shouldBeEmpty()
            twicePlan.removedExisting.shouldBeEmpty()
            ids(apply(once, twicePlan)) shouldBe ids(once)
        }

        test("an old backup merged into a full history never destroys existing entries") {
            val existing = (0 until 500).map { entry(it, createdAtMs = 10_000L + it) }
            val oldBackup = (1000 until 1010).map { entry(it, createdAtMs = 100L + it) }
            val plan = ImportPlanner.plan(existing, oldBackup, ImportMode.MERGE)
            plan.removedExisting.shouldBeEmpty()
            plan.droppedIncoming shouldHaveSize 10
            ids(plan.finalEntries) shouldBe ids(existing)
            plan.requiresConfirmation shouldBe true // incoming data is discarded; the user must agree
        }

        test("a newer backup merged into a full history evicts the oldest unpinned, with confirmation") {
            val existing = (0 until 500).map { entry(it, createdAtMs = 1_000L + it) }
            val newBackup = (1000 until 1003).map { entry(it, createdAtMs = 900_000L + it) }
            val plan = ImportPlanner.plan(existing, newBackup, ImportMode.MERGE)
            plan.finalEntries shouldHaveSize 500
            ids(plan.removedExisting) shouldBe setOf("id-00000", "id-00001", "id-00002")
            plan.requiresConfirmation shouldBe true
            ids(apply(existing, plan)) shouldBe ids(plan.finalEntries)
        }

        test("existing pinned entries are never evicted by a merge") {
            val existing = (0 until 500).map { entry(it, createdAtMs = 1_000L + it, pinned = it < 5) }
            val newBackup = (1000 until 1010).map { entry(it, createdAtMs = 900_000L + it) }
            val plan = ImportPlanner.plan(existing, newBackup, ImportMode.MERGE)
            plan.finalEntries.filter { it.pinned } shouldHaveSize 5
            plan.removedExisting.none { it.pinned } shouldBe true
        }

        test("if the history is all pinned, incoming entries are dropped instead of deleting pinned data") {
            val existing = fullHistory(500, pinnedEvery = 1)
            val plan = ImportPlanner.plan(existing, listOf(entry(900, createdAtMs = 99_999_999)), ImportMode.MERGE)
            plan.removedExisting.shouldBeEmpty()
            plan.droppedIncoming.single().reason shouldBe DropReason.OVER_CAPACITY
            plan.finalEntries shouldHaveSize 500
        }

        test("category of a duplicate is only filled in when the existing one is GENERAL") {
            val e1 = listOf(entry(1, text = "x", category = ClipCategory.GENERAL))
            val e2 = listOf(entry(1, text = "x", category = ClipCategory.CODE))
            ImportPlanner.plan(e1, listOf(entry(5, text = "x", category = ClipCategory.URL)), ImportMode.MERGE)
                .finalEntries.single().category shouldBe ClipCategory.URL
            ImportPlanner.plan(e2, listOf(entry(5, text = "x", category = ClipCategory.URL)), ImportMode.MERGE)
                .finalEntries.single().category shouldBe ClipCategory.CODE
        }
    }

    context("SKIP_DUPLICATES") {
        test("never modifies existing entries") {
            val existing = listOf(entry(1, text = "x", tags = setOf("a")))
            val incoming = listOf(entry(9, text = "x", tags = setOf("b"), pinned = true), entry(10, text = "new"))
            val plan = ImportPlanner.plan(existing, incoming, ImportMode.SKIP_DUPLICATES)
            plan.mergedExisting.shouldBeEmpty()
            plan.finalEntries.first { it.id == "id-00001" }.tags shouldBe setOf("a")
            plan.added.map { it.text } shouldBe listOf("new")
            plan.skippedDuplicates shouldHaveSize 1
        }
    }

    context("unusable incoming entries") {
        test("blank and oversized entries are dropped with a reason") {
            val incoming = listOf(
                entry(1, text = "ok"),
                entry(2, text = "   "),
                entry(3, text = "x".repeat(DEFAULT_MAX_ITEM_CHARS + 1)),
            )
            val plan = ImportPlanner.plan(emptyList(), incoming, ImportMode.MERGE)
            plan.finalEntries.map { it.text } shouldBe listOf("ok")
            plan.droppedIncoming.map { it.reason }.toSet() shouldBe setOf(DropReason.BLANK, DropReason.TOO_LARGE)
            plan.requiresConfirmation shouldBe false // nothing was lost to the cap
        }
    }

    context("lower user limit") {
        test("the planner honours HistoryPolicy.maxEntries") {
            val plan = ImportPlanner.plan(emptyList(), fullHistory(20), ImportMode.MERGE, HistoryPolicy(maxEntries = 10))
            plan.finalEntries shouldHaveSize 10
            plan.droppedIncoming shouldHaveSize 10
        }
    }

    context("invariants under random inputs") {
        test("for any mix of modes the result is within the cap, consistent with its own diff, and loses no existing pinned entry unless REPLACE") {
            val rnd = Random(424242)
            repeat(300) { round ->
                fun randomEntries(n: Int, base: Int): List<ClipEntry> = (0 until n).map {
                    entry(
                        base + it,
                        text = "t${rnd.nextInt(base + 50)}",
                        pinned = rnd.nextInt(100) < 20,
                        tags = if (rnd.nextBoolean()) setOf("g${rnd.nextInt(3)}") else emptySet(),
                        createdAtMs = rnd.nextLong(0, 5_000),
                    )
                }
                val existing = randomEntries(rnd.nextInt(0, 520), 0)
                    .distinctBy { it.id }
                    // Real histories never exceed the cap or duplicate text under MOVE_TO_TOP; mimic that.
                    .distinctBy { it.text }.take(MAX_HISTORY_ENTRIES)
                val incoming = randomEntries(rnd.nextInt(0, 700), 10_000)
                val mode = ImportMode.entries[round % 3]
                val plan = ImportPlanner.plan(existing, incoming, mode)

                plan.finalEntries.size shouldBeLessThanOrEqual MAX_HISTORY_ENTRIES
                ids(apply(existing, plan)) shouldBe ids(plan.finalEntries)
                plan.finalEntries.map { it.id }.toSet().size shouldBe plan.finalEntries.size
                if (mode != ImportMode.REPLACE) {
                    ids(plan.finalEntries) shouldContainAll existing.filter { it.pinned }.map { it.id }
                }
            }
        }
    }
})
