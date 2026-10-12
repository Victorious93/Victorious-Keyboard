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

class SnippetPreviewTest : FunSpec({
    test("short text is unchanged") {
        SnippetPreview.of("hello", 10) shouldBe "hello"
    }
    test("line breaks, tabs and runs of spaces collapse to single spaces, ends are trimmed") {
        SnippetPreview.of("  a\n\n b\t\tc \r\n ", 50) shouldBe "a b c"
    }
    test("empty and whitespace-only bodies give an empty preview") {
        SnippetPreview.of("", 5) shouldBe ""
        SnippetPreview.of(" \n\t ", 5) shouldBe ""
    }
    test("text exactly at the limit has no ellipsis") {
        SnippetPreview.of("abcde", 5) shouldBe "abcde"
    }
    test("longer text is cut and gets an ellipsis") {
        SnippetPreview.of("abcdefgh", 5) shouldBe "abcde" + SnippetPreview.ELLIPSIS
    }
    test("a cut never leaves a trailing space before the ellipsis") {
        SnippetPreview.of("abcd efgh", 5) shouldBe "abcd" + SnippetPreview.ELLIPSIS
    }
    test("an emoji at the cut is kept whole or dropped, never split") {
        val e = "😀"
        SnippetPreview.of("ab" + e + e + "cd", 3) shouldBe "ab" + e + SnippetPreview.ELLIPSIS
        val out = SnippetPreview.of("a" + e.repeat(10), 4)
        out shouldBe "a" + e.repeat(3) + SnippetPreview.ELLIPSIS
        out.codePointCount(0, out.length) shouldBe 5
    }
    test("the result never exceeds the budget plus the ellipsis, even for a maximum-size body") {
        val body = "word ".repeat(SnippetLimits.MAX_BODY / 5)
        val p = SnippetPreview.of(body, 120)
        p.codePointCount(0, p.length) shouldBe 120 // 119 characters (the 120th would be a pending space) + ellipsis
        p.endsWith(SnippetPreview.ELLIPSIS) shouldBe true
    }
    test("control characters are treated like whitespace") {
        SnippetPreview.of("a\u0000b", 10) shouldBe "a b"
    }
    test("bidi controls are dropped so a body cannot reorder its own preview") {
        SnippetPreview.of("\u202Eabc\u2066d\u200Fe", 10) shouldBe "abcde"
    }
    test("no-break and other space separators collapse like spaces") {
        SnippetPreview.of("a\u00A0\u00A0b\u2003c", 10) shouldBe "a b c"
    }
    test("zero-width joiners inside an emoji sequence are kept") {
        val family = "\uD83D\uDC68\u200D\uD83D\uDC69\u200D\uD83D\uDC67"
        SnippetPreview.of(family, 10) shouldBe family
    }
})
