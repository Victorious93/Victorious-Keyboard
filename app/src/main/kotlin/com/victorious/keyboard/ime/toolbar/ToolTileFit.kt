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
 * How many label lines a tools-panel tile can show. A tile holds a glyph (18sp) and the label (12sp lines); below
 * [TWO_LINE_MIN_DP] a second label line is clipped from the bottom, so short panels (landscape) get one line.
 */
object ToolTileFit {
    const val TWO_LINE_MIN_DP = 64f

    fun labelLines(tileHeightDp: Float): Int =
        if (tileHeightDp.isFinite() && tileHeightDp >= TWO_LINE_MIN_DP) 2 else 1
}
