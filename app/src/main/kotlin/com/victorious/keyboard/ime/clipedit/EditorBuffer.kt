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

package com.victorious.keyboard.ime.clipedit

/**
 * A text buffer with a selection and a bounded undo stack. Pure Kotlin: the keyboard shell
 * redirects key events here so they never reach the host app. The string form is the text itself,
 * so callers must not log a buffer.
 */
data class EditorBuffer(
    val text: String = "",
    val start: Int = 0,
    val end: Int = 0,
    val undo: List<EditorSnapshot> = emptyList(),
    val redo: List<EditorSnapshot> = emptyList(),
) {
    val hasSelection: Boolean get() = start != end
    val selectionMin: Int get() = minOf(start, end).coerceIn(0, text.length)
    val selectionMax: Int get() = maxOf(start, end).coerceIn(0, text.length)
    val selectedText: String get() = text.substring(selectionMin, selectionMax)
    val canUndo: Boolean get() = undo.isNotEmpty()
    val canRedo: Boolean get() = redo.isNotEmpty()

    fun insert(chunk: String): EditorBuffer {
        if (chunk.isEmpty() && !hasSelection) return this
        val next = text.substring(0, selectionMin) + chunk + text.substring(selectionMax)
        if (next.length > MAX_CHARS) return this
        val caret = selectionMin + chunk.length
        return commit(next, caret, caret)
    }

    fun deleteBackward(words: Boolean): EditorBuffer {
        if (hasSelection) return insert("")
        val cursor = end.coerceIn(0, text.length)
        if (cursor == 0) return this
        val from = if (words) SelectionRanges.wordStart(text, cursor) else charBefore(text, cursor)
        return commit(text.removeRange(from, cursor), from, from)
    }

    fun deleteForward(words: Boolean): EditorBuffer {
        if (hasSelection) return insert("")
        val cursor = end.coerceIn(0, text.length)
        if (cursor >= text.length) return this
        val to = if (words) SelectionRanges.wordEnd(text, cursor) else charAfter(text, cursor)
        return commit(text.removeRange(cursor, to), cursor, cursor)
    }

    fun moveHorizontal(direction: Int, words: Boolean, extend: Boolean): EditorBuffer {
        val cursor = end.coerceIn(0, text.length)
        val next = when {
            direction < 0 && words -> SelectionRanges.wordStart(text, cursor)
            direction > 0 && words -> SelectionRanges.wordEnd(text, cursor)
            direction < 0 -> charBefore(text, cursor)
            else -> charAfter(text, cursor)
        }
        return place(next, extend)
    }

    fun moveVertical(lines: Int, extend: Boolean): EditorBuffer {
        val cursor = end.coerceIn(0, text.length)
        val (line, col) = SelectionRanges.lineColumn(text, cursor)
        val rows = SelectionRanges.lines(text)
        val target = (line + lines).coerceIn(0, rows.lastIndex)
        val next = SelectionRanges.offsetAt(rows, target, col.coerceAtMost(rows[target].length))
        return place(next, extend)
    }

    fun moveLineEdge(toEnd: Boolean, extend: Boolean): EditorBuffer {
        val (from, to) = SelectionRanges.line(text, end.coerceIn(0, text.length))
        return place(if (toEnd) to else from, extend)
    }

    fun setCaret(offset: Int, extend: Boolean = false): EditorBuffer = place(offset, extend)

    fun selectWord(): EditorBuffer {
        val (from, to) = SelectionRanges.word(text, end.coerceIn(0, text.length))
        return copy(start = from, end = to)
    }

    fun selectLine(): EditorBuffer {
        val (from, to) = SelectionRanges.line(text, end.coerceIn(0, text.length))
        return copy(start = from, end = to)
    }

    fun selectAll(): EditorBuffer = copy(start = 0, end = text.length)

    fun replaceAllText(next: String): EditorBuffer {
        if (next == text) return this
        if (next.length > MAX_CHARS) return this
        val caret = next.length
        return commit(next, caret, caret)
    }

    /** Selects the next case-insensitive match. Does not record undo. */
    fun findNext(query: String): FindHit {
        if (query.isEmpty()) return FindHit(this, false)
        val from = selectionMax
        var index = indexAfter(query, from)
        if (index < 0) index = text.indexOf(query, startIndex = 0, ignoreCase = true)
        if (index < 0) return FindHit(this, false)
        if (index == selectionMin && selectionMax - selectionMin == query.length) {
            val later = indexAfter(query, index + 1)
            index = when {
                later >= 0 -> later
                else -> text.indexOf(query, startIndex = 0, ignoreCase = true).takeIf { it in 0 until index } ?: -1
            }
        }
        if (index < 0) return FindHit(this, false)
        return FindHit(copy(start = index, end = index + query.length), true)
    }

    fun replaceMatch(query: String, replacement: String): EditorBuffer {
        if (query.isEmpty()) return this
        val selected = selectedText
        if (!selected.equals(query, ignoreCase = true)) return this
        return insert(replacement)
    }

    /** One undo step for every replacement. Returns how many matches changed. */
    fun replaceAll(query: String, replacement: String): Pair<EditorBuffer, Int> {
        if (query.isEmpty()) return this to 0
        val builder = StringBuilder()
        var count = 0
        var index = 0
        while (index < text.length) {
            val found = text.indexOf(query, startIndex = index, ignoreCase = true)
            if (found < 0) {
                builder.append(text.substring(index))
                break
            }
            builder.append(text.substring(index, found))
            builder.append(replacement)
            count++
            index = found + query.length
            if (query.isEmpty()) break
        }
        if (count == 0) return this to 0
        val next = builder.toString()
        if (next.length > MAX_CHARS) return this to 0
        return commit(next, next.length, next.length) to count
    }

    fun undo(): EditorBuffer {
        val previous = undo.lastOrNull() ?: return this
        val current = snapshot()
        return copy(
            text = previous.text,
            start = previous.start.coerceIn(0, previous.text.length),
            end = previous.end.coerceIn(0, previous.text.length),
            undo = undo.dropLast(1),
            redo = (redo + current).takeLast(UNDO_CAP),
        )
    }

    fun redo(): EditorBuffer {
        val next = redo.lastOrNull() ?: return this
        val current = snapshot()
        return copy(
            text = next.text,
            start = next.start.coerceIn(0, next.text.length),
            end = next.end.coerceIn(0, next.text.length),
            undo = (undo + current).takeLast(UNDO_CAP),
            redo = redo.dropLast(1),
        )
    }

    private fun place(offset: Int, extend: Boolean): EditorBuffer {
        val caret = offset.coerceIn(0, text.length)
        return if (extend) copy(end = caret) else copy(start = caret, end = caret)
    }

    private fun commit(next: String, nextStart: Int, nextEnd: Int): EditorBuffer {
        if (next == text && nextStart == start && nextEnd == end) return this
        return copy(
            text = next,
            start = nextStart.coerceIn(0, next.length),
            end = nextEnd.coerceIn(0, next.length),
            undo = (undo + snapshot()).takeLast(UNDO_CAP),
            redo = emptyList(),
        )
    }

    private fun snapshot() = EditorSnapshot(text, start, end)

    private fun indexAfter(query: String, from: Int): Int {
        if (from > text.length) return -1
        return text.indexOf(query, startIndex = from.coerceAtLeast(0), ignoreCase = true)
    }

    companion object {
        const val UNDO_CAP = 50
        const val MAX_CHARS = 100_000

        fun of(text: String): EditorBuffer {
            val bounded = if (text.length > MAX_CHARS) text.substring(0, MAX_CHARS) else text
            return EditorBuffer(bounded, bounded.length, bounded.length)
        }
    }
}

