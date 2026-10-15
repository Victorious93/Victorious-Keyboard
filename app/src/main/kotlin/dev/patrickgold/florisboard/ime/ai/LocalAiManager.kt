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

package dev.patrickgold.florisboard.ime.ai

import android.content.Context
import android.net.Uri
import android.os.StatFs
import android.provider.OpenableColumns
import dev.patrickgold.florisboard.ime.clipedit.EditorBuffer
import dev.patrickgold.florisboard.ime.clipedit.EditorKeyInput
import dev.patrickgold.florisboard.ime.clipedit.EditorKeyRouter
import dev.patrickgold.florisboard.ime.nlp.WordSuggestionCandidate
import dev.patrickgold.florisboard.nlpManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

data class AiFieldSnapshot(
    val passwordLike: Boolean,
    val editorOpen: Boolean,
    val editorIsVault: Boolean,
    val selection: String,
    val fieldText: String,
    val clipboard: String,
    val editorText: String,
)

data class AiPanel(
    val task: AiTask,
    val source: AiSource,
    val explicitVault: Boolean,
    val ask: EditorBuffer = EditorBuffer(),
    val askFocused: Boolean = false,
    val phase: AiPhase = AiPhase.COMPOSING,
    val output: String = "",
    val fromVault: Boolean = false,
    val notice: AiRefusal? = null,
    /** When set, the keyboard inserts [output] once and removes [replaceToken] if it is still before the caret. */
    val insertWhenReady: Boolean = false,
    val replaceToken: String = "",
    val systemOverride: String? = null,
)

/**
 * Loads a user-imported GGUF on a background thread, streams tokens, and drops the model
 * when memory is low or the keyboard has been idle. Prompt text is not logged.
 */
class LocalAiManager(context: Context) {
    private val appContext = context.applicationContext
    private val files = AiFiles(File(appContext.noBackupFilesDir, "ai"))
    private val worker = Executors.newSingleThreadExecutor()
    private val scheduler = Executors.newSingleThreadScheduledExecutor()
    private val ticket = AtomicInteger(0)
    private val cancelFlag = AtomicBoolean(false)
    private val gate = Any()
    private val nlp by context.nlpManager()

    private val _models = MutableStateFlow<List<ImportedModel>>(emptyList())
    val models: StateFlow<List<ImportedModel>> = _models
    private val _config = MutableStateFlow(AiConfig())
    val config: StateFlow<AiConfig> = _config
    private val _panel = MutableStateFlow<AiPanel?>(null)
    val panel: StateFlow<AiPanel?> = _panel
    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded

    @Volatile private var busy = false
    @Volatile private var lastUsed = System.currentTimeMillis()
    @Volatile private var suggestionPrefix: String? = null
    @Volatile private var suggestionText: String? = null
    @Volatile private var suggestionQueued: String? = null

    init {
        files.ensure()
        _config.value = files.readConfig(Runtime.getRuntime().availableProcessors())
        _models.value = files.readModels()
        scheduler.scheduleWithFixedDelay({ idleCheck() }, 30, 30, java.util.concurrent.TimeUnit.SECONDS)
    }

    fun updateConfig(config: AiConfig) {
        val previous = _config.value
        val next = AiConfigRules.clamp(config, Runtime.getRuntime().availableProcessors())
        _config.value = next
        worker.execute {
            try {
                files.writeConfig(next)
            } catch (_: Exception) {
                // The in-memory copy still applies for this session.
            }
            if (previous.selectedModelId != next.selectedModelId ||
                previous.contextSize != next.contextSize ||
                previous.threads != next.threads
            ) {
                unloadNow()
            }
        }
    }

    fun deleteModel(id: String) {
        worker.execute {
            if (_config.value.selectedModelId == id) unloadNow()
            val next = files.delete(id, _models.value)
            try {
                files.writeModels(next)
            } catch (_: Exception) {
                // The file is already gone; the list refreshes on the next read.
            }
            _models.value = files.readModels()
            if (_config.value.selectedModelId == id) {
                updateConfig(_config.value.copy(selectedModelId = null))
            }
        }
    }

