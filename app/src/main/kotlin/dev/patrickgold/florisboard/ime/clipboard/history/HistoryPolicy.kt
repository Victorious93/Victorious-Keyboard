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

package dev.patrickgold.florisboard.ime.clipboard.history

/** Hard ceiling for the number of active history entries. The user may lower, never raise, the effective limit. */
const val MAX_HISTORY_ENTRIES = 500

/** Default ceiling for the size of one entry. Larger clips are rejected (never silently truncated). */
const val DEFAULT_MAX_ITEM_CHARS = 50_000

/** Absolute ceiling the user can configure for one entry. */
const val HARD_MAX_ITEM_CHARS = 200_000

enum class DuplicateBehavior {
    /** An identical text moves to the top and keeps its pin/favourite/tags. (default) */
    MOVE_TO_TOP,

    /** An identical text is ignored; the existing entry keeps its position. */
    IGNORE,

    /** Duplicates are stored as separate entries. */
    KEEP_BOTH,
}

/**
 * Retention rules for the rolling clipboard history.
 *
 * Retention rules (documented in CLIPBOARD.md):
 *  1. The history holds at most [maxEntries] (≤ [MAX_HISTORY_ENTRIES]) entries, pinned ones included.
 *  2. When full, the oldest *unpinned* entries are evicted first.
 *  3. Pinned entries are never evicted automatically. If the history is full of pinned entries, a new clip is
 *     **rejected** (reported to the caller, so the UI can tell the user) instead of destroying pinned data.
 *  4. Anything the user wants to keep permanently belongs in the separate snippet collection, which is not subject
 *     to this cap.
 */
data class HistoryPolicy(
    val maxEntries: Int = MAX_HISTORY_ENTRIES,
    val maxItemChars: Int = DEFAULT_MAX_ITEM_CHARS,
    val duplicates: DuplicateBehavior = DuplicateBehavior.MOVE_TO_TOP,
) {
    init {
        require(maxEntries in 1..MAX_HISTORY_ENTRIES) { "maxEntries must be in 1..$MAX_HISTORY_ENTRIES" }
        require(maxItemChars in 1..HARD_MAX_ITEM_CHARS) { "maxItemChars must be in 1..$HARD_MAX_ITEM_CHARS" }
    }
}

/** Outcome of offering a new clip to the history. Pure data: applying it is the caller's job. */
sealed interface InsertPlan {
    /** Insert the candidate and delete [evictIds] (oldest unpinned entries) in the same transaction. */
    data class Add(val evictIds: List<String>) : InsertPlan

    /** Identical text exists: refresh its timestamp, keeping its flags and tags. */
    data class MoveToTop(val existingId: String) : InsertPlan

    /** Identical text exists and duplicates are ignored. */
    data class IgnoreDuplicate(val existingId: String) : InsertPlan

    data object RejectedBlank : InsertPlan

    data class RejectedTooLarge(val chars: Int, val limit: Int) : InsertPlan

    /** History is at capacity and every entry is pinned. Nothing was deleted. */
    data class RejectedHistoryFullOfPinned(val pinnedCount: Int) : InsertPlan
}

/** Outcome of lowering the limit. Deleting history is destructive, so callers must confirm [Evict] with the user. */
sealed interface TrimPlan {
    data object NothingToDo : TrimPlan

    data class Evict(val evictIds: List<String>) : TrimPlan

    /** More entries are pinned than the new limit allows. Nothing can be evicted automatically. */
    data class PinnedExceedLimit(val pinnedCount: Int, val limit: Int) : TrimPlan
}

private val evictionOrder = compareBy<ClipEntry>({ it.createdAtMs }, { it.id })

fun HistoryPolicy.planInsert(existing: List<ClipEntry>, candidate: ClipEntry): InsertPlan {
    if (candidate.text.isBlank()) return InsertPlan.RejectedBlank
    if (candidate.text.length > maxItemChars) {
        return InsertPlan.RejectedTooLarge(candidate.text.length, maxItemChars)
    }

    if (duplicates != DuplicateBehavior.KEEP_BOTH) {
        val dup = existing.firstOrNull { it.text == candidate.text }
        if (dup != null) {
            return when (duplicates) {
                DuplicateBehavior.MOVE_TO_TOP -> InsertPlan.MoveToTop(dup.id)
                else -> InsertPlan.IgnoreDuplicate(dup.id)
            }
        }
    }

    return capacityPlan(existing)
}

/** Capacity-only planning for rows that carry no text (images, videos): no blank/size/duplicate checks. */
fun HistoryPolicy.planInsertNonText(existing: List<ClipEntry>): InsertPlan = capacityPlan(existing)

private fun HistoryPolicy.capacityPlan(existing: List<ClipEntry>): InsertPlan {
    val needed = existing.size + 1 - maxEntries
    if (needed <= 0) return InsertPlan.Add(emptyList())

    val evictable = existing.filter { !it.pinned }.sortedWith(evictionOrder)
    if (evictable.size < needed) {
        return InsertPlan.RejectedHistoryFullOfPinned(existing.count { it.pinned })
    }
    return InsertPlan.Add(evictable.take(needed).map { it.id })
}

/** Plans the eviction needed to satisfy a lower [newMax]. Does not delete anything by itself. */
fun planTrim(existing: List<ClipEntry>, newMax: Int): TrimPlan {
    require(newMax in 1..MAX_HISTORY_ENTRIES)
    val excess = existing.size - newMax
    if (excess <= 0) return TrimPlan.NothingToDo
    val evictable = existing.filter { !it.pinned }.sortedWith(evictionOrder)
    if (evictable.size < excess) {
        return TrimPlan.PinnedExceedLimit(existing.count { it.pinned }, newMax)
    }
    return TrimPlan.Evict(evictable.take(excess).map { it.id })
}
