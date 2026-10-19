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

package dev.patrickgold.florisboard.ime.fleet

import dev.patrickgold.florisboard.lib.fleet.BridgeProtocol
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import java.io.File

class BridgeConfigTest : FunSpec({
    test("the bridge service is exported without a permission and does not read the editor") {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        val service = File("src/main/kotlin/com/victorious/keyboard/fleet/KeyboardBridgeService.kt").readText()
        manifest shouldContain "com.victorious.keyboard.fleet.KeyboardBridgeService"
        manifest shouldContain BridgeProtocol.ACTION
        val block = manifest.substringAfter("KeyboardBridgeService").substringBefore("</service>")
        block shouldNotContain "android:permission"
        service shouldNotContain "currentInputConnection"
        service shouldNotContain "getTextBeforeCursor"
        service shouldNotContain "commitText"
        service shouldNotContain "keystroke"
        service shouldContain "getCallingUid"
        service shouldContain "getPackagesForUid"
        service shouldContain "GET_SIGNING_CERTIFICATES"
    }

    test("the input port checks for password fields and never reads or logs the editor") {
        val port = File("src/main/kotlin/dev/patrickgold/florisboard/ime/fleet/ExternalInputPort.kt").readText()
        port shouldContain "isPasswordLike()"
        for (forbidden in listOf(
            "getTextBeforeCursor", "getTextAfterCursor", "getSelectedText", "getExtractedText", "textBeforeSelection",
            "selectedText", "activeContent", "Log.", "Timber", "println", "toolbar", "Toolbar", "clipboard", "Clipboard",
        )) {
            port shouldNotContain forbidden
        }
    }

    test("the service delegates all input rules to BridgeInput and logs nothing") {
        val service = File("src/main/kotlin/com/victorious/keyboard/fleet/KeyboardBridgeService.kt").readText()
        service shouldContain "BridgeInput.handle("
        service shouldNotContain "Log."
        service shouldNotContain "Timber"
        service shouldNotContain "println"
    }

    test("the manifest still has no INTERNET permission") {
        File("src/main/AndroidManifest.xml").readText() shouldNotContain "android.permission.INTERNET"
    }
})
