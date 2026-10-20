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
 * The stored list holds [ToolbarActionId] names and [ToolRef] extras (`slash:` and `fleet:`). `null` and an empty
 * list both mean nothing is pinned, which leaves the suggestion strip as it is. Unknown names, duplicates, and
 * anything past [MAX] are dropped when the list is read.
 */
object PinnedToolbar {
    const val MAX = 4

    /** Nothing pinned. The strip stays suggestions plus the tools button. */
    val defaults: List<ToolbarActionId> = emptyList()

    private val offered: Set<ToolbarActionId> by lazy { ToolsCatalog.actionIds().toSet() }

    private fun acceptable(raw: String): Boolean {
        if (offered.any { it.name == raw }) return true
        return ToolRef.isExtra(raw)
    }

    /**
     * Stored names cleaned up, in order. Built-in buttons and extra refs both count toward [MAX].
     * `null` is the unsaved layout: no pins.
     */
    fun tokens(stored: List<String>?): List<String> {
        if (stored == null) return emptyList()
        val out = LinkedHashSet<String>()
        for (raw in stored) {
            if (!acceptable(raw) || raw in out) continue
            out.add(raw)
            if (out.size == MAX) break
        }
        return out.toList()
    }

    /** Built-in buttons only, without the screenshot rule. */
    fun sanitized(stored: List<String>?): List<ToolbarActionId> {
        val byName = offered.associateBy { it.name }
        return tokens(stored).mapNotNull { byName[it] }
    }

    /** What the strip draws. Screenshot is left out while its setting is off, without unpinning it. */
    fun resolveTokens(stored: List<String>?, screenshotShown: Boolean): List<String> {
        val byName = offered.associateBy { it.name }
        return tokens(stored).filter { raw ->
            val id = byName[raw]
            screenshotShown || id != ToolbarActionId.SCREENSHOT
        }
    }

    /** What the strip draws, built-in buttons only. */
    fun resolve(stored: List<String>?, screenshotShown: Boolean): List<ToolbarActionId> {
        val byName = offered.associateBy { it.name }
        return resolveTokens(stored, screenshotShown).mapNotNull { byName[it] }
    }

    fun rowVisible(enabled: Boolean, resolved: List<*>): Boolean = enabled && resolved.isNotEmpty()

    /** Buttons that can still be pinned, grouped like the tools panel and in its order. */
    fun candidates(stored: List<String>?, screenshotShown: Boolean): List<ToolbarActionId> {
        val pinned = sanitized(stored).toSet()
        return ToolsCatalog.actionIds().filter {
            it !in pinned && (screenshotShown || it != ToolbarActionId.SCREENSHOT)
        }
    }

    /** [token] appended at the end. Unchanged when it is already pinned, is not acceptable, or the strip is full. */
    fun addingToken(stored: List<String>?, token: String): List<String> {
        val current = tokens(stored)
        if (!acceptable(token) || token in current || current.size >= MAX) return current
        return current + token
    }

    fun adding(stored: List<String>?, id: ToolbarActionId): List<String> = addingToken(stored, id.name)

    fun removingToken(stored: List<String>?, token: String): List<String> =
        tokens(stored).filter { it != token }

    fun removing(stored: List<String>?, id: ToolbarActionId): List<String> = removingToken(stored, id.name)

    fun movingEarlierToken(stored: List<String>?, token: String): List<String> {
        val current = tokens(stored).toMutableList()
        val index = current.indexOf(token)
        if (index > 0) {
            current[index] = current[index - 1]
            current[index - 1] = token
        }
        return current
    }

    fun movingLaterToken(stored: List<String>?, token: String): List<String> {
        val current = tokens(stored).toMutableList()
        val index = current.indexOf(token)
        if (index >= 0 && index < current.lastIndex) {
            current[index] = current[index + 1]
            current[index + 1] = token
        }
        return current
    }

    fun movingEarlier(stored: List<String>?, id: ToolbarActionId): List<String> = movingEarlierToken(stored, id.name)

    fun movingLater(stored: List<String>?, id: ToolbarActionId): List<String> = movingLaterToken(stored, id.name)
}
