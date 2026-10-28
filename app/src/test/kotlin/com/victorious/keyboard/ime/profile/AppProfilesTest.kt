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

package com.victorious.keyboard.ime.profile

import com.victorious.keyboard.ime.mode.TerminalKeyRow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class AppProfilesTest : FunSpec({
    test("terminal and editor presets turn autocorrect off and the row on") {
        listOf(
            "com.termux",
            "com.termux.api",
            "com.offsec.nhterm",
            "com.offsec.nethunter",
            "com.sonelli.juicessh",
            "com.aide.ui",
            "com.blacksquircle.ui",
            "org.gnu.emacs",
        ).forEach { packageName ->
            val effect = AppProfiles.effect(packageName, emptyList())
            effect.autocorrectOff shouldBe true
            effect.terminalRow shouldBe TerminalKeyRow.ALWAYS
            effect.suggestions shouldBe null
            effect.layoutSubtypeId shouldBe null
            effect.themeId shouldBe null
        }
        AppProfiles.effect("com.android.chrome", emptyList()) shouldBe ProfileEffect.None
    }

    test("a saved profile overrides one field and keeps the preset for the rest") {
        val saved = AppProfile(
            packageName = "com.termux",
            suggestions = false,
            layoutSubtypeId = 7L,
            themeId = "com.victorious.keyboard.themes:victorious_black",
        )
        val effect = AppProfiles.effect("com.termux", listOf(saved))
        effect.autocorrectOff shouldBe true
        effect.terminalRow shouldBe TerminalKeyRow.ALWAYS
        effect.suggestions shouldBe false
        effect.layoutSubtypeId shouldBe 7L
        effect.themeId shouldBe "com.victorious.keyboard.themes:victorious_black"
        val round = AppProfileCodec.decode(AppProfileCodec.encode(listOf(saved)))
        round shouldBe listOf(saved)
        AppProfileCodec.decode("{") shouldBe emptyList()
        AppProfileCodec.decode("""{"format":"victorious-app-profiles","version":1,"profiles":[{"packageName":"../x"}]}""") shouldBe emptyList()
    }

    test("layout and theme are restored when the next app has no override") {
        val termux = AppProfiles.effect("com.termux", listOf(AppProfile("com.termux", layoutSubtypeId = 4L, themeId = "night")))
        val (held, apply) = ProfileHoldMachine.step(ProfileHold(), termux, 1L, "day", "night-user")
        held.active shouldBe true
        held.layout shouldBe 1L
        apply shouldBe ProfileShift.Apply(4L, "night")
        val chrome = AppProfiles.effect("com.android.chrome", emptyList())
        val (cleared, restore) = ProfileHoldMachine.step(held, chrome, 4L, "night", "night")
        cleared.active shouldBe false
        restore shouldBe ProfileShift.Restore(1L, "day", "night-user")
        val (again, stay) = ProfileHoldMachine.step(cleared, chrome, 1L, "day", "night-user")
        again.active shouldBe false
        stay shouldBe ProfileShift.Stay
    }

    test("one theme id that will not parse does not drop the other") {
        ProfileThemePlan.kept("com.victorious.keyboard.themes:victorious_black") { it.split(':').size == 2 } shouldBe
            "com.victorious.keyboard.themes:victorious_black"
        ProfileThemePlan.kept("not:a:valid:theme") { it.split(':').size == 2 } shouldBe null
        ProfileThemePlan.kept("   ") { true } shouldBe null
        ProfileThemePlan.kept(null) { true } shouldBe null
    }
})
