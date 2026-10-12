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
import io.kotest.matchers.types.shouldBeInstanceOf
import java.io.File
import java.nio.file.Files

private fun sn(id: String, body: String = "B $id") = Snippet(id, "T $id", body)

private fun withDir(block: (File) -> Unit) {
    val dir = Files.createTempDirectory("snippets-test").toFile()
    try { block(dir) } finally { dir.deleteRecursively() }
}

class SnippetFileStoreTest : FunSpec({
    test("a missing file is an empty first run") {
        withDir { dir -> SnippetFileStore(File(dir, "none")).load() shouldBe LoadResult.Loaded(emptyList(), emptyList()) }
    }

    test("save then load round-trips, creating the directory") {
        withDir { dir ->
            val store = SnippetFileStore(File(dir, "nested/snippets"))
            store.save(listOf(sn("a"), sn("b"))) shouldBe SaveResult.Saved
            (store.load() as LoadResult.Loaded).snippets shouldBe listOf(sn("a"), sn("b"))
        }
    }

    test("no temp file is left behind after a successful save") {
        withDir { dir ->
            SnippetFileStore(dir).save(listOf(sn("a")))
            dir.list()!!.sorted() shouldBe listOf("snippets.json")
        }
    }

    test("a second save keeps the previous good file as .bak") {
        withDir { dir ->
            val store = SnippetFileStore(dir)
            store.save(listOf(sn("one")))
            store.save(listOf(sn("two")))
            val bak = SnippetBackup.decode(File(dir, "snippets.json.bak").readBytes()) as DecodeResult.Ok
            bak.snippets.map { it.id } shouldBe listOf("one")
            (store.load() as LoadResult.Loaded).snippets.map { it.id } shouldBe listOf("two")
        }
    }

    test("a corrupt file is reported, left untouched, and a later save keeps it as .corrupt next to the old .bak") {
        withDir { dir ->
            val store = SnippetFileStore(dir)
            store.save(listOf(sn("good")))
            store.save(listOf(sn("good2"))) // now .bak holds "good"
            File(dir, "snippets.json").writeText("{ this is not json")
            store.load().shouldBeInstanceOf<LoadResult.Unreadable>()
            File(dir, "snippets.json").readText() shouldBe "{ this is not json"

            store.save(listOf(sn("new"))) shouldBe SaveResult.Saved
            File(dir, "snippets.json.corrupt").readText() shouldBe "{ this is not json"
            val bak = SnippetBackup.decode(File(dir, "snippets.json.bak").readBytes()) as DecodeResult.Ok
            bak.snippets.map { it.id } shouldBe listOf("good") // the last good copy was not overwritten by the corrupt one
        }
    }

    test("an oversized file is unreadable and not loaded") {
        withDir { dir ->
            File(dir, "snippets.json").outputStream().use { out ->
                val chunk = ByteArray(1024 * 1024) { ' '.code.toByte() }
                repeat(9) { out.write(chunk) }
            }
            (SnippetFileStore(dir).load() as LoadResult.Unreadable).error shouldBe SnippetFileError.TOO_LARGE
        }
    }

    test("entries that no longer validate are reported on load instead of vanishing silently") {
        withDir { dir ->
            File(dir, "snippets.json").writeText(
                """{"format":"victorious-snippets","version":1,"snippets":[
                    {"id":"ok","title":"t","body":"b"},{"id":"bad id","title":"t","body":"b"}]}""",
            )
            val r = SnippetFileStore(dir).load() as LoadResult.Loaded
            r.snippets.map { it.id } shouldBe listOf("ok")
            r.invalid.size shouldBe 1
        }
    }

    test("saving into an impossible location reports failure instead of throwing") {
        withDir { dir ->
            val blocker = File(dir, "file-not-dir").also { it.writeText("x") }
            SnippetFileStore(File(blocker, "sub")).save(listOf(sn("a"))).shouldBeInstanceOf<SaveResult.Failed>()
        }
    }

    test("a snippet with every field survives the file round trip") {
        withDir { dir ->
            val s = Snippet("a-1_x", "Título", "multi\nline {{cursor}}", "ab", listOf("t1"), enabled = false, updatedAtMillis = 99)
            SnippetFileStore(dir).save(listOf(s))
            (SnippetFileStore(dir).load() as LoadResult.Loaded).snippets shouldBe listOf(s)
        }
    }
})
