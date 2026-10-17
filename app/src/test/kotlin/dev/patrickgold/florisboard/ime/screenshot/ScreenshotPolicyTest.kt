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

package dev.patrickgold.florisboard.ime.screenshot

import dev.patrickgold.florisboard.ime.toolbar.ToolbarActionId
import dev.patrickgold.florisboard.ime.toolbar.ToolsCatalog
import dev.patrickgold.florisboard.ime.toolbar.ToolsTab
import dev.patrickgold.florisboard.ime.toolbar.ui.ToolsPanelItems
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import java.io.File
import java.time.ZoneOffset

class ScreenshotPolicyTest : FunSpec({
    val flat = "dev.patrickgold.florisboard/dev.patrickgold.florisboard.ime.screenshot.ScreenshotAccessibilityService"
    val short = "dev.patrickgold.florisboard/.ScreenshotAccessibilityService"

    test("Android 10 and below never capture, even when the helper is on") {
        ScreenshotPolicy.decide(29, helperEnabled = true) shouldBe ScreenshotTap.NeedsAndroid11
        ScreenshotPolicy.decide(26, helperEnabled = false) shouldBe ScreenshotTap.NeedsAndroid11
    }

    test("Android 11 asks for the helper when it is off, and captures when it is on") {
        ScreenshotPolicy.decide(30, helperEnabled = false) shouldBe ScreenshotTap.NeedsHelper
        ScreenshotPolicy.decide(30, helperEnabled = true) shouldBe ScreenshotTap.Capture
        ScreenshotPolicy.decide(36, helperEnabled = true) shouldBe ScreenshotTap.Capture
    }

    test("the tile setting hides Screenshot without dropping the user's other hidden names") {
        val hidden = setOf("VOICE")
        ScreenshotPolicy.panelHidden(hidden, tileShown = true) shouldBe hidden
        ScreenshotPolicy.panelHidden(hidden, tileShown = false) shouldBe setOf("VOICE", "SCREENSHOT")
    }

    test("the helper is on only for an exact component match while accessibility is enabled") {
        ScreenshotPolicy.helperEnabled(false, flat, flat, short) shouldBe false
        ScreenshotPolicy.helperEnabled(true, null, flat, short) shouldBe false
        ScreenshotPolicy.helperEnabled(true, "", flat, short) shouldBe false
        ScreenshotPolicy.helperEnabled(true, "other.app/.Service", flat, short) shouldBe false
        ScreenshotPolicy.helperEnabled(true, flat + "Extra", flat, short) shouldBe false
        ScreenshotPolicy.helperEnabled(true, "other.app/.Service:$flat", flat, short) shouldBe true
        ScreenshotPolicy.helperEnabled(true, " $short ", flat, short) shouldBe true
        ScreenshotPolicy.helperEnabled(true, flat, "", short) shouldBe false
    }

    test("file names come from the clock and stay inside one path segment") {
        val name = ScreenshotPolicy.filename(0L, ZoneOffset.UTC)
        name shouldBe "screenshot-19700101-000000-000.png"
        ScreenshotPolicy.isSafeFilename(name) shouldBe true
        ScreenshotPolicy.isSafeFilename("screenshot-19700101-000000-000.png") shouldBe true
        ScreenshotPolicy.isSafeFilename("../screenshot-19700101-000000-000.png") shouldBe false
        ScreenshotPolicy.isSafeFilename("notes.png") shouldBe false
        name shouldNotContain "/"
        name shouldNotContain "password"
        ScreenshotPolicy.RELATIVE_PATH shouldBe "Pictures/Victorious Keyboard/"
        ScreenshotPolicy.HIDE_DELAY_MS shouldBe 400L
    }

    test("Screenshot is the last Quick tile and has a panel icon") {
        ToolsCatalog.actions(ToolsTab.QUICK).last() shouldBe ToolbarActionId.SCREENSHOT
        ToolsPanelItems.item(ToolbarActionId.SCREENSHOT) shouldBe ToolsPanelItems.item(ToolbarActionId.SCREENSHOT)
        (ToolsPanelItems.item(ToolbarActionId.SCREENSHOT) != null) shouldBe true
    }
})

class ScreenshotConfigTest : FunSpec({
    test("the helper asks for screenshots and nothing else") {
        val xml = File("src/main/res/xml/screenshot_accessibility_service.xml").readText()
        xml shouldContain "android:canRetrieveWindowContent=\"false\""
        xml shouldContain "android:canTakeScreenshot=\"true\""
        xml shouldContain "@string/screenshot__service_description"
        xml shouldNotContain "typeAllMask"
        xml shouldNotContain "accessibilityEventTypes"
        xml shouldNotContain "canRequestFilterKeyEvents"
        xml shouldNotContain "packageNames"

        val manifest = File("src/main/AndroidManifest.xml").readText()
        manifest shouldContain "dev.patrickgold.florisboard.ime.screenshot.ScreenshotAccessibilityService"
        manifest shouldContain "android.permission.BIND_ACCESSIBILITY_SERVICE"
        manifest shouldNotContain "android.permission.INTERNET"
        manifest shouldNotContain "READ_FRAME_BUFFER"

        val strings = File("src/main/res/values/strings.xml").readText()
        strings shouldContain "Used only to take screenshots when you tap the keyboard"

        val service = File(
            "src/main/kotlin/dev/patrickgold/florisboard/ime/screenshot/ScreenshotAccessibilityService.kt",
        ).readText()
        service shouldNotContain "getRootInActiveWindow"
        service shouldNotContain "android.util.Log"
        service shouldNotContain "flog"
    }
})
