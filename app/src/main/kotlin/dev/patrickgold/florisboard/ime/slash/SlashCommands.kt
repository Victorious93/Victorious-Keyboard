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

package dev.patrickgold.florisboard.ime.slash

import dev.patrickgold.florisboard.ime.ai.AiPrompt
import dev.patrickgold.florisboard.ime.ai.AiTask
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** The `/name` token at the start of the word before the caret. [replaceLength] is that word's UTF-16 length. */
data class SlashToken(
    val name: String,
    val argument: String,
    val replaceLength: Int,
)

object SlashMatcher {
    /** Built-ins whose text after the name is part of the command. Other names end at the first space. */
    private val argumentNames = setOf("snip", "ai", "shell")

    /**
     * A slash command starts at a `/` that is at a word boundary and runs to the caret.
     * Password fields return before the text is inspected. A slash in the middle of a word is ordinary text.
     * `/snip`, `/ai`, and `/shell` keep their following words; other commands end at the first space.
     */
    fun token(textBeforeCursor: String, passwordLike: Boolean): SlashToken? {
        if (passwordLike) return null
        if (textBeforeCursor.isEmpty()) return null
        var slash = -1
        var i = textBeforeCursor.length - 1
        while (i >= 0) {
            val ch = textBeforeCursor[i]
            if (ch == '\n') return null
            if (ch == '/' && (i == 0 || textBeforeCursor[i - 1].isWhitespace())) {
                slash = i
                break
            }
            i--
        }
        if (slash < 0) return null
        val raw = textBeforeCursor.substring(slash)
        if (raw.length > 200) return null
        val body = raw.substring(1)
        val space = body.indexOf(' ')
        val name = (if (space < 0) body else body.substring(0, space)).lowercase()
        if (name.any { it.isISOControl() }) return null
        if (space >= 0 && name.isNotEmpty() && name !in argumentNames) return null
        val argument = if (space < 0) "" else body.substring(space + 1)
        return SlashToken(name, argument, raw.length)
    }
}

enum class SlashBuiltin {
    DATE,
    TIME,
    CLIP,
    SNIP,
    AI,
    FIX,
    REWRITE,
    SUM,
    TRANSLATE,
    SHELL,
    EDIT,
    VAULT,
}

data class SlashOffer(
    val name: String,
    val description: String,
    val builtin: SlashBuiltin?,
    val customId: String?,
)

data class CustomSlashCommand(
    val id: String,
    val name: String,
    val description: String,
    val template: String,
    val enabled: Boolean = true,
    val order: Int = 0,
)

object SlashCatalog {
    data class Builtin(val kind: SlashBuiltin, val name: String, val description: String)

    val builtins: List<Builtin> = listOf(
        Builtin(SlashBuiltin.DATE, "date", "Insert today's date"),
        Builtin(SlashBuiltin.TIME, "time", "Insert the time"),
        Builtin(SlashBuiltin.CLIP, "clip", "Paste the latest clip"),
        Builtin(SlashBuiltin.SNIP, "snip", "Insert a snippet by name"),
        Builtin(SlashBuiltin.AI, "ai", "Ask Local AI and insert the reply"),
        Builtin(SlashBuiltin.FIX, "fix", "Fix grammar with Local AI"),
        Builtin(SlashBuiltin.REWRITE, "rewrite", "Rewrite the selection with Local AI"),
        Builtin(SlashBuiltin.SUM, "sum", "Summarize with Local AI"),
        Builtin(SlashBuiltin.TRANSLATE, "translate", "Translate with Local AI"),
        Builtin(SlashBuiltin.SHELL, "shell", "Insert one shell command. It is not run"),
        Builtin(SlashBuiltin.EDIT, "edit", "Open the clip editor"),
        Builtin(SlashBuiltin.VAULT, "vault", "Unlock the vault"),
    )

    private val reserved = builtins.map { it.name }.toSet()

    fun isReserved(name: String): Boolean = name.lowercase() in reserved

    fun match(namePrefix: String, commands: List<CustomSlashCommand>, enabled: Boolean): List<SlashOffer> {
        if (!enabled) return emptyList()
        val prefix = namePrefix.lowercase()
        val built = builtins.filter { prefix.isEmpty() || it.name.startsWith(prefix) }.map {
            SlashOffer(it.name, it.description, it.kind, null)
        }
        val custom = commands
            .filter { it.enabled && (prefix.isEmpty() || it.name.startsWith(prefix)) }
            .sortedBy { it.order }
            .map { SlashOffer(it.name, it.description, null, it.id) }
        return (built + custom).take(24)
    }
}

/** Values substituted into `{name}` are appended and never scanned again. */
data class SlashVars(
    val date: String = "",
    val time: String = "",
    val clipboard: String = "",
    val selection: String = "",
)

data class SlashRender(val text: String, val cursorBackCodePoints: Int?)

