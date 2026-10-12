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

package dev.patrickgold.florisboard.ime.snippets

import java.util.Locale

enum class LibraryError { INVALID, DUPLICATE_ID, DUPLICATE_ABBREVIATION, FULL, NOT_FOUND }

sealed interface LibraryResult {
    data class Ok(val library: SnippetLibrary) : LibraryResult
    data class Rejected(val error: LibraryError, val issues: List<SnippetIssue> = emptyList()) : LibraryResult
}

/**
 * Immutable snippet collection. Every change returns a new library or a [LibraryResult.Rejected]; nothing is dropped
 * silently, so a full or conflicting library never loses existing snippets.
 */
class SnippetLibrary private constructor(val snippets: List<Snippet>) {

    fun byId(id: String): Snippet? = snippets.firstOrNull { it.id == id }

    fun add(s: Snippet): LibraryResult {
        validate(s)?.let { return it }
        if (snippets.size >= SnippetLimits.MAX_SNIPPETS) return LibraryResult.Rejected(LibraryError.FULL)
        if (byId(s.id) != null) return LibraryResult.Rejected(LibraryError.DUPLICATE_ID)
        if (abbreviationTaken(s, ignoreId = null)) return LibraryResult.Rejected(LibraryError.DUPLICATE_ABBREVIATION)
        return LibraryResult.Ok(SnippetLibrary(snippets + s))
    }

    fun update(s: Snippet): LibraryResult {
        validate(s)?.let { return it }
        if (byId(s.id) == null) return LibraryResult.Rejected(LibraryError.NOT_FOUND)
        if (abbreviationTaken(s, ignoreId = s.id)) return LibraryResult.Rejected(LibraryError.DUPLICATE_ABBREVIATION)
        return LibraryResult.Ok(SnippetLibrary(snippets.map { if (it.id == s.id) s else it }))
    }

    fun remove(id: String): LibraryResult =
        if (byId(id) == null) LibraryResult.Rejected(LibraryError.NOT_FOUND)
        else LibraryResult.Ok(SnippetLibrary(snippets.filterNot { it.id == id }))

    /** Case-insensitive; every whitespace-separated word must occur in the title, abbreviation, tags or body. */
    fun search(query: String): List<Snippet> {
        val words = query.lowercase(Locale.ROOT).split(Regex("\\s+")).filter { it.isNotEmpty() }
        val sorted = snippets.sortedWith(compareBy({ it.title.lowercase(Locale.ROOT) }, { it.id }))
        if (words.isEmpty()) return sorted
        return sorted.filter { s ->
            val hay = (listOf(s.title, s.abbreviation.orEmpty(), s.body) + s.tags).joinToString("\n").lowercase(Locale.ROOT)
            words.all { hay.contains(it) }
        }
    }

    private fun validate(s: Snippet): LibraryResult.Rejected? {
        val v = SnippetValidator.validate(s)
        return if (v.isValid) null else LibraryResult.Rejected(LibraryError.INVALID, v.errors)
    }

    /** Abbreviations are unique case-insensitively across all snippets, enabled or not, so there is never ambiguity. */
    private fun abbreviationTaken(s: Snippet, ignoreId: String?): Boolean {
        val a = s.abbreviation ?: return false
        return snippets.any { it.id != ignoreId && it.abbreviation.equals(a, ignoreCase = true) }
    }

    companion object {
        val EMPTY = SnippetLibrary(emptyList())
    }
}
