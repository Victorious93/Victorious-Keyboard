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

package com.victorious.keyboard.ime.clipboard.vault

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Envelope key management for the clipboard vault.
 *
 *  * A random 256-bit **data key** encrypts the clipboard values in software ([PayloadCipher]); this keeps
 *    per-row cost low (no Keystore round trip per row, important with 500 entries).
 *  * The data key is stored only **wrapped** (AES-256-GCM) by a non-exportable **Android Keystore** key, in a file
 *    inside `noBackupFilesDir`, so Android auto-backup can never carry it to another device where the Keystore key
 *    does not exist.
 *  * If the Keystore entry or the file is lost/corrupted, [dataKey] throws [VaultKeyUnavailableException]. The
 *    caller must then refuse to store new items — it must never fall back to plaintext.
 *
 * The unwrapped key is cached in memory for the lifetime of the process (that is the cost of fast decryption) and
 * handed out as copies that callers zero after use.
 *
 * This class talks to the Android Keystore and therefore cannot be exercised by JVM unit tests; its device behaviour
 * is on the manual test checklist (TESTING.md) and is **not yet verified**.
 */
class KeystoreDataKeyProvider(
    private val keyFile: File,
    private val alias: String = DEFAULT_ALIAS,
    private val random: SecureRandom = SecureRandom(),
) : DataKeyProvider {

    private var cached: ByteArray? = null

    @Synchronized
    override fun dataKey(): ByteArray {
        cached?.let { return it.copyOf() }
        val key = try {
            if (keyFile.exists()) unwrap(keyFile.readBytes()) else createAndStore()
        } catch (e: VaultKeyUnavailableException) {
            throw e
        } catch (e: Exception) {
            throw VaultKeyUnavailableException("The clipboard vault key could not be loaded.", e)
        }
        cached = key
        return key.copyOf()
    }

    /** Destroys the key material. Every encrypted value becomes permanently unreadable. */
    @Synchronized
    fun destroy() {
        cached?.fill(0)
        cached = null
        keyFile.delete()
        runCatching { keyStore().deleteEntry(alias) }
    }

    private fun createAndStore(): ByteArray {
        val dataKey = ByteArray(KEY_LEN).also { random.nextBytes(it) }
        val kek = getOrCreateKek()
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.ENCRYPT_MODE, kek) // the Keystore generates the IV
        val iv = cipher.iv
        val wrapped = cipher.doFinal(dataKey)
        val out = byteArrayOf(FILE_VERSION) + iv + wrapped
        keyFile.parentFile?.mkdirs()
        val tmp = File(keyFile.path + ".tmp")
        tmp.writeBytes(out)
        Files.move(tmp.toPath(), keyFile.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        return dataKey
    }

    private fun unwrap(file: ByteArray): ByteArray {
        if (file.size < 1 + IV_LEN + KEY_LEN || file[0] != FILE_VERSION) {
            throw VaultKeyUnavailableException("The clipboard vault key file is damaged.")
        }
        val kek = (keyStore().getKey(alias, null) as? SecretKey)
            ?: throw VaultKeyUnavailableException("The Android Keystore entry for the clipboard vault is missing.")
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.DECRYPT_MODE, kek, GCMParameterSpec(TAG_BITS, file, 1, IV_LEN))
        return cipher.doFinal(file, 1 + IV_LEN, file.size - 1 - IV_LEN)
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    private fun getOrCreateKek(): SecretKey {
        (keyStore().getKey(alias, null) as? SecretKey)?.let { return it }
        val spec = KeyGenParameterSpec.Builder(
            alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            .apply { init(spec) }
            .generateKey()
    }

    companion object {
        const val DEFAULT_ALIAS = "victorious_clipboard_kek_v1"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORM = "AES/GCM/NoPadding"
        private const val FILE_VERSION: Byte = 1
        private const val KEY_LEN = 32
        private const val IV_LEN = 12
        private const val TAG_BITS = 128
    }
}
