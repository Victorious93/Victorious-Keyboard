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

package com.victorious.keyboard.app

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import java.io.File

/** The rename moves code and leaves stored identities where Android and the user already have them. */
class NamespaceStabilityTest : FunSpec({
    test("sticky component names stay, and the implementation moved") {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        manifest shouldContain "dev.patrickgold.florisboard.FlorisImeService"
        manifest shouldContain "dev.patrickgold.florisboard.FlorisSpellCheckerService"
        manifest shouldContain "dev.patrickgold.florisboard.ime.screenshot.ScreenshotAccessibilityService"
        manifest shouldContain "dev.patrickgold.florisboard.SettingsLauncherAlias"
        manifest shouldContain "com.victorious.keyboard.FlorisApplication"
        manifest shouldContain "com.victorious.keyboard.app.FlorisAppActivity"
        // INTERNET exists only for the opt-in pack update check; NetworkUseTest pins that.
        Regex("android.permission.INTERNET").findAll(manifest).count() shouldBe 1

        val gradle = File("build.gradle.kts").readText()
        gradle shouldContain "namespace = \"com.victorious.keyboard\""
        gradle shouldContain "applicationId = \"com.victorious.keyboard\""

        FlorisPreferenceModel.NAME shouldBe "florisboard-app-prefs"

        val jni = File("src/main/cpp/llama_jni.cpp").readText()
        jni shouldContain "Java_com_victorious_keyboard_ime_ai_LlamaNative_nativeLoad"
        jni shouldNotContain "Java_dev_patrickgold_florisboard_"

        val ime = File("src/main/kotlin/dev/patrickgold/florisboard/FlorisImeService.kt").readText()
        ime shouldContain "class FlorisImeService : com.victorious.keyboard.FlorisImeService()"
        val spell = File("src/main/kotlin/dev/patrickgold/florisboard/FlorisSpellCheckerService.kt").readText()
        spell shouldContain "class FlorisSpellCheckerService : com.victorious.keyboard.FlorisSpellCheckerService()"
        val shot = File(
            "src/main/kotlin/dev/patrickgold/florisboard/ime/screenshot/ScreenshotAccessibilityService.kt",
        ).readText()
        shot shouldContain "com.victorious.keyboard.ime.screenshot.ScreenshotAccessibilityService()"

        val restore = File(
            "src/main/kotlin/com/victorious/keyboard/app/settings/advanced/RestoreScreen.kt",
        ).readText()
        restore shouldContain "\"com.victorious.keyboard\""
        restore shouldContain "\"dev.patrickgold.florisboard\""
    }
})
