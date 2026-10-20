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

import dev.patrickgold.florisboard.ime.clipboard.backup.BackupReader
import dev.patrickgold.florisboard.ime.clipboard.backup.BackupWriter
import dev.patrickgold.florisboard.ime.clipboard.backup.ExportMeta
import dev.patrickgold.florisboard.ime.clipboard.history.entry
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain

class ToolbarLayoutTest : FunSpec({
    test("size and columns outside the allowed set fall back to today's grid") {
        ToolsLayout.size("HUGE") shouldBe ToolsLayout.FIT
        ToolsLayout.size(null) shouldBe ToolsLayout.FIT
        ToolsLayout.columns(-1) shouldBe ToolsLayout.COLUMNS_ADAPTIVE
        ToolsLayout.columns(1) shouldBe ToolsLayout.COLUMNS_MIN
        ToolsLayout.columns(9) shouldBe ToolsLayout.COLUMNS_MAX
        ToolsLayout.heightDp(ToolsLayout.FIT) shouldBe null
        ToolsLayout.heightDp(ToolsLayout.SMALL) shouldBe 52f
        ToolsLayout.heightDp(ToolsLayout.MEDIUM) shouldBe 72f
        ToolsLayout.heightDp(ToolsLayout.LARGE) shouldBe 88f
    }

    test("drag reorder moves an item and ignores a bad index") {
        ToolsLayout.move(listOf("a", "b", "c"), 0, 2) shouldBe listOf("b", "c", "a")
        ToolsLayout.move(listOf("a", "b", "c"), 2, 0) shouldBe listOf("c", "a", "b")
        val same = listOf("a", "b")
        ToolsLayout.move(same, -1, 0) shouldBe same
        ToolsLayout.move(same, 0, 4) shouldBe same
        ToolsLayout.move(same, 1, 1) shouldBe same
    }

    test("no saved layout keeps today's tiles, no pins, and the fitting grid") {
        val config = ToolbarConfigCodec.decode(null)
        config.pinned shouldBe null
        config.stripPins(screenshotShown = true) shouldBe emptyList()
        config.tileSize shouldBe ToolsLayout.FIT
        config.columns shouldBe ToolsLayout.COLUMNS_ADAPTIVE
        config.extraTiles shouldBe emptyList()
        ToolsCatalog.visible(ToolsTab.QUICK, emptySet(), emptyList()) shouldBe ToolsCatalog.actions(ToolsTab.QUICK)
        ToolbarLayoutTransfer.exportDocument(config) shouldBe null
    }

    test("hidden tiles are left out, and earlier, later, and drag keep the rest") {
        val hidden = ToolbarConfig().hiding(ToolbarActionId.PASTE.name)
        val shown = ToolsCatalog.visible(ToolsTab.CLIPBOARD, hidden.hiddenSet(), hidden.tabOrder(ToolsTab.CLIPBOARD))
        shown shouldNotContain ToolbarActionId.PASTE
        val later = hidden.movingLater(ToolsTab.CLIPBOARD, shown.first())
        val afterLater = ToolsCatalog.visible(ToolsTab.CLIPBOARD, later.hiddenSet(), later.tabOrder(ToolsTab.CLIPBOARD))
        afterLater.first() shouldBe shown[1]
        afterLater[1] shouldBe shown.first()
        val moved = later.moving(ToolsTab.CLIPBOARD, ToolbarActionId.CUT, to = 0)
        val names = ToolsCatalog.visible(ToolsTab.CLIPBOARD, moved.hiddenSet(), moved.tabOrder(ToolsTab.CLIPBOARD))
        names shouldNotContain ToolbarActionId.PASTE
        names.first() shouldBe ToolbarActionId.CUT
        val back = ToolbarConfigCodec.decode(ToolbarConfigCodec.encode(moved))
        back.hidden shouldContain ToolbarActionId.PASTE.name
        ToolsCatalog.visible(ToolsTab.CLIPBOARD, back.hiddenSet(), back.tabOrder(ToolsTab.CLIPBOARD)) shouldBe names
    }

    test("slash and fleet refs are tiles and pins, and a bad ref is not") {
        val slash = ToolRef.slash("sig")
        val fleet = ToolRef.fleet("com.example.app", "status")
        val config = ToolbarConfig()
            .showingExtra(slash)
            .showingExtra(fleet)
            .showingExtra("notepad")
            .pinningRef(slash)
            .withTileSize("LARGE")
            .withColumns(3)
        ToolsLayout.visibleExtras(config.extraTiles, config.hiddenSet()) shouldBe listOf(slash, fleet)
        config.stripPins(true) shouldBe listOf(slash)
        config.tileSize shouldBe ToolsLayout.LARGE
        config.columns shouldBe 3
        val hidden = config.hiding(fleet)
        ToolsLayout.visibleExtras(hidden.extraTiles, hidden.hiddenSet()) shouldBe listOf(slash)
        val shifted = config.movingExtra(0, 1)
        shifted.extraTiles shouldBe listOf(fleet, slash)
        val back = ToolbarConfigCodec.decode(ToolbarConfigCodec.encode(config))
        back.extraTiles shouldBe listOf(slash, fleet)
        back.pinned shouldBe listOf(slash)
        back.columns shouldBe 3
    }

    test("reset restores tiles, pins, size, and columns, and keeps commands and history") {
        val slash = ToolRef.slash("sig")
        val start = ToolbarConfig(
            hidden = listOf(ToolbarActionId.COPY.name, slash),
            order = mapOf(ToolsCatalog.orderKey(ToolsTab.QUICK) to listOf("EMOJI")),
            commands = listOf(SavedCommandDto("c1", "Ping", "ping", "net")),
            shellHistoryEnabled = true,
            recentCommands = listOf("ls"),
            pinned = listOf("PASTE"),
            tileSize = ToolsLayout.LARGE,
            columns = 2,
            extraTiles = listOf(slash),
            knownFleet = listOf(KnownTool(ToolRef.fleet("com.example.app", "ask"), "Ask")),
        )
        val reset = start.resetLayout()
        reset.hidden shouldBe emptyList()
        reset.order shouldBe emptyMap()
        reset.pinned shouldBe null
        reset.tileSize shouldBe ToolsLayout.FIT
        reset.columns shouldBe ToolsLayout.COLUMNS_ADAPTIVE
        reset.extraTiles shouldBe emptyList()
        reset.commands shouldBe start.commands
        reset.shellHistoryEnabled shouldBe true
        reset.recentCommands shouldBe listOf("ls")
        reset.knownFleet shouldBe start.knownFleet
    }

    test("an empty fleet snapshot does not wipe labels already saved") {
        val known = listOf(KnownTool(ToolRef.fleet("com.example.app", "ask"), "Ask"))
        val config = ToolbarConfig(knownFleet = known)
        config.notingFleet(emptyList()).knownFleet shouldBe known
        config.notingFleet(known).knownFleet shouldBe known
        val next = listOf(KnownTool(ToolRef.fleet("com.example.app", "scan"), "Scan"))
        config.notingFleet(next).knownFleet shouldBe next
        config.notingFleet(listOf(KnownTool("not-a-ref", "Nope"))).knownFleet shouldBe known
    }

    test("export omits shell history and an untouched layout, and import refuses a bad document") {
        val slash = ToolRef.slash("sig")
        val current = ToolbarConfig(
            pinned = listOf("PASTE"),
            recentCommands = listOf("secret-command"),
            knownFleet = listOf(KnownTool(ToolRef.fleet("com.example.app", "ask"), "Ask")),
            tileSize = ToolsLayout.SMALL,
            columns = 4,
            extraTiles = listOf(slash),
        )
        val document = ToolbarLayoutTransfer.exportDocument(current)!!
        document shouldNotContain "secret-command"
        document shouldNotContain "Ask"
        val decoded = ToolbarConfigCodec.decode(document)
        decoded.recentCommands shouldBe emptyList()
        decoded.knownFleet shouldBe emptyList()
        decoded.pinned shouldBe listOf("PASTE")
        decoded.extraTiles shouldBe listOf(slash)
        ToolbarLayoutTransfer.apply(current, "{", replace = true) shouldBe current
        ToolbarLayoutTransfer.apply(current, "", replace = false) shouldBe current
    }

    test("replace takes the incoming layout and keeps local history; merge fills only what is still default") {
        val local = ToolbarConfig(
            hidden = listOf("COPY"),
            pinned = listOf("PASTE"),
            tileSize = ToolsLayout.LARGE,
            columns = 2,
            recentCommands = listOf("local-history"),
            extraTiles = listOf(ToolRef.slash("mine")),
        )
        val incoming = ToolbarConfig(
            hidden = listOf("UNDO"),
            order = mapOf("tools:QUICK" to listOf("EMOJI")),
            pinned = listOf("CUT"),
            tileSize = ToolsLayout.SMALL,
            columns = 5,
            extraTiles = listOf(ToolRef.slash("theirs")),
            recentCommands = listOf("their-history"),
            commands = listOf(SavedCommandDto("c1", "Ping", "ping", "net")),
        )
        val doc = ToolbarConfigCodec.encode(incoming)
        val replaced = ToolbarLayoutTransfer.apply(local, doc, replace = true)
        replaced.pinned shouldBe listOf("CUT")
        replaced.tileSize shouldBe ToolsLayout.SMALL
        replaced.hidden shouldBe listOf("UNDO")
        replaced.recentCommands shouldBe listOf("local-history")
        val merged = ToolbarLayoutTransfer.apply(local, doc, replace = false)
        merged.hidden shouldBe listOf("COPY", "UNDO")
        merged.pinned shouldBe listOf("PASTE")
        merged.tileSize shouldBe ToolsLayout.LARGE
        merged.columns shouldBe 2
        merged.extraTiles shouldBe listOf(ToolRef.slash("mine"), ToolRef.slash("theirs"))
        merged.order shouldBe mapOf("tools:QUICK" to listOf("EMOJI"))
        merged.recentCommands shouldBe listOf("local-history")
        merged.commands shouldBe incoming.commands
        val fresh = ToolbarLayoutTransfer.apply(ToolbarConfig(), doc, replace = false)
        fresh.pinned shouldBe listOf("CUT")
        fresh.tileSize shouldBe ToolsLayout.SMALL
        fresh.columns shouldBe 5
        fresh.commands shouldBe incoming.commands
        fresh.recentCommands shouldBe emptyList()
    }

    test("clipboard zip carries the toolbar layout without changing a zip that has none or the entries hash") {
        val meta = ExportMeta(createdAtMs = 1_700_000_000_000L, appVersion = "test")
        val clips = listOf(entry(1, "plain"))
        val plain = BackupWriter.writeZip(clips, meta)
        BackupWriter.writeZip(clips, meta, toolbarJson = null).toList() shouldBe plain.toList()
        val bare = BackupReader.read(plain, null)
        bare.toolbarDocument shouldBe null
        val layout = ToolbarConfig(pinned = listOf("PASTE"), tileSize = ToolsLayout.SMALL, columns = 3)
        val doc = ToolbarConfigCodec.encode(layout)
        val with = BackupWriter.writeZip(clips, meta, toolbarJson = doc.encodeToByteArray())
        val parsed = BackupReader.read(with, null)
        parsed.entries.single().text shouldBe "plain"
        parsed.toolbarDocument shouldBe doc
        parsed.manifest.entriesSha256 shouldBe bare.manifest.entriesSha256
        val locked = BackupWriter.writePasswordZip(
            clips,
            meta,
            password = "correct horse".toCharArray(),
            toolbarJson = doc.encodeToByteArray(),
        )
        val opened = BackupReader.read(locked, "correct horse".toCharArray())
        opened.passwordProtected shouldBe true
        opened.toolbarDocument shouldBe doc
        opened.entries.single().text shouldBe "plain"
    }
})
