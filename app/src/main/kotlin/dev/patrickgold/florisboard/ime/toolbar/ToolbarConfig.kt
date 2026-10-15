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

package dev.patrickgold.florisboard.ime.toolbar

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * What the user changed about the toolbar. Stored under `noBackupFilesDir` because it can hold
 * command text and an opt-in shell history. A broken file becomes the defaults and is not logged.
 */
@Serializable
data class ToolbarConfig(
    val hidden: List<String> = emptyList(),
    val order: Map<String, List<String>> = emptyMap(),
    val commands: List<SavedCommandDto> = emptyList(),
    val shellHistoryEnabled: Boolean = false,
    val bracketPairing: Boolean = false,
    val recentCommands: List<String> = emptyList(),
    val page: String = ToolbarPage.TERMINAL.name,
) {
    fun hiddenSet(): Set<String> = hidden.toSet()

    fun pageOrder(page: ToolbarPage): List<String> = order[page.name].orEmpty()

    fun extraCommands(): List<SavedCommand> = commands.mapNotNull {
        SavedCommandRules.normalize(it.label, it.text, it.tag, it.id)
    }

    fun withPage(next: ToolbarPage): ToolbarConfig = copy(page = next.name)

    fun hiding(id: String): ToolbarConfig {
        val next = if (id in hidden) hidden - id else hidden + id
        return copy(hidden = next)
    }

    fun movingEarlier(page: ToolbarPage, id: String): ToolbarConfig {
        val current = ToolbarCatalog.visible(page, hiddenSet(), pageOrder(page)).map { it.id.name }
        val index = current.indexOf(id)
        if (index <= 0) return this
        val swapped = current.toMutableList()
        val previous = swapped[index - 1]
        swapped[index - 1] = id
        swapped[index] = previous
        return copy(order = order + (page.name to swapped))
    }

    /** The user's order for a tools-panel tab (button names), empty for the default order. */
    fun tabOrder(tab: ToolsTab): List<String> = order[ToolsCatalog.orderKey(tab)].orEmpty()

    fun movingEarlier(tab: ToolsTab, id: ToolbarActionId): ToolbarConfig {
        val next = ToolsCatalog.movingEarlier(tab, hiddenSet(), tabOrder(tab), id)
        return copy(order = order + (ToolsCatalog.orderKey(tab) to next))
    }

    /** Hidden buttons shown again and every tab back in its default order. Saved commands and switches are kept. */
    fun withDefaultTools(): ToolbarConfig = copy(
        hidden = hidden.filter { name -> ToolsCatalog.actionIds().none { it.name == name } && !name.startsWith("cmd:") },
        order = order.filterKeys { key -> ToolsTab.entries.none { ToolsCatalog.orderKey(it) == key } },
    )
}

@Serializable
data class SavedCommandDto(val id: String = "", val label: String = "", val text: String = "", val tag: String = "")

object ToolbarConfigCodec {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    const val MAX_CHARS = 200_000

    fun encode(config: ToolbarConfig): String = json.encodeToString(ToolbarConfig.serializer(), config)

    fun decode(raw: String?): ToolbarConfig {
        if (raw.isNullOrBlank() || raw.length > MAX_CHARS) return ToolbarConfig()
        return try {
            json.decodeFromString(ToolbarConfig.serializer(), raw)
        } catch (_: Exception) {
            ToolbarConfig()
        }
    }
}
