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

import com.victorious.keyboard.ime.clipboard.backup.BackupCrypto
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

// 100_000 is the smallest iteration count the reader accepts; it keeps the suite fast.
private const val ITERS = 100_000
private val PASSWORD = "correct horse battery".toCharArray()

private val SNIPPETS = listOf(
    Snippet("a", "Título 🔐", "line1\nline2\t{{cursor}}", "sig", listOf("x", "y"), enabled = false, updatedAtMillis = 123),
    Snippet("b", "Second", "SECRET-BODY-MARKER-98765", updatedAtMillis = 7),
)

private fun export(snippets: List<Snippet> = SNIPPETS, password: CharArray = PASSWORD): ByteArray =
    (SnippetEncryptedBackup.encode(snippets, password, iterations = ITERS) as EncryptedExport.Ok).bytes

private fun failed(r: DecodeResult) = (r as DecodeResult.Failed).error

class SnippetEncryptedBackupTest : FunSpec({
    test("round trip keeps every field, including unicode and newlines") {
        val r = SnippetEncryptedBackup.decode(export(), PASSWORD) as DecodeResult.Ok
        r.snippets shouldBe SNIPPETS
        r.invalid shouldBe emptyList()
    }

    test("an empty library round trips") {
        (SnippetEncryptedBackup.decode(export(emptyList()), PASSWORD) as DecodeResult.Ok).snippets shouldBe emptyList()
    }

    test("the file is the shared container and shows neither titles nor bodies") {
        val enc = export()
        SnippetEncryptedBackup.isEncrypted(enc) shouldBe true
        val text = String(enc, Charsets.ISO_8859_1)
        text.contains("SECRET-BODY-MARKER") shouldBe false
        text.contains("victorious-snippets") shouldBe false
    }

    test("salt and nonce are random per file") {
        val a = export()
        val b = export()
        a.toList() shouldNotBe b.toList()
        a.copyOfRange(12, 40).toList() shouldNotBe b.copyOfRange(12, 40).toList()
    }

    test("a wrong password is reported as WRONG_PASSWORD") {
        failed(SnippetEncryptedBackup.decode(export(), "not the password".toCharArray())) shouldBe
            SnippetFileError.WRONG_PASSWORD
    }

    test("a changed byte anywhere in the ciphertext is reported like a wrong password") {
        val enc = export()
        enc[enc.size - 20] = (enc[enc.size - 20].toInt() xor 1).toByte()
        failed(SnippetEncryptedBackup.decode(enc, PASSWORD)) shouldBe SnippetFileError.WRONG_PASSWORD
    }

    test("a changed salt byte is detected, because the header is authenticated") {
        val enc = export()
        enc[15] = (enc[15].toInt() xor 1).toByte()
        failed(SnippetEncryptedBackup.decode(enc, PASSWORD)) shouldBe SnippetFileError.WRONG_PASSWORD
    }

    test("a truncated file fails closed") {
        val enc = export()
        failed(SnippetEncryptedBackup.decode(enc.copyOf(enc.size - 1), PASSWORD)) shouldBe SnippetFileError.WRONG_PASSWORD
        failed(SnippetEncryptedBackup.decode(enc.copyOf(20), PASSWORD)) shouldBe SnippetFileError.MALFORMED
    }

    test("a crafted iteration count is refused before any key is derived") {
        val enc = export()
        // Bytes 8..11 hold the iteration count; 0x7FFFFFFF is far above the reader's cap.
        enc[8] = 0x7F; enc[9] = 0xFF.toByte(); enc[10] = 0xFF.toByte(); enc[11] = 0xFF.toByte()
        failed(SnippetEncryptedBackup.decode(enc, PASSWORD)) shouldBe SnippetFileError.MALFORMED
    }

    test("a newer container version is reported as NEWER_VERSION") {
        val enc = export()
        enc[4] = 2
        failed(SnippetEncryptedBackup.decode(enc, PASSWORD)) shouldBe SnippetFileError.NEWER_VERSION
    }

    test("a password shorter than the minimum is refused and nothing is written") {
        val r = SnippetEncryptedBackup.encode(SNIPPETS, "short".toCharArray(), iterations = ITERS)
        (r as EncryptedExport.Failed).error shouldBe SnippetFileError.WEAK_PASSWORD
    }

    test("a password of exactly the minimum length works") {
        val pw = "x".repeat(BackupCrypto.MIN_PASSWORD_LENGTH).toCharArray()
        (SnippetEncryptedBackup.decode(export(password = pw), pw) as DecodeResult.Ok).snippets shouldBe SNIPPETS
    }

    test("the same visible password in a different Unicode form opens the file (NFC)") {
        val composed = "pässword-ünï".toCharArray()
        val decomposed = java.text.Normalizer.normalize("pässword-ünï", java.text.Normalizer.Form.NFD).toCharArray()
        (SnippetEncryptedBackup.decode(export(password = composed), decomposed) as DecodeResult.Ok).snippets shouldBe SNIPPETS
    }

    test("a file that is not encrypted is refused here, and nothing is read as JSON") {
        failed(SnippetEncryptedBackup.decode(SnippetBackup.encode(SNIPPETS), PASSWORD)) shouldBe SnippetFileError.WRONG_FORMAT
    }

    test("the plain reader names an encrypted file instead of calling it malformed") {
        failed(SnippetBackup.decode(export())) shouldBe SnippetFileError.ENCRYPTED
    }

    test("a file over the size cap is refused before decryption") {
        failed(SnippetEncryptedBackup.decode(ByteArray(SnippetEncryptedBackup.MAX_ENCRYPTED_BYTES + 1), PASSWORD)) shouldBe
            SnippetFileError.TOO_LARGE
    }

    test("a clipboard container with the right password is not read as snippets") {
        // Same container, different payload: a plain ZIP header stands in for a clipboard backup.
        val clipboardLike = BackupCrypto.encrypt(byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0, 0, 0, 0), PASSWORD, iterations = ITERS)
        failed(SnippetEncryptedBackup.decode(clipboardLike, PASSWORD)) shouldBe SnippetFileError.MALFORMED
    }

    test("a valid container around another JSON format is refused as WRONG_FORMAT") {
        val other = """{"format":"clipboard","version":1,"snippets":[]}""".toByteArray()
        val enc = BackupCrypto.encrypt(other, PASSWORD, iterations = ITERS)
        failed(SnippetEncryptedBackup.decode(enc, PASSWORD)) shouldBe SnippetFileError.WRONG_FORMAT
    }

    test("invalid entries inside a valid container are left out and reported, like a plain import") {
        val json = """{"format":"victorious-snippets","version":1,"snippets":[
            {"id":"ok","title":"t","body":"b"},{"id":"bad id","title":"t","body":"b"}]}"""
        val enc = BackupCrypto.encrypt(json.toByteArray(), PASSWORD, iterations = ITERS)
        val r = SnippetEncryptedBackup.decode(enc, PASSWORD) as DecodeResult.Ok
        r.snippets.map { it.id } shouldBe listOf("ok")
        r.invalid.size shouldBe 1
    }

    test("exporting more than the readable size is refused rather than writing an unreadable file") {
        // 500 snippets at the body limit is ~5 MB, under the cap, so build the overflow through the title+body maximum.
        val big = (1..500).map { Snippet("id$it", "t", "x".repeat(SnippetLimits.MAX_BODY), updatedAtMillis = 1) }
        val plainSize = SnippetBackup.encode(big).size
        val r = SnippetEncryptedBackup.encode(big, PASSWORD, iterations = ITERS)
        if (plainSize > SnippetLimits.MAX_FILE_BYTES) {
            (r as EncryptedExport.Failed).error shouldBe SnippetFileError.TOO_LARGE
        } else {
            (SnippetEncryptedBackup.decode((r as EncryptedExport.Ok).bytes, PASSWORD) as DecodeResult.Ok).snippets.size shouldBe 500
        }
    }
})
