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

package com.victorious.keyboard.ime.ai

import com.victorious.keyboard.ime.nlp.prediction.LearningGate
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** What the on-device model is asked to do. Names are not persisted. */
enum class AiTask {
    REWRITE,
    GRAMMAR,
    CONTINUE,
    SUMMARIZE,
    TRANSLATE,
    SHELL,
    ASK,
    SUGGEST,
}

/** Where the text came from. Vault and clipboard are never chosen automatically. */
enum class AiSource {
    SELECTION,
    FIELD,
    CLIPBOARD,
    EDITOR,
    VAULT,
}

enum class AiRefusal {
    NO_MODEL,
    NATIVE_MISSING,
    EMPTY,
    PASSWORD,
    VAULT,
    TOO_LONG,
}

enum class AiPhase { COMPOSING, RUNNING, RESULT, FAILED }

enum class ImportDecision {
    ACCEPT,
    WARN,
    REJECT_EMPTY,
    REJECT_MAGIC,
    REJECT_TOO_LARGE,
    REJECT_NO_SPACE,
    REJECT_TOO_MANY,
    REJECT_NAME,
}

object GgufHeader {
    fun matches(header: ByteArray): Boolean {
        if (header.size < 4) return false
        return header[0] == 'G'.code.toByte() &&
            header[1] == 'G'.code.toByte() &&
            header[2] == 'U'.code.toByte() &&
            header[3] == 'F'.code.toByte()
    }
}

object ModelImport {
    const val WARN_BYTES = 512L * 1024L * 1024L
    const val MAX_BYTES = 4L * 1024L * 1024L * 1024L
    const val SLACK_BYTES = 64L * 1024L * 1024L
    const val MAX_MODELS = 20
    private val ID = Regex("""[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}""")

    fun fileName(id: String): String? {
        if (!ID.matches(id)) return null
        return "$id.gguf"
    }

    fun decide(
        sizeBytes: Long?,
        freeBytes: Long,
        modelCount: Int,
        acknowledged: Boolean,
    ): ImportDecision {
        if (modelCount >= MAX_MODELS) return ImportDecision.REJECT_TOO_MANY
        if (sizeBytes != null && sizeBytes <= 0L) return ImportDecision.REJECT_EMPTY
        if (sizeBytes != null && sizeBytes > MAX_BYTES) return ImportDecision.REJECT_TOO_LARGE
        val need = (sizeBytes ?: WARN_BYTES) + SLACK_BYTES
        if (freeBytes in 0 until need) return ImportDecision.REJECT_NO_SPACE
        val large = sizeBytes == null || sizeBytes >= WARN_BYTES
        if (large && !acknowledged) return ImportDecision.WARN
        return ImportDecision.ACCEPT
    }
}

data class ImportedModel(
    val id: String,
    val displayName: String,
    val bytes: Long,
    val importedAt: Long,
)

object ModelIndexCodec {
    const val FORMAT = "victorious-local-ai-models"
    const val MAX_CHARS = 64 * 1024
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class File(
        val format: String = "",
        val version: Int = 0,
        val models: List<Row> = emptyList(),
    )

    @Serializable
    private data class Row(
        val id: String = "",
        val displayName: String = "",
        val bytes: Long = 0,
        val importedAt: Long = 0,
    )

    fun encode(models: List<ImportedModel>): String {
        val file = File(
            format = FORMAT,
            version = 1,
            models = models.map {
                Row(it.id, it.displayName.take(120), it.bytes, it.importedAt)
            },
        )
        return json.encodeToString(File.serializer(), file)
    }

    fun decode(text: String): List<ImportedModel> {
        if (text.length > MAX_CHARS) return emptyList()
        return try {
            val file = json.decodeFromString(File.serializer(), text)
            if (file.format != FORMAT || file.version != 1) return emptyList()
            file.models.mapNotNull { row ->
                if (ModelImport.fileName(row.id) == null) return@mapNotNull null
                if (row.bytes <= 0L || row.bytes > ModelImport.MAX_BYTES) return@mapNotNull null
                val name = row.displayName.trim().filter { !it.isISOControl() }.take(120)
                if (name.isEmpty()) return@mapNotNull null
                ImportedModel(row.id.lowercase(), name, row.bytes, row.importedAt)
            }.take(ModelImport.MAX_MODELS)
        } catch (_: Exception) {
            emptyList()
        }
    }
}

@Serializable
data class AiConfig(
    val selectedModelId: String? = null,
    val contextSize: Int = 2048,
    val threads: Int = 4,
    val temperature: Float = 0.7f,
    val maxTokens: Int = 256,
    val systemPrompt: String = "",
    val suggestionsEnabled: Boolean = false,
    val translateLanguage: String = "English",
)

