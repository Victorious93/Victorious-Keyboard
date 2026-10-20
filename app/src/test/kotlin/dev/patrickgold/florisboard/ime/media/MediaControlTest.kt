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

package dev.patrickgold.florisboard.ime.media

import android.media.AudioManager
import android.view.KeyEvent
import dev.patrickgold.florisboard.ime.developer.DevKeyAction
import dev.patrickgold.florisboard.ime.developer.DevStep
import dev.patrickgold.florisboard.ime.developer.DeveloperKeyResolver
import dev.patrickgold.florisboard.ime.developer.DeveloperRows
import dev.patrickgold.florisboard.ime.developer.LanguageProfile
import dev.patrickgold.florisboard.ime.developer.RowOptions
import dev.patrickgold.florisboard.ime.developer.TerminalRowLayout
import dev.patrickgold.florisboard.ime.terminal.Modifiers
import dev.patrickgold.florisboard.lib.fleet.CertDigest
import dev.patrickgold.florisboard.lib.fleet.FleetGrant
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import kotlinx.serialization.json.long

class MediaControlTest : FunSpec({
    test("media key codes and volume flags match the platform") {
        MediaCodes.PLAY_PAUSE shouldBe KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
        MediaCodes.NEXT shouldBe KeyEvent.KEYCODE_MEDIA_NEXT
        MediaCodes.PREVIOUS shouldBe KeyEvent.KEYCODE_MEDIA_PREVIOUS
        MediaCodes.REWIND shouldBe KeyEvent.KEYCODE_MEDIA_REWIND
        MediaCodes.FAST_FORWARD shouldBe KeyEvent.KEYCODE_MEDIA_FAST_FORWARD
        MediaCodes.ACTION_DOWN shouldBe KeyEvent.ACTION_DOWN
        MediaCodes.ACTION_UP shouldBe KeyEvent.ACTION_UP
        MediaCodes.STREAM_MUSIC shouldBe AudioManager.STREAM_MUSIC
        MediaCodes.ADJUST_LOWER shouldBe AudioManager.ADJUST_LOWER
        MediaCodes.ADJUST_RAISE shouldBe AudioManager.ADJUST_RAISE
        MediaCodes.FLAG_SHOW_UI shouldBe AudioManager.FLAG_SHOW_UI
    }

    test("without the bridge every transport button is a down event then an up event") {
        val sink = FakeSink()
        for (command in listOf(
            MediaCommand.PLAY_PAUSE,
            MediaCommand.PREVIOUS,
            MediaCommand.NEXT,
            MediaCommand.REWIND,
            MediaCommand.FAST_FORWARD,
        )) {
            sink.presses.clear()
            val outcome = MediaControl.run(MediaControl.plan(command, useBridge = false, id = "m1"), sink)
            outcome shouldBe MediaOutcome.Keys
            val code = MediaCodes.keyCode(command)!!
            sink.presses shouldBe listOf(KeyEvent.ACTION_DOWN to code, KeyEvent.ACTION_UP to code)
        }
        sink.sent.shouldBeEmpty()
    }

    test("volume always adjusts the music stream and never calls the bridge") {
        val sink = FakeSink("""{"ok":true}""")
        MediaControl.run(MediaControl.plan(MediaCommand.VOLUME_DOWN, useBridge = true, id = "m1"), sink) shouldBe
            MediaOutcome.Volume
        MediaControl.run(MediaControl.plan(MediaCommand.VOLUME_UP, useBridge = true, id = "m1"), sink) shouldBe
            MediaOutcome.Volume
        sink.volumes shouldBe listOf(
            Triple(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI),
            Triple(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI),
        )
        sink.sent.shouldBeEmpty()
        sink.presses.shouldBeEmpty()
    }

    test("player volume uses media.volume and never the music stream") {
        FileManagerProtocol.nextPlayerLevel(null, louder = true) shouldBe 60
        FileManagerProtocol.nextPlayerLevel(null, louder = false) shouldBe 40
        FileManagerProtocol.nextPlayerLevel(5, louder = false) shouldBe 0
        FileManagerProtocol.nextPlayerLevel(95, louder = true) shouldBe 100
        val sink = FakeSink("""{"ok":true}""")
        MediaControl.run(MediaControl.planPlayerVolume(60, useBridge = true, id = "p"), sink) shouldBe
            MediaOutcome.Bridged(null)
        sink.sent.single() shouldContain "\"op\":\"media.volume\""
        sink.sent.single() shouldContain "\"level\":60"
        sink.volumes.shouldBeEmpty()
        sink.presses.shouldBeEmpty()
        val off = FakeSink("""{"ok":true}""")
        MediaControl.run(MediaControl.planPlayerVolume(40, useBridge = false, id = "p"), off) shouldBe
            MediaOutcome.Unavailable
        off.sent.shouldBeEmpty()
        off.volumes.shouldBeEmpty()
    }

    test("an approved bridge sends the command and falls back to the media key on NO_SESSION") {
        val sink = FakeSink("""{"ok":false,"error":"NO_SESSION"}""")
        val outcome = MediaControl.run(MediaControl.plan(MediaCommand.PLAY_PAUSE, useBridge = true, id = "m1"), sink)
        outcome shouldBe MediaOutcome.FellBack("NO_SESSION")
        sink.sent.single() shouldContain "\"op\":\"media.toggle\""
        sink.presses shouldBe listOf(
            KeyEvent.ACTION_DOWN to KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            KeyEvent.ACTION_UP to KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
        )
        val idle = FakeSink("""{"ok":false,"error":"no_session"}""")
        MediaControl.run(MediaControl.plan(MediaCommand.NEXT, useBridge = true, id = "n"), idle) shouldBe
            MediaOutcome.FellBack("no_session")
        idle.presses shouldBe listOf(
            KeyEvent.ACTION_DOWN to KeyEvent.KEYCODE_MEDIA_NEXT,
            KeyEvent.ACTION_UP to KeyEvent.KEYCODE_MEDIA_NEXT,
        )
    }

    test("external_control_disabled is its own result and does not change the music stream") {
        val sink = FakeSink("""{"ok":false,"error":"external_control_disabled"}""")
        MediaControl.run(MediaControl.plan(MediaCommand.PLAY_PAUSE, useBridge = true, id = "m"), sink) shouldBe
            MediaOutcome.ExternalControl(fellBack = true)
        sink.presses shouldBe listOf(
            KeyEvent.ACTION_DOWN to KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            KeyEvent.ACTION_UP to KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
        )
        sink.volumes.shouldBeEmpty()
        val player = FakeSink("""{"ok":false,"error":{"category":"external_control_disabled"}}""")
        MediaControl.run(MediaControl.planPlayerVolume(40, useBridge = true, id = "p"), player) shouldBe
            MediaOutcome.ExternalControl(fellBack = false)
        player.volumes.shouldBeEmpty()
        player.presses.shouldBeEmpty()
        player.sent.single() shouldContain "\"op\":\"media.volume\""
    }

    test("rewind and fast-forward over the bridge are ten-second seeks, with a key fallback") {
        val sink = FakeSink("""{"ok":true}""")
        MediaControl.run(MediaControl.plan(MediaCommand.REWIND, useBridge = true, id = "rw"), sink) shouldBe
            MediaOutcome.Bridged(null)
        MediaControl.run(MediaControl.plan(MediaCommand.FAST_FORWARD, useBridge = true, id = "ff"), sink) shouldBe
            MediaOutcome.Bridged(null)
        val rewind = Json.parseToJsonElement(sink.sent[0]).jsonObject
        val forward = Json.parseToJsonElement(sink.sent[1]).jsonObject
        rewind["op"]!!.jsonPrimitive.content shouldBe "media.seekBy"
        rewind["deltaMs"]!!.jsonPrimitive.long shouldBe -10_000L
        forward["deltaMs"]!!.jsonPrimitive.long shouldBe 10_000L
        sink.presses.shouldBeEmpty()

        val missed = FakeSink("not json")
        MediaControl.run(MediaControl.plan(MediaCommand.REWIND, useBridge = true, id = "rw"), missed) shouldBe
            MediaOutcome.FellBack("unreadable")
        missed.presses shouldBe listOf(
            KeyEvent.ACTION_DOWN to KeyEvent.KEYCODE_MEDIA_REWIND,
            KeyEvent.ACTION_UP to KeyEvent.KEYCODE_MEDIA_REWIND,
        )
    }

    test("a bridge failure with no media-key fallback reports no session or unavailable") {
        val none = MediaPlan.Bridge(FileManagerProtocol.encode("media.seek", "s", positionMs = 50)!!, fallback = null)
        MediaControl.run(none, FakeSink("""{"ok":false,"error":"NO_SESSION"}""")) shouldBe
            MediaOutcome.NoSession("NO_SESSION")
        MediaControl.run(none, FakeSink("""{"ok":false,"error":"busy"}""")) shouldBe MediaOutcome.Unavailable
        MediaControl.run(none, object : MediaSink {
            override fun press(action: Int, keyCode: Int) = Unit
            override fun volume(stream: Int, direction: Int, flags: Int) = Unit
            override fun exchange(body: String): String = throw IllegalStateException("down")
        }) shouldBe MediaOutcome.Unavailable
    }

    test("media.state returns the title and drops a title that is not text") {
        val raw = """{"playing":true,"positionMs":12,"durationMs":99,"title":"  Hi\u0001 there  ","source":"fm"}"""
        val state = FileManagerProtocol.parse(raw) as FileManagerReply.State
        state.playing shouldBe true
        state.positionMs shouldBe 12L
        state.durationMs shouldBe 99L
        state.title shouldBe "Hi there"
        state.source shouldBe "fm"
        val long = "x".repeat(200)
        (FileManagerProtocol.parse("""{"title":"$long"}""") as FileManagerReply.State).title.length shouldBe 120
        FileManagerProtocol.parse("x".repeat(8_001)) shouldBe FileManagerReply.Unreadable
        FileManagerProtocol.parse("""{"ok":false,"error":"NO_SESSION","title":"secret"}""") shouldBe
            FileManagerReply.Failed("NO_SESSION")
        MediaControl.run(MediaPlan.Bridge(FileManagerProtocol.encode("media.state", "st")!!, null), FakeSink(raw)) shouldBe
            MediaOutcome.Bridged(state)
    }

    test("each File Manager command encodes, and a bad argument is refused") {
        for (op in FileManagerProtocol.OPS) {
            val body = when (op) {
                "media.seek" -> FileManagerProtocol.encode(op, "id", positionMs = 0)
                "media.seekBy" -> FileManagerProtocol.encode(op, "id", deltaMs = 1)
                "media.volume" -> FileManagerProtocol.encode(op, "id", level = 0)
                else -> FileManagerProtocol.encode(op, "id")
            }
            body.shouldNotBeNull()
            body shouldContain "\"op\":\"$op\""
            body shouldNotContain "clipboard"
        }
        FileManagerProtocol.encode("media.volume", "id", level = 100).shouldNotBeNull()
        FileManagerProtocol.encode("media.volume", "id", level = -1).shouldBeNull()
        FileManagerProtocol.encode("media.volume", "id", level = 101).shouldBeNull()
        FileManagerProtocol.encode("media.volume", "id").shouldBeNull()
        FileManagerProtocol.encode("media.seek", "id", positionMs = -1).shouldBeNull()
        FileManagerProtocol.encode("media.seek", "id").shouldBeNull()
        FileManagerProtocol.encode("media.seekBy", "id").shouldBeNull()
        FileManagerProtocol.encode("keyboard.input.text", "id").shouldBeNull()
        FileManagerProtocol.encode("media.play", "").shouldBeNull()
        FileManagerProtocol.encode("media.play", "id", level = 1).shouldBeNull()
    }

    test("the bridge is used only for an approved File Manager certificate, and seek starts off") {
        val cert = "AB".repeat(32)
        val other = "CD".repeat(32)
        val printed = (0 until 32).joinToString(":") { "AB" }
        FileManagerProtocol.allowed("ai.victorious.victorfiles") shouldBe false
        FileManagerProtocol.pin(emptyList(), "ai.victorious.victorfiles", cert).shouldBeNull()
        FileManagerProtocol.pin(emptyList(), FileManagerProtocol.PACKAGE, "ABCD").shouldBeNull()
        FileManagerProtocol.pin(emptyList(), FileManagerProtocol.PACKAGE, "").shouldBeNull()

        val pinned = FileManagerProtocol.pin(emptyList(), FileManagerProtocol.PACKAGE, printed).shouldNotBeNull()
        val grant = pinned.single()
        grant.certSha256 shouldBe cert
        grant.approved shouldBe true
        grant.enabled shouldBe true
        FileManagerProtocol.trusted(FileManagerProtocol.PACKAGE, cert, grant, seekEnabled = true) shouldBe true
        FileManagerProtocol.trusted(FileManagerProtocol.PACKAGE, cert, grant, seekEnabled = false) shouldBe false
        FileManagerProtocol.trusted(FileManagerProtocol.PACKAGE, other, grant, seekEnabled = true) shouldBe false
        FileManagerProtocol.trusted("com.example.player", cert, grant, seekEnabled = true) shouldBe false
        FileManagerProtocol.trusted(FileManagerProtocol.PACKAGE, cert, null, seekEnabled = true) shouldBe false
        FileManagerProtocol.trusted(
            FileManagerProtocol.PACKAGE,
            cert,
            grant.copy(approved = false),
            seekEnabled = true,
        ) shouldBe false
        FileManagerProtocol.trusted(
            FileManagerProtocol.PACKAGE,
            cert,
            grant.copy(enabled = false),
            seekEnabled = true,
        ) shouldBe false

        val debug = FleetGrant(FileManagerProtocol.PACKAGE_DEBUG, cert, approved = true, enabled = true)
        val both = listOf(FileManagerProtocol.PACKAGE to cert, FileManagerProtocol.PACKAGE_DEBUG to cert)
        FileManagerProtocol.bridgePackage(both, pinned + debug, seekEnabled = false).shouldBeNull()
        FileManagerProtocol.bridgePackage(both, pinned + debug, seekEnabled = true) shouldBe FileManagerProtocol.PACKAGE
        FileManagerProtocol.bridgePackage(both.reversed(), listOf(debug), seekEnabled = true) shouldBe
            FileManagerProtocol.PACKAGE_DEBUG
    }

    test("the debug certificate is pinned, release is not auto-trusted, and a missing app is ignored") {
        val debugCert = CertDigest.normalize(FileManagerProtocol.DEBUG_CERT_SHA256)
        debugCert.length shouldBe 64
        FileManagerProtocol.pinnedCert(FileManagerProtocol.PACKAGE) shouldBe ""
        FileManagerProtocol.pinnedCert(FileManagerProtocol.PACKAGE_DEBUG) shouldBe FileManagerProtocol.DEBUG_CERT_SHA256
        FileManagerProtocol.actionFor(FileManagerProtocol.PACKAGE) shouldBe FileManagerProtocol.ACTION
        FileManagerProtocol.actionFor(FileManagerProtocol.PACKAGE_DEBUG) shouldBe FileManagerProtocol.ACTION_DEBUG
        FileManagerProtocol.actionFor("ai.victorious.victorfiles").shouldBeNull()

        FileManagerProtocol.trusted(
            FileManagerProtocol.PACKAGE_DEBUG,
            debugCert,
            grant = null,
            seekEnabled = true,
        ) shouldBe true
        FileManagerProtocol.trusted(
            FileManagerProtocol.PACKAGE_DEBUG,
            debugCert,
            grant = null,
            seekEnabled = false,
        ) shouldBe false
        FileManagerProtocol.trusted(
            FileManagerProtocol.PACKAGE,
            debugCert,
            grant = null,
            seekEnabled = true,
        ) shouldBe false
        FileManagerProtocol.trusted(
            FileManagerProtocol.PACKAGE_DEBUG,
            "AB".repeat(32),
            grant = null,
            seekEnabled = true,
        ) shouldBe false
        val switchedOff = FleetGrant(
            FileManagerProtocol.PACKAGE_DEBUG,
            debugCert,
            approved = true,
            enabled = false,
        )
        FileManagerProtocol.trusted(
            FileManagerProtocol.PACKAGE_DEBUG,
            debugCert,
            switchedOff,
            seekEnabled = true,
        ) shouldBe false

        val seen = listOf(
            FileManagerProtocol.Seen(
                "ai.victorious.victorfiles",
                "ai.victorious.victorfiles.action.BRIDGE",
                debugCert,
            ),
            FileManagerProtocol.Seen(
                FileManagerProtocol.PACKAGE_DEBUG,
                FileManagerProtocol.ACTION,
                debugCert,
            ),
            FileManagerProtocol.Seen(
                FileManagerProtocol.PACKAGE_DEBUG,
                FileManagerProtocol.ACTION_DEBUG,
                FileManagerProtocol.DEBUG_CERT_SHA256,
            ),
            FileManagerProtocol.Seen(FileManagerProtocol.PACKAGE, FileManagerProtocol.ACTION, "CD".repeat(32)),
        )
        FileManagerProtocol.discover(seen) shouldBe listOf(
            FileManagerProtocol.PACKAGE to "CD".repeat(32),
            FileManagerProtocol.PACKAGE_DEBUG to debugCert,
        )
        FileManagerProtocol.discover(emptyList()) shouldBe emptyList()
        FileManagerProtocol.discover(
            listOf(
                FileManagerProtocol.Seen(
                    FileManagerProtocol.PACKAGE_DEBUG,
                    FileManagerProtocol.ACTION,
                    debugCert,
                ),
            ),
        ) shouldBe emptyList()
        FileManagerProtocol.bridgePackage(FileManagerProtocol.discover(seen), emptyList(), seekEnabled = true) shouldBe
            FileManagerProtocol.PACKAGE_DEBUG
        FileManagerProtocol.bridgePackage(FileManagerProtocol.discover(seen), emptyList(), seekEnabled = false)
            .shouldBeNull()

        val manifest = File("src/main/AndroidManifest.xml").readText()
        manifest shouldContain FileManagerProtocol.ACTION
        manifest shouldContain FileManagerProtocol.ACTION_DEBUG
        manifest.contains("<package ") shouldBe false
        manifest.contains("victorfiles") shouldBe false
        val runtime = File("src/main/kotlin/dev/patrickgold/florisboard/ime/media/MediaRuntime.kt").readText()
        runtime shouldContain "queryIntentServices"
        runtime shouldNotContain "victorfiles"
    }

    test("a blank terminal row is unchanged, and a saved order can add a media key") {
        val base = DeveloperRows.row(
            LanguageProfile.SHELL,
            RowOptions(terminalKeys = true, navigationKeys = true, symbols = true),
        )
        TerminalRowLayout.arrange(base, "", "") shouldBe base
        TerminalRowLayout.defaultIds shouldNotContain "media_play"
        base.map { it.label } shouldNotContain "Play"
        val keys = TerminalRowLayout.keysFor("media_play,media_vol_up")
        keys.map { it.label } shouldBe listOf("Play", "Vol+")
        val play = keys[0].action as DevKeyAction.Media
        play.command shouldBe MediaCommand.PLAY_PAUSE
        val resolved = DeveloperKeyResolver.resolve(play, Modifiers(ctrl = true))
        resolved.consumesModifiers shouldBe false
        resolved.steps shouldBe listOf(DevStep.Media(MediaCommand.PLAY_PAUSE))
    }
})

private class FakeSink(var reply: String? = null) : MediaSink {
    val presses = mutableListOf<Pair<Int, Int>>()
    val volumes = mutableListOf<Triple<Int, Int, Int>>()
    val sent = mutableListOf<String>()

    override fun press(action: Int, keyCode: Int) {
        presses += action to keyCode
    }

    override fun volume(stream: Int, direction: Int, flags: Int) {
        volumes += Triple(stream, direction, flags)
    }

    override fun exchange(body: String): String? {
        sent += body
        return reply
    }
}
