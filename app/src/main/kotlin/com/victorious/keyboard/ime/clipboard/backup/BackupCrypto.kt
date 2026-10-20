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

package com.victorious.keyboard.ime.clipboard.backup

import java.nio.ByteBuffer
import java.security.GeneralSecurityException
import java.security.SecureRandom
import java.text.Normalizer
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Password-based authenticated encryption for backup files, built only from standard JCE primitives
 * (no custom cryptography):
 *
 *  * KDF: PBKDF2-HMAC-SHA256, random 16-byte salt per file, iteration count stored in the header
 *  * Cipher: AES-256-GCM, random 12-byte nonce per file, 128-bit tag
 *  * The whole 40-byte header is bound as AEAD associated data, so tampering with any header field (version,
 *    iterations, salt, nonce) makes decryption fail.
 *
 * Layout (big-endian):
 * ```
 * 0  "VKBK"            4 bytes magic
 * 4  version = 1       1 byte
 * 5  kdf     = 1       1 byte  (1 = PBKDF2-HMAC-SHA256)
 * 6  cipher  = 1       1 byte  (1 = AES-256-GCM)
 * 7  reserved = 0      1 byte
 * 8  iterations        4 bytes unsigned
 * 12 salt             16 bytes
 * 28 nonce            12 bytes
 * 40 ciphertext || tag
 * ```
 * No key, password or password hint is ever written to the file.
 */
object BackupCrypto {
    const val MIN_PASSWORD_LENGTH = 8
    const val DEFAULT_ITERATIONS = 600_000

    private val MAGIC = byteArrayOf('V'.code.toByte(), 'K'.code.toByte(), 'B'.code.toByte(), 'K'.code.toByte())
    private const val VERSION: Byte = 1
    private const val KDF_PBKDF2_SHA256: Byte = 1
    private const val CIPHER_AES_256_GCM: Byte = 1
    private const val SALT_LEN = 16
    private const val NONCE_LEN = 12
    private const val HEADER_LEN = 4 + 1 + 1 + 1 + 1 + 4 + SALT_LEN + NONCE_LEN // 40
    private const val TAG_BITS = 128

    fun isEncrypted(bytes: ByteArray): Boolean =
        bytes.size >= 4 && bytes[0] == MAGIC[0] && bytes[1] == MAGIC[1] && bytes[2] == MAGIC[2] && bytes[3] == MAGIC[3]

    fun encrypt(
        plaintext: ByteArray,
        password: CharArray,
        random: SecureRandom = SecureRandom(),
        iterations: Int = DEFAULT_ITERATIONS,
    ): ByteArray {
        if (password.size < MIN_PASSWORD_LENGTH) throw BackupException.WeakPassword(MIN_PASSWORD_LENGTH)
        val salt = ByteArray(SALT_LEN).also { random.nextBytes(it) }
        val nonce = ByteArray(NONCE_LEN).also { random.nextBytes(it) }
        val header = ByteBuffer.allocate(HEADER_LEN)
            .put(MAGIC).put(VERSION).put(KDF_PBKDF2_SHA256).put(CIPHER_AES_256_GCM).put(0)
            .putInt(iterations).put(salt).put(nonce)
            .array()
        val key = deriveKey(password, salt, iterations)
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
            cipher.updateAAD(header)
            val ct = cipher.doFinal(plaintext)
            return header + ct
        } finally {
            key.fill(0)
        }
    }

    fun decrypt(file: ByteArray, password: CharArray, limits: BackupLimits = BackupLimits()): ByteArray {
        if (!isEncrypted(file) || file.size < HEADER_LEN + TAG_BITS / 8) throw BackupException.NotABackup()
        val buf = ByteBuffer.wrap(file)
        buf.position(4)
        val version = buf.get().toInt()
        if (version != VERSION.toInt()) throw BackupException.UnsupportedVersion(version)
        val kdf = buf.get()
        val cipherId = buf.get()
        buf.get() // reserved
        if (kdf != KDF_PBKDF2_SHA256 || cipherId != CIPHER_AES_256_GCM) {
            throw BackupException.Malformed("unknown key-derivation or cipher id")
        }
        val iterations = buf.getInt()
        // Bound attacker-controlled work: a crafted header must not make us spin for minutes.
        if (iterations !in limits.minKdfIterations..limits.maxKdfIterations) {
            throw BackupException.Malformed("key-derivation iteration count out of range")
        }
        val salt = ByteArray(SALT_LEN).also { buf.get(it) }
        val nonce = ByteArray(NONCE_LEN).also { buf.get(it) }
        val header = file.copyOfRange(0, HEADER_LEN)

        val key = deriveKey(password, salt, iterations)
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
            cipher.updateAAD(header)
            return cipher.doFinal(file, HEADER_LEN, file.size - HEADER_LEN)
        } catch (_: AEADBadTagException) {
            throw BackupException.WrongPasswordOrTampered()
        } catch (_: GeneralSecurityException) {
            throw BackupException.WrongPasswordOrTampered()
        } finally {
            key.fill(0)
        }
    }

    private fun deriveKey(password: CharArray, salt: ByteArray, iterations: Int): ByteArray {
        // NFC-normalise so the same visible password always derives the same key across keyboards/platforms.
        val normalized = Normalizer.normalize(String(password), Normalizer.Form.NFC).toCharArray()
        val spec = PBEKeySpec(normalized, salt, iterations, 256)
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
            normalized.fill('\u0000')
        }
    }
}
