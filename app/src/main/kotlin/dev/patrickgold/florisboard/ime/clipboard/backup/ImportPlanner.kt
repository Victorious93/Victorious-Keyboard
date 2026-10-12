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
import dev.patrickgold.florisboard.ime.clipboard.history.HistoryPolicy

enum class ImportMode {
    /** Delete the whole current history (pinned included) and replace it with the backup. Always needs confirmation. */
    REPLACE,

    /** Keep current entries; add new ones; for duplicates, union tags and OR the pin/favourite flags. */
    MERGE,

    /** Keep current entries untouched; add only entries that are not already present. */
    SKIP_DUPLICATES,
}

enum class DropReason { TOO_LARGE, OVER_CAPACITY, BLANK }

data class DroppedEntry(val entry: ClipEntry, val reason: DropReason)

/**
 * The exact outcome of an import, computed **before** anything is changed, so the UI can show a preview and ask for
 * confirmation. Applying it is a single transaction: delete [deleteIds], upsert [upserts] — if anything fails the
 * database rolls back and the previous history is intact.
 */
data class ImportPlan(
    val mode: ImportMode,
    /** The history after applying the plan, newest first. */
    val finalEntries: List<ClipEntry>,
    val added: List<ClipEntry>,
    /** Existing entries that change (tags/flags merged, or replaced by a newer edit of the same id). */
    val mergedExisting: List<ClipEntry>,
    val skippedDuplicates: List<ClipEntry>,
    /** Existing entries that will no longer exist: all of them for REPLACE, otherwise cap evictions. */
    val removedExisting: List<ClipEntry>,
    val droppedIncoming: List<DroppedEntry>,
    /** Same id, different text, in MERGE mode: the more recently modified version wins. */
    val conflicts: Int,
) {
    /** Ids to delete. An id that the plan re-inserts (REPLACE with a same-id entry) is an upsert, not a delete. */
    val deleteIds: Set<String>
        get() {
            val keep = finalEntries.mapTo(HashSet()) { it.id }
            return removedExisting.mapNotNullTo(HashSet()) { if (it.id in keep) null else it.id }
        }

    val upserts: List<ClipEntry> get() = added + mergedExisting

    val removedPinned: Int get() = removedExisting.count { it.pinned }

    /**
     * True when applying the plan deletes existing data or discards incoming data because of the 500-entry cap. The
     * UI must show the numbers and obtain explicit consent before applying such a plan.
     */
    val requiresConfirmation: Boolean
        get() = removedExisting.isNotEmpty() || droppedIncoming.any { it.reason == DropReason.OVER_CAPACITY }
}

object ImportPlanner {
    private val newestFirst = compareByDescending<ClipEntry> { it.createdAtMs }.thenBy { it.id }
    private val oldestFirst = compareBy<ClipEntry>({ it.createdAtMs }, { it.id })

    fun plan(
        existing: List<ClipEntry>,
        incoming: List<ClipEntry>,
        mode: ImportMode,
        policy: HistoryPolicy = HistoryPolicy(),
    ): ImportPlan {
        val dropped = ArrayList<DroppedEntry>()
        val usable = ArrayList<ClipEntry>(incoming.size)
        for (e in incoming) {
            when {
                e.text.isBlank() -> dropped.add(DroppedEntry(e, DropReason.BLANK))
                e.text.length > policy.maxItemChars -> dropped.add(DroppedEntry(e, DropReason.TOO_LARGE))
                else -> usable.add(e)
            }
        }

        val existingIds = existing.mapTo(HashSet()) { it.id }
        val removed = ArrayList<ClipEntry>()
        val added = ArrayList<ClipEntry>()
        val merged = LinkedHashMap<String, ClipEntry>()
        val skipped = ArrayList<ClipEntry>()
        var conflicts = 0

        // Working set after the mode-specific step, before the capacity step.
        val working = LinkedHashMap<String, ClipEntry>()

        if (mode == ImportMode.REPLACE) {
            removed.addAll(existing)
            for (e in usable) {
                if (working.containsKey(e.id)) {
                    skipped.add(e)
                } else {
                    working[e.id] = e
                    added.add(e)
                }
            }
        } else {
            existing.forEach { working[it.id] = it }
            val byText = HashMap<String, String>() // text -> id, over everything currently in `working`
            existing.forEach { byText.putIfAbsent(it.text, it.id) }

            for (inc in usable) {
                val matchId = if (working.containsKey(inc.id)) inc.id else byText[inc.text]
                if (matchId == null) {
                    working[inc.id] = inc
                    byText[inc.text] = inc.id
                    added.add(inc)
                    continue
                }
                if (mode == ImportMode.SKIP_DUPLICATES) {
                    skipped.add(inc)
                    continue
                }
                // MERGE
                val cur = working.getValue(matchId)
                var next = cur.copy(
                    tags = cur.tags + inc.tags,
                    pinned = cur.pinned || inc.pinned,
                    favorite = cur.favorite || inc.favorite,
                    category = if (cur.category == ClipCategory.GENERAL) inc.category else cur.category,
                )
                if (inc.text != cur.text) {
                    conflicts++
                    if (inc.lastModifiedMs > cur.lastModifiedMs) {
                        next = next.copy(text = inc.text, editedAtMs = inc.lastModifiedMs)
                    }
                }
                if (next != cur) {
                    working[matchId] = next
                    if (next.text != cur.text) {
                        if (byText[cur.text] == matchId) byText.remove(cur.text)
                        byText[next.text] = matchId
                    }
                    // Only entries that already existed before the import are reported as "merged existing".
                    if (matchId in existingIds) merged[matchId] = next
                    else {
                        // The match was an entry added earlier in this same import: fold into `added`.
                        val idx = added.indexOfFirst { it.id == matchId }
                        if (idx >= 0) added[idx] = next
                    }
                } else {
                    skipped.add(inc)
                }
            }
        }

        // Capacity step: never evict a pinned entry; evict the oldest unpinned of the combined set; if only pinned
        // entries remain over the cap, drop *incoming* ones (existing pinned data is never destroyed by a merge).
        val capacityRemoved = ArrayList<ClipEntry>()
        var overflow = working.size - policy.maxEntries
        if (overflow > 0) {
            val unpinned = working.values.filter { !it.pinned }.sortedWith(oldestFirst)
            for (victim in unpinned) {
                if (overflow <= 0) break
                working.remove(victim.id)
                overflow--
                if (victim.id in existingIds && mode != ImportMode.REPLACE) {
                    capacityRemoved.add(existing.first { it.id == victim.id })
                    merged.remove(victim.id)
                } else {
                    dropped.add(DroppedEntry(victim, DropReason.OVER_CAPACITY))
                    added.removeAll { it.id == victim.id }
                }
            }
            if (overflow > 0) {
                val incomingPinned = working.values.filter { it.id !in existingIds || mode == ImportMode.REPLACE }
                    .filter { it.pinned }.sortedWith(oldestFirst)
                for (victim in incomingPinned) {
                    if (overflow <= 0) break
                    working.remove(victim.id)
                    overflow--
                    dropped.add(DroppedEntry(victim, DropReason.OVER_CAPACITY))
                    added.removeAll { it.id == victim.id }
                }
            }
        }
        removed.addAll(capacityRemoved)

        return ImportPlan(
            mode = mode,
            finalEntries = working.values.sortedWith(newestFirst),
            added = added.filter { it.id in working },
            mergedExisting = merged.values.filter { it.id in working }.toList(),
            skippedDuplicates = skipped,
            removedExisting = removed,
            droppedIncoming = dropped,
            conflicts = conflicts,
        )
    }
}
