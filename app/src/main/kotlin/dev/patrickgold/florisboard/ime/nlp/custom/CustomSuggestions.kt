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

package dev.patrickgold.florisboard.ime.nlp.custom

import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * A phrase the user asked to see in the suggestion strip. [trigger] is an optional shortcut.
 * An empty trigger matches when the phrase itself starts with what is being typed.
 * Nothing in this file is logged.
 */
data class CustomSuggestion(
    val id: String,
    val text: String,
    val trigger: String = "",
    val createdAt: Long = 0L,
)

object CustomSuggestionRules {
    const val FORMAT = "victorious-custom-suggestions"
    const val VERSION = 1
    const val MAX_ITEMS = 500
    const val MAX_TEXT = 500
    const val MAX_TRIGGER = 64
    const val MAX_FILE_BYTES = 512 * 1024
    private val ID = Regex("""[A-Za-z0-9_-]{1,64}""")

    fun cleanText(raw: String): String? {
        val text = raw.trim()
        if (text.isEmpty() || text.length > MAX_TEXT) return null
        if (text.any { it.isISOControl() && it != '\n' && it != '\t' }) return null
        return text
    }

    fun cleanTrigger(raw: String): String? {
        val trigger = raw.trim()
        if (trigger.isEmpty()) return ""
        if (trigger.length > MAX_TRIGGER) return null
        if (trigger.any { it.isWhitespace() || it.isISOControl() || it == '/' }) return null
        return trigger
    }

    fun cleanId(raw: String): String? = raw.takeIf { ID.matches(it) }
}

object CustomSuggestionMatcher {
    /**
     * A trigger matches when the typed token equals it, starts with it, or is a prefix of it.
     * Without a trigger, the phrase must start with the token. An empty token matches nothing.
     */
    fun matches(item: CustomSuggestion, prefix: String): Boolean {
        if (prefix.isEmpty()) return false
        val typed = prefix.lowercase()
        val trigger = item.trigger.trim()
        if (trigger.isNotEmpty()) {
            val shortcut = trigger.lowercase()
            return typed == shortcut || typed.startsWith(shortcut) || shortcut.startsWith(typed)
        }
        return item.text.lowercase().startsWith(typed)
    }

    /** Newer phrases first. Callers place this list ahead of dictionary candidates. */
    fun rank(items: List<CustomSuggestion>, prefix: String): List<CustomSuggestion> =
        items.filter { matches(it, prefix) }.sortedByDescending { it.createdAt }
}

object CustomSuggestionList {
    fun add(
        items: List<CustomSuggestion>,
        text: String,
        trigger: String,
        now: Long,
        id: String = UUID.randomUUID().toString(),
    ): List<CustomSuggestion>? {
        val cleanText = CustomSuggestionRules.cleanText(text) ?: return null
        val cleanTrigger = CustomSuggestionRules.cleanTrigger(trigger) ?: return null
        val cleanId = CustomSuggestionRules.cleanId(id) ?: return null
        if (items.any { it.id == cleanId }) return null
        if (items.any { it.text.equals(cleanText, ignoreCase = true) && it.trigger.equals(cleanTrigger, ignoreCase = true) }) {
            return null
        }
        val next = listOf(CustomSuggestion(cleanId, cleanText, cleanTrigger, now)) + items
        return next.take(CustomSuggestionRules.MAX_ITEMS)
    }

    fun update(items: List<CustomSuggestion>, id: String, text: String, trigger: String): List<CustomSuggestion>? {
        if (items.none { it.id == id }) return null
        val cleanText = CustomSuggestionRules.cleanText(text) ?: return null
        val cleanTrigger = CustomSuggestionRules.cleanTrigger(trigger) ?: return null
        val clash = items.any {
            it.id != id && it.text.equals(cleanText, ignoreCase = true) && it.trigger.equals(cleanTrigger, ignoreCase = true)
        }
        if (clash) return null
        return items.map { if (it.id == id) it.copy(text = cleanText, trigger = cleanTrigger) else it }
    }

    fun delete(items: List<CustomSuggestion>, id: String): List<CustomSuggestion>? =
        if (items.none { it.id == id }) null else items.filterNot { it.id == id }

    /**
     * [replace] drops the previous list. Otherwise the same id is updated and new ids are appended.
     * The result is capped. Callers must not pass a list decoded from hostile input as a replace.
     */
    fun merge(existing: List<CustomSuggestion>, incoming: List<CustomSuggestion>, replace: Boolean): List<CustomSuggestion> {
        if (replace) return incoming.take(CustomSuggestionRules.MAX_ITEMS)
        val byId = LinkedHashMap<String, CustomSuggestion>()
        for (item in existing) byId[item.id] = item
        val order = existing.map { it.id }.toMutableList()
        for (item in incoming) {
            if (item.id in byId) {
                byId[item.id] = item
            } else if (byId.size < CustomSuggestionRules.MAX_ITEMS) {
                byId[item.id] = item
                order.add(item.id)
            }
        }
        return order.mapNotNull { byId[it] }.take(CustomSuggestionRules.MAX_ITEMS)
    }
}

sealed class SuggestionDocument {
    data class Ok(val items: List<CustomSuggestion>) : SuggestionDocument()
    data object Invalid : SuggestionDocument()
}

object CustomSuggestionCodec {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Serializable
    private data class FileModel(
        val format: String = "",
        val version: Int = 0,
        val items: List<ItemModel> = emptyList(),
    )

    @Serializable
    private data class ItemModel(
        val id: String = "",
        val text: String = "",
        val trigger: String = "",
        val createdAt: Long = 0L,
    )

    fun encode(items: List<CustomSuggestion>): String {
        val model = FileModel(
            format = CustomSuggestionRules.FORMAT,
            version = CustomSuggestionRules.VERSION,
            items = items.take(CustomSuggestionRules.MAX_ITEMS).map {
                ItemModel(it.id, it.text, it.trigger, it.createdAt)
            },
        )
        return json.encodeToString(model)
    }

    /** Untrusted input. A wrong, oversized, or corrupt document is [SuggestionDocument.Invalid] and must not wipe data. */
    fun decode(raw: String): SuggestionDocument {
        if (raw.length > CustomSuggestionRules.MAX_FILE_BYTES) return SuggestionDocument.Invalid
        val model = try {
            json.decodeFromString<FileModel>(raw)
        } catch (_: SerializationException) {
            return SuggestionDocument.Invalid
        } catch (_: IllegalArgumentException) {
            return SuggestionDocument.Invalid
        } catch (_: StackOverflowError) {
            return SuggestionDocument.Invalid
        }
        if (model.format != CustomSuggestionRules.FORMAT || model.version != CustomSuggestionRules.VERSION) {
            return SuggestionDocument.Invalid
        }
        val items = ArrayList<CustomSuggestion>()
        val seen = HashSet<String>()
        for (item in model.items) {
            val id = CustomSuggestionRules.cleanId(item.id) ?: continue
            if (!seen.add(id)) continue
            val text = CustomSuggestionRules.cleanText(item.text) ?: continue
            val trigger = CustomSuggestionRules.cleanTrigger(item.trigger) ?: continue
            if (item.createdAt < 0) continue
            items.add(CustomSuggestion(id, text, trigger, item.createdAt))
            if (items.size >= CustomSuggestionRules.MAX_ITEMS) break
        }
        return SuggestionDocument.Ok(items)
    }
}
