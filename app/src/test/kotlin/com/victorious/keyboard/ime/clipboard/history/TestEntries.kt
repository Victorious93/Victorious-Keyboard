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

/** Shared builders for the clipboard unit tests. */
internal fun entry(
    n: Int,
    text: String = "item $n",
    pinned: Boolean = false,
    favorite: Boolean = false,
    tags: Set<String> = emptySet(),
    category: ClipCategory = ClipCategory.GENERAL,
    createdAtMs: Long = 1_000L + n,
    editedAtMs: Long? = null,
) = ClipEntry(
    id = "id-%05d".format(n),
    text = text,
    createdAtMs = createdAtMs,
    pinned = pinned,
    favorite = favorite,
    tags = tags,
    category = category,
    editedAtMs = editedAtMs,
)

internal fun fullHistory(size: Int = MAX_HISTORY_ENTRIES, pinnedEvery: Int = 0): List<ClipEntry> =
    (0 until size).map { entry(it, pinned = pinnedEvery > 0 && it % pinnedEvery == 0) }
