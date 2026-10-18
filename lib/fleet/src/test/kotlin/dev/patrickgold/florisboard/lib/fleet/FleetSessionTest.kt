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

class FakeFleetProvider(
    var pingOk: Boolean = true,
    var failPing: Boolean = false,
    var displayName: String = "Example",
    var namespaceToken: String = "ex",
    var iconBytes: ByteArray = ByteArray(0),
    var commandPayload: String = """[{"name":"status","description":"Status"}]""",
    var snippetPayload: String = "[]",
    var actionPayload: String = "[]",
    var replies: Map<String, String> = mapOf("" to "answer"),
) : FleetProvider {
    val calls = mutableListOf<String>()

    override fun ping(): Boolean {
        if (failPing) throw IllegalStateException("down")
        return pingOk
    }

    override fun providerName(): String = displayName

    override fun namespace(): String = namespaceToken

    override fun icon(): ByteArray = iconBytes

    override fun slashCommands(): String = commandPayload

    override fun snippets(): String = snippetPayload

    override fun actions(): String = actionPayload

    override fun ask(action: String, text: String): String {
        calls += "ask:$action:${text.length}"
        return replies[action] ?: ""
    }

    override fun runCommand(name: String, args: List<String>): String {
        calls += "run:$name"
        return "text-for-$name"
    }
}

