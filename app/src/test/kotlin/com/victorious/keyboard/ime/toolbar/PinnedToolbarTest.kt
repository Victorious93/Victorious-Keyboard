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
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class PinnedToolbarTest : FunSpec({
    test("never edited: the defaults, and every default has an icon or glyph") {
        PinnedToolbar.sanitized(null) shouldBe PinnedToolbar.defaults
        for (id in PinnedToolbar.defaults) ToolsPanelItems.item(id) shouldNotBe null
        PinnedToolbar.defaults.distinct().size shouldBe PinnedToolbar.defaults.size
        (PinnedToolbar.defaults.size <= PinnedToolbar.MAX) shouldBe true
    }

    test("every default is something the tools panel offers, so a pinned button does what its tile does") {
        val offered = ToolsCatalog.actionIds().toSet()
        for (id in PinnedToolbar.defaults) (id in offered) shouldBe true
    }

    test("an edited list is kept as stored, an empty list stays empty") {
        PinnedToolbar.sanitized(listOf("PASTE", "COPY")) shouldBe listOf(ToolbarActionId.PASTE, ToolbarActionId.COPY)
        PinnedToolbar.sanitized(emptyList()) shouldBe emptyList()
    }

    test("unknown names, names the panel does not offer, and duplicates are dropped; order is kept") {
        val stored = listOf("PASTE", "NOPE", "PIPE", "PASTE", "", "COPY", "paste")
        // PIPE exists as an action id but is not a tools-panel button.
        PinnedToolbar.sanitized(stored) shouldBe listOf(ToolbarActionId.PASTE, ToolbarActionId.COPY)
    }

    test("a stored list longer than the cap is cut at the cap") {
        val all = ToolsCatalog.actionIds().map { it.name }
        (all.size > PinnedToolbar.MAX) shouldBe true
        PinnedToolbar.sanitized(all).size shouldBe PinnedToolbar.MAX
    }

    test("the Screenshot button is hidden while its setting is off, but stays pinned") {
        val stored = listOf("SCREENSHOT", "PASTE")
        PinnedToolbar.resolve(stored, screenshotShown = false) shouldBe listOf(ToolbarActionId.PASTE)
        PinnedToolbar.resolve(stored, screenshotShown = true) shouldBe
            listOf(ToolbarActionId.SCREENSHOT, ToolbarActionId.PASTE)
        // Editing while the tile is off must not drop the pin.
        PinnedToolbar.adding(stored, ToolbarActionId.COPY) shouldContain "SCREENSHOT"
        PinnedToolbar.candidates(stored, screenshotShown = false) shouldNotContain ToolbarActionId.SCREENSHOT
        PinnedToolbar.candidates(null, screenshotShown = false) shouldNotContain ToolbarActionId.SCREENSHOT
        PinnedToolbar.candidates(null, screenshotShown = true) shouldContain ToolbarActionId.SCREENSHOT
    }

    test("the row is drawn only when it is on and has a button") {
        PinnedToolbar.rowVisible(true, listOf(ToolbarActionId.PASTE)) shouldBe true
        PinnedToolbar.rowVisible(false, listOf(ToolbarActionId.PASTE)) shouldBe false
        PinnedToolbar.rowVisible(true, emptyList<String>()) shouldBe false
    }

    test("adding appends once, and ignores buttons the panel does not offer") {
        val one = PinnedToolbar.adding(listOf("PASTE"), ToolbarActionId.COPY)
        one shouldBe listOf("PASTE", "COPY")
        PinnedToolbar.adding(one, ToolbarActionId.COPY) shouldBe one
        PinnedToolbar.adding(one, ToolbarActionId.PIPE) shouldBe one
    }

    test("a layout with no saved pins stays empty, and the first pin is only that button") {
        PinnedToolbar.tokens(null) shouldBe emptyList()
        PinnedToolbar.adding(null, ToolbarActionId.CTRL_C) shouldBe listOf("CTRL_C")
        PinnedToolbar.removing(null, ToolbarActionId.PASTE) shouldBe emptyList()
    }

    test("adding to a full row changes nothing") {
        val full = ToolsCatalog.actionIds().take(PinnedToolbar.MAX).map { it.name }
        val extra = ToolsCatalog.actionIds().first { it.name !in full }
        PinnedToolbar.adding(full, extra) shouldBe full
    }

    test("removing a button that is not pinned changes nothing") {
        PinnedToolbar.removing(listOf("PASTE"), ToolbarActionId.COPY) shouldBe listOf("PASTE")
    }

    test("moving earlier and later swap neighbours and stop at the ends") {
        val row = listOf("PASTE", "COPY", "CUT")
        PinnedToolbar.movingEarlier(row, ToolbarActionId.CUT) shouldBe listOf("PASTE", "CUT", "COPY")
        PinnedToolbar.movingEarlier(row, ToolbarActionId.PASTE) shouldBe row
        PinnedToolbar.movingLater(row, ToolbarActionId.PASTE) shouldBe listOf("COPY", "PASTE", "CUT")
        PinnedToolbar.movingLater(row, ToolbarActionId.CUT) shouldBe row
        PinnedToolbar.movingEarlier(row, ToolbarActionId.UNDO) shouldBe row
        PinnedToolbar.movingLater(row, ToolbarActionId.UNDO) shouldBe row
    }

    test("candidates are what is not pinned, in the tools panel order") {
        val stored = listOf("PASTE")
        val candidates = PinnedToolbar.candidates(stored, screenshotShown = true)
        candidates shouldNotContain ToolbarActionId.PASTE
        candidates shouldBe ToolsCatalog.actionIds().filter { it != ToolbarActionId.PASTE }
    }

    test("ToolbarConfig: edits go through the same rules and keep the other settings") {
        val start = ToolbarConfig(bracketPairing = true, hidden = listOf("COPY"))
        val edited = start.pinning(ToolbarActionId.COPY).pinning(ToolbarActionId.CTRL_C).pinnedEarlier(ToolbarActionId.CTRL_C)
        edited.bracketPairing shouldBe true
        edited.hidden shouldBe listOf("COPY")
        edited.pinnedActions(true) shouldBe listOf(ToolbarActionId.CTRL_C, ToolbarActionId.COPY)
        edited.pinnedActions(true) shouldNotContain ToolbarActionId.EMOJI
        // Hiding a button in the tools panel does not unpin it.
        edited.unpinning(ToolbarActionId.EMOJI).pinnedActions(true) shouldContain ToolbarActionId.COPY
    }

    test("ToolbarConfig: restoring the defaults keeps the row switch") {
        val config = ToolbarConfig(pinned = listOf("PASTE"), pinnedRow = false).withDefaultPinned()
        config.pinned shouldBe null
        config.pinnedRow shouldBe false
        config.pinnedActions(true) shouldBe emptyList()
    }

    test("a toolbar file written before the pinned row existed reads as no pins, with the strip switch on") {
        val old = """{"hidden":["COPY"],"shellHistoryEnabled":true,"page":"CODE"}"""
        val config = ToolbarConfigCodec.decode(old)
        config.hidden shouldBe listOf("COPY")
        config.shellHistoryEnabled shouldBe true
        config.pinned shouldBe null
        config.pinnedRow shouldBe true
        config.pinnedActions(true) shouldBe emptyList()
        config.tileSize shouldBe ToolsLayout.FIT
        config.columns shouldBe ToolsLayout.COLUMNS_ADAPTIVE
        config.extraTiles shouldBe emptyList()
    }

    test("pinned buttons and the row switch survive a save and load") {
        val config = ToolbarConfig(pinned = listOf("PASTE", "UNDO"), pinnedRow = false)
        val back = ToolbarConfigCodec.decode(ToolbarConfigCodec.encode(config))
        back.pinned shouldBe listOf("PASTE", "UNDO")
        back.pinnedRow shouldBe false
        // An empty list is different from "never edited".
        ToolbarConfigCodec.decode(ToolbarConfigCodec.encode(ToolbarConfig(pinned = emptyList()))).pinned shouldBe emptyList()
    }

    test("a damaged file falls back to no pins instead of failing") {
        val config = ToolbarConfigCodec.decode("""{"pinned": "not a list""")
        config.pinnedActions(true) shouldBe emptyList()
        ToolbarConfigCodec.decode("""{"pinned": 5}""").pinnedActions(true) shouldBe emptyList()
    }

    test("slash commands and fleet actions pin, and they count toward the cap of four") {
        val slash = ToolRef.slash("sig")
        val fleet = ToolRef.fleet("com.example.app", "ask")
        val stored = PinnedToolbar.addingToken(PinnedToolbar.addingToken(null, slash), fleet)
        stored shouldBe listOf(slash, fleet)
        PinnedToolbar.resolveTokens(stored, screenshotShown = true) shouldBe stored
        val full = listOf("PASTE", "COPY", "CUT", "UNDO")
        PinnedToolbar.addingToken(full, slash) shouldBe full
        PinnedToolbar.addingToken(listOf(slash, slash, "NOPE"), ToolbarActionId.PASTE.name) shouldBe listOf(slash, "PASTE")
    }
})
