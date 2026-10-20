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

/**
 * The tabs of the tools panel that replaces the key area. Names are persisted (the last tab, and per-tab order in the
 * toolbar file): never rename or reorder them; only add.
 */
enum class ToolsTab {
    CLIPBOARD,
    AI,
    TERMINAL,
    CODE,
    QUICK;

    companion object {
        fun fromName(name: String?): ToolsTab = entries.firstOrNull { it.name == name } ?: CLIPBOARD
    }
}

/**
 * Which buttons each tab of the tools panel holds. Pure; the Android shell supplies icons and translated labels.
 * Command seeds such as nmap are not tiles; terminal keys send key events, and shell text inserted elsewhere is never
 * executed. The user's own saved commands are listed separately on the Terminal tab ([ToolbarConfig.extraCommands]).
 */
object ToolsCatalog {
    val tabs: Map<ToolsTab, List<ToolbarActionId>> = linkedMapOf(
        ToolsTab.CLIPBOARD to listOf(
            ToolbarActionId.OPEN_CLIPBOARD,
            ToolbarActionId.PASTE,
            ToolbarActionId.COPY,
            ToolbarActionId.CUT,
            ToolbarActionId.SEL_ALL,
            ToolbarActionId.UNDO,
            ToolbarActionId.REDO,
            ToolbarActionId.OPEN_SNIPPETS,
            ToolbarActionId.EDIT_CLIP,
            ToolbarActionId.PINNED,
            ToolbarActionId.OPEN_VAULT,
            ToolbarActionId.ZIP,
        ),
        ToolsTab.AI to listOf(
            ToolbarActionId.AI_ASK,
            ToolbarActionId.AI_REWRITE,
            ToolbarActionId.AI_GRAMMAR,
            ToolbarActionId.AI_CONTINUE,
            ToolbarActionId.AI_SUMMARIZE,
            ToolbarActionId.AI_TRANSLATE,
            ToolbarActionId.AI_SHELL,
        ),
        ToolsTab.TERMINAL to listOf(
            ToolbarActionId.CTRL,
            ToolbarActionId.ALT,
            ToolbarActionId.ESC,
            ToolbarActionId.TAB,
            ToolbarActionId.LEFT,
            ToolbarActionId.DOWN,
            ToolbarActionId.UP,
            ToolbarActionId.RIGHT,
            ToolbarActionId.HOME,
            ToolbarActionId.END,
            ToolbarActionId.PGUP,
            ToolbarActionId.PGDN,
            ToolbarActionId.CTRL_C,
            ToolbarActionId.CTRL_D,
            ToolbarActionId.CTRL_Z,
            ToolbarActionId.CTRL_L,
            ToolbarActionId.CTRL_R,
            ToolbarActionId.DEVROW,
        ),
        ToolsTab.CODE to listOf(
            ToolbarActionId.TEXT_TOOLS,
            ToolbarActionId.CAMEL,
            ToolbarActionId.SNAKE,
            ToolbarActionId.KEBAB,
            ToolbarActionId.PASCAL,
            ToolbarActionId.INDENT,
            ToolbarActionId.OUTDENT,
            ToolbarActionId.COMMENT,
            ToolbarActionId.JSON,
            ToolbarActionId.B64E,
            ToolbarActionId.B64D,
            ToolbarActionId.UUID,
            ToolbarActionId.TIME,
        ),
        ToolsTab.QUICK to listOf(
            ToolbarActionId.INCOGNITO,
            ToolbarActionId.LEARNING,
            ToolbarActionId.EMOJI,
            ToolbarActionId.VOICE,
            ToolbarActionId.ONE_HAND,
            ToolbarActionId.BOTTOM_GAP,
            ToolbarActionId.THEME,
            ToolbarActionId.SETTINGS,
            ToolbarActionId.SCREENSHOT,
            ToolbarActionId.MEDIA,
        ),
    )

    /** The strip shows suggestions, not these tabs. */
    val pageTabsOnStrip: Boolean = false

    fun actions(tab: ToolsTab): List<ToolbarActionId> = tabs.getValue(tab)

    fun tabOf(id: ToolbarActionId): ToolsTab? = tabs.entries.firstOrNull { id in it.value }?.key

    /** Key of a tab's custom order in [ToolbarConfig.order]; distinct from the old strip page names. */
    fun orderKey(tab: ToolsTab): String = "tools:${tab.name}"

    /**
     * The buttons a tab shows: the user's [order] first (unknown or foreign names are ignored), then the rest in the
     * default order, without the [hidden] ones.
     */
    fun visible(tab: ToolsTab, hidden: Set<String>, order: List<String>): List<ToolbarActionId> {
        val all = actions(tab).filter { it.name !in hidden }
        if (order.isEmpty()) return all
        val byName = all.associateBy { it.name }
        val ordered = order.distinct().mapNotNull { byName[it] }
        return ordered + all.filter { it !in ordered }
    }

    /** [id] moved one place earlier among the visible buttons of its tab; the new order to store. */
    fun movingEarlier(tab: ToolsTab, hidden: Set<String>, order: List<String>, id: ToolbarActionId): List<String> =
        moving(tab, hidden, order, id, -1)

    /** [id] moved one place later among the visible buttons of its tab; the new order to store. */
    fun movingLater(tab: ToolsTab, hidden: Set<String>, order: List<String>, id: ToolbarActionId): List<String> =
        moving(tab, hidden, order, id, 1)

    /** [id] moved by [delta] places (−1 earlier, +1 later), or to an absolute index when [to] is set. */
    fun moving(
        tab: ToolsTab,
        hidden: Set<String>,
        order: List<String>,
        id: ToolbarActionId,
        delta: Int = 0,
        to: Int? = null,
    ): List<String> {
        val current = visible(tab, hidden, order).map { it.name }
        val from = current.indexOf(id.name)
        if (from < 0) return current
        val destination = to ?: (from + delta)
        return ToolsLayout.move(current, from, destination)
    }

    fun label(id: ToolbarActionId): String =
        ToolbarCatalog.actions.firstOrNull { it.id == id }?.label ?: id.name

    fun actionIds(): List<ToolbarActionId> = tabs.values.flatten()
}