object AiConfigRules {
    val CONTEXT_CHOICES = listOf(512, 1024, 2048, 4096, 8192)
    const val FORMAT = "victorious-local-ai"
    const val MAX_CHARS = 64 * 1024
    const val MAX_SYSTEM = 2000
    const val MAX_LANGUAGE = 40

    fun defaultThreads(processors: Int): Int = minOf(4, maxOf(1, processors))

    fun clamp(raw: AiConfig, processors: Int = 4): AiConfig {
        val context = CONTEXT_CHOICES.minByOrNull { kotlin.math.abs(it - raw.contextSize) } ?: 2048
        val language = raw.translateLanguage.trim().filter { it.isLetter() || it == ' ' || it == '-' }
            .take(MAX_LANGUAGE).ifEmpty { "English" }
        val id = raw.selectedModelId?.let { ModelImport.fileName(it)?.let { _ -> it.lowercase() } }
        return raw.copy(
            selectedModelId = id,
            contextSize = context,
            threads = raw.threads.coerceIn(1, 8).let { if (raw.threads == 0) defaultThreads(processors) else it },
            temperature = raw.temperature.coerceIn(0f, 1.5f),
            maxTokens = raw.maxTokens.coerceIn(16, 1024),
            systemPrompt = raw.systemPrompt.filter { it != '\u0000' }.take(MAX_SYSTEM),
            translateLanguage = language,
        )
    }
}

object AiConfigCodec {
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class File(
        val format: String = "",
        val version: Int = 0,
        val config: AiConfig = AiConfig(),
    )

    fun encode(config: AiConfig): String {
        val file = File(AiConfigRules.FORMAT, 1, AiConfigRules.clamp(config))
        return json.encodeToString(File.serializer(), file)
    }

    fun decode(text: String, processors: Int = 4): AiConfig {
        if (text.length > AiConfigRules.MAX_CHARS) return AiConfig(threads = AiConfigRules.defaultThreads(processors))
        return try {
            val file = json.decodeFromString(File.serializer(), text)
            if (file.format != AiConfigRules.FORMAT || file.version != 1) {
                AiConfig(threads = AiConfigRules.defaultThreads(processors))
            } else {
                AiConfigRules.clamp(file.config, processors)
            }
        } catch (_: Exception) {
            AiConfig(threads = AiConfigRules.defaultThreads(processors))
        }
    }
}

object AiLifecycle {
    const val IDLE_MS = 120_000L
    const val MEMORY_LOW = 10

    fun shouldUnload(loaded: Boolean, busy: Boolean, now: Long, lastUsed: Long): Boolean =
        loaded && !busy && now - lastUsed >= IDLE_MS

    fun shouldUnloadForMemory(level: Int): Boolean = level >= MEMORY_LOW
}

object AiPrivacy {
    fun maySuggest(
        suggestionsEnabled: Boolean,
        modelSelected: Boolean,
        learningEnabled: Boolean,
        predictionEnabled: Boolean,
        field: LearningGate.Field,
    ): Boolean = suggestionsEnabled &&
        modelSelected &&
        LearningGate.allowsLearning(learningEnabled, field) &&
        LearningGate.allowsPrediction(predictionEnabled, field)
}

data class AiRequest(
    val task: AiTask,
    val source: AiSource,
    val explicitVault: Boolean,
    val passwordLike: Boolean,
    val editorIsVault: Boolean,
    val selection: String = "",
    val fieldText: String = "",
    val clipboard: String = "",
    val editorText: String = "",
    val askText: String = "",
    val systemPrompt: String = "",
    val translateLanguage: String = "English",
    val modelSelected: Boolean = false,
    val nativeAvailable: Boolean = true,
    val contextSize: Int = 2048,
    val maxTokens: Int = 256,
)

sealed class AiPlan {
    data class Ready(
        val system: String,
        val user: String,
        val fromVault: Boolean,
        val maxTokens: Int,
    ) : AiPlan()

    data class Refuse(val reason: AiRefusal) : AiPlan()
}

object AiPrompt {
    const val SHELL_RULE =
        "Reply with one short shell command or one sentence explaining it. Do not include exploit steps, wordlists, or attack procedures. The text is inserted and is never executed."
    const val MAX_SOURCE = 6000

    fun chooseDefault(
        selection: String,
        fieldText: String,
        editorText: String,
        passwordLike: Boolean,
        editorOpen: Boolean,
        editorIsVault: Boolean,
    ): AiSource? = when {
        !passwordLike && selection.isNotBlank() -> AiSource.SELECTION
        editorOpen && !editorIsVault && editorText.isNotBlank() -> AiSource.EDITOR
        !passwordLike && fieldText.isNotBlank() -> AiSource.FIELD
        else -> null
    }

