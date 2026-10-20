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

package com.victorious.keyboard.ime.dictionary.packs

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

private fun manifest(
    id: String = "test-pack", title: String = "Test pack", description: String = "d",
    format: String = PackLimits.FORMAT, version: Int = PackLimits.VERSION,
) = """{"format":"$format","version":$version,"id":"$id","title":"$title","description":"$description"}"""

private fun zip(vararg files: Pair<String, ByteArray>): ByteArray {
    val bos = ByteArrayOutputStream()
    ZipOutputStream(bos).use { z ->
        for ((name, data) in files) {
            z.putNextEntry(ZipEntry(name))
            z.write(data)
            z.closeEntry()
        }
    }
    return bos.toByteArray()
}

private fun words(vararg lines: String) = lines.joinToString("\n").toByteArray()
private fun ok(bytes: ByteArray) = DictionaryPackReader.read(bytes).shouldBeInstanceOf<PackReadResult.Ok>().pack
private fun failed(bytes: ByteArray) = DictionaryPackReader.read(bytes).shouldBeInstanceOf<PackReadResult.Failed>().error

private val GOOD_WORDS = words("dictionary=x;date=1;version=1", " w=nmap;f=100;l=all", " w=hashcat;f=90;l=all")

class DictionaryPackReaderTest : FunSpec({
    test("a valid pack is read") {
        val p = ok(zip("pack.json" to manifest().toByteArray(), "words.txt" to GOOD_WORDS))
        p.manifest.id shouldBe "test-pack"
        p.entries shouldBe listOf(PackEntry("nmap", 100), PackEntry("hashcat", 90))
        p.rejectedLines shouldBe 0
    }

    test("extra plain text files such as a README are ignored") {
        val p = ok(zip("pack.json" to manifest().toByteArray(), "words.txt" to GOOD_WORDS, "README.txt" to "hello".toByteArray()))
        p.entries.size shouldBe 2
    }

    test("not a zip, empty and truncated input are refused") {
        failed("hello world".toByteArray()) shouldBe PackError.NOT_A_ZIP
        failed(ByteArray(0)) shouldBe PackError.NOT_A_ZIP
        val good = zip("pack.json" to manifest().toByteArray(), "words.txt" to GOOD_WORDS)
        failed(good.copyOf(good.size / 2)) shouldBe PackError.NOT_A_ZIP
    }

    test("path traversal, nested and absolute names, and directories are refused") {
        for (bad in listOf("../words.txt", "a/words.txt", "/words.txt", ".hidden", "sub/", "..", "a\\b.txt")) {
            failed(zip("pack.json" to manifest().toByteArray(), bad to GOOD_WORDS)) shouldBe PackError.UNSAFE_NAME
        }
    }

    test("too many files are refused") {
        val files = (0..PackLimits.MAX_ZIP_ENTRIES).map { "f$it.txt" to "x".toByteArray() }.toTypedArray()
        failed(zip(*files)) shouldBe PackError.TOO_MANY_FILES
    }

    test("a decompression bomb is stopped by counting decompressed bytes") {
        val bomb = ByteArray(PackLimits.MAX_ENTRY_BYTES + 1) // compresses to a few hundred bytes
        val z = zip("pack.json" to manifest().toByteArray(), "words.txt" to GOOD_WORDS, "README.txt" to bomb)
        (z.size < 5_000) shouldBe true
        failed(z) shouldBe PackError.TOO_LARGE
    }

    test("many files that are each small but together too large are refused") {
        val chunk = ByteArray(PackLimits.MAX_ENTRY_BYTES - 1)
        failed(zip("a.txt" to chunk, "b.txt" to chunk, "c.txt" to chunk)) shouldBe PackError.TOO_LARGE
    }

    test("input larger than the zip limit is refused before it is opened") {
        failed(ByteArray(PackLimits.MAX_ZIP_BYTES + 1)) shouldBe PackError.TOO_LARGE
    }

    test("missing manifest or words") {
        failed(zip("words.txt" to GOOD_WORDS)) shouldBe PackError.MISSING_MANIFEST
        failed(zip("pack.json" to manifest().toByteArray())) shouldBe PackError.MISSING_WORDS
    }

    test("bad manifests") {
        fun bad(m: String) = failed(zip("pack.json" to m.toByteArray(), "words.txt" to GOOD_WORDS))
        bad("not json") shouldBe PackError.BAD_MANIFEST
        bad("") shouldBe PackError.BAD_MANIFEST
        bad(manifest(format = "something-else")) shouldBe PackError.BAD_MANIFEST
        bad(manifest(id = "Has Space")) shouldBe PackError.BAD_MANIFEST
        bad(manifest(id = "../x")) shouldBe PackError.BAD_MANIFEST
        bad(manifest(id = "")) shouldBe PackError.BAD_MANIFEST
        bad(manifest(title = "")) shouldBe PackError.BAD_MANIFEST
        bad(manifest(title = "t".repeat(PackLimits.MAX_TITLE + 1))) shouldBe PackError.BAD_MANIFEST
        bad(manifest(description = "d".repeat(PackLimits.MAX_DESCRIPTION + 1))) shouldBe PackError.BAD_MANIFEST
        bad(manifest(version = 2)) shouldBe PackError.UNSUPPORTED_VERSION
    }

    test("unknown manifest fields are ignored") {
        val m = """{"format":"victorious-dictionary-pack","version":1,"id":"p","title":"T","description":"","future":[1,2]}"""
        ok(zip("pack.json" to m.toByteArray(), "words.txt" to GOOD_WORDS)).manifest.id shouldBe "p"
    }

    test("a pack with only invalid lines has no valid words") {
        failed(zip("pack.json" to manifest().toByteArray(), "words.txt" to words("nonsense", "w=;f=1"))) shouldBe PackError.NO_VALID_WORDS
    }

    test("more words than the limit are refused") {
        val lines = (0..PackLimits.MAX_WORDS).map { "w=word$it;f=10;l=all" }
        failed(zip("pack.json" to manifest().toByteArray(), "words.txt" to lines.joinToString("\n").toByteArray())) shouldBe PackError.TOO_MANY_WORDS
    }

    test("exactly the maximum number of words is accepted") {
        val lines = (1..PackLimits.MAX_WORDS).map { "w=word$it;f=10;l=all" }
        ok(zip("pack.json" to manifest().toByteArray(), "words.txt" to lines.joinToString("\n").toByteArray())).entries.size shouldBe PackLimits.MAX_WORDS
    }
})

