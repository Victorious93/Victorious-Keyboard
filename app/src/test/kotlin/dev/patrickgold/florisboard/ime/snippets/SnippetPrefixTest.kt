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

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class SnippetPrefixTest : FunSpec({
    test("enum names are persisted: this list may only grow at the end") {
        SnippetPrefix.entries.map { it.name } shouldBe
            listOf("SEMICOLON", "COLON", "SLASH", "BACKSLASH", "DOUBLE_SEMICOLON", "DOUBLE_COLON")
    }

    test("every offered prefix is a valid expansion prefix and they are all distinct") {
        for (p in SnippetPrefix.entries) ExpansionSettings(enabled = true, prefix = p.text).isValid shouldBe true
        SnippetPrefix.entries.map { it.text }.distinct().size shouldBe SnippetPrefix.entries.size
    }

    test("each prefix actually triggers an expansion") {
        val sig = Snippet("a", "t", "BODY", "sig")
        for (p in SnippetPrefix.entries) {
            val m = ExpansionMatcher.match("x ${p.text}sig", ' ', listOf(sig), ExpansionSettings(true, p.text), sensitiveField = false)
            m?.deleteCount shouldBe p.text.length + 3
        }
    }
})
