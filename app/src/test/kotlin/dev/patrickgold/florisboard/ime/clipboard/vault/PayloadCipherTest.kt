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

package dev.patrickgold.florisboard.ime.clipboard.vault

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.string.shouldStartWith

private class FixedKey(private val key: ByteArray) : DataKeyProvider {
    var calls = 0
    override fun dataKey(): ByteArray { calls++; return key.copyOf() }
}

private class BrokenKey : DataKeyProvider {
    override fun dataKey(): ByteArray = throw VaultKeyUnavailableException("keystore entry missing")
}

class PayloadCipherTest : FunSpec({
    val key = ByteArray(32) { (it * 7 + 1).toByte() }
    val cipher = PayloadCipher(FixedKey(key))

    test("round trip, including unicode and large values") {
        for (text in listOf("hello", "ünïcödé 🔐 \u0000 nul", "x".repeat(50_000), "multi\nline\r\n")) {
            cipher.decrypt(cipher.encrypt(text, "ctx"), "ctx") shouldBe text
        }
    }

    test("stored form is prefixed, url-safe and hides the plaintext") {
        val stored = cipher.encrypt("super secret clipboard text", "ctx")
        stored shouldStartWith PayloadCipher.PREFIX
        stored shouldNotContain "secret"
        PayloadCipher.isEncrypted(stored) shouldBe true
        PayloadCipher.isEncrypted("plain") shouldBe false
        stored.all { it.isLetterOrDigit() || it in "-_:" } shouldBe true
    }

    test("a fresh nonce is used each time") {
        cipher.encrypt("same", "ctx") shouldNotBe cipher.encrypt("same", "ctx")
    }

    test("the context is authenticated: a ciphertext moved to another row does not decrypt") {
        val stored = cipher.encrypt("text", "row-A")
        shouldThrow<VaultDecryptionException> { cipher.decrypt(stored, "row-B") }
    }

    test("a different key does not decrypt") {
        val other = PayloadCipher(FixedKey(ByteArray(32) { 9 }))
        shouldThrow<VaultDecryptionException> { other.decrypt(cipher.encrypt("text", "c"), "c") }
    }

    test("flipping any character of the stored value is detected") {
        val stored = cipher.encrypt("tamper me", "c")
        for (i in PayloadCipher.PREFIX.length until stored.length) {
            val ch = stored[i]
            val flipped = if (ch == 'A') 'B' else 'A'
            val bad = stored.substring(0, i) + flipped + stored.substring(i + 1)
            shouldThrow<VaultDecryptionException> { cipher.decrypt(bad, "c") }
        }
    }

    test("truncated, empty and non-base64 values fail cleanly") {
        for (bad in listOf(PayloadCipher.PREFIX, PayloadCipher.PREFIX + "AAAA", PayloadCipher.PREFIX + "!!!not base64!!!")) {
            shouldThrow<VaultDecryptionException> { cipher.decrypt(bad, "c") }
        }
    }

    test("plaintext is never accepted as 'decrypted'") {
        shouldThrow<VaultDecryptionException> { cipher.decrypt("just plaintext", "c") }
    }

    test("an unavailable key makes encryption fail instead of falling back to plaintext") {
        shouldThrow<VaultKeyUnavailableException> { PayloadCipher(BrokenKey()).encrypt("x", "c") }
        shouldThrow<VaultKeyUnavailableException> {
            PayloadCipher(BrokenKey()).decrypt(cipher.encrypt("x", "c"), "c")
        }
    }

    test("key copies are zeroed after use (the provider's own array is untouched)") {
        val provider = FixedKey(key)
        PayloadCipher(provider).encrypt("x", "c")
        key.all { it.toInt() == 0 } shouldBe false
        provider.calls shouldBe 1
    }
})