    fun importModel(uri: Uri, acknowledged: Boolean): ImportDecision {
        val resolver = appContext.contentResolver
        val (name, size) = query(uri)
        val free = try {
            StatFs(appContext.noBackupFilesDir.absolutePath).availableBytes
        } catch (_: Exception) {
            Long.MAX_VALUE
        }
        val decision = ModelImport.decide(size, free, _models.value.size, acknowledged)
        if (decision != ImportDecision.ACCEPT) return decision
        val stream = resolver.openInputStream(uri) ?: return ImportDecision.REJECT_EMPTY
        val outcome = files.import(name, size, free, _models.value, true, stream)
        return when (outcome) {
            is ImportOutcome.Blocked -> outcome.decision
            is ImportOutcome.Copied -> {
                val next = (_models.value + outcome.model).takeLast(ModelImport.MAX_MODELS)
                try {
                    files.writeModels(next)
                } catch (_: Exception) {
                    return ImportDecision.REJECT_EMPTY
                }
                _models.value = files.readModels()
                updateConfig(_config.value.copy(selectedModelId = outcome.model.id))
                ImportDecision.ACCEPT
            }
        }
    }

    fun open(
        task: AiTask,
        snapshot: AiFieldSnapshot,
        askPrefill: String = "",
        insertWhenReady: Boolean = false,
        replaceToken: String = "",
        systemOverride: String? = null,
    ): AiRefusal? {
        val source = AiPrompt.chooseDefault(
            snapshot.selection,
            snapshot.fieldText,
            snapshot.editorText,
            snapshot.passwordLike,
            snapshot.editorOpen,
            snapshot.editorIsVault,
        )
        val prefill = askPrefill.take(2000)
        val askNow = task == AiTask.ASK && prefill.isNotEmpty()
        val panel = AiPanel(
            task = task,
            source = source ?: AiSource.SELECTION,
            explicitVault = false,
            ask = if (prefill.isEmpty()) EditorBuffer() else EditorBuffer.of(prefill),
            askFocused = task == AiTask.ASK && prefill.isEmpty(),
            phase = if (task == AiTask.ASK && prefill.isEmpty()) AiPhase.COMPOSING else AiPhase.RUNNING,
            notice = if (source == null && !askNow) AiRefusal.EMPTY else null,
            insertWhenReady = insertWhenReady,
            replaceToken = replaceToken,
            systemOverride = systemOverride,
        )
        if (source == null && !askNow) {
            _panel.value = panel.copy(phase = AiPhase.FAILED, notice = AiRefusal.EMPTY, insertWhenReady = false)
            return AiRefusal.EMPTY
        }
        _panel.value = panel
        if (task == AiTask.ASK && prefill.isEmpty()) return null
        return run(panel, snapshot)
    }

    fun chooseSource(source: AiSource, snapshot: AiFieldSnapshot) {
        val current = _panel.value ?: return
        val next = current.copy(
            source = source,
            explicitVault = source == AiSource.VAULT,
            askFocused = false,
            phase = AiPhase.RUNNING,
            output = "",
            notice = null,
        )
        _panel.value = next
        run(next, snapshot)
    }

    fun submitAsk(snapshot: AiFieldSnapshot) {
        val current = _panel.value ?: return
        if (current.task != AiTask.ASK) return
        val next = current.copy(askFocused = false, phase = AiPhase.RUNNING, output = "", notice = null)
        _panel.value = next
        run(next, snapshot)
    }

    fun focusAsk() {
        val current = _panel.value ?: return
        if (current.task == AiTask.ASK && current.phase != AiPhase.RUNNING) {
            _panel.value = current.copy(askFocused = true)
        }
    }