    fun plan(request: AiRequest): AiPlan {
        if (!request.nativeAvailable) return AiPlan.Refuse(AiRefusal.NATIVE_MISSING)
        if (!request.modelSelected) return AiPlan.Refuse(AiRefusal.NO_MODEL)
        val vault = request.source == AiSource.VAULT
        if (vault && (!request.explicitVault || !request.editorIsVault)) {
            return AiPlan.Refuse(AiRefusal.VAULT)
        }
        val body = when (request.source) {
            AiSource.SELECTION -> if (request.passwordLike) return AiPlan.Refuse(AiRefusal.PASSWORD) else request.selection
            AiSource.FIELD -> if (request.passwordLike) return AiPlan.Refuse(AiRefusal.PASSWORD) else request.fieldText
            AiSource.CLIPBOARD -> request.clipboard
            AiSource.EDITOR -> if (request.editorIsVault) return AiPlan.Refuse(AiRefusal.VAULT) else request.editorText
            AiSource.VAULT -> request.editorText
        }.trim()
        val ask = request.askText.trim().take(2000)
        if (request.task != AiTask.ASK && body.isEmpty()) return AiPlan.Refuse(AiRefusal.EMPTY)
        if (request.task == AiTask.ASK && ask.isEmpty()) return AiPlan.Refuse(AiRefusal.EMPTY)
        val fitted = trim(body, request.contextSize, request.maxTokens)
        if (request.task != AiTask.ASK && fitted.isEmpty()) return AiPlan.Refuse(AiRefusal.TOO_LONG)
        val instruction = instruction(request.task, request.translateLanguage)
        val user = when (request.task) {
            AiTask.ASK -> if (fitted.isEmpty()) ask else "$ask\n\nContext:\n$fitted"
            AiTask.SUGGEST -> instruction + "\n" + fitted
            else -> instruction + "\n\n" + fitted
        }
        return AiPlan.Ready(
            system = request.systemPrompt.take(AiConfigRules.MAX_SYSTEM),
            user = user,
            fromVault = vault,
            maxTokens = if (request.task == AiTask.SUGGEST) 16 else request.maxTokens,
        )
    }

    fun suggestionUser(prefix: String): String =
        "Complete this prefix with one word or a short phrase of at most four words. Reply with only that word or phrase.\n" +
            prefix.take(48)

    private fun instruction(task: AiTask, language: String): String = when (task) {
        AiTask.REWRITE -> "Rewrite the text so it is clearer. Reply with only the rewritten text."
        AiTask.GRAMMAR -> "Fix grammar and spelling. Reply with only the corrected text."
        AiTask.CONTINUE -> "Continue the text in the same style. Reply with only the continuation."
        AiTask.SUMMARIZE -> "Summarize the text in a few sentences. Reply with only the summary."
        AiTask.TRANSLATE -> "Translate the text to $language. Reply with only the translation."
        AiTask.SHELL -> SHELL_RULE
        AiTask.ASK -> ""
        AiTask.SUGGEST -> "Complete this prefix with one word or a short phrase of at most four words. Reply with only that word or phrase."
    }

    private fun trim(text: String, contextSize: Int, maxTokens: Int): String {
        val budget = ((contextSize - maxTokens - 64).coerceAtLeast(128)) * 3
        val cap = minOf(MAX_SOURCE, budget)
        return text.take(cap).trim()
    }
}

object AiSuggestionText {
    fun candidate(prefix: String, raw: String): String? {
        val line = raw.lineSequence().firstOrNull { it.isNotBlank() }?.trim() ?: return null
        val text = line.trim('"', '\'', '`', '“', '”').trim()
        if (text.isEmpty() || text.any { it.isISOControl() }) return null
        val words = text.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.size > 4) return null
        val clipped = text.take(48).trim()
        if (clipped.length < 2) return null
        val merged = when {
            clipped.startsWith(prefix, ignoreCase = true) -> clipped
            prefix.isNotEmpty() && !clipped.any { it.isWhitespace() } -> prefix + clipped
            else -> return null
        }
        if (merged.equals(prefix, ignoreCase = true)) return null
        return merged.take(48)
    }
}

class StreamAssembler(private val maxChars: Int = 8000) {
    private val buffer = StringBuilder()
    var cancelled: Boolean = false

    fun push(piece: String): Boolean {
        if (cancelled) return false
        if (piece.isEmpty()) return true
        val room = maxChars - buffer.length
        if (room <= 0) {
            cancelled = true
            return false
        }
        buffer.append(if (piece.length > room) piece.take(room) else piece)
        if (buffer.length >= maxChars) cancelled = true
        return !cancelled
    }

    fun text(): String = buffer.toString()
}
