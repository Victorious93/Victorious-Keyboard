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

package com.victorious.keyboard.ime.toolbar

/**
 * Tile size and column count for the tools panel. [FIT] and [COLUMNS_ADAPTIVE] are today's grid:
 * tiles share the panel height, and the column width stays at least 72 dp.
 */
object ToolsLayout {
    const val FIT = "FIT"
    const val SMALL = "SMALL"
    const val MEDIUM = "MEDIUM"
    const val LARGE = "LARGE"
    const val COLUMNS_ADAPTIVE = 0
    const val COLUMNS_MIN = 2
    const val COLUMNS_MAX = 5

    fun size(name: String?): String = when (name) {
        SMALL, MEDIUM, LARGE -> name
        else -> FIT
    }

    /** `0` keeps the adaptive grid. Any other stored number is clamped to [COLUMNS_MIN]..[COLUMNS_MAX]. */
    fun columns(stored: Int): Int =
        if (stored <= 0) COLUMNS_ADAPTIVE else stored.coerceIn(COLUMNS_MIN, COLUMNS_MAX)

    /** Fixed tile height in dp, or null when the panel should size tiles from the space it has. */
    fun heightDp(size: String): Float? = when (size) {
        SMALL -> 52f
        MEDIUM -> 72f
        LARGE -> 88f
        else -> null
    }

    fun minWidthDp(size: String): Float = when (size) {
        SMALL -> 64f
        LARGE -> 96f
        else -> 72f
    }

    fun visibleExtras(extra: List<String>, hidden: Set<String>): List<String> =
        extra.filter { ToolRef.isExtra(it) && it !in hidden }.distinct()

    /** Moves the item at [from] to [to]. Out-of-range indexes leave the list as it is. */
    fun move(ids: List<String>, from: Int, to: Int): List<String> {
        if (from !in ids.indices || to !in ids.indices || from == to) return ids
        val next = ids.toMutableList()
        val item = next.removeAt(from)
        next.add(to, item)
        return next
    }
}
