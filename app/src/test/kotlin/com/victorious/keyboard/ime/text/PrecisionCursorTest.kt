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

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class PrecisionCursorTest : FunSpec({
    test("a drag moves the caret and a long-press drag selects") {
        PrecisionCursor.gesture(longPressed = false, moved = true) shouldBe SpaceGesture.MOVE
        PrecisionCursor.gesture(longPressed = false, moved = false) shouldBe SpaceGesture.MOVE
        PrecisionCursor.gesture(longPressed = true, moved = false) shouldBe SpaceGesture.LONG_PRESS
        PrecisionCursor.gesture(longPressed = true, moved = true) shouldBe SpaceGesture.SELECT
    }

    test("cursor steps stay inside the text and report one tick per character") {
        PrecisionCursor.move(4, 10, 3) shouldBe CursorPlace(7, 7)
        PrecisionCursor.move(4, 10, -9) shouldBe CursorPlace(0, 0)
        PrecisionCursor.move(4, 10, 20) shouldBe CursorPlace(10, 10)
        PrecisionCursor.ticks(4, 7) shouldBe 3
        PrecisionCursor.ticks(7, 4) shouldBe 3
        PrecisionCursor.ticks(4, 4) shouldBe 0
    }

    test("selection grows from the anchor and clamps to the visible window") {
        PrecisionCursor.select(origin = 0, length = 20, anchor = 10, units = -4) shouldBe CursorPlace(6, 10)
        PrecisionCursor.select(origin = 0, length = 20, anchor = 10, units = 15) shouldBe CursorPlace(10, 20)
        PrecisionCursor.select(origin = 100, length = 8, anchor = 104, units = -10) shouldBe CursorPlace(100, 104)
        PrecisionCursor.select(origin = 0, length = 5, anchor = 2, units = 0) shouldBe CursorPlace(2, 2)
    }

    test("word jump lands on the next or previous word edge") {
        val text = "alpha beta gamma"
        PrecisionCursor.jump(text, 7, forward = false) shouldBe 6
        PrecisionCursor.jump(text, 6, forward = false) shouldBe 0
        PrecisionCursor.jump(text, 0, forward = true) shouldBe 5
        PrecisionCursor.jump(text, 5, forward = true) shouldBe 10
    }

    test("a drag grows the selection that was already there") {
        PrecisionCursor.selectFrom(0, 20, 6, 11, 1) shouldBe CursorPlace(6, 12)
        PrecisionCursor.selectFrom(0, 20, 6, 11, -2) shouldBe CursorPlace(4, 11)
        PrecisionCursor.selectFrom(0, 20, 6, 11, 0) shouldBe CursorPlace(6, 11)
        PrecisionCursor.selectFrom(0, 20, 10, 10, -4) shouldBe CursorPlace(6, 10)
    }

    test("an open clip editor supplies the touch-down caret") {
        PrecisionCursor.anchors(clipOpen = true, clipStart = 5, clipEnd = 5, hostStart = 500, hostEnd = 500) shouldBe (5 to 5)
        PrecisionCursor.anchors(clipOpen = false, clipStart = 5, clipEnd = 5, hostStart = 10, hostEnd = 12) shouldBe (10 to 12)
    }
})
