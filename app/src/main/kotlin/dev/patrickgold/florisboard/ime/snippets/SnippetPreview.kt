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

/**
 * One-line preview of a snippet body for the picker list. Works on the raw template (never the rendered text, which
 * would read the clipboard and the selection just to draw a list), collapses whitespace and line breaks, and cuts on a
 * code point boundary so an emoji is never split.
 */
object SnippetPreview {
    const val ELLIPSIS = "…"

    fun of(body: String, maxCodePoints: Int): String {
        require(maxCodePoints >= 1)
        // Never look at more than the budget plus the slack the whitespace collapsing can eat.
        val sb = StringBuilder()
        var count = 0
        var pendingSpace = false
        var cut = false
        var i = 0
        while (i < body.length) {
            val cp = body.codePointAt(i)
            i += Character.charCount(cp)
            if (isBidiControl(cp)) continue // would let a body visually reorder its own preview
            if (Character.isWhitespace(cp) || Character.isSpaceChar(cp) || Character.isISOControl(cp)) {
                pendingSpace = sb.isNotEmpty()
                continue
            }
            if (count >= maxCodePoints) { cut = true; break }
            if (pendingSpace) {
                if (count + 1 >= maxCodePoints) { cut = true; break }
                sb.append(' '); count++; pendingSpace = false
            }
            sb.appendCodePoint(cp); count++
        }
        return if (cut) sb.toString().trimEnd() + ELLIPSIS else sb.toString()
    }

    private fun isBidiControl(cp: Int): Boolean =
        cp == 0x061C || cp == 0x200E || cp == 0x200F || cp in 0x202A..0x202E || cp in 0x2066..0x2069
}
