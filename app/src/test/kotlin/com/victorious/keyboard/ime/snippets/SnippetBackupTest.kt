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

private fun sn(id: String, abbr: String? = null, title: String = "T $id", body: String = "B $id") =
    Snippet(id, title, body, abbr, updatedAtMillis = 7)
private fun file(json: String) = json.toByteArray(Charsets.UTF_8)
private fun ok(r: DecodeResult) = r as DecodeResult.Ok
private fun failed(r: DecodeResult) = (r as DecodeResult.Failed).error

class SnippetBackupTest : FunSpec({
    test("round trip keeps every field, including unicode and newlines") {
        val list = listOf(
            Snippet("a", "Título 🔐", "line1\nline2\t{{cursor}}", "sig", listOf("x", "y"), enabled = false, updatedAtMillis = 123),
            sn("b"),
        )
        ok(SnippetBackup.decode(SnippetBackup.encode(list))).snippets shouldBe list
    }

    test("the file declares its format and version") {
        val text = SnippetBackup.encode(emptyList()).toString(Charsets.UTF_8)
        text.contains("\"format\": \"victorious-snippets\"") shouldBe true
        text.contains("\"version\": 1") shouldBe true
    }

    test("unknown keys are ignored (forward compatible within a version)") {
        val r = ok(SnippetBackup.decode(file(
            """{"format":"victorious-snippets","version":1,"extra":{"a":[1,2,3]},
               "snippets":[{"id":"a","title":"t","body":"b","future":true}]}""",
        )))
        r.snippets.map { it.id } shouldBe listOf("a")
    }

    test("wrong format, newer version, version 0 and non-JSON are refused") {
        failed(SnippetBackup.decode(file("""{"format":"clipboard","version":1,"snippets":[]}"""))) shouldBe SnippetFileError.WRONG_FORMAT
        failed(SnippetBackup.decode(file("""{"format":"victorious-snippets","version":2,"snippets":[]}"""))) shouldBe SnippetFileError.NEWER_VERSION
        failed(SnippetBackup.decode(file("""{"format":"victorious-snippets","version":0,"snippets":[]}"""))) shouldBe SnippetFileError.MALFORMED
        failed(SnippetBackup.decode(file("not json"))) shouldBe SnippetFileError.MALFORMED
        failed(SnippetBackup.decode(file(""))) shouldBe SnippetFileError.MALFORMED
        failed(SnippetBackup.decode(file("[]"))) shouldBe SnippetFileError.MALFORMED
        failed(SnippetBackup.decode(file("""{"format":"victorious-snippets","version":1,"snippets":"nope"}"""))) shouldBe SnippetFileError.MALFORMED
        failed(SnippetBackup.decode(ByteArray(64) { (it * 37).toByte() })) shouldBe SnippetFileError.MALFORMED
    }

    test("oversized input is rejected before parsing") {
        failed(SnippetBackup.decode(ByteArray(SnippetLimits.MAX_FILE_BYTES + 1) { ' '.code.toByte() })) shouldBe SnippetFileError.TOO_LARGE
    }

    test("too many snippets are refused as a whole") {
        val many = List(SnippetLimits.MAX_SNIPPETS + 1) { sn("s$it") }
        failed(SnippetBackup.decode(SnippetBackup.encode(many))) shouldBe SnippetFileError.TOO_MANY
        ok(SnippetBackup.decode(SnippetBackup.encode(many.take(SnippetLimits.MAX_SNIPPETS)))).snippets.size shouldBe 500
    }

    test("invalid entries are left out and reported; valid ones survive") {
        val bytes = file(
            """{"format":"victorious-snippets","version":1,"snippets":[
                {"id":"good","title":"ok","body":"b"},
                {"id":"bad id","title":"ok","body":"b"},
                {"id":"nobody","title":"ok","body":""},
                {"id":"nul","title":"ok","body":"a\u0000b"},
                {"id":"good","title":"dup","body":"b"},
                {"id":"fine","title":"ok","body":"b","abbreviation":"x y"}]}""",
        )
        val r = ok(SnippetBackup.decode(bytes))
        r.snippets.map { it.id } shouldBe listOf("good")
        r.invalid.map { it.index } shouldBe listOf(1, 2, 3, 4, 5)
        r.invalid[3].duplicateId shouldBe true
        r.invalid[0].issues shouldBe listOf(SnippetIssue.BAD_ID)
    }

    test("hostile nesting and huge numbers do not crash the decoder") {
        val deep = "[".repeat(100_000) + "]".repeat(100_000)
        val nested = """{"format":"victorious-snippets","version":1,"unknown":$deep,"snippets":[]}"""
        // either skipped as an unknown key or refused: both are fine, a crash or hang is not
        val r = try { SnippetBackup.decode(file(nested)) } catch (e: StackOverflowError) { error("stack overflow on nested input") }
        (r is DecodeResult.Ok || r is DecodeResult.Failed) shouldBe true
        failed(SnippetBackup.decode(file("""{"format":"victorious-snippets","version":99999999999999999999,"snippets":[]}"""))) shouldBe SnippetFileError.MALFORMED
    }

    test("import SKIP_EXISTING never changes an existing snippet") {
        val lib = SnippetLibrary.EMPTY.add(sn("a", "sig", title = "mine")).let { (it as LibraryResult.Ok).library }
        val out = SnippetImporter.merge(lib, listOf(sn("a", title = "theirs"), sn("b")), ImportMode.SKIP_EXISTING)
        out.added shouldBe 1
        out.updated shouldBe 0
        out.skipped shouldBe listOf(ImportSkip("theirs", LibraryError.DUPLICATE_ID))
        out.library.byId("a")!!.title shouldBe "mine"
        out.library.snippets.size shouldBe 2
    }

    test("import OVERWRITE_BY_ID replaces by id and adds the rest") {
        val lib = SnippetLibrary.EMPTY.add(sn("a", title = "mine")).let { (it as LibraryResult.Ok).library }
        val out = SnippetImporter.merge(lib, listOf(sn("a", title = "theirs"), sn("b")), ImportMode.OVERWRITE_BY_ID)
        out.added shouldBe 1
        out.updated shouldBe 1
        out.library.byId("a")!!.title shouldBe "theirs"
    }

    test("an imported abbreviation that clashes with another snippet is skipped, not applied") {
        val lib = SnippetLibrary.EMPTY.add(sn("a", "sig")).let { (it as LibraryResult.Ok).library }
        val out = SnippetImporter.merge(lib, listOf(sn("b", "SIG"), sn("c", "ok")), ImportMode.OVERWRITE_BY_ID)
        out.skipped shouldBe listOf(ImportSkip("T b", LibraryError.DUPLICATE_ABBREVIATION))
        out.library.snippets.map { it.id } shouldBe listOf("a", "c")
    }

    test("a full library skips the overflow and keeps everything it had") {
        var lib = SnippetLibrary.EMPTY
        repeat(SnippetLimits.MAX_SNIPPETS) { lib = (lib.add(sn("s$it")) as LibraryResult.Ok).library }
        val out = SnippetImporter.merge(lib, listOf(sn("new1"), sn("new2")), ImportMode.OVERWRITE_BY_ID)
        out.added shouldBe 0
        out.skipped.map { it.reason } shouldBe listOf(LibraryError.FULL, LibraryError.FULL)
        out.library.snippets.size shouldBe SnippetLimits.MAX_SNIPPETS
    }

    test("import of an empty list changes nothing") {
        val out = SnippetImporter.merge(SnippetLibrary.EMPTY, emptyList(), ImportMode.SKIP_EXISTING)
        out.library.snippets shouldBe emptyList()
        out.skipped shouldBe emptyList()
    }
})
