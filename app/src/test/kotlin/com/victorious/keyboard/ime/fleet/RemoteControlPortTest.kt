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

package com.victorious.keyboard.ime.fleet

import android.media.AudioManager
import android.view.KeyEvent
import com.victorious.keyboard.lib.fleet.RemoteEnvelope
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import java.io.File

class RemoteControlPortTest : FunSpec({
    test("remote media key codes match the platform") {
        RemoteEnvelope.KEY_PLAY_PAUSE shouldBe KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
        RemoteEnvelope.KEY_NEXT shouldBe KeyEvent.KEYCODE_MEDIA_NEXT
        RemoteEnvelope.KEY_PREVIOUS shouldBe KeyEvent.KEYCODE_MEDIA_PREVIOUS
        RemoteEnvelope.KEY_REWIND shouldBe KeyEvent.KEYCODE_MEDIA_REWIND
        RemoteEnvelope.KEY_FAST_FORWARD shouldBe KeyEvent.KEYCODE_MEDIA_FAST_FORWARD
        AudioManager.STREAM_MUSIC shouldBe 3
        AudioManager.FLAG_SHOW_UI shouldBe 1
    }

    test("the remote port does not type or read the editor, the clipboard, or the vault") {
        val port = File("src/main/kotlin/com/victorious/keyboard/ime/fleet/RemoteControlPort.kt").readText()
        val service = File("src/main/kotlin/com/victorious/keyboard/fleet/KeyboardBridgeService.kt").readText()
        service shouldContain "RemoteEnvelope.handle("
        service shouldContain "BridgeInput.handle("
        for (forbidden in listOf(
            "commitText", "sendDownUpKeyEvent", "getTextBeforeCursor", "getTextAfterCursor",
            "getSelectedText", "clipboard", "Clipboard", "vault", "Vault", "Log.", "println",
        )) {
            port shouldNotContain forbidden
        }
    }
})
