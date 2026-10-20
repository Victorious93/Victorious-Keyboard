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

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class ToolTileFitTest : FunSpec({
    test("short tiles show one label line, tall tiles two") {
        ToolTileFit.labelLines(52f) shouldBe 1
        ToolTileFit.labelLines(63.9f) shouldBe 1
        ToolTileFit.labelLines(64f) shouldBe 2
        ToolTileFit.labelLines(88f) shouldBe 2
    }

    test("a non-finite height falls back to one line") {
        ToolTileFit.labelLines(Float.NaN) shouldBe 1
        ToolTileFit.labelLines(Float.POSITIVE_INFINITY) shouldBe 1
    }
})