    fun askConsumes(code: Int, character: Boolean): Boolean {
        val current = _panel.value ?: return false
        return current.askFocused && EditorKeyRouter.consumes(code, character)
    }

    fun applyAskKey(input: EditorKeyInput): Boolean {
        val current = _panel.value ?: return false
        if (!current.askFocused || !EditorKeyRouter.consumes(input.code, input.character)) return false
        val applied = EditorKeyRouter.apply(current.ask, input.copy(singleLine = false, pairBrackets = false))
        _panel.value = current.copy(ask = EditorKeyRouter.caretAfterPair(current.ask, applied, input))
        return true
    }

    fun cancel() {
        synchronized(gate) {
            cancelFlag.set(true)
            ticket.incrementAndGet()
            LlamaRuntime.cancel()
        }
        val current = _panel.value ?: return
        if (current.phase == AiPhase.RUNNING) {
            _panel.value = current.copy(phase = if (current.output.isEmpty()) AiPhase.FAILED else AiPhase.RESULT)
        }
    }

    fun close() {
        cancel()
        _panel.value = null
    }

    fun onTrimMemory(level: Int) {
        if (AiLifecycle.shouldUnloadForMemory(level)) {
            cancel()
            worker.execute { unloadNow() }
        }
    }

    fun offerSuggestion(prefix: String?): WordSuggestionCandidate? {
        if (prefix == null || prefix.length !in 2..48) {
            suggestionPrefix = null
            suggestionText = null
            suggestionQueued = null
            return null
        }
        scheduleSuggestion(prefix)
        val cached = suggestionText
        if (suggestionPrefix != prefix || cached.isNullOrBlank()) return null
        return WordSuggestionCandidate(
            text = cached,
            confidence = 0.35,
            isEligibleForAutoCommit = false,
            isEligibleForUserRemoval = false,
            sourceProvider = null,
        )
    }

    private fun run(panel: AiPanel, snapshot: AiFieldSnapshot): AiRefusal? {
        val config = _config.value
        val request = AiRequest(
            task = panel.task,
            source = panel.source,
            explicitVault = panel.explicitVault,
            passwordLike = snapshot.passwordLike,
            editorIsVault = snapshot.editorIsVault,
            selection = snapshot.selection,
            fieldText = snapshot.fieldText,
            clipboard = snapshot.clipboard,
            editorText = snapshot.editorText,
            askText = panel.ask.text,
            systemPrompt = panel.systemOverride ?: config.systemPrompt,
            translateLanguage = config.translateLanguage,
            modelSelected = config.selectedModelId != null && _models.value.any { it.id == config.selectedModelId },
            nativeAvailable = LlamaNative.available,
            contextSize = config.contextSize,
            maxTokens = config.maxTokens,
        )
        when (val plan = AiPrompt.plan(request)) {
            is AiPlan.Refuse -> {
                _panel.value = panel.copy(phase = AiPhase.FAILED, notice = plan.reason, fromVault = false)
                return plan.reason
            }
            is AiPlan.Ready -> {
                _panel.value = panel.copy(phase = AiPhase.RUNNING, fromVault = plan.fromVault, notice = null, output = "")
                startGenerate(plan, config)
                return null
            }
        }
    }