object SlashTemplate {
    private val keys = setOf("date", "time", "clipboard", "selection", "cursor")

    fun uses(template: String, key: String): Boolean {
        var i = 0
        while (i < template.length) {
            if (template[i] == '{') {
                val end = template.indexOf('}', i + 1)
                if (end > i && template.substring(i + 1, end) == key) return true
            }
            i++
        }
        return false
    }

    fun render(template: String, vars: SlashVars): SlashRender {
        val out = StringBuilder()
        var cursorAt: Int? = null
        var i = 0
        while (i < template.length) {
            if (template[i] == '{') {
                val end = template.indexOf('}', i + 1)
                if (end > i) {
                    val key = template.substring(i + 1, end)
                    if (key in keys) {
                        if (key == "cursor") {
                            if (cursorAt == null) cursorAt = out.length
                        } else {
                            out.append(value(key, vars))
                        }
                        i = end + 1
                        continue
                    }
                }
            }
            out.append(template[i])
            i++
        }
        val text = out.toString()
        val back = cursorAt?.let { at ->
            if (at > text.length) null else text.codePointCount(at, text.length).takeIf { it > 0 }
        }
        return SlashRender(text, back)
    }

    private fun value(key: String, vars: SlashVars): String = when (key) {
        "date" -> vars.date
        "time" -> vars.time
        "clipboard" -> vars.clipboard
        "selection" -> vars.selection
        else -> ""
    }
}

object SlashClock {
    private val dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val timeFormat = DateTimeFormatter.ofPattern("HH:mm")

    fun date(instant: Instant, zone: ZoneId): String = dateFormat.withZone(zone).format(instant)

    fun time(instant: Instant, zone: ZoneId): String = timeFormat.withZone(zone).format(instant)
}

/** What tapping a slash chip asks the keyboard to do. Text is inserted; nothing here is executed. */
sealed class SlashAction {
    data class Insert(val text: String, val cursorBackCodePoints: Int?) : SlashAction()
    data class Custom(val command: CustomSlashCommand) : SlashAction()
    data object PasteClip : SlashAction()
    data class Snippet(val name: String) : SlashAction()
    data class Ai(val task: AiTask, val prompt: String, val systemOverride: String?) : SlashAction()
    data object OpenEditor : SlashAction()
    data object UnlockVault : SlashAction()
    data object Unknown : SlashAction()
}

object SlashActions {
    fun resolve(offer: SlashOffer, argument: String, commands: List<CustomSlashCommand>, now: Instant, zone: ZoneId): SlashAction {
        val builtin = offer.builtin
        if (builtin != null) {
            return when (builtin) {
                SlashBuiltin.DATE -> SlashAction.Insert(SlashClock.date(now, zone), null)
                SlashBuiltin.TIME -> SlashAction.Insert(SlashClock.time(now, zone), null)
                SlashBuiltin.CLIP -> SlashAction.PasteClip
                SlashBuiltin.SNIP -> if (argument.isBlank()) SlashAction.Snippet("") else SlashAction.Snippet(argument.trim())
                SlashBuiltin.AI -> SlashAction.Ai(AiTask.ASK, argument.trim(), null)
                SlashBuiltin.FIX -> SlashAction.Ai(AiTask.GRAMMAR, "", null)
                SlashBuiltin.REWRITE -> SlashAction.Ai(AiTask.REWRITE, "", null)
                SlashBuiltin.SUM -> SlashAction.Ai(AiTask.SUMMARIZE, "", null)
                SlashBuiltin.TRANSLATE -> SlashAction.Ai(AiTask.TRANSLATE, "", null)
                SlashBuiltin.SHELL -> SlashAction.Ai(AiTask.ASK, argument.trim(), AiPrompt.SHELL_RULE)
                SlashBuiltin.EDIT -> SlashAction.OpenEditor
                SlashBuiltin.VAULT -> SlashAction.UnlockVault
            }
        }
        val command = commands.firstOrNull { it.id == offer.customId && it.enabled } ?: return SlashAction.Unknown
        return SlashAction.Custom(command)
    }

    /** The prompt sent for `/shell`. The rule is fixed; the description is appended and not scanned for commands. */
    fun shellUser(description: String): String = AiPrompt.SHELL_RULE + "\n" + description
}

object SlashCommandRules {
    const val FORMAT = "victorious-slash-commands"
    const val VERSION = 1
    const val MAX_ITEMS = 100
    const val MAX_NAME = 32
    const val MAX_DESCRIPTION = 120
    const val MAX_TEMPLATE = 2000
    const val MAX_FILE_BYTES = 256 * 1024
    private val NAME = Regex("""[a-z][a-z0-9_-]{0,31}""")
    private val ID = Regex("""[A-Za-z0-9_-]{1,64}""")

    fun cleanName(raw: String): String? {
        val name = raw.trim().lowercase()
        if (!NAME.matches(name) || name.length > MAX_NAME) return null
        if (SlashCatalog.isReserved(name)) return null
        return name
    }

