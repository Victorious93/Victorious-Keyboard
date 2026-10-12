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

enum class TerminalModifier { CTRL, ALT }

enum class ModifierMode {
    OFF,

    /** Applies to the next key only ("sticky" / one-shot). */
    ARMED,

    /** Applies to every key until toggled off. */
    LOCKED,
}

/** The modifiers that are in effect for one key. [shift] is owned by the keyboard's own shift state, not by us. */
data class Modifiers(val ctrl: Boolean = false, val alt: Boolean = false, val shift: Boolean = false) {
    val any: Boolean get() = ctrl || alt
}

/**
 * Sticky / locked / held Ctrl and Alt.
 *
 * Gestures, driven by key *down* and *up* events (a plain tap is a down immediately followed by an up):
 * * tap while OFF → ARMED (applies to the next key, then returns to OFF)
 * * second tap within [doubleTapWindowMs] while ARMED → LOCKED; a later tap while ARMED → OFF
 * * tap while LOCKED → OFF
 * * hold the modifier, press another key, release → the modifier applied to that key and the mode is left unchanged
 *   (so a chord does not arm the modifier afterwards)
 * * a dedicated lock key ([toggleLock]) switches OFF/ARMED ↔ LOCKED directly
 *
 * Time is passed in, never read, so behaviour is deterministic. Not thread-safe: call from the main thread only.
 */
class ModifierStateMachine(private val doubleTapWindowMs: Long = 400L) {
    private class Slot {
        var mode = ModifierMode.OFF
        var held = false
        var usedWhileHeld = false
        var lastTapMs = Long.MIN_VALUE / 2
    }

    private val slots = TerminalModifier.entries.associateWith { Slot() }

    fun mode(modifier: TerminalModifier): ModifierMode = slots.getValue(modifier).mode

    fun isHeld(modifier: TerminalModifier): Boolean = slots.getValue(modifier).held

    /** What applies to a key sent right now. [shift] is passed through from the caller. */
    fun current(shift: Boolean = false): Modifiers = Modifiers(
        ctrl = isActive(TerminalModifier.CTRL),
        alt = isActive(TerminalModifier.ALT),
        shift = shift,
    )

    val anyActive: Boolean get() = TerminalModifier.entries.any { isActive(it) }

    private fun isActive(modifier: TerminalModifier): Boolean {
        val s = slots.getValue(modifier)
        return s.held || s.mode != ModifierMode.OFF
    }

    fun press(modifier: TerminalModifier) {
        val s = slots.getValue(modifier)
        s.held = true
        s.usedWhileHeld = false
    }

    fun release(modifier: TerminalModifier, nowMs: Long) {
        val s = slots.getValue(modifier)
        if (!s.held) return
        s.held = false
        if (!s.usedWhileHeld) tap(s, nowMs)
        s.usedWhileHeld = false
    }

    /** The press was cancelled (finger slid away): no tap, no change. */
    fun cancel(modifier: TerminalModifier) {
        val s = slots.getValue(modifier)
        s.held = false
        s.usedWhileHeld = false
    }

    fun toggleLock(modifier: TerminalModifier) {
        val s = slots.getValue(modifier)
        s.mode = if (s.mode == ModifierMode.LOCKED) ModifierMode.OFF else ModifierMode.LOCKED
    }

    /** Call after a key has been sent with [current]: one-shot modifiers are spent, locked ones stay. */
    fun consume() {
        for (s in slots.values) {
            if (s.held) s.usedWhileHeld = true
            if (s.mode == ModifierMode.ARMED) s.mode = ModifierMode.OFF
        }
    }

    /** E.g. when the focused field changes: nothing may stay armed or locked into another app. */
    fun reset() {
        for (s in slots.values) {
            s.mode = ModifierMode.OFF
            s.held = false
            s.usedWhileHeld = false
            s.lastTapMs = Long.MIN_VALUE / 2
        }
    }

    private fun tap(s: Slot, nowMs: Long) {
        s.mode = when (s.mode) {
            ModifierMode.OFF -> ModifierMode.ARMED
            ModifierMode.ARMED -> if (nowMs - s.lastTapMs in 0..doubleTapWindowMs) ModifierMode.LOCKED else ModifierMode.OFF
            ModifierMode.LOCKED -> ModifierMode.OFF
        }
        s.lastTapMs = nowMs
    }
}
