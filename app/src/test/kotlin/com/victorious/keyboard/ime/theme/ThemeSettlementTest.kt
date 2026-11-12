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

package com.victorious.keyboard.ime.theme

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/** Regression for the gray upstream fallback style drawn before the selected theme was loaded. */
class ThemeSettlementTest : FunSpec({
    test("a theme missing from a still-empty index is not final, so the fallback is not drawn") {
        ThemeSettlement.isSettledWhenNotFound(indexInitialized = false) shouldBe false
    }
    test("a theme missing from an index that was read is final: the fallback is the honest result") {
        ThemeSettlement.isSettledWhenNotFound(indexInitialized = true) shouldBe true
    }
})
