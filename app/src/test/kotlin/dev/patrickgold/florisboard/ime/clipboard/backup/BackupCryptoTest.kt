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

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.nio.ByteBuffer
import java.security.SecureRandom

class BackupCryptoTest : FunSpec({
    // 100_000 is the minimum the reader accepts; keeps the suite fast.
    val iters = 100_000
    val password = "correct horse battery".toCharArray()
    val plain = "hello clipboard — ünïcödé 🔐".toByteArray(Charsets.UTF_8)

    test("round trip") {
        val enc = BackupCrypto.encrypt(plain, password, iterations = iters)
        BackupCrypto.isEncrypted(enc) shouldBe true
        BackupCrypto.decrypt(enc, password).toList() shouldBe plain.toList()
    }

    test("plaintext does not appear in the encrypted file") {
        val secret = "TOP-SECRET-MARKER-12345".toByteArray()
        val enc = BackupCrypto.encrypt(secret, password, iterations = iters)
        String(enc, Charsets.ISO_8859_1).contains("TOP-SECRET-MARKER") shouldBe false
    }

    test("salt and nonce are random per file, so identical inputs give different files") {
        val a = BackupCrypto.encrypt(plain, password, iterations = iters)
        val b = BackupCrypto.encrypt(plain, password, iterations = iters)
        a.toList() shouldNotBe b.toList()
        a.copyOfRange(12, 40).toList() shouldNotBe b.copyOfRange(12, 40).toList() // salt+nonce
    }

    test("wrong password fails with a typed error") {
        val enc = BackupCrypto.encrypt(plain, password, iterations = iters)
        shouldThrow<BackupException.WrongPasswordOrTampered> {
            BackupCrypto.decrypt(enc, "not the password".toCharArray())
        }
    }

    test("flipping any single byte anywhere is detected") {
        val enc = BackupCrypto.encrypt(plain, password, iterations = iters)
        // Header bytes that are validated structurally fail as Malformed/Unsupported/NotABackup; everything else
        // must fail authentication. In no case may tampered input decrypt successfully.
        for (i in enc.indices) {
            val bad = enc.copyOf().also { it[i] = (it[i].toInt() xor 0x01).toByte() }
            val result = runCatching { BackupCrypto.decrypt(bad, password) }
            result.isFailure shouldBe true
            (result.exceptionOrNull() is BackupException) shouldBe true
        }
    }

    test("truncation is detected") {
        val enc = BackupCrypto.encrypt(plain, password, iterations = iters)
        for (cut in listOf(0, 3, 10, 39, 40, 45, enc.size - 1)) {
            shouldThrow<BackupException> { BackupCrypto.decrypt(enc.copyOf(cut), password) }
        }
    }

    test("a crafted huge iteration count is refused before any key derivation") {
        val enc = BackupCrypto.encrypt(plain, password, iterations = iters)
        val bad = enc.copyOf()
        ByteBuffer.wrap(bad).putInt(8, Int.MAX_VALUE)
        val started = System.nanoTime()
        shouldThrow<BackupException.Malformed> { BackupCrypto.decrypt(bad, password) }
        ((System.nanoTime() - started) / 1_000_000 < 1_000) shouldBe true
    }

    test("a crafted too-low iteration count is refused") {
        val enc = BackupCrypto.encrypt(plain, password, iterations = iters)
        val bad = enc.copyOf()
        ByteBuffer.wrap(bad).putInt(8, 1)
        shouldThrow<BackupException.Malformed> { BackupCrypto.decrypt(bad, password) }
    }

    test("weak passwords are refused at export time") {
        shouldThrow<BackupException.WeakPassword> { BackupCrypto.encrypt(plain, "short".toCharArray()) }
    }

    test("equivalent unicode spellings of the same password work (NFC normalisation)") {
        val composed = "pässwörd-123".toCharArray()
        val decomposed = java.text.Normalizer.normalize("pässwörd-123", java.text.Normalizer.Form.NFD).toCharArray()
        (composed.concatToString() == decomposed.concatToString()) shouldBe false
        val enc = BackupCrypto.encrypt(plain, composed, iterations = iters)
        BackupCrypto.decrypt(enc, decomposed).toList() shouldBe plain.toList()
    }

    test("an unknown format version is reported as unsupported") {
        val enc = BackupCrypto.encrypt(plain, password, iterations = iters)
        val bad = enc.copyOf().also { it[4] = 9 }
        shouldThrow<BackupException.UnsupportedVersion> { BackupCrypto.decrypt(bad, password) }
    }

    test("a deterministic SecureRandom still yields a valid, decryptable file") {
        val fixed = object : SecureRandom() {
            override fun nextBytes(bytes: ByteArray) = bytes.fill(7)
        }
        val enc = BackupCrypto.encrypt(plain, password, fixed, iters)
        BackupCrypto.decrypt(enc, password).toList() shouldBe plain.toList()
    }

    test("the password never appears in the file") {
        val pw = "uniquepassword-xyz".toCharArray()
        val enc = BackupCrypto.encrypt(plain, pw, iterations = iters)
        String(enc, Charsets.ISO_8859_1).contains("uniquepassword") shouldBe false
    }
})
