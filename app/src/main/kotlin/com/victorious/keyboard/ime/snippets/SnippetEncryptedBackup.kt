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
import com.victorious.keyboard.ime.clipboard.backup.BackupException
import java.security.SecureRandom

/** The result of building an encrypted export. */
sealed interface EncryptedExport {
    data class Ok(val bytes: ByteArray) : EncryptedExport
    data class Failed(val error: SnippetFileError) : EncryptedExport
}

/**
 * Password-encrypted snippet export. It is the clipboard backup container (`BackupCrypto`: PBKDF2-HMAC-SHA256,
 * AES-256-GCM, random salt and nonce per file, the whole header authenticated, no key or hint in the file) around the
 * same JSON that [SnippetBackup] writes. Nothing here adds cryptography of its own.
 *
 * The container does not say what is inside it, so a clipboard `.vkbk` and a snippets `.vkbk` look alike until they are
 * opened. A clipboard file opened here decrypts (if the password matches) and is then rejected by [SnippetBackup.decode]
 * as not a snippets file; the content is never interpreted as anything else.
 *
 * Input is untrusted: the file is size-bounded before any key is derived, the iteration count is bounded by
 * `BackupCrypto`, and the decrypted bytes go through the same validation as a plain import. Errors never contain
 * snippet text.
 */
object SnippetEncryptedBackup {
    /** Largest encrypted file read: the plain limit plus the container's 40-byte header and 16-byte tag. */
    const val MAX_ENCRYPTED_BYTES = SnippetLimits.MAX_FILE_BYTES + 40 + 16

    fun isEncrypted(bytes: ByteArray): Boolean = BackupCrypto.isEncrypted(bytes)

    fun encode(
        snippets: List<Snippet>,
        password: CharArray,
        random: SecureRandom = SecureRandom(),
        iterations: Int = BackupCrypto.DEFAULT_ITERATIONS,
    ): EncryptedExport {
        if (password.size < BackupCrypto.MIN_PASSWORD_LENGTH) return EncryptedExport.Failed(SnippetFileError.WEAK_PASSWORD)
        val plain = SnippetBackup.encode(snippets)
        // The reader refuses plain content over the limit, so do not write a file this app could not read back.
        if (plain.size > SnippetLimits.MAX_FILE_BYTES) return EncryptedExport.Failed(SnippetFileError.TOO_LARGE)
        return try {
            EncryptedExport.Ok(BackupCrypto.encrypt(plain, password, random, iterations))
        } catch (_: BackupException.WeakPassword) {
            EncryptedExport.Failed(SnippetFileError.WEAK_PASSWORD)
        } finally {
            plain.fill(0)
        }
    }

    fun decode(file: ByteArray, password: CharArray): DecodeResult {
        if (file.size > MAX_ENCRYPTED_BYTES) return DecodeResult.Failed(SnippetFileError.TOO_LARGE)
        if (!BackupCrypto.isEncrypted(file)) return DecodeResult.Failed(SnippetFileError.WRONG_FORMAT)
        val plain = try {
            BackupCrypto.decrypt(file, password)
        } catch (_: BackupException.WrongPasswordOrTampered) {
            return DecodeResult.Failed(SnippetFileError.WRONG_PASSWORD)
        } catch (_: BackupException.UnsupportedVersion) {
            return DecodeResult.Failed(SnippetFileError.NEWER_VERSION)
        } catch (_: BackupException) {
            // NotABackup (too short), Malformed (unknown ids, iteration count out of range).
            return DecodeResult.Failed(SnippetFileError.MALFORMED)
        }
        try {
            return SnippetBackup.decode(plain)
        } finally {
            plain.fill(0)
        }
    }
}
