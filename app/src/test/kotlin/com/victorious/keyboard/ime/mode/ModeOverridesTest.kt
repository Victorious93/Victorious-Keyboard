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

package com.victorious.keyboard.ime.mode

import com.victorious.keyboard.ime.developer.LanguageProfile
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class ModeOverridesTest : FunSpec({
    test("equals sign or whitespace separates the package from the mode; mode is case-insensitive") {
        val p = ModeOverrides.parse("com.example.ide=developer\ncom.example.term TERMUX\n  org.x.y\t=\tStandard  ")
        p.overrides shouldBe mapOf(
            "com.example.ide" to OperatingMode.DEVELOPER,
            "com.example.term" to OperatingMode.TERMUX,
            "org.x.y" to OperatingMode.STANDARD,
        )
        p.rejected shouldBe emptyList()
    }

    test("comments and blank lines are ignored") {
        val p = ModeOverrides.parse("# my apps\n\ncom.a.b=TERMUX # a terminal\n   # indented comment")
        p.overrides shouldBe mapOf("com.a.b" to OperatingMode.TERMUX)
        p.rejected shouldBe emptyList()
    }

    test("modes that are not available are rejected, not accepted") {
        val p = ModeOverrides.parse("com.a.b=KALI\ncom.a.c=NETHUNTER_AI\ncom.a.d=CLAUDE_CODE")
        p.overrides shouldBe emptyMap()
        p.rejected.size shouldBe 3
    }

    test("malformed lines are reported and do not affect the valid ones") {
        val p = ModeOverrides.parse("justonetoken\ncom.a.b=TERMUX=extra\nnodots=TERMUX\n1bad.pkg=TERMUX\ncom.a.b=NOSUCH\ncom.ok.app=DEVELOPER")
        p.overrides shouldBe mapOf("com.ok.app" to OperatingMode.DEVELOPER)
        p.rejected.size shouldBe 5
    }

    test("package names must look like package names (no paths, no wildcards, no shell characters)") {
        for (bad in listOf("com.a/b=TERMUX", "com.*=TERMUX", "com.a;rm=TERMUX", "../x=TERMUX", "com..a=TERMUX", ".com.a=TERMUX")) {
            ModeOverrides.parse(bad).overrides shouldBe emptyMap()
        }
    }

    test("a package listed twice keeps the last mode") {
        ModeOverrides.parse("com.a.b=TERMUX\ncom.a.b=DEVELOPER").overrides shouldBe mapOf("com.a.b" to OperatingMode.DEVELOPER)
    }

    test("at most 50 entries; the overflow is reported") {
        val text = (1..55).joinToString("\n") { "com.app$it.x=TERMUX" }
        val p = ModeOverrides.parse(text)
        p.overrides.size shouldBe ModeOverrides.MAX_ENTRIES
        p.rejected.size shouldBe 5
    }

    test("a huge input is handled without trouble") {
        ModeOverrides.parse("com.a.b=TERMUX\n".repeat(100_000)).overrides.size shouldBe 1
    }

    test("parsed overrides drive the active mode and suggestions") {
        val o = ModeOverrides.parse("com.example.ide=DEVELOPER").overrides
        ModeSuggester.suggest("com.example.ide", o) shouldBe OperatingMode.DEVELOPER
        ActiveMode.resolve(OperatingMode.STANDARD, autoDetect = true, packageName = "com.example.ide", overrides = o) shouldBe OperatingMode.DEVELOPER
        ActiveMode.resolve(OperatingMode.STANDARD, autoDetect = false, packageName = "com.example.ide", overrides = o) shouldBe OperatingMode.STANDARD
    }

    test("the new row options flow through the mode presets") {
        val prefs = DeveloperRowPrefs(enabled = true, profile = LanguageProfile.PYTHON, terminalKeys = false, navigationKeys = false, symbols = false)
        ModePresets.developerRow(OperatingMode.STANDARD, prefs) shouldBe EffectiveDeveloperRow(true, LanguageProfile.PYTHON, false, false, false)
        ModePresets.developerRow(OperatingMode.DEVELOPER, prefs) shouldBe EffectiveDeveloperRow(true, LanguageProfile.PYTHON, false, false, false)
        // Termux mode is a preset: everything on, Shell symbols
        ModePresets.developerRow(OperatingMode.TERMUX, prefs) shouldBe EffectiveDeveloperRow(true, LanguageProfile.SHELL, true, true, true)
    }
})
