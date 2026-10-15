/*
 * Copyright (C) 2021-2025 The FlorisBoard Contributors
 * Copyright (C) 2026 Victorious Keyboard Contributors (clipboard vault changes)
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

package dev.patrickgold.florisboard.ime.clipboard

import android.content.ClipData
import android.content.Context
import dev.patrickgold.florisboard.BuildConfig
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.appContext
import dev.patrickgold.florisboard.customSuggestions
import dev.patrickgold.florisboard.editorInstance
import dev.patrickgold.florisboard.ime.editor.isPasswordLike
import dev.patrickgold.florisboard.ime.clipboard.backup.BackupException
import dev.patrickgold.florisboard.ime.clipboard.backup.ImportPlan
import dev.patrickgold.florisboard.ime.clipboard.history.ClipCategory
import dev.patrickgold.florisboard.ime.clipboard.history.ClipEntry
import dev.patrickgold.florisboard.ime.clipboard.history.ClipClassifier
import dev.patrickgold.florisboard.ime.clipboard.history.ClipTags
import dev.patrickgold.florisboard.ime.clipboard.history.HistoryPolicy
import dev.patrickgold.florisboard.ime.clipboard.history.InsertPlan
import dev.patrickgold.florisboard.ime.clipboard.history.MAX_HISTORY_ENTRIES
import dev.patrickgold.florisboard.ime.clipboard.history.planInsert
import dev.patrickgold.florisboard.ime.clipboard.lanes.ClipRoute
import dev.patrickgold.florisboard.ime.clipboard.lanes.ClipRouteInput
import dev.patrickgold.florisboard.ime.clipboard.lanes.ClipRouter
import dev.patrickgold.florisboard.ime.clipboard.lanes.SensitiveVault
import dev.patrickgold.florisboard.ime.clipboard.lanes.VaultLock
import dev.patrickgold.florisboard.ime.clipboard.lanes.VaultPlain
import dev.patrickgold.florisboard.ime.clipboard.lanes.VaultReason
import dev.patrickgold.florisboard.ime.clipboard.lanes.VaultSession
import dev.patrickgold.florisboard.ime.clipboard.history.planInsertNonText
import dev.patrickgold.florisboard.ime.clipboard.provider.ClipboardHistoryDatabase
import dev.patrickgold.florisboard.ime.clipboard.provider.ClipboardItem
import dev.patrickgold.florisboard.ime.clipboard.provider.ItemType
import dev.patrickgold.florisboard.ime.clipboard.vault.ClipboardBackupService
import dev.patrickgold.florisboard.ime.clipboard.vault.VaultEntryRepository
import dev.patrickgold.florisboard.ime.clipboard.vault.ClipboardEntryRepository
import dev.patrickgold.florisboard.ime.clipboard.vault.ClipboardStore
import dev.patrickgold.florisboard.ime.clipboard.vault.HistorySnapshot
import dev.patrickgold.florisboard.ime.clipboard.vault.KeystoreDataKeyProvider
import dev.patrickgold.florisboard.ime.clipboard.vault.PayloadCipher
import dev.patrickgold.florisboard.ime.clipboard.vault.formatTagColumn
import dev.patrickgold.florisboard.ime.clipboard.vault.toClipEntry
import dev.patrickgold.florisboard.ime.clipboard.vault.toNewClipboardItem
import dev.patrickgold.florisboard.ime.clipboard.vault.withEntryFields
import java.io.Closeable
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.florisboard.lib.android.AndroidClipboardManager
import org.florisboard.lib.android.AndroidClipboardManager_OnPrimaryClipChangedListener
import org.florisboard.lib.android.clearPrimaryClipAnyApi
import org.florisboard.lib.android.setOrClearPrimaryClip
import org.florisboard.lib.android.showShortToast
import org.florisboard.lib.android.showShortToastSync
import org.florisboard.lib.android.systemService
import org.florisboard.lib.kotlin.tryOrNull

/**
 * [ClipboardManager] manages the clipboard and clipboard history.
 *
 * Also just going to document how all the classes here work.
 *
 * [ClipboardManager] handles storage and retrieval of clipboard items. All manipulation of the
 * clipboard goes through here.
 *
 * Victorious Keyboard changes (see CLIPBOARD.md):
 *  * Persistent history is stored through [ClipboardStore]: clipboard text is encrypted at rest.
 *  * The history never exceeds [MAX_HISTORY_ENTRIES]; the rules live in the pure-Kotlin [HistoryPolicy], which never
 *    evicts pinned entries. The old "limit on/off" switch no longer disables the cap.
 *  * Changing the size limit never deletes anything by itself; surplus entries are evicted when the next clip arrives.
 *  * Text that looks like a credential is not stored unless the user opts in.
 *  * Every mutation is serialised through one mutex and runs on the IO dispatcher; failures degrade to "not saved"
 *    and never throw into the keyboard.
 */
