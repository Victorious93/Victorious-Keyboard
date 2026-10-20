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

package com.victorious.keyboard.ime.toolbar

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
    /** Pins on the suggestion strip. `null` and an empty list both mean nothing is pinned. */
    val pinned: List<String>? = null,
    /** Whether pinned icons are drawn on the suggestion strip. */
    val pinnedRow: Boolean = true,
    /** [ToolsLayout.FIT], [ToolsLayout.SMALL], [ToolsLayout.MEDIUM], or [ToolsLayout.LARGE]. */
    val tileSize: String = ToolsLayout.FIT,
    /** [ToolsLayout.COLUMNS_ADAPTIVE] or a fixed column count. */
    val columns: Int = ToolsLayout.COLUMNS_ADAPTIVE,
    /** Slash and fleet refs shown as extra tiles on the Quick tab. */
    val extraTiles: List<String> = emptyList(),
    /** Fleet actions last seen by the keyboard, so Settings can offer them. Labels only. */
    val knownFleet: List<KnownTool> = emptyList(),
) {
    fun pinnedActions(screenshotShown: Boolean): List<ToolbarActionId> =
        PinnedToolbar.resolve(pinned, screenshotShown)

    fun stripPins(screenshotShown: Boolean): List<String> =
        PinnedToolbar.resolveTokens(pinned, screenshotShown)

    fun pinning(id: ToolbarActionId): ToolbarConfig = pinningRef(id.name)

    fun pinningRef(ref: String): ToolbarConfig = copy(pinned = PinnedToolbar.addingToken(pinned, ref))

    fun unpinning(id: ToolbarActionId): ToolbarConfig = unpinningRef(id.name)

    fun unpinningRef(ref: String): ToolbarConfig = copy(pinned = PinnedToolbar.removingToken(pinned, ref))

    fun pinnedEarlier(id: ToolbarActionId): ToolbarConfig = pinnedEarlierRef(id.name)

    fun pinnedEarlierRef(ref: String): ToolbarConfig = copy(pinned = PinnedToolbar.movingEarlierToken(pinned, ref))

    fun pinnedLater(id: ToolbarActionId): ToolbarConfig = pinnedLaterRef(id.name)

    fun pinnedLaterRef(ref: String): ToolbarConfig = copy(pinned = PinnedToolbar.movingLaterToken(pinned, ref))

    /** No pins. The strip switch is left as the user set it. */
    fun withDefaultPinned(): ToolbarConfig = copy(pinned = null)

    fun withTileSize(name: String): ToolbarConfig = copy(tileSize = ToolsLayout.size(name))

    fun withColumns(count: Int): ToolbarConfig = copy(columns = ToolsLayout.columns(count))

    fun showingExtra(ref: String): ToolbarConfig {
        if (!ToolRef.isExtra(ref)) return this
        val next = if (ref in extraTiles) extraTiles else extraTiles + ref
        return copy(extraTiles = next, hidden = hidden - ref)
    }

    fun movingExtra(from: Int, to: Int): ToolbarConfig =
        copy(extraTiles = ToolsLayout.move(ToolsLayout.visibleExtras(extraTiles, hiddenSet()), from, to))

    /**
     * Remembers fleet action labels so Settings can offer them. An empty snapshot keeps the last list.
     * Labels only; command and snippet text are not stored.
     */
    fun notingFleet(tools: List<KnownTool>): ToolbarConfig {
        val clean = KnownTools.clean(tools)
        if (clean.isEmpty() || clean == knownFleet) return this
        return copy(knownFleet = clean)
    }

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

    fun movingEarlier(tab: ToolsTab, id: ToolbarActionId): ToolbarConfig = moving(tab, id, -1)

    fun movingLater(tab: ToolsTab, id: ToolbarActionId): ToolbarConfig = moving(tab, id, 1)

    fun moving(tab: ToolsTab, id: ToolbarActionId, delta: Int = 0, to: Int? = null): ToolbarConfig {
        val next = ToolsCatalog.moving(tab, hiddenSet(), tabOrder(tab), id, delta, to)
        return copy(order = order + (ToolsCatalog.orderKey(tab) to next))
    }

    /** Hidden buttons shown again and every tab back in its default order. Saved commands and switches are kept. */
    fun withDefaultTools(): ToolbarConfig = copy(
        hidden = hidden.filter { name -> ToolsCatalog.actionIds().none { it.name == name } && !name.startsWith("cmd:") },
        order = order.filterKeys { key -> ToolsTab.entries.none { ToolsCatalog.orderKey(it) == key } },
    )

    /**
     * Tiles, order, pins, size, and columns back to the built-in layout. Saved commands, shell history, and the
     * bracket switch stay.
     */
    fun resetLayout(): ToolbarConfig {
        val cleared = withDefaultTools()
        return cleared.copy(
            pinned = null,
            tileSize = ToolsLayout.FIT,
            columns = ToolsLayout.COLUMNS_ADAPTIVE,
            extraTiles = emptyList(),
            hidden = cleared.hidden.filter { !ToolRef.isExtra(it) },
        )
    }
}

