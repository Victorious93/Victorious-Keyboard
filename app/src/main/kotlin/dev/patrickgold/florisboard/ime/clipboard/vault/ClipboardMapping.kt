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

package dev.patrickgold.florisboard.ime.clipboard.vault

import dev.patrickgold.florisboard.ime.clipboard.history.ClipCategory
import dev.patrickgold.florisboard.ime.clipboard.history.ClipEntry
import dev.patrickgold.florisboard.ime.clipboard.history.ClipTags
import dev.patrickgold.florisboard.ime.clipboard.provider.ClipboardItem
import dev.patrickgold.florisboard.ime.clipboard.provider.ItemType

/** Parses the comma-separated tag column. Tags can't contain commas (see [ClipTags]). */
fun parseTagColumn(raw: String): Set<String> =
    if (raw.isEmpty()) emptySet() else raw.split(',').filter { it.isNotEmpty() }.toCollection(LinkedHashSet())

fun formatTagColumn(tags: Set<String>): String = tags.sorted().joinToString(",")

/**
 * Domain view of a stored row. Image/video rows are mapped to entries with **blank text**: they take part in the
 * 500-entry cap and in eviction ordering, but are never exported (the backup format v1 is text-only).
 */
fun ClipboardItem.toClipEntry(): ClipEntry = ClipEntry(
    id = uuid,
    text = if (type == ItemType.TEXT) text.orEmpty() else "",
    createdAtMs = creationTimestampMs,
    pinned = isPinned,
    favorite = isFavorite,
    tags = parseTagColumn(tags),
    category = ClipCategory.parse(category),
    sensitive = isSensitive,
    editedAtMs = editedAtMs,
)

val ClipboardItem.isTextItem: Boolean get() = type == ItemType.TEXT

/** Builds a new text row from a domain entry (used by restore). The database id is assigned on insert. */
fun ClipEntry.toNewClipboardItem(): ClipboardItem = ClipboardItem(
    id = 0,
    type = ItemType.TEXT,
    text = text,
    uri = null,
    creationTimestampMs = createdAtMs,
    isPinned = pinned,
    mimeTypes = listOf("text/plain"),
    isSensitive = sensitive,
    uuid = id,
    tags = formatTagColumn(tags),
    category = category.name,
    isFavorite = favorite,
    editedAtMs = editedAtMs,
)

/** Copies the editable fields of [entry] onto an existing row, keeping its database id and non-text data. */
fun ClipboardItem.withEntryFields(entry: ClipEntry): ClipboardItem = copy(
    text = if (type == ItemType.TEXT) entry.text else text,
    isPinned = entry.pinned,
    isFavorite = entry.favorite,
    tags = formatTagColumn(entry.tags),
    category = entry.category.name,
    editedAtMs = entry.editedAtMs,
)