class ClipboardManager(
    context: Context,
) : AndroidClipboardManager_OnPrimaryClipChangedListener, Closeable {
    companion object {
        // 1 minute
        private const val INTERVAL = 60 * 1000L

        private const val VAULT_KEY_FILE = "clipboard_vault.key"
        private const val SENSITIVE_VAULT_FILE = "sensitive-clipboard.vault"
        private const val SENSITIVE_KEY_FILE = "sensitive_clipboard.key"
        private const val SENSITIVE_KEY_ALIAS = "victorious_sensitive_clipboard_kek_v1"
        private const val NOTICE_THROTTLE_MS = 10_000L

        /**
         * Taken from ClipboardDescription.java from the AOSP
         *
         * Helper to compare two MIME types, where one may be a pattern.
         * @param concreteType A fully-specified MIME type.
         * @param desiredType A desired MIME type that may be a pattern such as * / *.
         * @return Returns true if the two MIME types match.
         */
        fun compareMimeTypes(concreteType: String, desiredType: String): Boolean {
            val typeLength = desiredType.length
            if (typeLength == 3 && desiredType == "*/*") {
                return true
            }
            val slashpos = desiredType.indexOf('/')
            if (slashpos > 0) {
                if (typeLength == slashpos + 2 && desiredType[slashpos + 1] == '*') {
                    if (desiredType.regionMatches(0, concreteType, 0, slashpos + 1)) {
                        return true
                    }
                } else if (desiredType == concreteType) {
                    return true
                }
            }
            return false
        }
    }

    private val prefs by FlorisPreferenceStore
    private val appContext by context.appContext()
    private val editorInstance by context.editorInstance()
    private val systemClipboardManager = context.systemService(AndroidClipboardManager::class)

    private val ioScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val cleanUpJob: Job

    private val keyProvider by lazy {
        KeystoreDataKeyProvider(File(appContext.noBackupFilesDir, VAULT_KEY_FILE))
    }
    private val storeReady = CompletableDeferred<ClipboardStore>()
    private val sensitiveVaultReady = CompletableDeferred<SensitiveVault>()
    private val historyMutex = Mutex()
    private var lastNoticeAtMs = 0L

    val historyFlow: StateFlow<ClipboardHistory>
        field = MutableStateFlow(ClipboardHistory.EMPTY)
    val currentHistory: ClipboardHistory
        get() = historyFlow.value

    /** Text rows that cannot be decrypted (key lost). They are hidden, never auto-deleted. 0 normally. */
    val unreadableCountFlow: StateFlow<Int>
        field = MutableStateFlow(0)

    /** Decrypted vault rows. Empty while the vault is locked, so the text is not held for the UI. */
    val vaultItemsFlow: StateFlow<List<VaultPlain>>
        field = MutableStateFlow(emptyList())
    val vaultCountFlow: StateFlow<Int>
        field = MutableStateFlow(0)
    val vaultLockedFlow: StateFlow<Boolean>
        field = MutableStateFlow(true)

    private val primaryClipLastFromCallbackGuard = Mutex(locked = false)
    private var primaryClipLastFromCallback: ClipData? = null
    val primaryClipFlow: StateFlow<ClipboardItem?>
        field = MutableStateFlow(null)
    inline var primaryClip
        get() = primaryClipFlow.value
        private set(v) {
            primaryClipFlow.value = v
        }

    init {
        systemClipboardManager.addPrimaryClipChangedListener(this)
        cleanUpJob = ioScope.launch {
            while (isActive) {
                delay(INTERVAL)
                enforceExpiryDate(currentHistory)
                enforceVaultExpiry()
            }
        }
    }

    fun initializeForContext(context: Context) {
        ioScope.launch {
            if (!sensitiveVaultReady.isCompleted) {
                try {
                    sensitiveVaultReady.complete(openSensitiveVault())
                    publishVault()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    sensitiveVaultReady.completeExceptionally(e)
                }
            }
            if (storeReady.isCompleted) return@launch
            try {
                val db = ClipboardHistoryDatabase.new(context.applicationContext)
                val store = ClipboardStore(db, PayloadCipher(keyProvider))
                // A missing key must not stop the keyboard: rows stay hidden and counted instead.
                try {
                    store.migrateLegacyRows()
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                }
                storeReady.complete(store)
                launch { store.unreadableCount.collect { unreadableCountFlow.value = it } }
                store.observe().collect { items -> updateHistory(items) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Clipboard history is an optional feature; typing must not depend on it.
                storeReady.completeExceptionally(e)
            }
        }
    }

    private fun updateHistory(items: List<ClipboardItem>) {
        historyFlow.value = ClipboardHistory(items.sortedByDescending { it.creationTimestampMs })
    }

    /** The active retention policy. The cap is always enforced; the user's limit can only lower it. */
    fun currentPolicy(): HistoryPolicy = HistoryPolicy(
        maxEntries = prefs.clipboard.historySizeLimit.get().coerceIn(1, MAX_HISTORY_ENTRIES),
        duplicates = prefs.clipboard.historyDuplicateBehavior.get(),
    )

    /**
     * [currentPolicy] minus the rows hidden because they cannot be decrypted: those still occupy database space, so
     * they count against the cap (otherwise a lost key would let the table grow past 500).
     */
    private fun effectivePolicy(): HistoryPolicy {
        val base = currentPolicy()
        val reserved = unreadableCountFlow.value
        return if (reserved == 0) base else base.copy(maxEntries = (base.maxEntries - reserved).coerceAtLeast(1))
    }

    /**
     * Runs [block] against the store under the history mutex on the IO dispatcher. Any failure is swallowed (and the
     * user told, once in a while) so that an optional feature can never crash the keyboard process.
     */
    private fun mutate(block: suspend (ClipboardStore) -> Unit) {
        ioScope.launch {
            historyMutex.withLock {
                try {
                    block(storeReady.await())
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    notify(R.string.clipboard__vault_error)
                }
            }
        }
    }

    private fun notify(resId: Int) {
        val now = System.currentTimeMillis()
        if (now - lastNoticeAtMs < NOTICE_THROTTLE_MS) return
        lastNoticeAtMs = now
        ioScope.launch { appContext.showShortToast(resId) }
    }

    /**
     * Sets the current primary clip without updating the internal clipboard history.
     */
    fun updatePrimaryClip(item: ClipboardItem?) {
        primaryClip = item
        if (prefs.clipboard.useInternalClipboard.get()) {
            val syncBehavior = prefs.clipboard.syncToSystem.get()
            val clipData = item?.toClipData(appContext)
            if (clipData != null && syncBehavior.shouldSyncSet) {
                systemClipboardManager.setPrimaryClip(clipData)
            } else if (clipData == null && syncBehavior.shouldSyncClear) {
                systemClipboardManager.clearPrimaryClipAnyApi()
            }
        } else {
            systemClipboardManager.setOrClearPrimaryClip(item?.toClipData(appContext))
        }
    }

    /**
     * Called by system clipboard when the system primary clip has changed.
     */
    override fun onPrimaryClipChanged() {
        val syncBehavior = prefs.clipboard.syncToFloris.get()
        if (!prefs.clipboard.useInternalClipboard.get() || syncBehavior != ClipboardSyncBehavior.NO_EVENTS) {
            val systemPrimaryClip = systemClipboardManager.primaryClip
            ioScope.launch {
                val isDuplicate: Boolean
                primaryClipLastFromCallbackGuard.withLock {
                    val a = primaryClipLastFromCallback?.getItemAt(0)
                    val b = systemPrimaryClip?.getItemAt(0)
                    isDuplicate = when {
                        a === b -> true
                        a == null || b == null -> false
                        else -> a.text == b.text && a.uri == b.uri
                    }
                    primaryClipLastFromCallback = systemPrimaryClip
                }
                if (isDuplicate) return@launch

                val internalPrimaryClip = primaryClip

                if (systemPrimaryClip == null) {
                    if (syncBehavior.shouldSyncClear) {
                        primaryClip = null
                    }
                    return@launch
                }

                if (systemPrimaryClip.getItemAt(0).let { it.text == null && it.uri == null }) {
                    if (syncBehavior.shouldSyncClear) {
                        primaryClip = null
                    }
                    return@launch
                }

                if (!syncBehavior.shouldSyncSet) {
                    return@launch
                }

                val isEqual = internalPrimaryClip?.isEqualTo(systemPrimaryClip) == true
                if (!isEqual) {
                    val item = ClipboardItem.fromClipData(appContext, systemPrimaryClip, cloneUri = true)
                    primaryClip = item
                    insertOrMoveBeginning(item)
                }
            }
        }
    }

    /**
     * Change the current text on clipboard, update history (if enabled).
     */
    private fun addNewClip(item: ClipboardItem) {
        insertOrMoveBeginning(item)
        updatePrimaryClip(item)
    }

    /**
     * Wraps some plaintext in a ClipData and calls [addNewClip]
     */
    fun addNewPlaintext(newText: String) {
        val newData = ClipboardItem.text(newText)
        addNewClip(newData)
    }

    /**
     * Stores a password-field copy in the sensitive vault only. Returns false when history is off,
     * so the caller leaves the editor unchanged. Does not write the system clipboard.
     */
    fun capturePasswordText(text: String): Boolean {
        if (!prefs.clipboard.historyEnabled.get() || text.isBlank()) return false
        // Wait for the write so a cut does not delete the field before the vault has the text.
        return runBlocking(Dispatchers.IO) {
            historyMutex.withLock {
                try {
                    val plan = offerVault(text, VaultReason.PASSWORD_FIELD)
                    val saved = plan is InsertPlan.Add || plan is InsertPlan.MoveToTop || plan is InsertPlan.IgnoreDuplicate
                    if (saved) primaryClip = ClipboardItem.text(text).copy(isSensitive = true)
                    saved
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    notify(R.string.clipboard__vault_error)
                    false
                }
            }
        }
    }

    fun moveToVault(item: ClipboardItem) {
        val text = item.text ?: return
        if (item.type != ItemType.TEXT || !prefs.clipboard.historyEnabled.get()) return
        mutate { store ->
            val plan = offerVault(text, VaultReason.MOVED)
            if (plan is InsertPlan.Add || plan is InsertPlan.MoveToTop || plan is InsertPlan.IgnoreDuplicate) {
                store.delete(item.id)
                item.close(appContext)
            }
        }
    }

    fun moveVaultToClipboard(id: String) {
        mutate { store ->
            if (vaultIsLocked()) return@mutate
            val vault = sensitiveVaultReady.await()
            val plain = vault.remove(id) ?: return@mutate
            val item = ClipboardItem.text(plain.text).copy(category = ClipClassifier.classify(plain.text).name)
            val history = currentHistory.all
            val plan = effectivePolicy().planInsert(history.map { it.toClipEntry() }, item.toClipEntry())
            when (plan) {
                is InsertPlan.Add -> {
                    val evicted = history.filter { it.uuid in plan.evictIds }
                    store.replaceInTransaction(evicted, listOf(item))
                    evicted.forEach { it.close(appContext) }
                }
                is InsertPlan.MoveToTop -> {
                    val old = history.first { it.uuid == plan.existingId }
                    store.replaceInTransaction(
                        listOf(old),
                        listOf(item.copy(uuid = old.uuid, isPinned = old.isPinned, isFavorite = old.isFavorite, tags = old.tags)),
                    )
                }
                is InsertPlan.IgnoreDuplicate -> Unit
                is InsertPlan.RejectedTooLarge -> {
                    vault.offer(plain.text, plain.reason)
                    notify(R.string.clipboard__item_too_large)
                }
                is InsertPlan.RejectedHistoryFullOfPinned -> {
                    vault.offer(plain.text, plain.reason)
                    notify(R.string.clipboard__full_of_pinned)
                }
                InsertPlan.RejectedBlank -> vault.offer(plain.text, plain.reason)
            }
            publishVault()
        }
    }

    fun setVaultPinned(id: String, pinned: Boolean) {
        mutate {
            sensitiveVaultReady.await().setPinned(id, pinned)
            publishVault()
        }
    }

    fun deleteVaultItem(id: String) {
        mutate {
            sensitiveVaultReady.await().remove(id)
            publishVault()
        }
    }

    /** Replaces one unlocked vault row. A locked vault is left untouched and the text is not logged. */
    fun replaceVaultText(id: String, text: String) {
        if (id.isBlank() || text.isBlank()) {
            notify(R.string.clip_editor__failed)
            return
        }
        mutate {
            if (vaultIsLocked()) {
                notify(R.string.clip_editor__vault_locked)
                return@mutate
            }
            val saved = sensitiveVaultReady.await().replaceText(id, text)
            if (saved) publishVault() else notify(R.string.clip_editor__failed)
        }
    }

    /** Adds a new vault row. This does not write the regular history or the system clipboard. */
    fun addVaultText(text: String) {
        if (text.isBlank()) {
            notify(R.string.clip_editor__failed)
            return
        }
        mutate {
            if (vaultIsLocked()) {
                notify(R.string.clip_editor__vault_locked)
                return@mutate
            }
            val plan = offerVault(text, VaultReason.USER)
            val saved = plan is InsertPlan.Add || plan is InsertPlan.MoveToTop || plan is InsertPlan.IgnoreDuplicate
            if (!saved) notify(R.string.clip_editor__failed)
            publishVault()
        }
    }

    fun syncVaultVisibility() {
        ioScope.launch {
            historyMutex.withLock {
                try {
                    publishVault()
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                }
            }
        }
    }

    /** Plain vault rows for an explicit encrypted export. Empty while the vault is locked. */
    suspend fun exportVaultEntries(): List<ClipEntry> =
        withContext(Dispatchers.IO) {
            if (vaultIsLocked()) return@withContext emptyList()
            sensitiveVaultReady.await().loadPlain().map { it.toEntry() }
        }

    /**
     * Offers a new item to the clipboard history (if enabled). What happens is decided by the pure [HistoryPolicy]:
     * insert (evicting the oldest *unpinned* entries if full), move an identical entry to the top, ignore a
     * duplicate, or reject. Pinned entries are never evicted; if the history is full of them, the clip is not saved
     * and the user is told.
     */
    private fun insertOrMoveBeginning(newItem: ClipboardItem) {
        if (!prefs.clipboard.historyEnabled.get()) return
        mutate { store ->
            val isText = newItem.type == ItemType.TEXT
            val text = newItem.text
            if (isText) {
                if (text == null) return@mutate
                when (val route = ClipRouter.route(routeInput(text, newItem.isSensitive))) {
                    is ClipRoute.Vault -> {
                        if (!newItem.isSensitive) primaryClip = newItem.copy(isSensitive = true)
                        offerVault(text, route.reason)
                        return@mutate
                    }
                    ClipRoute.Dropped -> {
                        notify(R.string.clipboard__secret_not_saved)
                        return@mutate
                    }
                    ClipRoute.Regular -> Unit
                }
            }
            val history = currentHistory.all
            val entries = history.map { it.toClipEntry() }
            val policy = effectivePolicy()
            val candidate = if (isText) {
                newItem.copy(category = ClipClassifier.classify(text.orEmpty()).name)
            } else {
                newItem
            }
            val plan = if (isText) policy.planInsert(entries, candidate.toClipEntry())
            else policy.planInsertNonText(entries)

            when (plan) {
                is InsertPlan.Add -> {
                    val evicted = history.filter { it.uuid in plan.evictIds }
                    store.replaceInTransaction(evicted, listOf(candidate))
                    evicted.forEach { it.close(appContext) }
                }
                is InsertPlan.MoveToTop -> {
                    val old = history.first { it.uuid == plan.existingId }
                    // Keep everything the user attached to the entry; only the position (timestamp) changes.
                    store.replaceInTransaction(
                        listOf(old),
                        listOf(
                            candidate.copy(
                                uuid = old.uuid, isPinned = old.isPinned, isFavorite = old.isFavorite,
                                tags = old.tags, category = old.category, editedAtMs = old.editedAtMs,
                            )
                        ),
                    )
                }
                is InsertPlan.IgnoreDuplicate, InsertPlan.RejectedBlank -> Unit
                is InsertPlan.RejectedTooLarge -> notify(R.string.clipboard__item_too_large)
                is InsertPlan.RejectedHistoryFullOfPinned -> notify(R.string.clipboard__full_of_pinned)
            }
        }
    }

    private fun openSensitiveVault(): SensitiveVault = SensitiveVault(
        file = File(appContext.noBackupFilesDir, SENSITIVE_VAULT_FILE),
        cipher = PayloadCipher(
            KeystoreDataKeyProvider(
                File(appContext.noBackupFilesDir, SENSITIVE_KEY_FILE),
                alias = SENSITIVE_KEY_ALIAS,
            ),
        ),
        policy = { currentPolicy() },
    )

    private fun routeInput(text: String, androidSensitive: Boolean) = ClipRouteInput(
        text = text,
        fromPasswordField = editorInstance.activeInfo.inputAttributes.isPasswordLike(),
        androidSensitive = androidSensitive,
        detectSecrets = prefs.clipboard.detectSecretsToVault.get(),
        storeDetectedInRegular = prefs.clipboard.storeDetectedSecrets.get(),
    )

    private fun vaultIsLocked(): Boolean = VaultLock.isLocked(
        requireCredential = prefs.clipboard.vaultRequireUnlock.get(),
        unlockedUntilMs = VaultSession.unlockedUntilMs.value,
        nowMs = System.currentTimeMillis(),
    )

    private suspend fun offerVault(text: String, reason: VaultReason): InsertPlan {
        val plan = sensitiveVaultReady.await().offer(text, reason)
        when (plan) {
            is InsertPlan.RejectedTooLarge -> notify(R.string.clipboard__item_too_large)
            is InsertPlan.RejectedHistoryFullOfPinned -> notify(R.string.clipboard__full_of_pinned)
            else -> Unit
        }
        publishVault()
        return plan
    }

    private fun insertVault(text: String, reason: VaultReason) {
        mutate { offerVault(text, reason) }
    }

    private suspend fun publishVault() {
        val vault = sensitiveVaultReady.await()
        val locked = vaultIsLocked()
        vaultLockedFlow.value = locked
        vaultCountFlow.value = runCatching { vault.count() }.getOrDefault(0)
        vaultItemsFlow.value = if (locked) emptyList() else runCatching { vault.loadPlain() }.getOrDefault(emptyList())
    }

    private fun enforceVaultExpiry() {
        mutate {
            val ttl = prefs.clipboard.vaultLifetime.get().ttlMs()
            sensitiveVaultReady.await().deleteExpired(System.currentTimeMillis(), ttl)
            publishVault()
        }
    }

    private fun enforceExpiryDate(clipHistory: ClipboardHistory) {
        val itemsToRemove = mutableSetOf<ClipboardItem>()
        if (prefs.clipboard.historyAutoCleanOldEnabled.get()) {
            val nonPinnedItems = clipHistory.recent + clipHistory.other
            val expiryTime = System.currentTimeMillis() - (prefs.clipboard.historyAutoCleanOldAfter.get() * 60 * 1000)
            itemsToRemove.addAll(nonPinnedItems.filter { it.creationTimestampMs < expiryTime })
        }
        if (prefs.clipboard.historyAutoCleanSensitiveEnabled.get()) {
            val sensitiveData = clipHistory.all.filter { it.isSensitive }
            val expiryTime = System.currentTimeMillis() - (prefs.clipboard.historyAutoCleanSensitiveAfter.get() * 1000)
            itemsToRemove.addAll(sensitiveData.filter { it.creationTimestampMs < expiryTime })
        }
        if (itemsToRemove.isNotEmpty()) {
            mutate { store -> store.delete(itemsToRemove.toList()) }
        }
    }

    /** Inserts [item] unconditionally at the top (used by tests/legacy callers); prefer [addNewPlaintext]. */
    fun insertClip(item: ClipboardItem) {
        mutate { store ->
            val id = store.insert(item)
            item.id = id
        }
    }

    fun clearExactHistory(items: List<ClipboardItem>) {
        mutate { store ->
            for (item in items) {
                item.close(appContext)
            }
            store.delete(items)
        }
    }

    /**
     * Clears all unpinned items from the clipboard history
     */
    fun clearHistory() {
        mutate { store ->
            for (item in currentHistory.all) {
                item.close(appContext)
            }
            store.deleteAllUnpinned()
        }
    }

    /**
     * Clears the full clipboard history
     */
    fun clearFullHistory() {
        mutate { store ->
            for (item in currentHistory.all) {
                item.close(appContext)
            }
            store.deleteAll()
        }
    }

    /**
     * Restores image/video (and legacy text) items from an old-style backup. This path **never evicts**: items are
     * added only while there is room, so a legacy restore can't silently delete existing entries. Text backups
     * should use the clipboard backup service (preview + consent) instead.
     */
    fun restoreHistory(items: List<ClipboardItem>) {
        mutate { store ->
            val maxEntries = effectivePolicy().maxEntries
            val existing = store.loadAll()
            var count = existing.size + unreadableCountFlow.value
            val seen = existing.mapTo(HashSet()) { Triple(it.type, it.text, it.uri) }
            var skippedFull = 0
            for (item in items) {
                if (!seen.add(Triple(item.type, item.text, item.uri))) continue // already present
                if (count >= maxEntries) {
                    skippedFull++
                    continue
                }
                store.insert(item.copy(id = 0, uuid = java.util.UUID.randomUUID().toString()))
                count++
            }
            if (skippedFull > 0) notify(R.string.clipboard__restore_partial_full)
        }
    }

    fun deleteClip(item: ClipboardItem, onlyIfUnpinned: Boolean) {
        mutate { store ->
            if (onlyIfUnpinned) {
                store.deleteIfUnpinned(item.id)
            } else {
                store.delete(item.id)
            }
            tryOrNull {
                val uri = item.uri
                if (uri != null) {
                    appContext.contentResolver.delete(uri, null, null)
                }
            }
        }
    }

    fun deleteClips(items: List<ClipboardItem>) {
        mutate { store ->
            for (item in items) {
                item.close(appContext)
            }
            store.delete(items)
        }
    }

    /**
     * Applies [change] to the **current stored version** of [item] (re-read inside the mutex). The UI's copy may
     * be stale; applying a change to it would silently undo edits made since.
     */
    private fun update(item: ClipboardItem, change: (ClipboardItem) -> ClipboardItem) {
        mutate { store ->
            val current = store.get(item.uuid) ?: return@mutate
            store.update(change(current))
        }
    }

    fun pinClip(item: ClipboardItem) = update(item) { it.copy(isPinned = true) }

    fun unpinClip(item: ClipboardItem) = update(item) { it.copy(isPinned = false) }

    fun setPinned(items: List<ClipboardItem>, pinned: Boolean) {
        mutate { store ->
            store.update(items.mapNotNull { store.get(it.uuid) }.map { it.copy(isPinned = pinned) })
        }
    }

    fun setFavorite(item: ClipboardItem, favorite: Boolean) = update(item) { it.copy(isFavorite = favorite) }

    fun setTags(item: ClipboardItem, rawTags: Collection<String>) =
        update(item) { it.copy(tags = formatTagColumn(ClipTags.normalizeAll(rawTags))) }

    fun addTag(items: List<ClipboardItem>, rawTag: String) {
        val tag = ClipTags.normalize(rawTag) ?: return
        mutate { store ->
            store.update(items.mapNotNull { store.get(it.uuid) }.map { current ->
                current.copy(tags = formatTagColumn(ClipTags.normalizeAll(current.toClipEntry().tags + tag)))
            })
        }
    }

    fun setCategory(item: ClipboardItem, category: ClipCategory) =
        update(item) { it.copy(category = category.name) }

    /**
     * Applies all user-editable fields in **one** write. [newText] null keeps the text. Returns false (and changes
     * nothing) if the new text is blank, too long, or the item is not text.
     */
    fun updateUserFields(
        item: ClipboardItem,
        newText: String?,
        rawTags: Collection<String>,
        category: ClipCategory,
        favorite: Boolean,
        pinned: Boolean,
    ): Boolean {
        if (newText != null) {
            if (item.type != ItemType.TEXT || newText.isBlank() || newText.length > currentPolicy().maxItemChars) {
                return false
            }
        }
        val tags = formatTagColumn(ClipTags.normalizeAll(rawTags))
        update(item) { cur ->
            val textChanged = newText != null && newText != cur.text
            cur.copy(
                text = if (newText != null) newText else cur.text,
                editedAtMs = if (textChanged) System.currentTimeMillis() else cur.editedAtMs,
                tags = tags,
                category = category.name,
                isFavorite = favorite,
                isPinned = pinned,
            )
        }
        return true
    }

    /** Storage seam for the backup service: domain snapshots in, atomic plans out. */
    val entryRepository: ClipboardEntryRepository = object : ClipboardEntryRepository {
        override suspend fun snapshot(): HistorySnapshot = withContext(Dispatchers.IO) {
            val rows = storeReady.await().loadAll()
            HistorySnapshot(
                entries = rows.map { it.toClipEntry() },
                mediaIds = rows.filter { it.type != ItemType.TEXT }.mapTo(HashSet()) { it.uuid },
            )
        }

        override suspend fun apply(plan: ImportPlan) = withContext(Dispatchers.IO) {
            historyMutex.withLock {
                val store = storeReady.await()
                val rows = store.loadAll().associateBy { it.uuid }
                val toDelete = plan.deleteIds.mapNotNull { rows[it] }
                val upserts = plan.upserts.map { e -> rows[e.id]?.withEntryFields(e) ?: e.toNewClipboardItem() }
                store.replaceInTransaction(toDelete, upserts) // one transaction: all or nothing
                toDelete.forEach { it.close(appContext) } // media files are removed only after the commit
            }
        }
    }

    /** Storage seam for vault rows inside a ZIP import. Refuses to read or write while the vault is locked. */
    val vaultRepository: VaultEntryRepository = object : VaultEntryRepository {
        override fun isLocked(): Boolean = vaultIsLocked()

        override suspend fun snapshot(): List<ClipEntry> = withContext(Dispatchers.IO) {
            if (vaultIsLocked()) throw BackupException.VaultLocked()
            sensitiveVaultReady.await().loadPlain().map { it.toEntry() }
        }

        override suspend fun apply(plan: ImportPlan) = withContext(Dispatchers.IO) {
            historyMutex.withLock {
                if (vaultIsLocked()) throw BackupException.VaultLocked()
                sensitiveVaultReady.await().applyImport(plan)
                publishVault()
            }
        }
    }

    fun backupService(): ClipboardBackupService = ClipboardBackupService(
        repo = entryRepository,
        appVersion = BuildConfig.VERSION_NAME,
        policy = ::currentPolicy,
        vault = vaultRepository,
        suggestionsExport = { appContext.customSuggestions().value.exportDocumentOrNull() },
        suggestionsImport = { document, mode -> appContext.customSuggestions().value.importDocument(document, mode) },
    )

    /**
     * Destroys the vault key and deletes every stored clipboard row. Only for the "key lost" recovery flow; the UI
     * must ask for explicit confirmation first.
     */
    fun resetVault() {
        mutate { store ->
            store.deleteAll()
            keyProvider.destroy()
        }
    }

    fun pasteItem(item: ClipboardItem) {
        val editorInstance by appContext.editorInstance()
        editorInstance.commitClipboardItem(item).also { result ->
            if (!result) {
                appContext.showShortToastSync("Failed to paste item.")
            }
        }
    }

    /**
     * Returns true if the editor can accept the clip item, else false.
     */
    fun canBePasted(clipItem: ClipboardItem?): Boolean {
        if (clipItem == null) return false

        return clipItem.mimeTypes.contains("text/plain") || editorInstance.activeInfo.contentMimeTypes.any { editorType ->
            clipItem.mimeTypes.any { clipType ->
                compareMimeTypes(clipType, editorType)
            }
        }
    }

    /**
     * Cleans up.
     *
     * Unregisters the system clipboard listener, cancels clipboard clean ups.
     */
    override fun close() {
        systemClipboardManager.removePrimaryClipChangedListener(this)
        cleanUpJob.cancel()
    }
}
