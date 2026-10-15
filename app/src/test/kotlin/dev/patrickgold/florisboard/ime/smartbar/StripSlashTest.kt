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

package dev.patrickgold.florisboard.ime.smartbar

import dev.patrickgold.florisboard.ime.ai.AiPrompt
import dev.patrickgold.florisboard.ime.ai.AiTask
import dev.patrickgold.florisboard.ime.clipboard.backup.BackupReader
import dev.patrickgold.florisboard.ime.clipboard.backup.BackupWriter
import dev.patrickgold.florisboard.ime.clipboard.backup.ExportMeta
import dev.patrickgold.florisboard.ime.clipboard.history.entry
import dev.patrickgold.florisboard.ime.mode.TerminalKeyRow
import dev.patrickgold.florisboard.ime.mode.TerminalRowPolicy
import dev.patrickgold.florisboard.ime.nlp.custom.CustomSuggestion
import dev.patrickgold.florisboard.ime.nlp.custom.CustomSuggestionCodec
import dev.patrickgold.florisboard.ime.nlp.custom.CustomSuggestionList
import dev.patrickgold.florisboard.ime.nlp.custom.CustomSuggestionMatcher
import dev.patrickgold.florisboard.ime.nlp.custom.SuggestionDocument
import dev.patrickgold.florisboard.ime.slash.CustomSlashCommand
import dev.patrickgold.florisboard.ime.slash.SlashAction
import dev.patrickgold.florisboard.ime.slash.SlashActions
import dev.patrickgold.florisboard.ime.slash.SlashBuiltin
import dev.patrickgold.florisboard.ime.slash.SlashCatalog
import dev.patrickgold.florisboard.ime.slash.SlashCommandCodec
import dev.patrickgold.florisboard.ime.slash.SlashCommandList
import dev.patrickgold.florisboard.ime.slash.SlashDocument
import dev.patrickgold.florisboard.ime.slash.SlashMatcher
import dev.patrickgold.florisboard.lib.fleet.FleetCommand
import dev.patrickgold.florisboard.lib.fleet.FleetHubContract
import java.io.File
import dev.patrickgold.florisboard.ime.slash.SlashOffer
import dev.patrickgold.florisboard.ime.slash.SlashTemplate
import dev.patrickgold.florisboard.ime.slash.SlashVars
import dev.patrickgold.florisboard.ime.toolbar.ToolbarActionId
import dev.patrickgold.florisboard.ime.toolbar.ToolsCatalog
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

    test("tools grid has the requested groups and no page tabs or attack seeds") {
        ToolsCatalog.pageTabsOnStrip shouldBe false
        ToolsCatalog.groups.map { it.title } shouldBe listOf(
            "Clipboard",
            "Clip editor",
            "Vault",
            "AI",
            "Terminal keys",
            "Code",
            "Privacy",
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

    test("hub slash commands join the strip only when the hub is connected") {
        SlashMatcher.token("/note hello", passwordLike = false) shouldBe null
        val connected = listOf(
            FleetCommand("build", "build"),
            FleetCommand("status", "status"),
            FleetCommand("note", "note"),
            FleetCommand("deploy", "deploy"),
        )
        val token = SlashMatcher.token("/note hello", passwordLike = false, argumentNames = connected.map { it.name }.toSet())
        token!!.name shouldBe "note"
        token.argument shouldBe "hello"
        val plan = StripPlanner.plan(
            passwordLike = false,
            slashEnabled = true,
            textBeforeCursor = "/b",
            selection = "",
            custom = emptyList(),
            commands = emptyList(),
            clipboardAvailable = false,
            fleetCommands = connected,
        )
        plan.shouldBeInstanceOf<StripPlan.Slash>()
        plan.offers.map { it.name } shouldBe listOf("build")
        plan.offers.single().fleet shouldBe true
        val hidden = StripPlanner.plan(
            passwordLike = true,
            slashEnabled = true,
            textBeforeCursor = "/note hello",
            selection = "vault",
            custom = emptyList(),
            commands = emptyList(),
            clipboardAvailable = false,
            fleetCommands = connected,
        )
        hidden.shouldBeInstanceOf<StripPlan.Words>()
    }

    test("manifest queries the unconfirmed hub and does not request internet") {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        val contract = File("../lib/fleet/src/main/kotlin/dev/patrickgold/florisboard/lib/fleet/FleetHubContract.kt").readText()
        contract shouldContain "TODO-confirm-with-hub"
        manifest shouldContain "TODO-confirm-with-hub"
        manifest shouldContain "android:name=\"${FleetHubContract.PACKAGE_NAME}\""
        manifest shouldContain "android:name=\"${FleetHubContract.SERVICE_ACTION}\""
        manifest shouldContain "android:name=\"${FleetHubContract.PERMISSION}\""
        manifest.contains("android.permission.INTERNET") shouldBe false
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