@Serializable
data class KnownTool(val ref: String = "", val label: String = "")

object KnownTools {
    const val MAX = 32
    const val MAX_LABEL = 40

    fun clean(tools: List<KnownTool>): List<KnownTool> {
        val out = ArrayList<KnownTool>()
        for (tool in tools) {
            if (!ToolRef.isFleet(tool.ref) || out.any { it.ref == tool.ref }) continue
            val fallback = tool.ref.substringAfterLast(':')
            val label = tool.label.trim().take(MAX_LABEL).ifEmpty { fallback }
            out.add(KnownTool(tool.ref, label))
            if (out.size == MAX) break
        }
        return out
    }
}

/**
 * The toolbar layout inside a clipboard export. Shell history is not included. An invalid document changes nothing.
 */
object ToolbarLayoutTransfer {
    fun exportDocument(config: ToolbarConfig): String? {
        val layout = config.copy(recentCommands = emptyList(), knownFleet = emptyList())
        if (isUntouched(layout)) return null
        return ToolbarConfigCodec.encode(layout)
    }

    fun isUntouched(config: ToolbarConfig): Boolean =
        config.hidden.isEmpty() &&
            config.order.isEmpty() &&
            config.commands.isEmpty() &&
            !config.shellHistoryEnabled &&
            !config.bracketPairing &&
            config.pinned == null &&
            config.pinnedRow &&
            config.tileSize == ToolsLayout.FIT &&
            config.columns == ToolsLayout.COLUMNS_ADAPTIVE &&
            config.extraTiles.isEmpty() &&
            config.knownFleet.isEmpty() &&
            config.recentCommands.isEmpty() &&
            config.page == ToolbarPage.TERMINAL.name

    fun apply(current: ToolbarConfig, document: String, replace: Boolean): ToolbarConfig {
        val incoming = ToolbarConfigCodec.decodeOrNull(document) ?: return current
        val keptHistory = current.recentCommands
        if (replace) return incoming.copy(recentCommands = keptHistory)
        return current.copy(
            hidden = (current.hidden + incoming.hidden).distinct(),
            order = current.order + incoming.order.filterKeys { it !in current.order },
            commands = if (current.commands.isEmpty()) incoming.commands else current.commands,
            pinned = current.pinned ?: incoming.pinned,
            pinnedRow = current.pinnedRow && incoming.pinnedRow,
            tileSize = if (current.tileSize == ToolsLayout.FIT) ToolsLayout.size(incoming.tileSize) else current.tileSize,
            columns = if (current.columns == 0) ToolsLayout.columns(incoming.columns) else current.columns,
            extraTiles = (current.extraTiles + incoming.extraTiles.filter { ToolRef.isExtra(it) }).distinct(),
            knownFleet = if (current.knownFleet.isEmpty()) KnownTools.clean(incoming.knownFleet) else current.knownFleet,
            recentCommands = keptHistory,
        )
    }
}

@Serializable
data class SavedCommandDto(val id: String = "", val label: String = "", val text: String = "", val tag: String = "")

object ToolbarConfigCodec {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    const val MAX_CHARS = 200_000

    fun encode(config: ToolbarConfig): String = json.encodeToString(ToolbarConfig.serializer(), config)

    fun decode(raw: String?): ToolbarConfig = decodeOrNull(raw) ?: ToolbarConfig()

    /** Null when the text is missing, too large, or not a toolbar layout. */
    fun decodeOrNull(raw: String?): ToolbarConfig? {
        if (raw.isNullOrBlank() || raw.length > MAX_CHARS) return null
        return try {
            json.decodeFromString(ToolbarConfig.serializer(), raw)
        } catch (_: Exception) {
            null
        }
    }
}
