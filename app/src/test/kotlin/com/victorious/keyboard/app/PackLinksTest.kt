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


package com.victorious.keyboard.app

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith

class PackLinksTest : FunSpec({
    test("a built-in extension without links gets this repository's pages") {
        PackLinks.homepage("org.florisboard.themes", null) shouldBe ProjectUrls.REPO
        PackLinks.issueTracker("org.florisboard.layouts", "") shouldBe ProjectUrls.ISSUES
        PackLinks.homepage("com.victorious.something", "   ") shouldBe ProjectUrls.REPO
    }

    test("an extension that names its own links keeps them") {
        PackLinks.homepage("org.florisboard.themes", "https://example.org/x") shouldBe "https://example.org/x"
        PackLinks.issueTracker("com.victorious.x", "https://example.org/bugs") shouldBe "https://example.org/bugs"
    }

    test("a third-party extension without links shows none") {
        PackLinks.homepage("org.example.pack", null) shouldBe null
        PackLinks.issueTracker("org.example.pack", "") shouldBe null
        PackLinks.homepage("org.florisboardx.pack", null) shouldBe null
        PackLinks.isBuiltIn("") shouldBe false
    }

    test("every Get more target is on this repository") {
        listOf(
            ProjectUrls.GET_EXTENSIONS,
            ProjectUrls.GET_THEMES,
            ProjectUrls.GET_LAYOUTS,
            ProjectUrls.GET_LANGUAGE_PACKS,
            ProjectUrls.GET_DICTIONARY_PACKS,
        ).forEach { it shouldStartWith ProjectUrls.REPO + "/" }
    }
})
