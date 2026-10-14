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

import dev.patrickgold.florisboard.ime.clipboard.lanes.VaultTextCipher
import java.security.GeneralSecurityException
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** The stored value could not be decrypted (wrong key, corruption, or tampering). Never carries content. */
class VaultDecryptionException(message: String) : Exception(message)

/** The data key could not be obtained (e.g. the Android Keystore entry was lost). */
class VaultKeyUnavailableException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Supplies the 256-bit data key. Implementations must never persist it unwrapped. */
interface DataKeyProvider {
    @Throws(VaultKeyUnavailableException::class)
    fun dataKey(): ByteArray
}

/**
 * Encrypts individual database values (clipboard text) with AES-256-GCM.
 *
 * Stored form: `vkenc1:` + base64url(nonce[12] || ciphertext || tag[16]) — no padding, safe for a SQLite TEXT column.
 *
 * * A fresh random 12-byte nonce is used for every value, so equal texts produce different ciphertexts.
 * * [context] (the entry's UUID) is bound as AEAD associated data, so a ciphertext copied onto a different row fails
 *   authentication instead of silently decrypting there.
 * * Decryption never falls back to returning the raw input: a value that does not authenticate throws.
 *   (Legacy plaintext rows are recognised by the missing prefix *before* calling this class — see [isEncrypted].)
 */
class PayloadCipher(
    private val keyProvider: DataKeyProvider,
    private val random: SecureRandom = SecureRandom(),
) : VaultTextCipher {
    override fun encrypt(plain: String, context: String): String {
        val key = keyProvider.dataKey()
        try {
            val nonce = ByteArray(NONCE_LEN).also { random.nextBytes(it) }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
            cipher.updateAAD(context.toByteArray(Charsets.UTF_8))
            val ct = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
            return PREFIX + ENCODER.encodeToString(nonce + ct)
        } finally {
            key.fill(0)
        }
    }

    override fun decrypt(stored: String, context: String): String {
        if (!isEncrypted(stored)) throw VaultDecryptionException("value is not in the encrypted format")
        val encoded = stored.substring(PREFIX.length)
        val raw = try {
            DECODER.decode(encoded)
        } catch (_: IllegalArgumentException) {
            throw VaultDecryptionException("malformed encrypted value")
        }
        // Unpadded base64url is not canonical in its last character (unused low bits are ignored by the decoder), so an
        // altered value could decode to identical bytes and still authenticate. Accept only the exact form we produce.
        if (ENCODER.encodeToString(raw) != encoded) throw VaultDecryptionException("non-canonical encoding")
        if (raw.size < NONCE_LEN + TAG_BITS / 8) throw VaultDecryptionException("encrypted value too short")
        val key = keyProvider.dataKey()
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"),
                GCMParameterSpec(TAG_BITS, raw, 0, NONCE_LEN),
            )
            cipher.updateAAD(context.toByteArray(Charsets.UTF_8))
            return String(cipher.doFinal(raw, NONCE_LEN, raw.size - NONCE_LEN), Charsets.UTF_8)
        } catch (_: GeneralSecurityException) {
            throw VaultDecryptionException("authentication failed")
        } finally {
            key.fill(0)
        }
    }

    companion object {
        const val PREFIX = "vkenc1:"
        private const val NONCE_LEN = 12
        private const val TAG_BITS = 128
        private val ENCODER = Base64.getUrlEncoder().withoutPadding()
        private val DECODER = Base64.getUrlDecoder()

        fun isEncrypted(stored: String): Boolean = stored.startsWith(PREFIX)
    }
}
