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

package dev.patrickgold.florisboard.ime.clipboard

/**
 * Ids and sizes for clipboard images and videos. A missing or non-numeric uri used to crash the
 * keyboard when the clipboard tile drew the item.
 */
object ClipMediaRef {
    /** Numeric id at the end of a media uri, or null when the uri cannot name a stored file. */
    fun idOf(uri: String?): Long? {
        if (uri.isNullOrBlank()) return null
        val path = uri.substringBefore('?').substringBefore('#')
        val last = path.substringAfterLast('/')
        val id = last.toLongOrNull() ?: return null
        return id.takeIf { it > 0 }
    }

    /**
     * Pixel size for an API 29 video thumbnail. Null when the metadata is missing or unusable,
     * so the caller can use the older thumbnail call instead of throwing.
     */
    fun videoPixels(width: String?, height: String?): Pair<Int, Int>? {
        val w = width?.toIntOrNull() ?: return null
        val h = height?.toIntOrNull() ?: return null
        if (w <= 0 || h <= 0) return null
        return w to h
    }
}
