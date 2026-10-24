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

package com.victorious.keyboard.ime.smartbar

import com.victorious.keyboard.ime.ai.AiPrompt
import com.victorious.keyboard.ime.ai.AiTask
import com.victorious.keyboard.ime.clipboard.backup.BackupReader
import com.victorious.keyboard.ime.clipboard.backup.BackupWriter
import com.victorious.keyboard.ime.clipboard.backup.ExportMeta
import com.victorious.keyboard.ime.clipboard.history.entry
import com.victorious.keyboard.ime.mode.TerminalKeyRow
import com.victorious.keyboard.ime.mode.TerminalRowPolicy
import com.victorious.keyboard.ime.nlp.custom.CustomSuggestion
import com.victorious.keyboard.ime.nlp.custom.CustomSuggestionCodec
import com.victorious.keyboard.ime.nlp.custom.CustomSuggestionList
import com.victorious.keyboard.ime.nlp.custom.CustomSuggestionMatcher
import com.victorious.keyboard.ime.nlp.custom.SuggestionDocument
import com.victorious.keyboard.ime.slash.CustomSlashCommand
import com.victorious.keyboard.ime.slash.SlashAction
import com.victorious.keyboard.ime.slash.SlashActions
import com.victorious.keyboard.ime.slash.SlashBuiltin
import com.victorious.keyboard.ime.slash.SlashCatalog
import com.victorious.keyboard.ime.slash.SlashCommandCodec
import com.victorious.keyboard.ime.slash.SlashCommandList
import com.victorious.keyboard.ime.slash.SlashDocument
import com.victorious.keyboard.ime.slash.SlashMatcher
import com.victorious.keyboard.lib.fleet.FleetCommand
import com.victorious.keyboard.lib.fleet.FleetMessages
import java.io.File
import com.victorious.keyboard.ime.slash.SlashOffer
import com.victorious.keyboard.ime.slash.SlashTemplate
import com.victorious.keyboard.ime.slash.SlashVars
import com.victorious.keyboard.ime.toolbar.ToolbarActionId
import com.victorious.keyboard.ime.toolbar.ToolsCatalog
import com.victorious.keyboard.ime.toolbar.ToolsTab
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.types.shouldBeInstanceOf
import java.time.Instant
import java.time.ZoneOffset

