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

package dev.patrickgold.florisboard.ime.mode

import dev.patrickgold.florisboard.ime.developer.LanguageProfile
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

private fun prefs(enabled: Boolean = false, profile: LanguageProfile = LanguageProfile.PYTHON, terminal: Boolean = false) =
    DeveloperRowPrefs(enabled, profile, terminal)

class OperatingModeTest : FunSpec({
    test("enum names are persisted: this list may only grow at the end") {
        OperatingMode.entries.map { it.name } shouldBe
            listOf("STANDARD", "DEVELOPER", "TERMUX", "CLAUDE_CODE", "KALI", "NETHUNTER_AI")
    }

    test("only modes with real behaviour are selectable") {
        OperatingMode.selectable shouldBe listOf(OperatingMode.STANDARD, OperatingMode.DEVELOPER, OperatingMode.TERMUX)
        for (m in listOf(OperatingMode.CLAUDE_CODE, OperatingMode.KALI, OperatingMode.NETHUNTER_AI)) {
            m.isSelectable shouldBe false
            OperatingMode.sanitize(m) shouldBe OperatingMode.STANDARD
        }
    }

    test("sanitize keeps selectable modes and maps null to Standard") {
        for (m in OperatingMode.selectable) OperatingMode.sanitize(m) shouldBe m
        OperatingMode.sanitize(null) shouldBe OperatingMode.STANDARD
    }

    test("Standard mode leaves the developer row exactly to the user's preferences") {
        ModePresets.developerRow(OperatingMode.STANDARD, prefs(enabled = false)) shouldBe
            EffectiveDeveloperRow(false, LanguageProfile.PYTHON, false)
        ModePresets.developerRow(OperatingMode.STANDARD, prefs(enabled = true, terminal = true)) shouldBe
            EffectiveDeveloperRow(true, LanguageProfile.PYTHON, true)
    }

    test("Developer mode always shows the row with the user's profile and terminal choice") {
        ModePresets.developerRow(OperatingMode.DEVELOPER, prefs(enabled = false, terminal = false)) shouldBe
            EffectiveDeveloperRow(true, LanguageProfile.PYTHON, false)
    }

    test("Termux mode forces the row, terminal keys and Shell symbols") {
        ModePresets.developerRow(OperatingMode.TERMUX, prefs(enabled = false, profile = LanguageProfile.JSON, terminal = false)) shouldBe
            EffectiveDeveloperRow(true, LanguageProfile.SHELL, true)
    }

    test("planned modes behave like Standard") {
        for (m in listOf(OperatingMode.CLAUDE_CODE, OperatingMode.KALI, OperatingMode.NETHUNTER_AI)) {
            ModePresets.developerRow(m, prefs(enabled = false)) shouldBe ModePresets.developerRow(OperatingMode.STANDARD, prefs(enabled = false))
        }
    }

    test("known terminal apps are suggested Termux mode; others and null are not") {
        ModeSuggester.suggest("com.termux") shouldBe OperatingMode.TERMUX
        ModeSuggester.suggest("org.connectbot") shouldBe OperatingMode.TERMUX
        ModeSuggester.suggest("com.example.notes") shouldBe null
        ModeSuggester.suggest("") shouldBe null
        ModeSuggester.suggest(null) shouldBe null
    }

    test("package matching is exact, not by prefix or substring") {
        ModeSuggester.suggest("com.termux.api") shouldBe null
        ModeSuggester.suggest("evil.com.termux") shouldBe null
        ModeSuggester.suggest("COM.TERMUX") shouldBe null
    }

    test("user overrides win, but never to a mode that is not available") {
        ModeSuggester.suggest("com.example.ide", mapOf("com.example.ide" to OperatingMode.DEVELOPER)) shouldBe OperatingMode.DEVELOPER
        ModeSuggester.suggest("com.termux", mapOf("com.termux" to OperatingMode.STANDARD)) shouldBe OperatingMode.STANDARD
        ModeSuggester.suggest("com.example.x", mapOf("com.example.x" to OperatingMode.KALI)) shouldBe null
    }

    test("active mode: auto-detect off ignores the app; on uses the suggestion; otherwise the default") {
        ActiveMode.resolve(OperatingMode.STANDARD, autoDetect = false, packageName = "com.termux") shouldBe OperatingMode.STANDARD
        ActiveMode.resolve(OperatingMode.STANDARD, autoDetect = true, packageName = "com.termux") shouldBe OperatingMode.TERMUX
        ActiveMode.resolve(OperatingMode.DEVELOPER, autoDetect = true, packageName = "com.example.notes") shouldBe OperatingMode.DEVELOPER
        ActiveMode.resolve(OperatingMode.DEVELOPER, autoDetect = true, packageName = null) shouldBe OperatingMode.DEVELOPER
    }

    test("a stored default that is not available falls back to Standard") {
        ActiveMode.resolve(OperatingMode.KALI, autoDetect = false, packageName = null) shouldBe OperatingMode.STANDARD
        ActiveMode.resolve(OperatingMode.NETHUNTER_AI, autoDetect = true, packageName = "com.example.notes") shouldBe OperatingMode.STANDARD
    }
})
