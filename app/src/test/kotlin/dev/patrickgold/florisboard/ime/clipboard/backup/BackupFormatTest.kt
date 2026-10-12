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

package dev.patrickgold.florisboard.ime.clipboard.backup

import dev.patrickgold.florisboard.ime.clipboard.history.ClipCategory
import dev.patrickgold.florisboard.ime.clipboard.history.ClipEntry
import dev.patrickgold.florisboard.ime.clipboard.history.ClipHash
import dev.patrickgold.florisboard.ime.clipboard.history.entry
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

private val meta = ExportMeta(createdAtMs = 1_700_000_000_000L, appVersion = "0.1.0-test")
private const val ITERS = 100_000

private val sample: List<ClipEntry> = listOf(
    entry(1, "plain text", pinned = true, favorite = true, tags = setOf("a", "b"), category = ClipCategory.GENERAL),
    entry(2, "https://example.org", category = ClipCategory.URL, editedAtMs = 9_999L),
    entry(3, "multi\nline\n\ttabbed — ünïcödé 🔐 \u0000 nul", category = ClipCategory.CODE),
    entry(4, "```fenced``` with ``` backticks", category = ClipCategory.MARKDOWN),
)

private fun zipOf(vararg members: Pair<String, ByteArray>): ByteArray {
    val out = ByteArrayOutputStream()
    ZipOutputStream(out).use { z ->
        for ((n, b) in members) {
            z.putNextEntry(ZipEntry(n)); z.write(b); z.closeEntry()
        }
    }
    return out.toByteArray()
}

private fun backupEntryJson(e: ClipEntry, hash: String = e.contentHash, id: String = e.id, type: String = "text") =
    """{"id":"$id","type":"$type","text":${jsonString(e.text)},"createdAtMs":${e.createdAtMs},"contentHash":"$hash"}"""

private fun jsonString(s: String) = kotlinx.serialization.json.JsonPrimitive(s).toString()

private fun manifestJson(count: Int, entriesSha: String?, version: Int = 1, format: String = BACKUP_FORMAT_NAME) =
    """{"format":"$format","formatVersion":$version,"createdAtMs":1,"appVersion":"x","itemCount":$count""" +
        (if (entriesSha != null) ""","entriesSha256":"$entriesSha"""" else "") + "}"

/** Builds a ZIP backup from raw JSON so tests can corrupt individual layers. */
private fun craftedZip(entriesJson: String, count: Int, version: Int = 1, format: String = BACKUP_FORMAT_NAME): ByteArray {
    val eb = entriesJson.toByteArray()
    return zipOf(
        "manifest.json" to manifestJson(count, ClipHash.sha256Hex(eb), version, format).toByteArray(),
        "entries.json" to eb,
    )
}

