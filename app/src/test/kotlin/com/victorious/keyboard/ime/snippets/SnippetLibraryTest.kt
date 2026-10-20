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
import io.kotest.matchers.types.shouldBeInstanceOf

private fun sn(id: String, abbr: String? = null, title: String = "Title $id", body: String = "body $id", tags: List<String> = emptyList()) =
    Snippet(id, title, body, abbr, tags)

private fun LibraryResult.lib(): SnippetLibrary = (this as LibraryResult.Ok).library

class SnippetLibraryTest : FunSpec({
    test("add, update and remove") {
        var lib = SnippetLibrary.EMPTY.add(sn("a")).lib()
        lib.snippets.size shouldBe 1
        lib = lib.update(sn("a", title = "New")).lib()
        lib.byId("a")!!.title shouldBe "New"
        lib = lib.remove("a").lib()
        lib.snippets shouldBe emptyList()
    }

    test("duplicate ids and unknown ids are rejected") {
        val lib = SnippetLibrary.EMPTY.add(sn("a")).lib()
        lib.add(sn("a")) shouldBe LibraryResult.Rejected(LibraryError.DUPLICATE_ID)
        lib.update(sn("zzz")) shouldBe LibraryResult.Rejected(LibraryError.NOT_FOUND)
        lib.remove("zzz") shouldBe LibraryResult.Rejected(LibraryError.NOT_FOUND)
    }

    test("abbreviations are unique case-insensitively, but a snippet may keep its own") {
        val lib = SnippetLibrary.EMPTY.add(sn("a", "sig")).lib()
        lib.add(sn("b", "SIG")) shouldBe LibraryResult.Rejected(LibraryError.DUPLICATE_ABBREVIATION)
        lib.update(sn("a", "sig", title = "edited")).shouldBeInstanceOf<LibraryResult.Ok>()
        val two = lib.add(sn("b", "addr")).lib()
        two.update(sn("b", "Sig")) shouldBe LibraryResult.Rejected(LibraryError.DUPLICATE_ABBREVIATION)
    }

    test("a snippet without an abbreviation never conflicts") {
        var lib = SnippetLibrary.EMPTY
        repeat(3) { lib = lib.add(sn("n$it")).lib() }
        lib.snippets.size shouldBe 3
    }

    test("invalid snippets are rejected with the reasons") {
        val r = SnippetLibrary.EMPTY.add(Snippet("bad id!", " ", "", "x y", List(11) { "t" }))
        r.shouldBeInstanceOf<LibraryResult.Rejected>()
        r.error shouldBe LibraryError.INVALID
        r.issues.toSet() shouldBe setOf(
            SnippetIssue.BAD_ID, SnippetIssue.BLANK_TITLE, SnippetIssue.EMPTY_BODY,
            SnippetIssue.BAD_ABBREVIATION, SnippetIssue.TOO_MANY_TAGS,
        )
    }

    test("field limits") {
        fun issues(s: Snippet) = SnippetValidator.validate(s).errors
        issues(sn("a", title = "x".repeat(81))) shouldBe listOf(SnippetIssue.TITLE_TOO_LONG)
        issues(sn("a", body = "x".repeat(10_001))) shouldBe listOf(SnippetIssue.BODY_TOO_LONG)
        issues(sn("a", body = "x".repeat(10_000))) shouldBe emptyList()
        issues(sn("a", title = "line\nbreak")) shouldBe listOf(SnippetIssue.BAD_TITLE_CHARS)
        issues(sn("a", body = "ok\n\tfine\r\n")) shouldBe emptyList()
        issues(sn("a", body = "nul\u0000here")) shouldBe listOf(SnippetIssue.BAD_BODY_CHARS)
        issues(sn("a", abbr = "x")) shouldBe listOf(SnippetIssue.BAD_ABBREVIATION)
        issues(sn("a", abbr = "x".repeat(33))) shouldBe listOf(SnippetIssue.BAD_ABBREVIATION)
        issues(sn("a", tags = listOf("ok", ""))) shouldBe listOf(SnippetIssue.BAD_TAG)
    }

    test("validation reports unknown placeholders without failing") {
        val v = SnippetValidator.validate(sn("a", body = "{{date}} {{exec:ls}}"))
        v.isValid shouldBe true
        v.unknownPlaceholders shouldBe listOf("exec:ls")
    }

    test("the library is full at 500 and keeps what it has") {
        var lib = SnippetLibrary.EMPTY
        repeat(SnippetLimits.MAX_SNIPPETS) { lib = lib.add(sn("s$it")).lib() }
        lib.add(sn("one-too-many")) shouldBe LibraryResult.Rejected(LibraryError.FULL)
        lib.snippets.size shouldBe SnippetLimits.MAX_SNIPPETS
        lib.update(sn("s0", title = "still editable")).shouldBeInstanceOf<LibraryResult.Ok>()
    }

    test("search: all words, case-insensitive, over title, abbreviation, tags and body; sorted by title") {
        val lib = SnippetLibrary.EMPTY
            .add(sn("1", "gco", title = "Git checkout", body = "git checkout -b {{cursor}}", tags = listOf("git"))).lib()
            .add(sn("2", "ssh", title = "SSH key", body = "ssh-keygen -t ed25519", tags = listOf("net"))).lib()
            .add(sn("3", title = "Another git thing", body = "git status")).lib()
        lib.search("").map { it.id } shouldBe listOf("3", "1", "2")
        lib.search("GIT").map { it.id } shouldBe listOf("3", "1")
        lib.search("git checkout").map { it.id } shouldBe listOf("1")
        lib.search("ed25519 ssh").map { it.id } shouldBe listOf("2")
        lib.search("net").map { it.id } shouldBe listOf("2")
        lib.search("gco").map { it.id } shouldBe listOf("1")
        lib.search("nonexistent") shouldBe emptyList()
    }
})
