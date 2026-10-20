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

package com.victorious.keyboard.ime.clipboard

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class ClipMediaRefTest : FunSpec({
    test("a media uri yields its file id and a broken uri yields nothing") {
        ClipMediaRef.idOf("content://com.victorious.keyboard.clipboard/images/42") shouldBe 42L
        ClipMediaRef.idOf("content://clips/7?grant=1") shouldBe 7L
        ClipMediaRef.idOf(null) shouldBe null
        ClipMediaRef.idOf("") shouldBe null
        ClipMediaRef.idOf("content://clips/images") shouldBe null
        ClipMediaRef.idOf("content://clips/not-a-number") shouldBe null
        ClipMediaRef.idOf("content://clips/0") shouldBe null
        ClipMediaRef.idOf("content://clips/-3") shouldBe null
    }

    test("video thumbnail size is used only when both dimensions are positive") {
        ClipMediaRef.videoPixels("320", "240") shouldBe (320 to 240)
        ClipMediaRef.videoPixels(null, "240") shouldBe null
        ClipMediaRef.videoPixels("320", null) shouldBe null
        ClipMediaRef.videoPixels("0", "240") shouldBe null
        ClipMediaRef.videoPixels("wide", "240") shouldBe null
    }
})