class BackupFormatTest : FunSpec({
    context("round trips preserve every field") {
        fun check(parsed: ParsedBackup) {
            parsed.entries.sortedBy { it.id } shouldBe sample.sortedBy { it.id }
            parsed.manifest.itemCount shouldBe sample.size
            parsed.manifest.formatVersion shouldBe BACKUP_FORMAT_VERSION
            parsed.unsupportedEntries shouldBe 0
        }
        test("JSON") { check(BackupReader.read(BackupWriter.writeJson(sample, meta), null)) }
        test("ZIP") { check(BackupReader.read(BackupWriter.writeZip(sample, meta), null)) }
        test("encrypted") {
            val pw = "a long enough password".toCharArray()
            check(BackupReader.read(BackupWriter.writeEncrypted(sample, meta, pw, iterations = ITERS), pw))
        }
    }

    test("manifest records the metadata the brief requires") {
        val m = BackupReader.read(BackupWriter.writeZip(sample, meta), null).manifest
        m.format shouldBe "victorious-keyboard-clipboard"
        m.createdAtMs shouldBe meta.createdAtMs
        m.appVersion shouldBe "0.1.0-test"
        m.supportedEntryTypes shouldBe listOf("text")
        (m.entriesSha256?.length) shouldBe 64
        m.attachments shouldBe emptyList()
    }

    test("export is deterministic for identical input") {
        BackupWriter.writeZip(sample, meta).toList() shouldBe BackupWriter.writeZip(sample.reversed(), meta).toList()
        BackupWriter.writeJson(sample, meta).toList() shouldBe BackupWriter.writeJson(sample.shuffled(), meta).toList()
    }

    test("the JSON export is human readable") {
        val text = BackupWriter.writeJson(sample, meta).toString(Charsets.UTF_8)
        text shouldContain "\"text\": \"plain text\""
        text shouldContain "\"formatVersion\": 1"
    }

    test("detection") {
        BackupReader.detect(BackupWriter.writeJson(sample, meta)) shouldBe BackupKind.JSON
        BackupReader.detect(BackupWriter.writeZip(sample, meta)) shouldBe BackupKind.ZIP
        BackupReader.detect(
            BackupWriter.writeEncrypted(sample, meta, "long enough pw".toCharArray(), iterations = ITERS)
        ) shouldBe BackupKind.ENCRYPTED
        BackupReader.detect("hello".toByteArray()) shouldBe BackupKind.UNKNOWN
        BackupReader.detect(ByteArray(0)) shouldBe BackupKind.UNKNOWN
    }

    context("rejecting files that are not (valid) backups") {
        test("arbitrary bytes") {
            shouldThrow<BackupException.NotABackup> { BackupReader.read("just some text".toByteArray(), null) }
            shouldThrow<BackupException.NotABackup> { BackupReader.read(ByteArray(0), null) }
        }
        test("JSON that is another format") {
            shouldThrow<BackupException> { BackupReader.read("""{"hello":"world"}""".toByteArray(), null) }
        }
        test("wrong format name") {
            val z = craftedZip("""{"entries":[]}""", 0, format = "something-else")
            shouldThrow<BackupException.NotABackup> { BackupReader.read(z, null) }
        }
        test("truncated ZIP") {
            val z = BackupWriter.writeZip(sample, meta)
            shouldThrow<BackupException> { BackupReader.read(z.copyOf(z.size / 2), null) }
        }
        test("garbage that starts like a ZIP") {
            shouldThrow<BackupException> { BackupReader.read(byteArrayOf(0x50, 0x4B, 1, 2, 3, 4, 5), null) }
        }
        test("malformed JSON inside a valid ZIP") {
            val eb = "{ not json".toByteArray()
            val z = zipOf(
                "manifest.json" to manifestJson(0, ClipHash.sha256Hex(eb)).toByteArray(),
                "entries.json" to eb,
            )
            shouldThrow<BackupException.Malformed> { BackupReader.read(z, null) }
        }
        test("missing members") {
            shouldThrow<BackupException.Malformed> { BackupReader.read(zipOf("manifest.json" to "{}".toByteArray()), null) }
            shouldThrow<BackupException.Malformed> { BackupReader.read(zipOf("other.txt" to "x".toByteArray()), null) }
        }
    }

    context("versions and migrations") {
        test("a newer format is refused with a clear, typed error") {
            val z = craftedZip("""{"entries":[]}""", 0, version = 2)
            val e = shouldThrow<BackupException.UnsupportedVersion> { BackupReader.read(z, null) }
            e.found shouldBe 2
        }
        test("version 0 and negative versions are malformed") {
            shouldThrow<BackupException.Malformed> { BackupReader.read(craftedZip("""{"entries":[]}""", 0, version = 0), null) }
            shouldThrow<BackupException.Malformed> { BackupReader.read(craftedZip("""{"entries":[]}""", 0, version = -3), null) }
        }
        test("unknown extra fields are ignored (forward compatibility)") {
            val e = sample[0]
            val ej = """{"entries":[{"id":"${e.id}","text":"hi","createdAtMs":5,"contentHash":"${ClipHash.sha256Hex("hi")}","futureField":{"x":1}}],"alsoNew":1}"""
            BackupReader.read(craftedZip(ej, 1), null).entries.single().text shouldBe "hi"
        }
        test("an unknown category name falls back to GENERAL") {
            val ej = """{"entries":[{"id":"a1","text":"hi","createdAtMs":5,"category":"FROM_THE_FUTURE","contentHash":"${ClipHash.sha256Hex("hi")}"}]}"""
            BackupReader.read(craftedZip(ej, 1), null).entries.single().category shouldBe ClipCategory.GENERAL
        }
        test("entries of an unsupported type are counted, not imported") {
            val a = backupEntryJson(entry(1, "keep"))
            val b = backupEntryJson(entry(2, "image-ish"), type = "image")
            val parsed = BackupReader.read(craftedZip("""{"entries":[$a,$b]}""", 2), null)
            parsed.entries.map { it.text } shouldBe listOf("keep")
            parsed.unsupportedEntries shouldBe 1
        }
    }

    context("integrity") {
        test("modified entries.json no longer matches the manifest hash") {
            val good = BackupWriter.writeZip(sample, meta)
            val parsedZip = java.util.zip.ZipInputStream(good.inputStream())
            val members = HashMap<String, ByteArray>()
            while (true) {
                val ze = parsedZip.nextEntry ?: break
                members[ze.name] = parsedZip.readBytes()
            }
            val tampered = members["entries.json"]!!.toString(Charsets.UTF_8).replace("plain text", "PLAIN TEXT")
            val z = zipOf("manifest.json" to members["manifest.json"]!!, "entries.json" to tampered.toByteArray())
            shouldThrow<BackupException.Corrupt> { BackupReader.read(z, null) }
        }
        test("an entry whose text no longer matches its own hash") {
            val e = entry(1, "real")
            val ej = """{"entries":[${backupEntryJson(e, hash = ClipHash.sha256Hex("forged"))}]}"""
            shouldThrow<BackupException.Corrupt> { BackupReader.read(craftedZip(ej, 1), null) }
        }
        test("item count that disagrees with the manifest") {
            val ej = """{"entries":[${backupEntryJson(entry(1))}]}"""
            shouldThrow<BackupException.Corrupt> { BackupReader.read(craftedZip(ej, 5), null) }
        }
        test("duplicate entry ids") {
            val a = backupEntryJson(entry(1, "one"))
            val b = backupEntryJson(entry(2, "two"), id = entry(1).id)
            shouldThrow<BackupException.Malformed> { BackupReader.read(craftedZip("""{"entries":[$a,$b]}""", 2), null) }
        }
        test("invalid ids are rejected") {
            for (bad in listOf("", "has space", "../x", "a".repeat(65), "semi;colon")) {
                val ej = """{"entries":[${backupEntryJson(entry(1), id = bad)}]}"""
                shouldThrow<BackupException.Malformed> { BackupReader.read(craftedZip(ej, 1), null) }
            }
        }
        test("negative timestamps are rejected") {
            val ej = """{"entries":[{"id":"a","text":"x","createdAtMs":-5,"contentHash":"${ClipHash.sha256Hex("x")}"}]}"""
            shouldThrow<BackupException.Malformed> { BackupReader.read(craftedZip(ej, 1), null) }
        }
        test("a single-file JSON backup claiming a ZIP-style entries hash is refused") {
            val original = BackupWriter.writeJson(sample, meta).toString(Charsets.UTF_8)
            original shouldContain "\"entriesSha256\": null"
            val doc = original.replace("\"entriesSha256\": null", "\"entriesSha256\": \"00\"")
            shouldThrow<BackupException.Malformed> { BackupReader.read(doc.toByteArray(), null) }
        }
    }

    context("hostile archives") {
        test("path traversal member names are rejected, nothing is ever written to disk") {
            for (name in listOf("../evil.txt", "a/../../evil", "/etc/passwd", "..\\evil", "C:\\evil", "x\u0000y")) {
                val z = zipOf("manifest.json" to "{}".toByteArray(), name to "x".toByteArray())
                shouldThrow<BackupException.Malformed> { BackupReader.read(z, null) }
            }
        }
        test("unknown members are ignored, not extracted") {
            val ej = """{"entries":[]}"""
            val eb = ej.toByteArray()
            val z = zipOf(
                "manifest.json" to manifestJson(0, ClipHash.sha256Hex(eb)).toByteArray(),
                "entries.json" to eb,
                "attachments/readme.txt" to "ignored".toByteArray(),
            )
            BackupReader.read(z, null).entries.size shouldBe 0
        }
        test("duplicate member names are rejected") {
            // java.util.zip refuses to *write* duplicates, so write a distinct name and patch it into a duplicate.
            val eb = """{"entries":[]}""".toByteArray()
            val z = zipOf(
                "manifest.json" to manifestJson(0, ClipHash.sha256Hex(eb)).toByteArray(),
                "entries.json" to eb,
                "entries.jsoN" to eb,
            )
            val from = "entries.jsoN".toByteArray()
            val to = "entries.json".toByteArray()
            var patched = 0
            var i = 0
            while (i <= z.size - from.size) {
                if (from.indices.all { z[i + it] == from[it] }) {
                    to.copyInto(z, i); patched++; i += from.size
                } else i++
            }
            (patched >= 1) shouldBe true
            shouldThrow<BackupException.Malformed> { BackupReader.read(z, null) }
        }
        test("a ZIP bomb is stopped by the uncompressed-size limit") {
            val big = ByteArray(5_000_000) // 5 MB of zeros compresses to a few KB
            val z = zipOf("manifest.json" to big, "entries.json" to "{}".toByteArray())
            (z.size < 100_000) shouldBe true
            val limits = BackupLimits(maxUncompressedBytes = 1_000_000)
            shouldThrow<BackupException.TooLarge> { BackupReader.read(z, null, limits) }
        }
        test("too many archive members") {
            val members = (0 until 30).map { "f$it.txt" to ByteArray(1) }.toTypedArray()
            shouldThrow<BackupException.TooLarge> { BackupReader.read(zipOf(*members), null) }
        }
        test("oversized files are refused before parsing") {
            shouldThrow<BackupException.TooLarge> {
                BackupReader.read(ByteArray(2_000), null, BackupLimits(maxFileBytes = 1_000))
            }
        }
        test("too many entries") {
            val ej = """{"entries":[${(1..5).joinToString(",") { backupEntryJson(entry(it)) }}]}"""
            shouldThrow<BackupException.TooLarge> {
                BackupReader.read(craftedZip(ej, 5), null, BackupLimits(maxEntries = 3))
            }
        }
        test("an over-long entry text") {
            val ej = """{"entries":[${backupEntryJson(entry(1, "x".repeat(50)))}]}"""
            shouldThrow<BackupException.TooLarge> {
                BackupReader.read(craftedZip(ej, 1), null, BackupLimits(maxTextChars = 10))
            }
        }
        test("JSON of the wrong shape (deep nesting) is rejected with a typed error, not a crash") {
            val deep = "[".repeat(100_000) + "]".repeat(100_000)
            val result = runCatching { BackupReader.read(craftedZip(deep, 0), null) }
            (result.exceptionOrNull() is BackupException) shouldBe true
        }
    }

    context("encryption integration") {
        val pw = "a long enough password".toCharArray()
        test("encrypted backups need a password") {
            val enc = BackupWriter.writeEncrypted(sample, meta, pw, iterations = ITERS)
            shouldThrow<BackupException.PasswordRequired> { BackupReader.read(enc, null) }
        }
        test("wrong password") {
            val enc = BackupWriter.writeEncrypted(sample, meta, pw, iterations = ITERS)
            shouldThrow<BackupException.WrongPasswordOrTampered> {
                BackupReader.read(enc, "another password!".toCharArray())
            }
        }
        test("the encrypted file reveals neither entry text nor metadata") {
            val enc = BackupWriter.writeEncrypted(sample, meta, pw, iterations = ITERS)
            val s = String(enc, Charsets.ISO_8859_1)
            s shouldNotContain "plain text"
            s shouldNotContain "victorious-keyboard-clipboard"
            s shouldNotContain "0.1.0-test"
        }
    }

    context("human-readable exports") {
        test("markdown fences cannot be broken out of by clip content") {
            val md = BackupWriter.writeMarkdown(sample, meta).toString(Charsets.UTF_8)
            md shouldContain "````" // entry 4 contains a run of 3 backticks, so the fence is at least 4 long
            md shouldContain "plain text"
        }
        test("plain text export contains the texts") {
            BackupWriter.writeText(sample).toString(Charsets.UTF_8) shouldContain "https://example.org"
        }
    }
})
