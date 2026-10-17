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

package dev.patrickgold.florisboard.ime.snippets

import dev.patrickgold.florisboard.ime.clipboard.backup.BackupCrypto
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

const val SNIPPETS_FORMAT = "victorious-snippets"
const val SNIPPETS_FORMAT_VERSION = 1

@Serializable
internal data class SnippetFile(
    val format: String = SNIPPETS_FORMAT,
    val version: Int = SNIPPETS_FORMAT_VERSION,
    val snippets: List<Snippet> = emptyList(),
)

enum class SnippetFileError {
    TOO_LARGE, MALFORMED, WRONG_FORMAT, NEWER_VERSION, TOO_MANY,

    /** The file is a password-encrypted export; it needs [SnippetEncryptedBackup.decode] and a password. */
    ENCRYPTED,

    /** Wrong password, or the encrypted file was modified. The two are deliberately not told apart. */
    WRONG_PASSWORD,

    /** The password is shorter than [BackupCrypto.MIN_PASSWORD_LENGTH]. Export only. */
    WEAK_PASSWORD,
}

data class InvalidEntry(val index: Int, val issues: List<SnippetIssue>, val duplicateId: Boolean = false)

sealed interface DecodeResult {
    /** [invalid] entries were left out; the caller decides what to tell the user. */
    data class Ok(val snippets: List<Snippet>, val invalid: List<InvalidEntry>) : DecodeResult
    data class Failed(val error: SnippetFileError) : DecodeResult
}

/**
 * Versioned JSON for exporting, importing and storing snippets. **The file is plain text** and contains whatever the
 * user wrote in their snippets; treat an export like any other personal document. Input is untrusted: size-bounded,
 * count-bounded and every entry is validated.
 */
object SnippetBackup {
    private val writer = Json { prettyPrint = true; encodeDefaults = true }
    private val reader = Json { ignoreUnknownKeys = true }

    fun encode(snippets: List<Snippet>): ByteArray =
        writer.encodeToString(SnippetFile.serializer(), SnippetFile(snippets = snippets)).toByteArray(Charsets.UTF_8)

    fun decode(bytes: ByteArray): DecodeResult {
        if (bytes.size > SnippetLimits.MAX_FILE_BYTES) return DecodeResult.Failed(SnippetFileError.TOO_LARGE)
        // An encrypted export is not JSON; say what it is instead of "malformed".
        if (BackupCrypto.isEncrypted(bytes)) return DecodeResult.Failed(SnippetFileError.ENCRYPTED)
        val file = try {
            reader.decodeFromString(SnippetFile.serializer(), bytes.toString(Charsets.UTF_8))
        } catch (_: SerializationException) {
            return DecodeResult.Failed(SnippetFileError.MALFORMED)
        } catch (_: IllegalArgumentException) {
            return DecodeResult.Failed(SnippetFileError.MALFORMED)
        }
        if (file.format != SNIPPETS_FORMAT) return DecodeResult.Failed(SnippetFileError.WRONG_FORMAT)
        if (file.version < 1) return DecodeResult.Failed(SnippetFileError.MALFORMED)
        if (file.version > SNIPPETS_FORMAT_VERSION) return DecodeResult.Failed(SnippetFileError.NEWER_VERSION)
        if (file.snippets.size > SnippetLimits.MAX_SNIPPETS) return DecodeResult.Failed(SnippetFileError.TOO_MANY)

        val ok = mutableListOf<Snippet>()
        val invalid = mutableListOf<InvalidEntry>()
        val seen = HashSet<String>()
        file.snippets.forEachIndexed { i, s ->
            val v = SnippetValidator.validate(s)
            when {
                !v.isValid -> invalid += InvalidEntry(i, v.errors)
                !seen.add(s.id) -> invalid += InvalidEntry(i, emptyList(), duplicateId = true)
                else -> ok += s
            }
        }
        return DecodeResult.Ok(ok, invalid)
    }
}

enum class ImportMode {
    /** Existing snippets always win; only new ids are added. */
    SKIP_EXISTING,

    /** A snippet with an existing id replaces it; new ids are added. */
    OVERWRITE_BY_ID,
}

data class ImportSkip(val title: String, val reason: LibraryError)

data class ImportOutcome(
    val library: SnippetLibrary,
    val added: Int,
    val updated: Int,
    val skipped: List<ImportSkip>,
)

object SnippetImporter {
    /** Merges [incoming] (already validated by [SnippetBackup.decode]) into [existing]; never removes anything. */
    fun merge(existing: SnippetLibrary, incoming: List<Snippet>, mode: ImportMode): ImportOutcome {
        var lib = existing
        var added = 0
        var updated = 0
        val skipped = mutableListOf<ImportSkip>()
        for (s in incoming) {
            val exists = lib.byId(s.id) != null
            val result = when {
                !exists -> lib.add(s)
                mode == ImportMode.OVERWRITE_BY_ID -> lib.update(s)
                else -> { skipped += ImportSkip(s.title, LibraryError.DUPLICATE_ID); continue }
            }
            when (result) {
                is LibraryResult.Ok -> { lib = result.library; if (exists) updated++ else added++ }
                is LibraryResult.Rejected -> skipped += ImportSkip(s.title, result.error)
            }
        }
        return ImportOutcome(lib, added, updated, skipped)
    }
}