class FleetSessionTest : FunSpec({
    test("no trusted provider hides every fleet feature") {
        val snapshot = FleetSession.load(enabled = true, endpoints = emptyList())
        snapshot.commands(emptySet()) shouldBe emptyList()
        snapshot.panel() shouldBe FleetPanelState.Hidden

        val off = FleetSession.load(
            enabled = false,
            endpoints = listOf(FleetEndpoint("com.example.edl", FakeFleetProvider())),
        )
        off.panel() shouldBe FleetPanelState.Hidden
        off.commands(emptySet()) shouldBe emptyList()
    }

    test("two providers namespace a shared slash name and keep a unique name") {
        val edl = FakeFleetProvider(
            displayName = "EDL",
            namespaceToken = "edl",
            commandPayload = """[{"name":"status","description":"EDL status"},{"name":"flash","description":"Flash"}]""",
            snippetPayload = """[{"id":"adb1","label":"devices","kind":"adb","text":"adb devices"}]""",
            actionPayload = """[{"name":"flash","label":"Flash"}]""",
        )
        val swarm = FakeFleetProvider(
            displayName = "Swarm",
            namespaceToken = "swarm",
            commandPayload = """[{"name":"status","description":"Swarm status"},{"name":"date","description":"shadow"}]""",
            snippetPayload = """[{"id":"tx1","label":"info","kind":"termux","command":"termux-info","args":[]}]""",
        )
        val snapshot = FleetSession.load(
            enabled = true,
            endpoints = listOf(
                FleetEndpoint("com.example.edl", edl),
                FleetEndpoint("com.example.swarm", swarm),
            ),
        )
        val commands = snapshot.commands(reserved = setOf("date", "shell"))
        commands.map { it.name } shouldBe listOf("edl:status", "flash", "swarm:status", "swarm:date")
        commands.first { it.name == "edl:status" }.rawName shouldBe "status"
        commands.first { it.name == "edl:status" }.packageName shouldBe "com.example.edl"
        commands.first { it.name == "flash" }.rawName shouldBe "flash"
        snapshot.panel().statuses.map { it.title to it.connected } shouldBe listOf(
            "EDL" to true,
            "Swarm" to true,
        )
        snapshot.panel().sections.map { it.packageName } shouldBe listOf("com.example.edl", "com.example.swarm")
        snapshot.panel().sections.first().actions.map { it.name } shouldBe listOf("flash")
        snapshot.panel().sections.first().snippets.map { it.kind } shouldBe listOf(FleetSnippetKind.ADB)
        snapshot.panel().sections.last().snippets.map { it.kind } shouldBe listOf(FleetSnippetKind.TERMUX)
    }

    test("a failed ping stays in the status row and contributes no commands") {
        val down = FakeFleetProvider(pingOk = false, displayName = "Down", namespaceToken = "down")
        val up = FakeFleetProvider(displayName = "Up", namespaceToken = "up")
        val snapshot = FleetSession.load(
            enabled = true,
            endpoints = listOf(
                FleetEndpoint("com.example.down", down),
                FleetEndpoint("com.example.up", up),
            ),
        )
        snapshot.panel().statuses.map { it.connected } shouldBe listOf(false, true)
        snapshot.panel().statuses.first().title shouldBe "down"
        snapshot.commands(emptySet()).map { it.packageName }.toSet() shouldBe setOf("com.example.up")
        snapshot.panel().sections.map { it.packageName } shouldBe listOf("com.example.up")

        val thrown = FleetSession.load(
            enabled = true,
            endpoints = listOf(FleetEndpoint("com.example.down", FakeFleetProvider(failPing = true))),
        )
        thrown.panel().statuses.single().connected shouldBe false
        thrown.commands(emptySet()) shouldBe emptyList()
    }

    test("matching namespaces are disambiguated and a blank namespace uses the title") {
        val first = FakeFleetProvider(namespaceToken = "edl", commandPayload = """[{"name":"status","description":"a"}]""")
        val second = FakeFleetProvider(namespaceToken = "edl", commandPayload = """[{"name":"status","description":"b"}]""")
        val named = FleetSession.load(
            enabled = true,
            endpoints = listOf(
                FleetEndpoint("com.example.a", first),
                FleetEndpoint("com.example.b", second),
            ),
        )
        named.commands(emptySet()).map { it.name } shouldBe listOf("edl:status", "edl2:status")

        val titled = FakeFleetProvider(
            displayName = "Swarm AI",
            namespaceToken = "",
            commandPayload = """[{"name":"status","description":"only"}]""",
        )
        val fromTitle = FleetSession.load(true, listOf(FleetEndpoint("com.example.swarm", titled)))
        fromTitle.commands(emptySet()).single().name shouldBe "status"
        fromTitle.apps.single().namespace shouldBe "swarm"
    }

    test("release-signed providers are on, other certificates wait for approval, and every one can be switched off") {
        val release = "11".repeat(32).chunked(2).joinToString(":")
        val otherCert = "22".repeat(32)
        val grants = listOf(
            FleetGrant("com.example.approved", otherCert, approved = true, enabled = true),
            FleetGrant("com.example.edl", release, approved = false, enabled = false),
            FleetGrant("com.example.stale", "33".repeat(32), approved = true, enabled = true),
        )
        val offers = FleetDiscovery.classify(
            ownPackage = "com.victorious.keyboard",
            releaseSha256 = release,
            grants = grants,
            candidates = listOf(
                FleetServiceCandidate("com.victorious.keyboard", "Own", "Keyboard", release),
                FleetServiceCandidate("com.example.edl", "Z", "EDL", release),
                FleetServiceCandidate("com.example.edl", "A", "EDL", release),
                FleetServiceCandidate("com.example.swarm", "S", "Swarm", release),
                FleetServiceCandidate("com.example.approved", "O", "Other", otherCert),
                FleetServiceCandidate("com.example.stale", "T", "Stale", otherCert),
                FleetServiceCandidate("com.example.fresh", "F", "Fresh", otherCert),
                FleetServiceCandidate("com.example.empty", "E", "Empty", ""),
            ),
        )
        offers.map { it.packageName to it.access } shouldBe listOf(
            "com.example.approved" to FleetAccess.Active,
            "com.example.edl" to FleetAccess.Off,
            "com.example.empty" to FleetAccess.NeedsApproval,
            "com.example.fresh" to FleetAccess.NeedsApproval,
            "com.example.stale" to FleetAccess.NeedsApproval,
            "com.example.swarm" to FleetAccess.Active,
        )
        FleetDiscovery.active(offers) shouldBe listOf(
            FleetServiceTarget("com.example.approved", "O"),
            FleetServiceTarget("com.example.swarm", "S"),
        )
        val turnedOn = FleetGrants.setEnabled(grants, "com.example.edl", release, enabled = true)
        FleetTrust.access(release, release, FleetGrants.find(turnedOn, "com.example.edl")) shouldBe FleetAccess.Active
        val approved = FleetGrants.approve(emptyList(), "com.example.fresh", otherCert)
        FleetTrust.access(otherCert, release, FleetGrants.find(approved, "com.example.fresh")) shouldBe FleetAccess.Active
        FleetDiscovery.classify(
            "app",
            release,
            emptyList(),
            List(20) { FleetServiceCandidate("com.example.p$it", "C", "P", release) },
        ).size shouldBe FleetDiscovery.MAX_PROVIDERS
    }

    test("hostile documents and a non-png icon become empty") {
        FleetCodec.commands("[{") shouldBe emptyList()
        FleetCodec.commands("[" + "[".repeat(400)) shouldBe emptyList()
        FleetCodec.snippets("{not-a-list}") shouldBe emptyList()
        FleetCodec.actions("x".repeat(70_000)) shouldBe emptyList()
        val kept = FleetCodec.commands("""[{"name":"Build","description":"ok"},{"name":"../x","description":"no"}]""")
        kept.map { it.name } shouldBe listOf("build")
        val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        val provider = FakeFleetProvider(iconBytes = png)
        val snapshot = FleetSession.load(true, listOf(FleetEndpoint("com.example.edl", provider)))
        snapshot.apps.single().iconPng?.contentEquals(png) shouldBe true
        val huge = ByteArray(FleetIcons.MAX_BYTES + 1)
        png.copyInto(huge)
        FleetSession.load(true, listOf(FleetEndpoint("com.example.edl", FakeFleetProvider(iconBytes = huge))))
            .apps.single().iconPng shouldBe null
        FleetSession.load(true, listOf(FleetEndpoint("com.example.edl", FakeFleetProvider(iconBytes = byteArrayOf(1, 2, 3, 4)))))
            .apps.single().iconPng shouldBe null
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
        val edl = FakeFleetProvider(
            snippetPayload = """[{"id":"adb1","label":"devices","kind":"adb","text":"adb devices"}]""",
        )
        val swarm = FakeFleetProvider(
            namespaceToken = "swarm",
            snippetPayload = """[{"id":"tx1","label":"info","kind":"termux","command":"termux-info","args":[]}]""",
        )
        val snapshot = FleetSession.load(
            true,
            listOf(
                FleetEndpoint("com.example.edl", edl),
                FleetEndpoint("com.example.swarm", swarm),
            ),
        )
        val adb = snapshot.panel().sections.first().snippets.single()
        val adbOut = adb.insertText(edl)
        adbOut.text shouldBe "adb devices"
        adbOut.execute shouldBe false
        edl.calls.none { it.startsWith("run:") } shouldBe true
        val termux = snapshot.panel().sections.last().snippets.single()
        val termuxOut = termux.insertText(swarm)
        termuxOut.text shouldBe "text-for-termux-info"
        termuxOut.execute shouldBe false
        swarm.calls shouldContain "run:termux-info"
        edl.calls.none { it.startsWith("run:") } shouldBe true
    }
})