class PackWordListTest : FunSpec({
    test("header, comments, blank lines and CRLF are handled") {
        val p = PackWordList.parse("dictionary=x;date=1\r\n# c\r\n\r\n w=a1;f=5;l=all\r\n")
        p.entries shouldBe listOf(PackEntry("a1", 5))
        p.rejected shouldBe 0
    }

    test("the exporter's own format round-trips, including l=null") {
        PackWordList.parse(" w=nmap;f=128;l=null\n w=tcpdump;f=1;l=").entries.map { it.word } shouldBe listOf("nmap", "tcpdump")
    }

    test("long and short keys, any order, extra spaces") {
        PackWordList.parse("f = 7 ; word = abc").entries shouldBe listOf(PackEntry("abc", 7))
    }

    test("invalid lines are counted and never repaired") {
        val p = PackWordList.parse(
            listOf(
                "w=ok;f=10;l=all",
                "w=bad word;f=10",        // whitespace
                "w=a;b;f=10",             // semicolon splits into a bad property
                "w=x;f=0",                // frequency below range
                "w=x;f=256",              // above range
                "w=x;f=abc",
                "w=x",                    // no frequency
                "f=10",                   // no word
                "w=x;f=10;l=de_DE",       // packs are language independent
                "w=x;f=10;s=shortcut",    // shortcuts are not part of packs
                "w=x;f=10;unknown=1",
                "w=" + "w".repeat(PackLimits.MAX_WORD_LENGTH + 1) + ";f=10",
                "w=" + "w".repeat(PackLimits.MAX_LINE_LENGTH) + ";f=10",
                "w=a=b;f=10",
                "w=a,b;f=10",
            ).joinToString("\n")
        )
        p.entries shouldBe listOf(PackEntry("ok", 10))
        p.rejected shouldBe 14
    }

    test("a word at the length limit is accepted, control characters are not") {
        val w = "w".repeat(PackLimits.MAX_WORD_LENGTH)
        PackWordList.parse("w=$w;f=1").entries.size shouldBe 1
        PackWordList.parse("w=a\u0007b;f=1").entries.size shouldBe 0
    }

    test("duplicate words keep the first line and are not an error") {
        val p = PackWordList.parse("w=dup;f=10\nw=dup;f=200\nw=other;f=5")
        p.entries shouldBe listOf(PackEntry("dup", 10), PackEntry("other", 5))
        p.rejected shouldBe 0
    }

    test("slash commands and punctuation inside words are fine; case is kept") {
        PackWordList.parse("w=/compact;f=9\nw=PyTorch;f=9\nw=termux-battery-status;f=9\nw=c++;f=9").entries.map { it.word } shouldBe
            listOf("/compact", "PyTorch", "termux-battery-status", "c++")
    }
})

private fun pack(vararg e: Pair<String, Int>) =
    DictionaryPack(PackManifest(PackLimits.FORMAT, 1, "p", "P", ""), e.map { PackEntry(it.first, it.second) }, 0)

class PackPlannerTest : FunSpec({
    val p = pack("nmap" to 100, "hydra" to 100, "john" to 100)

    test("everything is new in an empty dictionary") {
        val plan = PackPlanner.planInstall(emptyList(), p)
        plan.toInsert.size shouldBe 3
        plan.alreadyPresent shouldBe 0
    }

    test("a word the user already has for all languages is left alone, whatever its frequency") {
        val plan = PackPlanner.planInstall(listOf(ExistingWord(1, "nmap", 7, null, "nm")), p)
        plan.toInsert.map { it.word } shouldBe listOf("hydra", "john")
        plan.alreadyPresent shouldBe 1
    }

    test("a language-specific entry does not block the all-languages word") {
        val plan = PackPlanner.planInstall(listOf(ExistingWord(1, "nmap", 100, "de", null)), p)
        plan.toInsert.size shouldBe 3
    }

    test("installing twice adds nothing the second time") {
        val first = PackPlanner.planInstall(emptyList(), p)
        val existing = first.toInsert.mapIndexed { i, e -> ExistingWord(i + 1L, e.word, e.freq, null, null) }
        PackPlanner.planInstall(existing, p).toInsert shouldBe emptyList()
    }

    test("removal deletes only entries identical to the pack's") {
        val existing = listOf(
            ExistingWord(1, "nmap", 100, null, null),   // identical: removed
            ExistingWord(2, "hydra", 99, null, null),   // user changed the frequency: kept
            ExistingWord(3, "john", 100, null, "j"),    // user added a shortcut: kept
            ExistingWord(4, "nmap", 100, "de", null),   // other language: kept
            ExistingWord(5, "unrelated", 100, null, null),
        )
        PackPlanner.planRemove(existing, p) shouldBe listOf(1L)
    }

    test("removal from an empty dictionary removes nothing") {
        PackPlanner.planRemove(emptyList(), p) shouldBe emptyList()
    }
})
