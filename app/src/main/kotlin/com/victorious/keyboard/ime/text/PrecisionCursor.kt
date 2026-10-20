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

package com.victorious.keyboard.ime.text

import com.victorious.keyboard.ime.clipedit.SelectionRanges
import kotlin.math.abs

/** What a space-bar gesture should do. Drag moves. Long-press then drag selects. */
enum class SpaceGesture {
    MOVE,
    SELECT,
    LONG_PRESS,
}

/** Selection-toolbar actions. Names are not persisted. */
enum class SelectionAction {
    WORD,
    LINE,
    ALL,
    CUT,
    COPY,
    PASTE,
    HOME,
    END,
    WORD_BACK,
    WORD_FORWARD,
    CLOSE,
}

/** A caret or a selection. [start] and [end] are inclusive/exclusive and ordered. */
data class CursorPlace(val start: Int, val end: Int) {
    val collapsed: Boolean get() = start == end
}

/**
 * Character steps for a space-bar drag. The view supplies the unit count from the gesture
 * detector. This object does not read the field and does not log text.
 */
object PrecisionCursor {
    fun gesture(longPressed: Boolean, moved: Boolean): SpaceGesture = when {
        longPressed && moved -> SpaceGesture.SELECT
        longPressed -> SpaceGesture.LONG_PRESS
        else -> SpaceGesture.MOVE
    }

    /** Signed character offset. [absUnits] is already signed: left is negative. */
    fun units(absUnits: Int): Int = absUnits

    fun move(cursor: Int, length: Int, delta: Int): CursorPlace {
        val next = (cursor + delta).coerceIn(0, length)
        return CursorPlace(next, next)
    }

    /**
     * Selection from a fixed [anchor] inside a window of [length] characters that starts at
     * [origin]. [units] is the signed distance from the anchor.
     */
    fun select(origin: Int, length: Int, anchor: Int, units: Int): CursorPlace {
        val windowStart = origin.coerceAtLeast(0)
        val windowEnd = windowStart + length.coerceAtLeast(0)
        val from = anchor.coerceIn(windowStart, windowEnd)
        val to = (from.toLong() + units).coerceIn(windowStart.toLong(), windowEnd.toLong()).toInt()
        return CursorPlace(minOf(from, to), maxOf(from, to))
    }

    /** Haptic ticks for one drag step. One tick per character the active edge actually moves. */
    fun ticks(previous: Int, next: Int): Int = abs(next - previous)

    fun jump(text: String, cursor: Int, forward: Boolean): Int =
        if (forward) SelectionRanges.wordEnd(text, cursor) else SelectionRanges.wordStart(text, cursor)
}