class StripSlashTest : FunSpec({
    test("terminal row stays hidden in chrome and appears for terminals on auto") {
        val chrome = "com.android.chrome"
        TerminalRowPolicy.shouldShow(TerminalKeyRow.AUTO, chrome, typeNull = false) shouldBe false
        TerminalRowPolicy.shouldShow(TerminalKeyRow.AUTO, "com.termux", typeNull = false) shouldBe true
        TerminalRowPolicy.shouldShow(TerminalKeyRow.AUTO, "com.termux.api", typeNull = false) shouldBe true
        TerminalRowPolicy.shouldShow(TerminalKeyRow.AUTO, "org.connectbot", typeNull = false) shouldBe true
        TerminalRowPolicy.shouldShow(TerminalKeyRow.AUTO, "com.sonelli.juicessh", typeNull = false) shouldBe true
        TerminalRowPolicy.shouldShow(TerminalKeyRow.AUTO, "com.offsec.nhterm", typeNull = false) shouldBe true
        TerminalRowPolicy.shouldShow(TerminalKeyRow.AUTO, "jackpal.androidterm", typeNull = false) shouldBe true
        TerminalRowPolicy.shouldShow(TerminalKeyRow.AUTO, chrome, typeNull = true) shouldBe true
        TerminalRowPolicy.shouldShow(TerminalKeyRow.ALWAYS, chrome, typeNull = false) shouldBe true
        TerminalRowPolicy.shouldShow(TerminalKeyRow.NEVER, "com.termux", typeNull = true) shouldBe false
    }

    test("tools panel has its tabs and no page tabs on the strip or attack seeds") {
        ToolsCatalog.pageTabsOnStrip shouldBe false
        ToolsCatalog.tabs.keys.toList() shouldBe listOf(
            ToolsTab.CLIPBOARD,
            ToolsTab.AI,
            ToolsTab.TERMINAL,
            ToolsTab.CODE,
            ToolsTab.QUICK,
        )
        val ids = ToolsCatalog.actionIds()
        ids shouldContain ToolbarActionId.EDIT_CLIP
        ids shouldContain ToolbarActionId.AI_ASK
        ids shouldContain ToolbarActionId.CTRL
        ids.none { ToolsCatalog.label(it).contains("nmap", ignoreCase = true) } shouldBe true
        ids.none { ToolsCatalog.label(it).contains("hydra", ignoreCase = true) } shouldBe true
        ids.none { ToolsCatalog.label(it).contains("msfconsole", ignoreCase = true) } shouldBe true
    }

    test("custom suggestions outrank the dictionary and honor a trigger") {
        val addr = CustomSuggestion("a", "12 Main Street", trigger = "addr", createdAt = 2)
        val older = CustomSuggestion("b", "hello there", createdAt = 1)
        CustomSuggestionMatcher.rank(listOf(older, addr), "ad").map { it.id } shouldBe listOf("a")
        CustomSuggestionMatcher.rank(listOf(older, addr), "hel").map { it.id } shouldBe listOf("b")
        CustomSuggestionMatcher.matches(addr, "") shouldBe false
        val plan = StripPlanner.plan(
            passwordLike = false,
            slashEnabled = true,
            textBeforeCursor = "ad",
            selection = "",
            custom = listOf(older, addr),
            commands = emptyList(),
            clipboardAvailable = true,
        )
        plan.shouldBeInstanceOf<StripPlan.Words>()
        plan.custom.map { it.id } shouldBe listOf("a")
        plan.showClipboard shouldBe false
        plan.showDictionary shouldBe true
    }

    test("password fields get no slash token and no custom phrases") {
        SlashMatcher.token("/date", passwordLike = true) shouldBe null
        val plan = StripPlanner.plan(
            passwordLike = true,
            slashEnabled = true,
            textBeforeCursor = "/shell id",
            selection = "secret",
            custom = listOf(CustomSuggestion("a", "secret phrase", trigger = "sec")),
            commands = emptyList(),
            clipboardAvailable = true,
        )
        plan.shouldBeInstanceOf<StripPlan.Words>()
        plan.custom shouldBe emptyList()
        plan.selection shouldBe null
    }

    test("slash matches only at a word start") {
        SlashMatcher.token("hello/date", passwordLike = false) shouldBe null
        val token = SlashMatcher.token("hello /snip sig", passwordLike = false)
        token!!.name shouldBe "snip"
        token.argument shouldBe "sig"
        token.replaceLength shouldBe "/snip sig".length
        val offers = SlashCatalog.match("s", emptyList(), enabled = true).map { it.name }
        offers shouldContain "snip"
        offers shouldContain "sum"
        offers shouldContain "shell"
        offers.contains("date") shouldBe false
        SlashCatalog.match("s", emptyList(), enabled = false) shouldBe emptyList()
    }

    test("a slash followed by a space is prose or math, not a command") {
        SlashMatcher.token("1 / 2", passwordLike = false) shouldBe null
        SlashMatcher.token("a / b / c", passwordLike = false) shouldBe null
        // A lone slash still opens the command list.
        SlashMatcher.token("hi /", passwordLike = false)!!.name shouldBe ""
    }

    test("a later slash inside an argument does not re-anchor an argument command") {
        val ai = SlashMatcher.token("/ai explain a / b", passwordLike = false)!!
        ai.name shouldBe "ai"
        ai.argument shouldBe "explain a / b"
        ai.replaceLength shouldBe "/ai explain a / b".length
        val shell = SlashMatcher.token("/shell ls /usr/bin", passwordLike = false)!!
        shell.name shouldBe "shell"
        shell.argument shouldBe "ls /usr/bin"
        // A plain command still ends at its first space, so a later slash starts a new token.
        SlashMatcher.token("/date /time", passwordLike = false)!!.name shouldBe "time"
    }

    test("a later real command replaces an argument command, and a path does not") {
        val date = SlashMatcher.token("/ai hello /date", passwordLike = false)!!
        date.name shouldBe "date"
        date.argument shouldBe ""
        date.replaceLength shouldBe "/date".length
        val shell = SlashMatcher.token("/ai note /shell ls /usr/bin", passwordLike = false)!!
        shell.name shouldBe "shell"
        shell.argument shouldBe "ls /usr/bin"
        SlashMatcher.token("cd /usr/bin", passwordLike = false) shouldBe null
        SlashMatcher.token("/shell ls /usr", passwordLike = false)!!.name shouldBe "shell"
        val custom = SlashMatcher.token("/deploy", passwordLike = false, knownNames = setOf("deploy"))!!
        custom.name shouldBe "deploy"
        SlashMatcher.token("/deploy", passwordLike = false) shouldBe null
    }

    test("a path does not replace the suggestion strip with an empty command list") {
        val plan = StripPlanner.plan(
            passwordLike = false,
            slashEnabled = true,
            textBeforeCursor = "cd /usr/bin",
            selection = "",
            custom = emptyList(),
            commands = emptyList(),
            clipboardAvailable = false,
        )
        plan.shouldBeInstanceOf<StripPlan.Words>()
    }

    test("clipboard values are not scanned for slash placeholders") {
        val rendered = SlashTemplate.render(
            "see {clipboard} then {date}{cursor}",
            SlashVars(date = "2026-01-02", clipboard = "note {date} {cursor}"),
        )
        rendered.text shouldBe "see note {date} {cursor} then 2026-01-02"
        rendered.cursorBackCodePoints shouldBe null
    }

    test("shell prompt carries the insert-only rule") {
        val prompt = SlashActions.shellUser("show the working directory")
        prompt shouldContain AiPrompt.SHELL_RULE
        prompt.shouldNotContain("msfvenom")
        prompt.shouldNotContain("aircrack")
        prompt.shouldNotContain("hydra")
        val action = SlashActions.resolve(
            SlashOffer("shell", "", SlashBuiltin.SHELL, null),
            "show the working directory",
            emptyList(),
            Instant.EPOCH,
            ZoneOffset.UTC,
        )
        val ai = action.shouldBeInstanceOf<SlashAction.Ai>()
        ai.task shouldBe AiTask.ASK
        ai.systemOverride shouldBe AiPrompt.SHELL_RULE
    }

    test("date and time use the injected clock") {
        val action = SlashActions.resolve(
            SlashOffer("date", "", SlashBuiltin.DATE, null),
            "",
            emptyList(),
            Instant.parse("2026-03-04T15:06:07Z"),
            ZoneOffset.UTC,
        )
        action shouldBe SlashAction.Insert("2026-03-04", null)
    }

    test("hostile suggestion json does not become a list") {
        (CustomSuggestionCodec.decode("[[[") as? SuggestionDocument.Ok) shouldBe null
        CustomSuggestionCodec.decode("[[[") shouldBe SuggestionDocument.Invalid
        val ok = CustomSuggestionCodec.decode(CustomSuggestionCodec.encode(listOf(CustomSuggestion("id1", "Hello"))))
        ok.shouldBeInstanceOf<SuggestionDocument.Ok>()
        ok.items.single().text shouldBe "Hello"
        val merged = CustomSuggestionList.merge(
            listOf(CustomSuggestion("id1", "Hello", createdAt = 1)),
            listOf(CustomSuggestion("id1", "Hello!", createdAt = 2), CustomSuggestion("id2", "Next", createdAt = 3)),
            replace = false,
        )
        merged.map { it.text } shouldBe listOf("Hello!", "Next")
    }

    test("slash command names cannot replace built-ins and reorder") {
        SlashCommandList.add(emptyList(), "date", "nope", "x") shouldBe null
        val added = SlashCommandList.add(emptyList(), "sig", "Signature", "Hi {cursor}there", id = "c1")!!
        val moved = SlashCommandList.move(added + CustomSlashCommand("c2", "bye", "", "bye", order = 1), "c2", -1)!!
        moved.map { it.name } shouldBe listOf("bye", "sig")
        (SlashCommandCodec.decode("{") ) shouldBe SlashDocument.Invalid
    }

    test("clipboard zip carries suggestions without changing a zip that has none") {
        val meta = ExportMeta(createdAtMs = 1_700_000_000_000L, appVersion = "test")
        val clips = listOf(entry(1, "plain"))
        val plain = BackupWriter.writeZip(clips, meta)
        BackupWriter.writeZip(clips, meta, suggestionsJson = null).toList() shouldBe plain.toList()
        BackupReader.read(plain, null).suggestionsDocument shouldBe null
        val doc = CustomSuggestionCodec.encode(listOf(CustomSuggestion("id1", "phrase", trigger = "ph")))
        val with = BackupWriter.writeZip(clips, meta, suggestionsJson = doc.encodeToByteArray())
        val parsed = BackupReader.read(with, null)
        parsed.entries.single().text shouldBe "plain"
        parsed.suggestionsDocument shouldBe doc
        val locked = BackupWriter.writePasswordZip(
            clips,
            meta,
            password = "correct horse".toCharArray(),
            suggestionsJson = doc.encodeToByteArray(),
        )
        val opened = BackupReader.read(locked, "correct horse".toCharArray())
        opened.passwordProtected shouldBe true
        opened.suggestionsDocument shouldBe doc
    }

    test("fleet slash commands namespace clashes and stay off in password fields") {
        val connected = listOf(
            FleetCommand("edl:status", "status", "EDL status", "com.example.one"),
            FleetCommand("swarm:status", "status", "Swarm status", "com.example.two"),
            FleetCommand("flash", "flash", "Flash", "com.example.one"),
        )
        val names = connected.map { it.name }.toSet()
        val qualified = SlashMatcher.token("/edl:status hello", passwordLike = false, argumentNames = names)
        qualified!!.name shouldBe "edl:status"
        qualified.argument shouldBe "hello"
        val shared = StripPlanner.plan(
            passwordLike = false,
            slashEnabled = true,
            textBeforeCursor = "/status",
            selection = "",
            custom = emptyList(),
            commands = emptyList(),
            clipboardAvailable = false,
            fleetCommands = connected,
        )
        shared.shouldBeInstanceOf<StripPlan.Slash>()
        shared.offers.map { it.name } shouldBe listOf("edl:status", "swarm:status")
        shared.offers.all { it.fleet } shouldBe true
        shared.offers.map { it.fleetRawName } shouldBe listOf("status", "status")
        val unique = StripPlanner.plan(
            passwordLike = false,
            slashEnabled = true,
            textBeforeCursor = "/flash",
            selection = "",
            custom = emptyList(),
            commands = emptyList(),
            clipboardAvailable = false,
            fleetCommands = connected,
        )
        unique.shouldBeInstanceOf<StripPlan.Slash>()
        unique.offers.single().name shouldBe "flash"
        unique.offers.single().fleetPackage shouldBe "com.example.one"
        val hidden = StripPlanner.plan(
            passwordLike = true,
            slashEnabled = true,
            textBeforeCursor = "/status",
            selection = "vault",
            custom = emptyList(),
            commands = emptyList(),
            clipboardAvailable = false,
            fleetCommands = connected,
        )
        hidden.shouldBeInstanceOf<StripPlan.Words>()
    }

    test("manifest discovers command providers and does not name apps") {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        val client = File("src/main/kotlin/com/victorious/keyboard/ime/fleet/FleetHubClient.kt").readText()
        manifest shouldContain "android:name=\"${FleetMessages.ACTION}\""
        manifest.contains("com.victorious.fleet.PERMISSION") shouldBe false
        manifest.contains("KEYBOARD_PROVIDER") shouldBe false
        manifest.contains("<package ") shouldBe false
        // INTERNET exists only for the opt-in pack update check; NetworkUseTest pins that.
        Regex("android.permission.INTERNET").findAll(manifest).count() shouldBe 1
        client shouldContain "queryIntentServices"
        client shouldContain "Messenger"
        val banned = listOf("edl-commander", "pentest-swarm", "victorlink", "victorsuite", "rootforge")
        val roots = listOf(
            File("src/main"),
            File("../lib/fleet/src/main"),
        )
        val hits = roots.flatMap { root ->
            root.walkTopDown().filter { it.isFile && it.extension in setOf("kt", "xml", "aidl", "pro") }
        }.flatMap { file ->
            val text = file.readText().lowercase()
            banned.filter { needle -> needle in text }.map { file.path }
        }
        hits shouldBe emptyList()
    }

    test("primary candidate is the auto-commit word") {
        CandidateHighlight.indexOf(
            autoCommit = listOf(false, true, false),
            word = listOf(true, true, false),
        ) shouldBe 1
        CandidateHighlight.indexOf(
            autoCommit = listOf(false, false),
            word = listOf(false, true),
        ) shouldBe 1
    }
})
