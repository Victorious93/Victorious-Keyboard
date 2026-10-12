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

package dev.patrickgold.florisboard.ime.terminal

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

private val CTRL = TerminalModifier.CTRL
private val ALT = TerminalModifier.ALT

private fun ModifierStateMachine.tap(m: TerminalModifier, at: Long) { press(m); release(m, at) }

class ModifierStateMachineTest : FunSpec({
    test("starts with nothing active") {
        val sm = ModifierStateMachine()
        sm.anyActive shouldBe false
        sm.current() shouldBe Modifiers()
    }

    test("a tap arms the modifier for exactly one key") {
        val sm = ModifierStateMachine()
        sm.tap(CTRL, 0)
        sm.mode(CTRL) shouldBe ModifierMode.ARMED
        sm.current().ctrl shouldBe true
        sm.consume()
        sm.mode(CTRL) shouldBe ModifierMode.OFF
        sm.current().ctrl shouldBe false
    }

    test("a quick second tap locks; a locked modifier survives any number of keys") {
        val sm = ModifierStateMachine(doubleTapWindowMs = 400)
        sm.tap(CTRL, 1000)
        sm.tap(CTRL, 1300)
        sm.mode(CTRL) shouldBe ModifierMode.LOCKED
        repeat(5) { sm.consume(); sm.current().ctrl shouldBe true }
    }

    test("a slow second tap disarms instead of locking") {
        val sm = ModifierStateMachine(doubleTapWindowMs = 400)
        sm.tap(CTRL, 1000)
        sm.tap(CTRL, 1401)
        sm.mode(CTRL) shouldBe ModifierMode.OFF
    }

    test("the double-tap window boundary is inclusive") {
        val sm = ModifierStateMachine(doubleTapWindowMs = 400)
        sm.tap(CTRL, 1000)
        sm.tap(CTRL, 1400)
        sm.mode(CTRL) shouldBe ModifierMode.LOCKED
    }

    test("a clock that goes backwards never locks") {
        val sm = ModifierStateMachine()
        sm.tap(CTRL, 5000)
        sm.tap(CTRL, 100)
        sm.mode(CTRL) shouldBe ModifierMode.OFF
    }

    test("tapping a locked modifier unlocks it") {
        val sm = ModifierStateMachine()
        sm.tap(CTRL, 0); sm.tap(CTRL, 100)
        sm.tap(CTRL, 10_000)
        sm.mode(CTRL) shouldBe ModifierMode.OFF
    }

    test("after the one-shot is spent, the next tap arms again (no stale double-tap)") {
        val sm = ModifierStateMachine(doubleTapWindowMs = 400)
        sm.tap(CTRL, 1000)
        sm.consume()
        sm.tap(CTRL, 1100) // within the window of the previous tap, but that one-shot was already spent
        sm.mode(CTRL) shouldBe ModifierMode.ARMED
    }

    test("holding the modifier while pressing a key applies it and does not arm it afterwards") {
        val sm = ModifierStateMachine()
        sm.press(CTRL)
        sm.current().ctrl shouldBe true
        sm.consume() // the other key was sent
        sm.release(CTRL, 100)
        sm.mode(CTRL) shouldBe ModifierMode.OFF
        sm.current().ctrl shouldBe false
    }

    test("holding with no other key is a tap") {
        val sm = ModifierStateMachine()
        sm.press(CTRL)
        sm.release(CTRL, 100)
        sm.mode(CTRL) shouldBe ModifierMode.ARMED
    }

    test("a cancelled press changes nothing") {
        val sm = ModifierStateMachine()
        sm.press(CTRL)
        sm.cancel(CTRL)
        sm.mode(CTRL) shouldBe ModifierMode.OFF
        sm.isHeld(CTRL) shouldBe false
        sm.release(CTRL, 5) // stray release after cancel is ignored
        sm.mode(CTRL) shouldBe ModifierMode.OFF
    }

    test("hold chord on top of a locked modifier keeps it locked") {
        val sm = ModifierStateMachine()
        sm.toggleLock(CTRL)
        sm.press(CTRL); sm.consume(); sm.release(CTRL, 10)
        sm.mode(CTRL) shouldBe ModifierMode.LOCKED
    }

    test("the lock key toggles lock directly") {
        val sm = ModifierStateMachine()
        sm.toggleLock(ALT)
        sm.mode(ALT) shouldBe ModifierMode.LOCKED
        sm.toggleLock(ALT)
        sm.mode(ALT) shouldBe ModifierMode.OFF
    }

    test("Ctrl and Alt are independent and combine") {
        val sm = ModifierStateMachine()
        sm.tap(CTRL, 0)
        sm.tap(ALT, 50)
        sm.current(shift = true) shouldBe Modifiers(ctrl = true, alt = true, shift = true)
        sm.consume()
        sm.anyActive shouldBe false
    }

    test("reset clears armed, locked and held state (e.g. when the field changes)") {
        val sm = ModifierStateMachine()
        sm.tap(CTRL, 0); sm.tap(CTRL, 10)
        sm.press(ALT)
        sm.reset()
        sm.anyActive shouldBe false
        sm.isHeld(ALT) shouldBe false
        // and a double tap right after reset does not lock from stale timing
        sm.tap(CTRL, 20)
        sm.mode(CTRL) shouldBe ModifierMode.ARMED
    }

    test("snapshot: off, armed, held counts as armed, locked") {
        val sm = ModifierStateMachine()
        sm.snapshot() shouldBe ModifierSnapshot()
        sm.press(CTRL)
        sm.snapshot().ctrl shouldBe ModifierDisplay.ARMED
        sm.release(CTRL, 0)
        sm.snapshot().ctrl shouldBe ModifierDisplay.ARMED
        sm.tap(CTRL, 100)
        sm.snapshot().ctrl shouldBe ModifierDisplay.LOCKED
        sm.snapshot().alt shouldBe ModifierDisplay.OFF
        sm.snapshot().of(TerminalModifier.CTRL) shouldBe ModifierDisplay.LOCKED
    }

    test("onChange fires exactly when the snapshot changes") {
        val sm = ModifierStateMachine()
        val seen = mutableListOf<ModifierSnapshot>()
        sm.onChange = { seen += it }
        sm.press(CTRL)            // OFF -> ARMED (held)
        sm.release(CTRL, 0)       // still ARMED (now one-shot): no change
        sm.consume()              // ARMED -> OFF
        sm.consume()              // already OFF: no change
        sm.toggleLock(ALT)        // OFF -> LOCKED
        sm.reset()                // LOCKED -> OFF
        sm.reset()                // no change
        seen shouldBe listOf(
            ModifierSnapshot(ctrl = ModifierDisplay.ARMED),
            ModifierSnapshot(),
            ModifierSnapshot(alt = ModifierDisplay.LOCKED),
            ModifierSnapshot(),
        )
    }

    test("a cancelled press clears the held display") {
        val sm = ModifierStateMachine()
        sm.press(CTRL); sm.cancel(CTRL)
        sm.snapshot() shouldBe ModifierSnapshot()
    }

    test("the double-tap window can be changed at runtime") {
        val sm = ModifierStateMachine(doubleTapWindowMs = 400)
        sm.doubleTapWindowMs shouldBe 400L
        sm.tap(CTRL, 1000)
        sm.doubleTapWindowMs = 200
        sm.tap(CTRL, 1300) // 300 ms apart: too slow for the new 200 ms window
        sm.mode(CTRL) shouldBe ModifierMode.OFF
        sm.doubleTapWindowMs = 800
        sm.tap(CTRL, 5000)
        sm.tap(CTRL, 5700) // 700 ms apart: within the new 800 ms window
        sm.mode(CTRL) shouldBe ModifierMode.LOCKED
    }
})
