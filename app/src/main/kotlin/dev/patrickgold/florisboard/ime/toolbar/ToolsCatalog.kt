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

/**
 * The tools panel that replaces the key area. The suggestion strip does not show these groups
 * or the old page tabs. Command seeds such as nmap are not tiles; terminal keys send key events,
 * and shell text inserted elsewhere is never executed.
 */
data class ToolsGroup(val title: String, val actions: List<ToolbarActionId>)

object ToolsCatalog {
    val groups: List<ToolsGroup> = listOf(
        ToolsGroup(
            "Clipboard",
            listOf(
                ToolbarActionId.OPEN_CLIPBOARD,
                ToolbarActionId.PASTE,
                ToolbarActionId.COPY,
                ToolbarActionId.CUT,
                ToolbarActionId.PINNED,
                ToolbarActionId.ZIP,
            ),
        ),
        ToolsGroup("Clip editor", listOf(ToolbarActionId.EDIT_CLIP)),
        ToolsGroup("Vault", listOf(ToolbarActionId.OPEN_VAULT)),
        ToolsGroup(
            "AI",
            listOf(
                ToolbarActionId.AI_REWRITE,
                ToolbarActionId.AI_GRAMMAR,
                ToolbarActionId.AI_CONTINUE,
                ToolbarActionId.AI_SUMMARIZE,
                ToolbarActionId.AI_TRANSLATE,
                ToolbarActionId.AI_SHELL,
                ToolbarActionId.AI_ASK,
            ),
        ),
        ToolsGroup(
            "Terminal keys",
            listOf(
                ToolbarActionId.CTRL,
                ToolbarActionId.ALT,
                ToolbarActionId.ESC,
                ToolbarActionId.TAB,
                ToolbarActionId.LEFT,
                ToolbarActionId.RIGHT,
                ToolbarActionId.UP,
                ToolbarActionId.DOWN,
                ToolbarActionId.HOME,
                ToolbarActionId.END,
                ToolbarActionId.PGUP,
                ToolbarActionId.PGDN,
                ToolbarActionId.CTRL_C,
                ToolbarActionId.CTRL_D,
                ToolbarActionId.CTRL_Z,
                ToolbarActionId.CTRL_L,
                ToolbarActionId.CTRL_R,
            ),
        ),
        ToolsGroup(
            "Code",
            listOf(
                ToolbarActionId.CAMEL,
                ToolbarActionId.SNAKE,
                ToolbarActionId.KEBAB,
                ToolbarActionId.PASCAL,
                ToolbarActionId.INDENT,
                ToolbarActionId.OUTDENT,
                ToolbarActionId.COMMENT,
                ToolbarActionId.B64E,
                ToolbarActionId.B64D,
                ToolbarActionId.JSON,
                ToolbarActionId.UUID,
                ToolbarActionId.TIME,
            ),
        ),
        ToolsGroup(
            "Privacy",
            listOf(
                ToolbarActionId.INCOGNITO,
                ToolbarActionId.LEARNING,
                ToolbarActionId.BOTTOM_GAP,
                ToolbarActionId.ONE_HAND,
                ToolbarActionId.THEME,
                ToolbarActionId.VOICE,
                ToolbarActionId.EMOJI,
                ToolbarActionId.SETTINGS,
            ),
        ),
    )

    /** The strip shows suggestions, not these page titles. */
    val pageTabsOnStrip: Boolean = false

    fun label(id: ToolbarActionId): String =
        ToolbarCatalog.actions.firstOrNull { it.id == id }?.label ?: id.name

    fun actionIds(): List<ToolbarActionId> = groups.flatMap { it.actions }
}
