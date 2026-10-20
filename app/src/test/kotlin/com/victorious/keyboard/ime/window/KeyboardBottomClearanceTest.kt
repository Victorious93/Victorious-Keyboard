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

package com.victorious.keyboard.ime.window

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class KeyboardBottomClearanceTest : FunSpec({
    test("the system inset is the largest of the navigation and gesture insets") {
        val gesture = BottomObstructionPx(
            navigationBars = 24,
            mandatorySystemGestures = 48,
            tappableElement = 0,
            captionBar = 16,
        )
        KeyboardBottomClearance.reportedInsetPx(gesture) shouldBe 48
        KeyboardBottomClearance.navigationStyle(gesture) shouldBe NavigationStyle.GESTURE

        val buttons = BottomObstructionPx(
            navigationBars = 48,
            mandatorySystemGestures = 0,
            tappableElement = 48,
            captionBar = 0,
        )
        KeyboardBottomClearance.reportedInsetPx(buttons) shouldBe 48
        KeyboardBottomClearance.navigationStyle(buttons) shouldBe NavigationStyle.BUTTONS
    }

    test("a missing inset uses the fallback only when one is supplied") {
        val none = BottomObstructionPx()
        KeyboardBottomClearance.reportedInsetPx(none) shouldBe 0
        KeyboardBottomClearance.resolvedSystemInsetPx(none, fallbackPx = 0) shouldBe 0
        KeyboardBottomClearance.resolvedSystemInsetPx(none, fallbackPx = 48) shouldBe 48
        KeyboardBottomClearance.navigationStyle(none) shouldBe NavigationStyle.UNKNOWN
        val reported = BottomObstructionPx(navigationBars = 20)
        KeyboardBottomClearance.resolvedSystemInsetPx(reported, fallbackPx = 48) shouldBe 20
    }

    test("the extra gap defaults to 16 dp on gesture navigation and 4 dp on buttons") {
        KeyboardBottomClearance.defaultGapDp(NavigationStyle.GESTURE) shouldBe 16
        KeyboardBottomClearance.defaultGapDp(NavigationStyle.BUTTONS) shouldBe 4
        KeyboardBottomClearance.defaultGapDp(NavigationStyle.UNKNOWN) shouldBe 16
        KeyboardBottomClearance.effectiveGapDp(KeyboardBottomClearance.GAP_AUTOMATIC, NavigationStyle.GESTURE) shouldBe 16
        KeyboardBottomClearance.effectiveGapDp(KeyboardBottomClearance.GAP_AUTOMATIC, NavigationStyle.BUTTONS) shouldBe 4
        KeyboardBottomClearance.effectiveGapDp(0, NavigationStyle.GESTURE) shouldBe 0
        KeyboardBottomClearance.effectiveGapDp(48, NavigationStyle.BUTTONS) shouldBe 48
        KeyboardBottomClearance.effectiveGapDp(80, NavigationStyle.GESTURE) shouldBe 48
        KeyboardBottomClearance.effectiveGapDp(-5, NavigationStyle.BUTTONS) shouldBe 4
    }

    test("a touch that ends in the system strip is not a key, and the user gap above it still is") {
        KeyboardBottomClearance.pointerInSystemStrip(pointerYInRootPx = 1000f, rootHeightPx = 1000, systemInsetPx = 48) shouldBe true
        KeyboardBottomClearance.pointerInSystemStrip(pointerYInRootPx = 952f, rootHeightPx = 1000, systemInsetPx = 48) shouldBe true
        KeyboardBottomClearance.pointerInSystemStrip(pointerYInRootPx = 951.9f, rootHeightPx = 1000, systemInsetPx = 48) shouldBe false
        KeyboardBottomClearance.pointerInSystemStrip(pointerYInRootPx = 900f, rootHeightPx = 1000, systemInsetPx = 0) shouldBe false
        KeyboardBottomClearance.pointerInSystemStrip(pointerYInRootPx = 10f, rootHeightPx = 0, systemInsetPx = 48) shouldBe false
    }
})
