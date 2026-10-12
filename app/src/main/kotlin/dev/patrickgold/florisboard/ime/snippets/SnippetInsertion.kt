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

import java.time.LocalDateTime

/** What the shell hands over when the user inserts a snippet (picker) or an abbreviation completes (expansion). */
data class InsertionRequest(
    val snippet: Snippet,
    val now: LocalDateTime,
    /** Plain clipboard text, or `null` if there is none or it is not text. */
    val clipboardText: String? = null,
    /** The text selected in the editor, or `null`/empty if nothing is selected. */
    val selectionText: String? = null,
    /** Password-like or otherwise sensitive field. Clipboard and selection are then never read into the result. */
    val sensitiveField: Boolean = false,
    val datePattern: String = "yyyy-MM-dd",
    val timePattern: String = "HH:mm",
)

sealed interface InsertionPlan {
    /**
     * Commit [text] (replacing any selection the editor still has), then move the caret left by [cursorBackCodePoints]
     * code points if it is not `null`.
     */
    data class Insert(val text: String, val cursorBackCodePoints: Int?) : InsertionPlan

    /** The rendered text would exceed [SnippetLimits.MAX_RENDERED]; the shell must insert nothing. */
    data object TooLarge : InsertionPlan

    /** The snippet is disabled or has no usable body; the shell must insert nothing. */
    data object NothingToInsert : InsertionPlan
}

/** What the shell reports back to the picker after trying to insert; it never carries snippet text. */
enum class SnippetInsertOutcome { INSERTED, TOO_LARGE, NOTHING, FAILED }

/**
 * The single place that turns a snippet into text to insert, used by abbreviation expansion and by the picker so both
 * obey the same privacy rule: in a sensitive field the clipboard and the selection are **never** substituted
 * (CLAUDE.md invariant 2), whatever the caller passes. Pure Kotlin; nothing here logs or stores snippet content.
 */
object SnippetInsertion {
    fun plan(request: InsertionRequest): InsertionPlan {
        val s = request.snippet
        if (!s.enabled || s.body.isEmpty() || s.body.length > SnippetLimits.MAX_BODY) return InsertionPlan.NothingToInsert
        val context = TemplateContext(
            now = request.now,
            clipboard = request.clipboardText.takeUnless { request.sensitiveField },
            selection = request.selectionText.takeUnless { request.sensitiveField },
            datePattern = request.datePattern,
            timePattern = request.timePattern,
        )
        return when (val r = SnippetTemplate.render(s.body, context)) {
            is RenderResult.TooLarge -> InsertionPlan.TooLarge
            is RenderResult.Rendered ->
                if (r.text.isEmpty()) InsertionPlan.NothingToInsert else InsertionPlan.Insert(r.text, r.cursorBackCodePoints)
        }
    }
}

/**
 * How many times the left arrow key must be pressed to put the caret on `{{cursor}}` after the text was inserted.
 * An editor's left arrow moves by user-perceived character (grapheme cluster), not by code point, so the tail after the
 * cursor is counted in clusters. A huge tail would flood the editor with key events on the main thread, so above
 * [MAX_KEY_PRESSES] no caret movement is attempted at all (the caret stays at the end of the inserted text).
 */
object CaretPlacement {
    const val MAX_KEY_PRESSES = 500

    fun leftPresses(insertedText: String, cursorBackCodePoints: Int?): Int {
        if (cursorBackCodePoints == null || cursorBackCodePoints <= 0) return 0
        val available = insertedText.codePointCount(0, insertedText.length)
        val back = minOf(cursorBackCodePoints, available)
        val tail = insertedText.substring(insertedText.offsetByCodePoints(insertedText.length, -back))
        val clusters = java.text.BreakIterator.getCharacterInstance(java.util.Locale.ROOT).let {
            it.setText(tail)
            var n = 0
            while (it.next() != java.text.BreakIterator.DONE) n++
            n
        }
        return if (clusters > MAX_KEY_PRESSES) 0 else clusters
    }
}

/**
 * The list the in-keyboard picker shows. Only **enabled** snippets are offered (a disabled snippet is the user's way
 * to park it), sorted by title, optionally narrowed to one tag. Typed search is not offered inside the keyboard (it
 * would need an IME-internal text field); tags are the tap-only way to narrow a long list.
 */
object SnippetPickerModel {
    fun items(library: SnippetLibrary, tag: String? = null): List<Snippet> =
        library.search("").filter { it.enabled && (tag == null || it.tags.any { t -> t.equals(tag, ignoreCase = true) }) }

    /** Tags used by enabled snippets, case-insensitively de-duplicated (first spelling wins), sorted. */
    fun tags(library: SnippetLibrary): List<String> {
        val seen = LinkedHashMap<String, String>()
        for (s in library.snippets) if (s.enabled) for (t in s.tags) seen.putIfAbsent(t.lowercase(java.util.Locale.ROOT), t)
        return seen.values.sortedBy { it.lowercase(java.util.Locale.ROOT) }
    }
}
