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

data class ClipFilter(
    val category: ClipCategory? = null,
    val pinnedOnly: Boolean = false,
    val favoritesOnly: Boolean = false,
    val tag: String? = null,
)

/**
 * In-memory full-text search over the (at most 500) history entries.
 *
 * Why not SQL `LIKE`: texts are encrypted at rest, so the database cannot search them. With the history capped at
 * 500 entries, a linear scan is cheap and does not allocate lowercase copies of every entry.
 *
 * Semantics: the query is split on whitespace; an entry matches when **every** term occurs, case-insensitively, in
 * its text or in one of its tags. Results are newest first.
 */
object ClipSearch {
    private val newestFirst = compareByDescending<ClipEntry> { it.createdAtMs }.thenBy { it.id }

    fun search(entries: List<ClipEntry>, query: String, filter: ClipFilter = ClipFilter()): List<ClipEntry> {
        val terms = query.trim().split(WHITESPACE).filter { it.isNotEmpty() }
        val tagFilter = filter.tag?.let { ClipTags.normalize(it) }
        return entries.asSequence()
            .filter { filter.category == null || it.category == filter.category }
            .filter { !filter.pinnedOnly || it.pinned }
            .filter { !filter.favoritesOnly || it.favorite }
            .filter { filter.tag == null || (tagFilter != null && tagFilter in it.tags) }
            .filter { entry -> terms.all { term -> matches(entry, term) } }
            .sortedWith(newestFirst)
            .toList()
    }

    private fun matches(entry: ClipEntry, term: String): Boolean {
        if (entry.text.contains(term, ignoreCase = true)) return true
        return entry.tags.any { it.contains(term, ignoreCase = true) }
    }

    private val WHITESPACE = Regex("\\s+")
}
