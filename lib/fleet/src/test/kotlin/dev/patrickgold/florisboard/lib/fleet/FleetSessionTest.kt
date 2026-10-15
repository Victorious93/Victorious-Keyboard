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

package dev.patrickgold.florisboard.lib.fleet

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe

class FakeFleetHub(
    var pingOk: Boolean = true,
    var failPing: Boolean = false,
    var commandPayload: String = """[{"name":"deploy","description":"Deploy"},{"name":"date","description":"shadow"}]""",
    var snippetPayload: String =
        """[{"id":"adb1","label":"devices","kind":"adb","text":"adb devices"},""" +
            """{"id":"fb1","label":"devices","kind":"fastboot","text":"fastboot devices"},""" +
            """{"id":"tx1","label":"info","kind":"termux","command":"termux-info","args":[]}]""",
    var replies: Map<String, String> = mapOf(
        "build" to "gradle assembleDebug",
        "status" to "idle",
        "note" to "noted",
        FleetHubContract.ACTION_ASK to "answer",
    ),
) : FleetHub {
    val calls = mutableListOf<String>()

    override fun askHub(action: String, text: String): String {
        calls += "ask:$action:${text.length}"
        if (action == FleetHubContract.ACTION_LIST_COMMANDS) return commandPayload
        return replies[action] ?: ""
    }

    override fun runCommand(name: String, args: List<String>): String {
        calls += "run:$name"
        return "text-for-$name"
    }

    override fun getSnippets(): String = snippetPayload

    override fun ping(): Boolean {
        if (failPing) throw IllegalStateException("down")
        return pingOk
    }
}

class FleetSessionTest : FunSpec({
    test("a missing hub hides every fleet feature") {
        val snapshot = FleetSession.load(enabled = true, installed = false, hub = FakeFleetHub())
        snapshot.link shouldBe FleetLink.ABSENT
        snapshot.commands(emptySet()) shouldBe emptyList()
        snapshot.panel() shouldBe FleetPanelState.Hidden
        snapshot.panel().showDot shouldBe false
    }

    test("a switched-off link stays hidden even when the hub answers") {
        val snapshot = FleetSession.load(enabled = false, installed = true, hub = FakeFleetHub())
        snapshot.link shouldBe FleetLink.ABSENT
        snapshot.panel().showDot shouldBe false
    }

    test("a hub that fails ping shows a disconnected dot and no commands") {
        val down = FakeFleetHub(pingOk = false)
        val snapshot = FleetSession.load(enabled = true, installed = true, hub = down)
        snapshot.link shouldBe FleetLink.INSTALLED
        snapshot.commands(emptySet()) shouldBe emptyList()
        snapshot.panel().showDot shouldBe true
        snapshot.panel().connected shouldBe false
        snapshot.snippets shouldBe emptyList()

        val thrown = FleetSession.load(enabled = true, installed = true, hub = FakeFleetHub(failPing = true))
        thrown.link shouldBe FleetLink.INSTALLED
    }

    test("a connected hub offers built-ins, advertised commands, and snippets") {
        val hub = FakeFleetHub()
        val snapshot = FleetSession.load(enabled = true, installed = true, hub = hub)
        snapshot.link shouldBe FleetLink.CONNECTED
        val names = snapshot.commands(reserved = setOf("date", "shell")).map { it.name }
        names shouldBe listOf("build", "status", "note", "deploy")
        names.contains("date") shouldBe false
        snapshot.panel().connected shouldBe true
        snapshot.snippets.map { it.kind } shouldBe listOf(
            FleetSnippetKind.ADB,
            FleetSnippetKind.FASTBOOT,
            FleetSnippetKind.TERMUX,
        )
        hub.calls shouldContain "ask:${FleetHubContract.ACTION_LIST_COMMANDS}:0"
    }

    test("hostile snippet and command documents become empty lists") {
        FleetCodec.commands("[{") shouldBe emptyList()
        FleetCodec.commands("[" + "[".repeat(400)) shouldBe emptyList()
        FleetCodec.snippets("{not-a-list}") shouldBe emptyList()
        FleetCodec.snippets("x".repeat(70_000)) shouldBe emptyList()
        val kept = FleetCodec.commands("""[{"name":"Build","description":"ok"},{"name":"../x","description":"no"}]""")
        kept.map { it.name } shouldBe listOf("build")
    }

    test("password fields and implicit vault text are not sent") {
        FleetPolicy.slashText("release", passwordLike = true) shouldBe null
        FleetPolicy.slashText("release", passwordLike = false) shouldBe "release"
        FleetPolicy.askText("secret", passwordLike = true, selectionIsVault = false, explicitVaultPick = true) shouldBe null
        FleetPolicy.askText("vault-line", passwordLike = false, selectionIsVault = true, explicitVaultPick = false) shouldBe null
        FleetPolicy.askText("vault-line", passwordLike = false, selectionIsVault = true, explicitVaultPick = true) shouldBe "vault-line"
        FleetPolicy.askText("   ", passwordLike = false, selectionIsVault = false, explicitVaultPick = true) shouldBe null
        FleetPolicy.askText("p".repeat(9_000), passwordLike = false, selectionIsVault = false, explicitVaultPick = true)!!
            .length shouldBe FleetPolicy.MAX_SEND
    }

    test("snippet text is inserted and runCommand text is not executed") {
        val hub = FakeFleetHub()
        val snapshot = FleetSession.load(enabled = true, installed = true, hub = hub)
        val adb = snapshot.snippets.first { it.kind == FleetSnippetKind.ADB }
        val termux = snapshot.snippets.first { it.kind == FleetSnippetKind.TERMUX }
        val adbOut = adb.insertText(hub)
        adbOut.text shouldBe "adb devices"
        adbOut.execute shouldBe false
        hub.calls.none { it.startsWith("run:") } shouldBe true
        val termuxOut = termux.insertText(hub)
        termuxOut.text shouldBe "text-for-termux-info"
        termuxOut.execute shouldBe false
        hub.calls shouldContain "run:termux-info"
    }
})