    fun cleanDescription(raw: String): String? {
        val text = raw.trim()
        if (text.length > MAX_DESCRIPTION) return null
        if (text.any { it.isISOControl() }) return null
        return text
    }

    fun cleanTemplate(raw: String): String? {
        if (raw.length > MAX_TEMPLATE) return null
        if (raw.any { it.isISOControl() && it != '\n' && it != '\t' }) return null
        return raw
    }

    fun cleanId(raw: String): String? = raw.takeIf { ID.matches(it) }
}

object SlashCommandList {
    fun add(
        items: List<CustomSlashCommand>,
        name: String,
        description: String,
        template: String,
        id: String = UUID.randomUUID().toString(),
    ): List<CustomSlashCommand>? {
        val cleanName = SlashCommandRules.cleanName(name) ?: return null
        val cleanDescription = SlashCommandRules.cleanDescription(description) ?: return null
        val cleanTemplate = SlashCommandRules.cleanTemplate(template) ?: return null
        val cleanId = SlashCommandRules.cleanId(id) ?: return null
        if (items.size >= SlashCommandRules.MAX_ITEMS) return null
        if (items.any { it.id == cleanId || it.name == cleanName }) return null
        val order = (items.maxOfOrNull { it.order } ?: -1) + 1
        return items + CustomSlashCommand(cleanId, cleanName, cleanDescription, cleanTemplate, true, order)
    }

    fun update(
        items: List<CustomSlashCommand>,
        id: String,
        name: String,
        description: String,
        template: String,
        enabled: Boolean,
    ): List<CustomSlashCommand>? {
        if (items.none { it.id == id }) return null
        val cleanName = SlashCommandRules.cleanName(name) ?: return null
        val cleanDescription = SlashCommandRules.cleanDescription(description) ?: return null
        val cleanTemplate = SlashCommandRules.cleanTemplate(template) ?: return null
        if (items.any { it.id != id && it.name == cleanName }) return null
        return items.map {
            if (it.id == id) it.copy(name = cleanName, description = cleanDescription, template = cleanTemplate, enabled = enabled) else it
        }
    }

    fun delete(items: List<CustomSlashCommand>, id: String): List<CustomSlashCommand>? =
        if (items.none { it.id == id }) null else items.filterNot { it.id == id }

    fun move(items: List<CustomSlashCommand>, id: String, direction: Int): List<CustomSlashCommand>? {
        val sorted = items.sortedBy { it.order }
        val index = sorted.indexOfFirst { it.id == id }
        if (index < 0) return null
        val target = index + direction
        if (target !in sorted.indices) return sorted
        val swapped = sorted.toMutableList()
        val item = swapped.removeAt(index)
        swapped.add(target, item)
        return swapped.mapIndexed { n, command -> command.copy(order = n) }
    }
}

sealed class SlashDocument {
    data class Ok(val items: List<CustomSlashCommand>) : SlashDocument()
    data object Invalid : SlashDocument()
}

object SlashCommandCodec {
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
        val name: String = "",
        val description: String = "",
        val template: String = "",
        val enabled: Boolean = true,
        val order: Int = 0,
    )

    fun encode(items: List<CustomSlashCommand>): String {
        val model = FileModel(
            format = SlashCommandRules.FORMAT,
            version = SlashCommandRules.VERSION,
            items = items.take(SlashCommandRules.MAX_ITEMS).map {
                ItemModel(it.id, it.name, it.description, it.template, it.enabled, it.order)
            },
        )
        return json.encodeToString(model)
    }

    fun decode(raw: String): SlashDocument {
        if (raw.length > SlashCommandRules.MAX_FILE_BYTES) return SlashDocument.Invalid
        val model = try {
            json.decodeFromString<FileModel>(raw)
        } catch (_: SerializationException) {
            return SlashDocument.Invalid
        } catch (_: IllegalArgumentException) {
            return SlashDocument.Invalid
        } catch (_: StackOverflowError) {
            return SlashDocument.Invalid
        }
        if (model.format != SlashCommandRules.FORMAT || model.version != SlashCommandRules.VERSION) {
            return SlashDocument.Invalid
        }
        val items = ArrayList<CustomSlashCommand>()
        val seen = HashSet<String>()
        val names = HashSet<String>()
        for (item in model.items) {
            val id = SlashCommandRules.cleanId(item.id) ?: continue
            val name = SlashCommandRules.cleanName(item.name) ?: continue
            if (!seen.add(id) || !names.add(name)) continue
            val description = SlashCommandRules.cleanDescription(item.description) ?: continue
            val template = SlashCommandRules.cleanTemplate(item.template) ?: continue
            items.add(CustomSlashCommand(id, name, description, template, item.enabled, item.order))
            if (items.size >= SlashCommandRules.MAX_ITEMS) break
        }
        return SlashDocument.Ok(items.sortedBy { it.order })
    }
}
