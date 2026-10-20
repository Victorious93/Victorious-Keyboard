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

import com.victorious.keyboard.ime.toolbar.ui.ToolsPanelItems
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class ToolsCatalogTest : FunSpec({
    test("tab names are persisted: this list may only grow at the end") {
        ToolsTab.entries.map { it.name } shouldBe listOf("CLIPBOARD", "AI", "TERMINAL", "CODE", "QUICK")
    }

    test("every tab has buttons and no button is on two tabs") {
        for (tab in ToolsTab.entries) {
            ToolsCatalog.actions(tab).isNotEmpty() shouldBe true
        }
        val ids = ToolsCatalog.actionIds()
        ids.groupBy { it }.filterValues { it.size > 1 }.keys.shouldBeEmpty()
        for (id in ids) ToolsCatalog.tabOf(id) shouldNotBe null
    }

    test("every button has an icon or glyph and a translatable label") {
        for (id in ToolsCatalog.actionIds()) {
            ToolsPanelItems.item(id) shouldNotBe null
        }
        for (tab in ToolsTab.entries) {
            ToolsPanelItems.tabLabel(tab) shouldNotBe 0
        }
    }

    test("the snippet picker, clip editor and vault are reachable from the Clipboard tab") {
        val clipboard = ToolsCatalog.actions(ToolsTab.CLIPBOARD)
        for (id in listOf(ToolbarActionId.OPEN_SNIPPETS, ToolbarActionId.EDIT_CLIP, ToolbarActionId.OPEN_VAULT)) {
            (id in clipboard) shouldBe true
        }
    }

    test("an unknown or missing tab name falls back to Clipboard") {
        ToolsTab.fromName("AI") shouldBe ToolsTab.AI
        ToolsTab.fromName("PRIVACY") shouldBe ToolsTab.CLIPBOARD
        ToolsTab.fromName("") shouldBe ToolsTab.CLIPBOARD
        ToolsTab.fromName(null) shouldBe ToolsTab.CLIPBOARD
    }

    test("hidden buttons are left out; the default order applies without a stored order") {
        val visible = ToolsCatalog.visible(ToolsTab.AI, setOf("AI_SHELL"), emptyList())
        visible shouldNotContain ToolbarActionId.AI_SHELL
        visible shouldBe ToolsCatalog.actions(ToolsTab.AI) - ToolbarActionId.AI_SHELL
    }

    test("a stored order comes first; unknown, foreign and repeated names are ignored") {
        val order = listOf("AI_SHELL", "NOT_A_BUTTON", "CTRL", "AI_SHELL", "AI_ASK")
        val visible = ToolsCatalog.visible(ToolsTab.AI, emptySet(), order)
        visible.take(2) shouldBe listOf(ToolbarActionId.AI_SHELL, ToolbarActionId.AI_ASK)
        visible.toSet() shouldBe ToolsCatalog.actions(ToolsTab.AI).toSet()
        visible.size shouldBe ToolsCatalog.actions(ToolsTab.AI).size
    }

    test("moving a button earlier swaps it with its visible neighbour; the first one stays") {
        val tab = ToolsTab.CODE
        val first = ToolsCatalog.actions(tab)[0]
        val second = ToolsCatalog.actions(tab)[1]
        val moved = ToolsCatalog.movingEarlier(tab, emptySet(), emptyList(), second)
        moved.take(2) shouldBe listOf(second.name, first.name)
        ToolsCatalog.movingEarlier(tab, emptySet(), emptyList(), first).first() shouldBe first.name
    }

    test("moving a hidden button changes nothing") {
        val tab = ToolsTab.CODE
        val hiddenId = ToolsCatalog.actions(tab)[2]
        val before = ToolsCatalog.visible(tab, setOf(hiddenId.name), emptyList()).map { it.name }
        ToolsCatalog.movingEarlier(tab, setOf(hiddenId.name), emptyList(), hiddenId) shouldBe before
    }

    test("tab orders are stored apart from the old strip pages and survive the file codec") {
        val config = ToolbarConfig().movingEarlier(ToolsTab.TERMINAL, ToolbarActionId.ALT)
        config.order.keys shouldBe setOf("tools:TERMINAL")
        config.pageOrder(ToolbarPage.TERMINAL) shouldBe emptyList()
        val decoded = ToolbarConfigCodec.decode(ToolbarConfigCodec.encode(config))
        decoded.tabOrder(ToolsTab.TERMINAL).take(2) shouldBe listOf("ALT", "CTRL")
    }

    test("restoring the default tools keeps saved commands, switches and old page data") {
        val config = ToolbarConfig(
            hidden = listOf("AI_ASK", "cmd:c1", "PIPE"),
            order = mapOf("tools:AI" to listOf("AI_SHELL"), "TERMINAL" to listOf("PIPE")),
            commands = listOf(SavedCommandDto("c1", "status", "git status", "git")),
            bracketPairing = true,
        )
        val reset = config.withDefaultTools()
        reset.hidden shouldBe listOf("PIPE")
        reset.order shouldBe mapOf("TERMINAL" to listOf("PIPE"))
        reset.commands shouldBe config.commands
        reset.bracketPairing shouldBe true
    }
})
