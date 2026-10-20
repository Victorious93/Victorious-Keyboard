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

package com.victorious.keyboard.ime.snippets

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

private fun sn(abbr: String?, enabled: Boolean = true, id: String = "id-${abbr ?: "x"}") =
    Snippet(id = id, title = "t", body = "BODY", abbreviation = abbr, enabled = enabled)
private val ON = ExpansionSettings(enabled = true)

class ExpansionMatcherTest : FunSpec({
    val sig = sn("sig")
    val all = listOf(sig, sn("addr"))

    fun m(before: String, typed: Char = ' ', s: ExpansionSettings = ON, sensitive: Boolean = false, list: List<Snippet> = all) =
        ExpansionMatcher.match(before, typed, list, s, sensitive)

    test("expansion is off by default") {
        m("x ;sig", s = ExpansionSettings()) shouldBe null
    }

    test("prefix + abbreviation + boundary expands and reports what to delete") {
        m(";sig") shouldBe ExpansionMatch(sig, 4)
        m("hello ;sig") shouldBe ExpansionMatch(sig, 4)
        m("hello\n;sig", '\n') shouldBe ExpansionMatch(sig, 4)
        m("a\t;sig", '\t') shouldBe ExpansionMatch(sig, 4)
    }

    test("only a boundary character triggers it") {
        m(";sig", 'x') shouldBe null
        m(";sig", '.') shouldBe null
    }

    test("never in a sensitive field") {
        m(";sig", sensitive = true) shouldBe null
    }

    test("the prefix is required and must start the word") {
        m("sig") shouldBe null
        m("foo;sig") shouldBe null
        m(";") shouldBe null
        m("") shouldBe null
        m("hello ") shouldBe null
        m(";unknown") shouldBe null
    }

    test("disabled snippets and snippets without abbreviation do not expand") {
        m(";sig", list = listOf(sn("sig", enabled = false))) shouldBe null
        m(";sig", list = listOf(sn(null))) shouldBe null
    }

    test("case sensitivity is configurable") {
        m(";SIG") shouldBe null
        m(";SIG", s = ON.copy(caseSensitive = false)) shouldBe ExpansionMatch(sig, 4)
    }

    test("a custom multi-character prefix") {
        val s = ON.copy(prefix = "::")
        m("::sig", s = s) shouldBe ExpansionMatch(sig, 5)
        m(";sig", s = s) shouldBe null
    }

    test("invalid settings never match") {
        for (bad in listOf(ON.copy(prefix = ""), ON.copy(prefix = " "), ON.copy(prefix = "abcd"), ON.copy(boundaries = emptySet()))) {
            m(";sig", s = bad) shouldBe null
        }
    }

    test("very long words are ignored cheaply") {
        m(";" + "a".repeat(500)) shouldBe null
        m("x".repeat(10_000) + " ;sig") shouldBe ExpansionMatch(sig, 4)
        m("x".repeat(10_000) + ";sig") shouldBe null
    }

    test("delete count is in UTF-16 chars") {
        val emoji = sn("😀😀")
        m(";😀😀", list = listOf(emoji)) shouldBe ExpansionMatch(emoji, 5)
    }

    test("matching is based on the text before the caret only, so a body is never re-expanded") {
        // After an expansion the shell inserts the body; the matcher is only consulted again on the next typed boundary,
        // and a body that merely *contains* ';sig' mid-word or earlier in the line does not match:
        m("note ;sig and then text") shouldBe null
    }
})
