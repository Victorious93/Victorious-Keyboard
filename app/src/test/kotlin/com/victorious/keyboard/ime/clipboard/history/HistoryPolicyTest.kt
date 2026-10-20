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

package com.victorious.keyboard.ime.clipboard.history

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.ints.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlin.random.Random

class HistoryPolicyTest : FunSpec({
    val policy = HistoryPolicy()

    context("policy construction") {
        test("the cap can never exceed 500") {
            shouldThrow<IllegalArgumentException> { HistoryPolicy(maxEntries = 501) }
            shouldThrow<IllegalArgumentException> { HistoryPolicy(maxEntries = 0) }
            HistoryPolicy(maxEntries = 500)
            HistoryPolicy(maxEntries = 1)
        }
        test("item size limit is bounded") {
            shouldThrow<IllegalArgumentException> { HistoryPolicy(maxItemChars = HARD_MAX_ITEM_CHARS + 1) }
        }
    }

    context("insert below capacity") {
        test("adds without eviction") {
            val plan = policy.planInsert(fullHistory(10), entry(999))
            plan shouldBe InsertPlan.Add(emptyList())
        }
    }

    context("insert at capacity") {
        test("evicts the single oldest unpinned entry") {
            val history = fullHistory(500)
            val plan = policy.planInsert(history, entry(9999, createdAtMs = 1_000_000L))
            plan shouldBe InsertPlan.Add(listOf("id-00000"))
        }

        test("skips pinned entries even when they are the oldest") {
            val history = fullHistory(500).map { if (it.id == "id-00000" || it.id == "id-00001") it.copy(pinned = true) else it }
            val plan = policy.planInsert(history, entry(9999, createdAtMs = 1_000_000L))
            plan shouldBe InsertPlan.Add(listOf("id-00002"))
        }

        test("ties on timestamp are broken deterministically by id") {
            val history = (0 until 500).map { entry(it, createdAtMs = 5L) }
            val plan = policy.planInsert(history, entry(9999, createdAtMs = 6L))
            plan shouldBe InsertPlan.Add(listOf("id-00000"))
        }

        test("a history full of pinned entries rejects the new clip and deletes nothing") {
            val history = fullHistory(500, pinnedEvery = 1)
            val plan = policy.planInsert(history, entry(9999))
            plan shouldBe InsertPlan.RejectedHistoryFullOfPinned(500)
        }

        test("a lower user limit is honoured") {
            val small = HistoryPolicy(maxEntries = 3)
            val plan = small.planInsert(fullHistory(3), entry(50, createdAtMs = 99_999))
            plan shouldBe InsertPlan.Add(listOf("id-00000"))
        }

        test("history already above the limit evicts enough entries to make room") {
            val small = HistoryPolicy(maxEntries = 3)
            val plan = small.planInsert(fullHistory(5), entry(50, createdAtMs = 99_999))
            plan.shouldBeInstanceOf<InsertPlan.Add>().evictIds shouldBe listOf("id-00000", "id-00001", "id-00002")
        }
    }

    context("rejections") {
        test("blank text") {
            policy.planInsert(emptyList(), entry(1, text = " \n\t ")) shouldBe InsertPlan.RejectedBlank
        }
        test("too large text is rejected rather than truncated") {
            val big = "x".repeat(DEFAULT_MAX_ITEM_CHARS + 1)
            policy.planInsert(emptyList(), entry(1, text = big)) shouldBe
                InsertPlan.RejectedTooLarge(DEFAULT_MAX_ITEM_CHARS + 1, DEFAULT_MAX_ITEM_CHARS)
        }
        test("exactly at the size limit is accepted") {
            val ok = "x".repeat(DEFAULT_MAX_ITEM_CHARS)
            policy.planInsert(emptyList(), entry(1, text = ok)) shouldBe InsertPlan.Add(emptyList())
        }
    }

    context("duplicates") {
        val existing = listOf(entry(1, text = "same", pinned = true), entry(2, text = "other"))
        test("MOVE_TO_TOP (default)") {
            policy.planInsert(existing, entry(3, text = "same")) shouldBe InsertPlan.MoveToTop("id-00001")
        }
        test("IGNORE") {
            HistoryPolicy(duplicates = DuplicateBehavior.IGNORE).planInsert(existing, entry(3, text = "same")) shouldBe
                InsertPlan.IgnoreDuplicate("id-00001")
        }
        test("KEEP_BOTH") {
            HistoryPolicy(duplicates = DuplicateBehavior.KEEP_BOTH).planInsert(existing, entry(3, text = "same")) shouldBe
                InsertPlan.Add(emptyList())
        }
        test("a duplicate at capacity never evicts anything") {
            val full = fullHistory(500)
            policy.planInsert(full, entry(9999, text = "item 7")) shouldBe InsertPlan.MoveToTop("id-00007")
        }
    }

    context("lowering the limit") {
        test("nothing to do when within the new limit") {
            planTrim(fullHistory(10), 10) shouldBe TrimPlan.NothingToDo
        }
        test("evicts oldest unpinned, preview only") {
            planTrim(fullHistory(10), 7) shouldBe TrimPlan.Evict(listOf("id-00000", "id-00001", "id-00002"))
        }
        test("reports when pinned entries exceed the new limit instead of deleting them") {
            planTrim(fullHistory(10, pinnedEvery = 1), 5) shouldBe TrimPlan.PinnedExceedLimit(10, 5)
        }
    }

    context("invariants under random operation sequences") {
        test("history never exceeds 500 and pinned entries are never lost") {
            val rnd = Random(20261011)
            var history = emptyList<ClipEntry>()
            val pinnedEver = HashSet<String>()
            var clock = 0L
            repeat(6_000) { i ->
                clock += rnd.nextInt(0, 3) // allow equal timestamps
                val pinned = rnd.nextInt(100) < 15
                val e = entry(i, text = "t${rnd.nextInt(2_000)}", pinned = pinned, createdAtMs = clock)
                when (val plan = policy.planInsert(history, e)) {
                    is InsertPlan.Add -> {
                        history = history.filter { it.id !in plan.evictIds } + e
                        if (pinned) pinnedEver.add(e.id)
                        plan.evictIds.forEach { pinnedEver.contains(it) shouldBe false }
                    }
                    is InsertPlan.MoveToTop -> history = history.map {
                        if (it.id == plan.existingId) it.copy(createdAtMs = clock) else it
                    }
                    else -> Unit
                }
                history.size shouldBeLessThanOrEqual MAX_HISTORY_ENTRIES
            }
            history.map { it.id } shouldContainAll pinnedEver.toList()
        }

        test("evicting never selects a pinned entry") {
            val history = fullHistory(500, pinnedEvery = 3)
            val plan = policy.planInsert(history, entry(9999, createdAtMs = 99_999)).shouldBeInstanceOf<InsertPlan.Add>()
            plan.evictIds shouldHaveSize 1
            history.filter { it.id in plan.evictIds }.none { it.pinned } shouldBe true
        }

        test("an empty history needs no eviction") {
            policy.planInsert(emptyList(), entry(1)).shouldBeInstanceOf<InsertPlan.Add>().evictIds.shouldBeEmpty()
        }
    }
})