    private fun startGenerate(plan: AiPlan.Ready, config: AiConfig) {
        val id = synchronized(gate) {
            cancelFlag.set(false)
            LlamaRuntime.clearCancel()
            ticket.incrementAndGet()
        }
        worker.execute {
            if (ticket.get() != id) return@execute
            busy = true
            lastUsed = System.currentTimeMillis()
            try {
                if (!ensureLoaded(config)) {
                    if (ticket.get() == id) fail(AiRefusal.NO_MODEL)
                    return@execute
                }
                val assembler = StreamAssembler()
                val code = LlamaRuntime.generate(
                    system = plan.system,
                    user = plan.user,
                    temperature = config.temperature,
                    maxTokens = plan.maxTokens,
                    sink = TokenSink { piece ->
                        if (cancelFlag.get() || ticket.get() != id) return@TokenSink false
                        val keep = assembler.push(piece)
                        val current = _panel.value
                        if (current != null && ticket.get() == id) {
                            _panel.value = current.copy(output = assembler.text(), phase = AiPhase.RUNNING)
                        }
                        keep && !assembler.cancelled
                    },
                )
                if (ticket.get() != id) return@execute
                val current = _panel.value ?: return@execute
                val text = assembler.text().ifEmpty { current.output }
                _panel.value = current.copy(
                    output = text,
                    phase = if (text.isNotEmpty() && code != 4 && code != 5) AiPhase.RESULT else AiPhase.FAILED,
                    notice = when (code) {
                        3 -> AiRefusal.TOO_LONG
                        5 -> AiRefusal.NO_MODEL
                        else -> if (text.isEmpty()) AiRefusal.EMPTY else null
                    },
                )
            } finally {
                busy = false
                lastUsed = System.currentTimeMillis()
            }
        }
    }

    private fun ensureLoaded(config: AiConfig): Boolean {
        val id = config.selectedModelId ?: return false
        val file = files.modelFile(id) ?: return false
        if (!file.isFile) return false
        if (_loaded.value && LlamaRuntime.loaded()) return true
        unloadNow()
        val ok = LlamaRuntime.load(file.absolutePath, config.contextSize, config.threads)
        _loaded.value = ok
        return ok
    }

    private fun unloadNow() {
        LlamaRuntime.unload()
        _loaded.value = false
    }

    private fun fail(reason: AiRefusal) {
        val current = _panel.value ?: return
        _panel.value = current.copy(phase = AiPhase.FAILED, notice = reason)
    }

    private fun idleCheck() {
        if (AiLifecycle.shouldUnload(_loaded.value, busy, System.currentTimeMillis(), lastUsed)) {
            worker.execute {
                if (AiLifecycle.shouldUnload(_loaded.value, busy, System.currentTimeMillis(), lastUsed)) {
                    unloadNow()
                }
            }
        }
    }

    private fun scheduleSuggestion(prefix: String) {
        if (suggestionQueued == prefix) return
        suggestionQueued = prefix
        scheduler.schedule({
            worker.execute { runSuggestion(prefix) }
        }, 450, java.util.concurrent.TimeUnit.MILLISECONDS)
    }

    private fun runSuggestion(prefix: String) {
        if (suggestionQueued != prefix || busy || _panel.value?.phase == AiPhase.RUNNING) return
        val config = _config.value
        if (config.selectedModelId == null) return
        busy = true
        try {
            if (!ensureLoaded(config)) return
            val assembler = StreamAssembler(maxChars = 64)
            LlamaRuntime.generate(
                system = "",
                user = AiPrompt.suggestionUser(prefix),
                temperature = minOf(config.temperature, 0.7f),
                maxTokens = 16,
                sink = TokenSink { piece ->
                    if (suggestionQueued != prefix || cancelFlag.get()) return@TokenSink false
                    assembler.push(piece)
                },
            )
            val cleaned = AiSuggestionText.candidate(prefix, assembler.text())
            if (suggestionQueued == prefix && cleaned != null) {
                suggestionPrefix = prefix
                suggestionText = cleaned
                try {
                    nlp.refreshSuggestions()
                } catch (_: Exception) {
                    // The next keystroke reads the cache.
                }
            }
        } finally {
            busy = false
            lastUsed = System.currentTimeMillis()
        }
    }

    private fun query(uri: Uri): Pair<String, Long?> {
        var name = "model.gguf"
        var size: Long? = null
        try {
            appContext.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (nameIndex >= 0) name = cursor.getString(nameIndex) ?: name
                        if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex)
                    }
                }
        } catch (_: Exception) {
            // The copy still checks the GGUF header and the byte cap.
        }
        return name to size
    }
}
