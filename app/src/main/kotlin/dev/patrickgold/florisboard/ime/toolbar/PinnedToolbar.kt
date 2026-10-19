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
 * The buttons the user pinned to the toolbar row above the suggestion strip. Pure; the Android shell draws them and
 * runs them through the same path as the tools panel, so a pinned button does exactly what its tile does.
 *
 * The stored list holds [ToolbarActionId] names. `null` means "never edited": the row shows [defaults]. Once the user
 * edits, the list is stored as it is, so an empty list means an empty row (and the row is not drawn). Unknown names,
 * names the tools panel does not offer, duplicates, and anything past [MAX] are dropped when the list is read.
 */
object PinnedToolbar {
    const val MAX = 24

    val defaults: List<ToolbarActionId> = listOf(
        ToolbarActionId.OPEN_CLIPBOARD,
        ToolbarActionId.OPEN_SNIPPETS,
        ToolbarActionId.SEL_ALL,
        ToolbarActionId.COPY,
        ToolbarActionId.PASTE,
        ToolbarActionId.UNDO,
        ToolbarActionId.REDO,
        ToolbarActionId.EMOJI,
    )

    private val offered: Set<ToolbarActionId> by lazy { ToolsCatalog.actionIds().toSet() }

    /** The stored names cleaned up, in order, without the screenshot rule (that is a display decision). */
    fun sanitized(stored: List<String>?): List<ToolbarActionId> {
        if (stored == null) return defaults
        val byName = offered.associateBy { it.name }
        return stored.mapNotNull { byName[it] }.distinct().take(MAX)
    }

    /** What the row draws. The Screenshot button is left out while its setting is off, without unpinning it. */
    fun resolve(stored: List<String>?, screenshotShown: Boolean): List<ToolbarActionId> =
        sanitized(stored).filter { screenshotShown || it != ToolbarActionId.SCREENSHOT }

    fun rowVisible(enabled: Boolean, resolved: List<ToolbarActionId>): Boolean = enabled && resolved.isNotEmpty()

    /** Buttons that can still be pinned, grouped like the tools panel and in its order. */
    fun candidates(stored: List<String>?, screenshotShown: Boolean): List<ToolbarActionId> {
        val pinned = sanitized(stored).toSet()
        return ToolsCatalog.actionIds().filter {
            it !in pinned && (screenshotShown || it != ToolbarActionId.SCREENSHOT)
        }
    }

    /** [id] appended at the end. Unchanged when it is already pinned, is not offered, or the row is full. */
    fun adding(stored: List<String>?, id: ToolbarActionId): List<String> {
        val current = sanitized(stored)
        if (id !in offered || id in current || current.size >= MAX) return current.map { it.name }
        return (current + id).map { it.name }
    }

    fun removing(stored: List<String>?, id: ToolbarActionId): List<String> =
        sanitized(stored).filter { it != id }.map { it.name }

    fun movingEarlier(stored: List<String>?, id: ToolbarActionId): List<String> {
        val current = sanitized(stored).toMutableList()
        val index = current.indexOf(id)
        if (index > 0) {
            current[index] = current[index - 1]
            current[index - 1] = id
        }
        return current.map { it.name }
    }

    fun movingLater(stored: List<String>?, id: ToolbarActionId): List<String> {
        val current = sanitized(stored).toMutableList()
        val index = current.indexOf(id)
        if (index >= 0 && index < current.lastIndex) {
            current[index] = current[index + 1]
            current[index + 1] = id
        }
        return current.map { it.name }
    }
}
