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

import dev.patrickgold.florisboard.ime.clipboard.history.ClipEntry

/** Which clipboard lists a ZIP export should contain. */
enum class ZipScope {
    REGULAR,
    VAULT,
    BOTH,
    ;

    fun includesRegular(): Boolean = this != VAULT

    fun includesVault(): Boolean = this != REGULAR
}

/** Password rules for a ZIP transfer. Vault rows are never written without a password. */
object ZipExportPolicy {
    fun requiresPassword(scope: ZipScope): Boolean = scope.includesVault()
}

/**
 * Keeps regular and vault rows from sharing an id inside one file.
 * A colliding vault id is renamed; the regular id stays so an existing history row still matches.
 */
object LaneIds {
    private val idPattern = Regex("^[A-Za-z0-9_-]{1,64}$")

    fun separate(regular: List<ClipEntry>, vault: List<ClipEntry>): Pair<List<ClipEntry>, List<ClipEntry>> {
        val used = regular.mapTo(HashSet()) { it.id }
        val vaultOut = vault.map { entry ->
            if (entry.id !in used && idPattern.matches(entry.id)) {
                used.add(entry.id)
                entry
            } else {
                val renamed = freshId(entry.id, used)
                used.add(renamed)
                entry.copy(id = renamed)
            }
        }
        return regular to vaultOut
    }

    private fun freshId(id: String, used: Set<String>): String {
        var n = 1
        while (n < 10_000) {
            val suffix = if (n == 1) "-v" else "-v$n"
            val candidate = id.take(64 - suffix.length) + suffix
            if (candidate !in used && idPattern.matches(candidate)) return candidate
            n++
        }
        throw BackupException.Malformed("could not assign an entry id")
    }
}
