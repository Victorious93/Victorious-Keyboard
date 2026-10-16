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

package dev.patrickgold.florisboard.ime.snippets

import dev.patrickgold.florisboard.ime.mode.OperatingMode
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

private val ALL = ExpansionTriggers(space = true, enter = true, tab = true)
private val SPACE = ExpansionTriggers(space = true, enter = false, tab = false)

private fun triggers(
    mode: OperatingMode?,
    rules: ModeExpansionRules = ModeExpansionRules(),
    master: Boolean = true,
    global: ExpansionTriggers = ALL,
) = ModeExpansionPolicy.triggers(mode, master, global, rules)

class ModeExpansionTest : FunSpec({
    test("enum names are persisted: this list may only grow at the end") {
        ModeExpansion.entries.map { it.name } shouldBe listOf("FOLLOW", "SPACE_ONLY", "OFF")
    }

    test("defaults keep the old behaviour: Standard and Developer follow the switches, Termux is off") {
        triggers(OperatingMode.STANDARD) shouldBe ALL
        triggers(OperatingMode.DEVELOPER) shouldBe ALL
        triggers(OperatingMode.TERMUX) shouldBe null
    }

    test("the master switch always wins; a mode rule cannot turn expansion on") {
        val everywhere = ModeExpansionRules(ModeExpansion.FOLLOW, ModeExpansion.FOLLOW, ModeExpansion.FOLLOW)
        for (mode in OperatingMode.selectable) {
            triggers(mode, everywhere, master = false) shouldBe null
        }
    }

    test("Follow returns exactly the user's keys") {
        triggers(OperatingMode.STANDARD, global = SPACE) shouldBe SPACE
        val spaceAndTab = ExpansionTriggers(space = true, enter = false, tab = true)
        triggers(OperatingMode.DEVELOPER, global = spaceAndTab) shouldBe spaceAndTab
    }

    test("Space only drops Enter and Tab even when they are switched on") {
        val rules = ModeExpansionRules(termux = ModeExpansion.SPACE_ONLY)
        triggers(OperatingMode.TERMUX, rules) shouldBe SPACE
    }

    test("Off disables only its own mode") {
        val rules = ModeExpansionRules(developer = ModeExpansion.OFF, termux = ModeExpansion.FOLLOW)
        triggers(OperatingMode.DEVELOPER, rules) shouldBe null
        triggers(OperatingMode.STANDARD, rules) shouldBe ALL
        triggers(OperatingMode.TERMUX, rules) shouldBe ALL
    }

    test("planned, unknown and null modes use Standard's rule") {
        val rules = ModeExpansionRules(standard = ModeExpansion.OFF, developer = ModeExpansion.FOLLOW, termux = ModeExpansion.FOLLOW)
        for (mode in OperatingMode.entries.filterNot { it.isSelectable }) {
            triggers(mode, rules) shouldBe null
        }
        triggers(null, rules) shouldBe null
        triggers(null) shouldBe ALL
    }

    test("a rule that leaves no key returns null instead of an empty set") {
        val noKeys = ExpansionTriggers(space = false, enter = false, tab = false)
        triggers(OperatingMode.STANDARD, global = noKeys) shouldBe null
        val onlyEnter = ExpansionTriggers(space = false, enter = true, tab = false)
        triggers(OperatingMode.TERMUX, ModeExpansionRules(termux = ModeExpansion.SPACE_ONLY), global = onlyEnter) shouldBe null
    }

    test("the retired Termux switch migrates without changing behaviour") {
        ModeExpansionPolicy.termuxFromLegacy("true") shouldBe ModeExpansion.FOLLOW
        ModeExpansionPolicy.termuxFromLegacy(" TRUE ") shouldBe ModeExpansion.FOLLOW
        ModeExpansionPolicy.termuxFromLegacy("false") shouldBe ModeExpansion.OFF
    }

    test("a damaged or missing legacy value becomes the safe default, Off") {
        for (raw in listOf(null, "", "yes", "1", "FOLLOW", "truex")) {
            ModeExpansionPolicy.termuxFromLegacy(raw) shouldBe ModeExpansion.OFF
        }
    }
})
