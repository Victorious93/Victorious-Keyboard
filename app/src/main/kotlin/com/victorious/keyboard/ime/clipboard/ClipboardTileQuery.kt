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

package com.victorious.keyboard.ime.clipboard

import com.victorious.keyboard.ime.text.key.KeyCode

/**
 * Search, pin, and favorite filters for the in-keyboard clipboard tile.
 * The query is typed with the keyboard itself, so this object also decides which keys edit it.
 * Clip text is never logged.
 */
object ClipboardTileQuery {
    const val MAX_QUERY_CHARS = 80

    /** Character keys, space, backspace, and enter. Shift and layout keys are left alone. */
    fun consumes(code: Int, character: Boolean): Boolean =
        character || code == KeyCode.DELETE || code == KeyCode.SPACE || code == KeyCode.ENTER

    /**
     * Applies one key to [query]. Enter stops capture and leaves the query in place.
     * A query that would grow past [MAX_QUERY_CHARS] is left unchanged.
     */
    fun type(query: String, code: Int, character: Boolean, text: String): Typed {
        return when {
            code == KeyCode.ENTER -> Typed(query, stop = true)
            code == KeyCode.DELETE -> Typed(query.dropLast(1), stop = false)
            code == KeyCode.SPACE -> Typed(append(query, text.ifEmpty { " " }), stop = false)
            character && text.isNotEmpty() -> Typed(append(query, text), stop = false)
            else -> Typed(query, stop = false)
        }
    }

    /**
     * Keeps rows whose text contains every word in [query], and that are pinned or favorite when
     * those switches are on. Order is unchanged. An empty query keeps every row that passes the switches.
     */
    fun <T> filter(
        items: List<T>,
        text: (T) -> String,
        pinned: (T) -> Boolean,
        favorite: (T) -> Boolean,
        query: String,
        pinnedOnly: Boolean,
        favoritesOnly: Boolean,
    ): List<T> {
        val terms = query.trim().split(WHITESPACE).filter { it.isNotEmpty() }
        return items.filter { item ->
            if (pinnedOnly && !pinned(item)) return@filter false
            if (favoritesOnly && !favorite(item)) return@filter false
            val haystack = text(item)
            terms.all { haystack.contains(it, ignoreCase = true) }
        }
    }

    private fun append(query: String, extra: String): String {
        if (extra.isEmpty()) return query
        val next = query + extra
        return if (next.length <= MAX_QUERY_CHARS) next else query
    }

    data class Typed(val query: String, val stop: Boolean)

    private val WHITESPACE = Regex("\\s+")
}
