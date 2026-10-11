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

import dev.patrickgold.florisboard.ime.clipboard.provider.ClipboardHistoryDatabase
import dev.patrickgold.florisboard.ime.clipboard.provider.ClipboardItem
import dev.patrickgold.florisboard.ime.clipboard.provider.ItemType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import java.util.UUID

/**
 * The only door to the clipboard table: encrypts clipboard text on write and decrypts on read.
 *
 * Rules enforced here:
 *  * Text is stored as `vkenc1:` ciphertext with the row's UUID as AEAD context. If the key is unavailable, writes
 *    **fail** (they throw); plaintext is never written as a fallback.
 *  * Rows that cannot be decrypted are **hidden and counted** ([unreadableCount]), never silently deleted, so a lost
 *    key does not turn into data destruction; the user decides (reset vault) in the UI.
 *  * Rows from before encryption existed (plaintext) are readable and are re-written encrypted by [migrateLegacyRows].
 *
 * All methods are blocking (Room DAO) and must be called off the main thread.
 */
class ClipboardStore(
    private val db: ClipboardHistoryDatabase,
    private val cipher: PayloadCipher,
) {
    private val dao get() = db.clipboardItemDao()

    private val _unreadableCount = MutableStateFlow(0)

    /** Number of text rows that failed to decrypt at the last read. 0 in normal operation. */
    val unreadableCount: StateFlow<Int> = _unreadableCount

    /** Live decrypted history. Decryption runs on [Dispatchers.Default], never on the main thread. */
    fun observe(): Flow<List<ClipboardItem>> = dao.getAllAsFlow().map { openAll(it) }.flowOn(Dispatchers.Default)

    fun loadAll(): List<ClipboardItem> = openAll(dao.getAll())

    fun insert(item: ClipboardItem): Long = dao.insert(seal(withUuid(item)))

    fun update(item: ClipboardItem) = dao.update(seal(withUuid(item)))

    fun update(items: List<ClipboardItem>) = dao.update(items.map { seal(withUuid(it)) })

    fun delete(id: Long) = dao.delete(id)

    fun deleteIfUnpinned(id: Long) = dao.deleteIfUnpinned(id)

    fun delete(items: List<ClipboardItem>) = dao.delete(items)

    fun deleteAll() = dao.deleteAll()

    fun deleteAllUnpinned() = dao.deleteAllUnpinned()

    /** Deletes [delete] and writes [upsert] (id 0 = insert, otherwise update) in **one transaction**. */
    fun replaceInTransaction(delete: List<ClipboardItem>, upsert: List<ClipboardItem>) {
        db.runInTransaction(Runnable {
            if (delete.isNotEmpty()) dao.delete(delete)
            for (item in upsert) {
                val sealed = seal(withUuid(item))
                if (sealed.id == 0L) dao.insert(sealed) else dao.update(sealed)
            }
        })
    }

    /**
     * One-off pass: gives rows without a UUID one, and encrypts plaintext text rows. Safe to call repeatedly.
     * Returns the number of rows rewritten.
     */
    fun migrateLegacyRows(): Int {
        var n = 0
        for (row in dao.getAll()) {
            val needsUuid = row.uuid.isEmpty()
            val needsEncryption = row.type == ItemType.TEXT && row.text != null && !PayloadCipher.isEncrypted(row.text)
            if (needsUuid || needsEncryption) {
                dao.update(seal(withUuid(row)))
                n++
            }
        }
        return n
    }

    private fun withUuid(item: ClipboardItem) =
        if (item.uuid.isEmpty()) item.copy(uuid = UUID.randomUUID().toString()) else item

    private fun seal(item: ClipboardItem): ClipboardItem {
        val text = item.text
        if (item.type != ItemType.TEXT || text == null) return item
        return item.copy(text = cipher.encrypt(text, item.uuid))
    }

    private fun openAll(rows: List<ClipboardItem>): List<ClipboardItem> {
        var unreadable = 0
        val out = ArrayList<ClipboardItem>(rows.size)
        for (row in rows) {
            val text = row.text
            if (row.type != ItemType.TEXT || text == null || !PayloadCipher.isEncrypted(text)) {
                out.add(row) // media rows, or legacy plaintext pending migration
                continue
            }
            try {
                out.add(row.copy(text = cipher.decrypt(text, row.uuid)))
            } catch (_: VaultDecryptionException) {
                unreadable++
            } catch (_: VaultKeyUnavailableException) {
                unreadable++
            }
        }
        _unreadableCount.value = unreadable
        return out
    }
}