data class EditorSnapshot(val text: String, val start: Int, val end: Int)

data class FindHit(val buffer: EditorBuffer, val found: Boolean)

object SelectionRanges {
    fun word(text: String, cursor: Int): Pair<Int, Int> {
        if (text.isEmpty()) return 0 to 0
        var index = cursor.coerceIn(0, text.length)
        if (index > 0 && (index == text.length || text[index].isWhitespace()) && !text[index - 1].isWhitespace()) {
            index--
        }
        if (index < text.length && text[index].isWhitespace()) {
            val from = index
            while (index < text.length && text[index].isWhitespace()) index++
            return from to index
        }
        var from = index
        var to = index
        while (from > 0 && !text[from - 1].isWhitespace()) from--
        while (to < text.length && !text[to].isWhitespace()) to++
        return from to to
    }

    fun wordStart(text: String, cursor: Int): Int {
        var index = cursor.coerceIn(0, text.length)
        while (index > 0 && text[index - 1].isWhitespace()) index--
        while (index > 0 && !text[index - 1].isWhitespace()) index--
        return index
    }

    fun wordEnd(text: String, cursor: Int): Int {
        var index = cursor.coerceIn(0, text.length)
        while (index < text.length && text[index].isWhitespace()) index++
        while (index < text.length && !text[index].isWhitespace()) index++
        return index
    }

    fun line(text: String, cursor: Int): Pair<Int, Int> {
        val index = cursor.coerceIn(0, text.length)
        val from = if (index == 0) 0 else {
            val newline = text.lastIndexOf('\n', index - 1)
            if (newline < 0) 0 else newline + 1
        }
        val newline = text.indexOf('\n', index)
        val to = if (newline < 0) text.length else newline
        return from to to
    }

    fun lines(text: String): List<String> = if (text.isEmpty()) listOf("") else text.split('\n')

    fun lineColumn(text: String, cursor: Int): Pair<Int, Int> {
        val (from, _) = line(text, cursor)
        val rows = lines(text)
        var offset = 0
        for (row in rows.indices) {
            if (offset == from) return row to (cursor - from).coerceAtLeast(0)
            offset += rows[row].length + 1
        }
        return 0 to 0
    }

    fun offsetAt(rows: List<String>, line: Int, column: Int): Int {
        var offset = 0
        for (row in 0 until line.coerceIn(0, rows.lastIndex)) {
            offset += rows[row].length + 1
        }
        return offset + column.coerceIn(0, rows[line.coerceIn(0, rows.lastIndex)].length)
    }
}

private fun charBefore(text: String, cursor: Int): Int {
    if (cursor <= 0) return 0
    val previous = cursor - 1
    if (previous > 0 && text[previous].isLowSurrogate() && text[previous - 1].isHighSurrogate()) {
        return previous - 1
    }
    return previous
}

private fun charAfter(text: String, cursor: Int): Int {
    if (cursor >= text.length) return text.length
    val next = cursor + 1
    if (next < text.length && text[cursor].isHighSurrogate() && text[next].isLowSurrogate()) {
        return next + 1
    }
    return next
}
