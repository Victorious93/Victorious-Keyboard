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

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.types.shouldBeInstanceOf
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

private fun zip(vararg files: Pair<String, String>): ByteArray {
    val out = ByteArrayOutputStream()
    ZipOutputStream(out).use { z ->
        for ((name, text) in files) {
            z.putNextEntry(ZipEntry(name))
            z.write(text.toByteArray())
            z.closeEntry()
        }
    }
    return out.toByteArray()
}

private fun manifest(id: String, title: String = "My pack") =
    """{"format":"victorious-dictionary-pack","version":1,"id":"$id","title":"$title","description":"mine"}"""

private fun pack(id: String, words: List<String> = listOf("alpha", "beta")): ByteArray =
    zip("pack.json" to manifest(id), "words.txt" to words.joinToString("\n") { " w=$it;f=128;l=all" })

private fun tempDir(): File = Files.createTempDirectory("packs-test").toFile().also { it.deleteOnExit() }

class ImportedPackStoreTest : FunSpec({
    val bundled = setOf("termux", "kali-tools")

    test("a valid pack is stored as <id>.zip, byte for byte, and listed") {
        val dir = tempDir()
        val store = ImportedPackStore(File(dir, "dictionary-packs"))
        val bytes = pack("my-words")
        val saved = store.save(bytes, bundled).shouldBeInstanceOf<ImportResult.Saved>()
        saved.replaced shouldBe false
        saved.pack.manifest.id shouldBe "my-words"
        saved.sha256.length shouldBe 64
        File(dir, "dictionary-packs/my-words.zip").readBytes().toList() shouldBe bytes.toList()
        store.list().map { it.manifest.id } shouldBe listOf("my-words")
        store.list().single().entries.map { it.word } shouldBe listOf("alpha", "beta")
    }

    test("the directory is created on first use, and the list of a missing directory is empty") {
        val dir = File(tempDir(), "not/yet/there")
        val store = ImportedPackStore(dir)
        store.list() shouldBe emptyList()
        store.save(pack("first"), bundled).shouldBeInstanceOf<ImportResult.Saved>()
        dir.isDirectory shouldBe true
    }

    test("a pack may not take the id of a bundled pack, and nothing is written") {
        val dir = tempDir()
        val store = ImportedPackStore(dir)
        store.save(pack("termux"), bundled) shouldBe ImportResult.Refused(ImportRefusal.RESERVED_BUNDLED_ID)
        store.save(pack("kali-tools"), bundled) shouldBe ImportResult.Refused(ImportRefusal.RESERVED_BUNDLED_ID)
        dir.listFiles()!!.size shouldBe 0
        store.list() shouldBe emptyList()
    }

    test("importing the same id again replaces the stored file atomically and leaves no temporary file") {
        val dir = tempDir()
        val store = ImportedPackStore(dir)
        store.save(pack("mine", listOf("one")), bundled)
        val second = store.save(pack("mine", listOf("two", "three")), bundled).shouldBeInstanceOf<ImportResult.Saved>()
        second.replaced shouldBe true
        store.list().single().entries.map { it.word } shouldBe listOf("two", "three")
        dir.listFiles()!!.map { it.name } shouldBe listOf("mine.zip")
    }

    test("at most 20 packs are kept; replacing one at the limit is still allowed") {
        val dir = tempDir()
        val store = ImportedPackStore(dir)
        for (i in 1..ImportedPackStore.MAX_IMPORTED) {
            store.save(pack("pack-$i"), bundled).shouldBeInstanceOf<ImportResult.Saved>()
        }
        store.save(pack("one-too-many"), bundled) shouldBe ImportResult.Refused(ImportRefusal.LIMIT_REACHED)
        store.list().size shouldBe ImportedPackStore.MAX_IMPORTED
        store.save(pack("pack-7", listOf("changed")), bundled).shouldBeInstanceOf<ImportResult.Saved>()
        store.list().size shouldBe ImportedPackStore.MAX_IMPORTED
        store.delete("pack-3") shouldBe true
        store.save(pack("one-too-many"), bundled).shouldBeInstanceOf<ImportResult.Saved>()
    }

    test("a file that is not a valid pack is refused with the reader's reason and nothing is written") {
        val dir = tempDir()
        val store = ImportedPackStore(dir)
        val cases = mapOf(
            "not a zip" to ("not a zip at all".toByteArray() to PackError.NOT_A_ZIP),
            "empty" to (ByteArray(0) to PackError.NOT_A_ZIP),
            "too large" to (ByteArray(PackLimits.MAX_ZIP_BYTES + 1) to PackError.TOO_LARGE),
            "no manifest" to (zip("words.txt" to " w=a;f=1;l=all") to PackError.MISSING_MANIFEST),
            "no words" to (zip("pack.json" to manifest("x")) to PackError.MISSING_WORDS),
            "unsafe name" to (zip("../pack.json" to manifest("x"), "words.txt" to " w=a;f=1;l=all") to PackError.UNSAFE_NAME),
            "bad id" to (zip("pack.json" to manifest("../evil"), "words.txt" to " w=a;f=1;l=all") to PackError.BAD_MANIFEST),
            "upper id" to (zip("pack.json" to manifest("Mine"), "words.txt" to " w=a;f=1;l=all") to PackError.BAD_MANIFEST),
            "no valid words" to (zip("pack.json" to manifest("x"), "words.txt" to "garbage") to PackError.NO_VALID_WORDS),
        )
        for ((name, case) in cases) {
            withClue(name) { store.save(case.first, bundled) shouldBe ImportResult.Invalid(case.second) }
        }
        dir.listFiles()!!.size shouldBe 0
    }

    test("a hostile id never becomes a path") {
        for (id in listOf("../x", "a/b", "a\\b", "", ".", "..", "A", "-x", "x".repeat(41), "x.zip", "x y", "x\u0000")) {
            withClue(id) { ImportedPackStore.fileNameFor(id) shouldBe null }
        }
        ImportedPackStore.fileNameFor("good-id-1") shouldBe "good-id-1.zip"
        val dir = tempDir()
        val outside = File(dir.parentFile, "outside-${System.nanoTime()}.zip").also { it.writeText("keep"); it.deleteOnExit() }
        val store = ImportedPackStore(dir)
        store.delete("../" + outside.name.removeSuffix(".zip")) shouldBe false
        outside.exists() shouldBe true
    }

    test("delete removes only that pack's file, and false for one that is not stored") {
        val dir = tempDir()
        val store = ImportedPackStore(dir)
        store.save(pack("keep"), bundled)
        store.save(pack("drop"), bundled)
        store.delete("drop") shouldBe true
        store.delete("drop") shouldBe false
        store.delete("never-there") shouldBe false
        store.list().map { it.manifest.id } shouldBe listOf("keep")
    }

    test("damaged, foreign and oversize files in the directory are skipped, never fatal, never deleted") {
        val dir = tempDir()
        val store = ImportedPackStore(dir)
        store.save(pack("good"), bundled)
        File(dir, "broken.zip").writeText("garbage")
        File(dir, "huge.zip").writeBytes(ByteArray(PackLimits.MAX_ZIP_BYTES + 10))
        File(dir, "notes.txt").writeText("hello")
        File(dir, "Upper.zip").writeBytes(pack("upper"))
        File(dir, "good.zip.tmp").writeBytes(pack("half"))
        File(dir, "subdir.zip").mkdir()
        // A valid pack stored under another pack's name must not pass as that pack.
        File(dir, "wrong-name.zip").writeBytes(pack("other-id"))
        store.list().map { it.manifest.id } shouldBe listOf("good")
        File(dir, "broken.zip").exists() shouldBe true
        File(dir, "notes.txt").exists() shouldBe true
    }

    test("the list is sorted by id") {
        val dir = tempDir()
        val store = ImportedPackStore(dir)
        for (id in listOf("zeta", "alpha", "mid")) store.save(pack(id), bundled)
        store.list().map { it.manifest.id } shouldBe listOf("alpha", "mid", "zeta")
    }

    test("the file that is stored is the file that was picked: its SHA-256 matches PackHash") {
        val dir = tempDir()
        val store = ImportedPackStore(dir)
        val bytes = pack("hashed")
        val saved = store.save(bytes, bundled).shouldBeInstanceOf<ImportResult.Saved>()
        com.victorious.keyboard.ime.packs.PackHash.matches(File(dir, "hashed.zip").readBytes(), saved.sha256) shouldBe true
        com.victorious.keyboard.ime.packs.PackHash.matches(bytes, saved.sha256) shouldBe true
    }

    test("the store is kept out of backups, off the main thread's concern, and never logs") {
        val installer = File("src/main/kotlin/com/victorious/keyboard/ime/dictionary/packs/DictionaryPackInstaller.kt").readText()
        installer.contains("noBackupFilesDir") shouldBe true
        // Never the backed-up app directories.
        installer.contains(".filesDir") shouldBe false
        installer.contains("getFilesDir") shouldBe false
        installer.contains("externalFilesDir") shouldBe false
        installer.contains("withContext(Dispatchers.IO)") shouldBe true
        for (name in listOf("ImportedPackStore.kt", "DictionaryPackInstaller.kt")) {
            val text = File("src/main/kotlin/com/victorious/keyboard/ime/dictionary/packs/$name").readText()
            for (m in listOf("Log.", "Timber", "println", "printStackTrace")) {
                withClue("$name uses $m") { text shouldNotContain m }
            }
        }
        // No Android types in the store itself, so it stays testable on the JVM.
        File("src/main/kotlin/com/victorious/keyboard/ime/dictionary/packs/ImportedPackStore.kt").readText() shouldNotContain "import android."
    }
})
