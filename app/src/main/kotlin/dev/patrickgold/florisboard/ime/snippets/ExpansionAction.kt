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

/**
 * A key that can complete an abbreviation. Space is the original trigger. Enter and Tab are separate opt-ins because
 * those keys also mean "send", "search" or "move focus", and must keep doing that when no snippet matches.
 */
enum class ExpansionTrigger(val boundary: Char) {
    SPACE(' '),
    ENTER('\n'),
    TAB('\t'),
}

/**
 * Which trigger keys the user turned on. Space stays on with the master expansion switch (the original behaviour).
 * Enter and Tab default off: each is its own opt-in.
 */
data class ExpansionTriggers(
    val space: Boolean = true,
    val enter: Boolean = false,
    val tab: Boolean = false,
) {
    fun allows(trigger: ExpansionTrigger): Boolean = when (trigger) {
        ExpansionTrigger.SPACE -> space
        ExpansionTrigger.ENTER -> enter
        ExpansionTrigger.TAB -> tab
    }

    /** Boundary characters [ExpansionMatcher] may treat as a completion. Empty when every trigger is off. */
    fun boundaryChars(): Set<Char> = buildSet {
        if (space) add(ExpansionTrigger.SPACE.boundary)
        if (enter) add(ExpansionTrigger.ENTER.boundary)
        if (tab) add(ExpansionTrigger.TAB.boundary)
    }
}

/**
 * Everything the pure decision needs. The shell fills this in and then performs [ExpansionOutcome]; it must not log
 * [textBeforeCursor] or any snippet body.
 */
data class ExpansionAttempt(
    val enabled: Boolean,
    val triggers: ExpansionTriggers = ExpansionTriggers(),
    val prefix: String = ";",
    val caseSensitive: Boolean = true,
    val trigger: ExpansionTrigger,
    val textBeforeCursor: String = "",
    /**
     * The library to match against. Required on purpose: it once defaulted to empty and the shell stopped passing it,
     * so typed expansion silently never matched.
     */
    val snippets: Collection<Snippet>,
    val sensitiveField: Boolean = false,
    val selectionActive: Boolean = false,
    /**
     * Shift, Ctrl or Alt is held. Enter and Tab are never taken then (a modified Enter/Tab is a real key). Space is
     * unchanged, matching the original space-key behaviour.
     */
    val modifierHeld: Boolean = false,
    /** After a successful expansion, perform the trigger key as well. Ignored when the snippet places `{{cursor}}`. */
    val keepTrigger: Boolean = true,
)

/**
 * What the shell must do with the key the user just pressed.
 *
 * [PassThrough] means this is not an expansion: the key is handled exactly as if expansion did not exist. A real Enter
 * still sends, a real Tab still tabs. A match that cannot be inserted (empty or too large) is also [PassThrough], so
 * the typed abbreviation stays and the key is not swallowed.
 */
sealed interface ExpansionOutcome {
    data object PassThrough : ExpansionOutcome

    data class Replace(
        val deleteCount: Int,
        val text: String,
        val cursorBackCodePoints: Int?,
        /** Perform the trigger key after inserting. False when the user turned that off, or the snippet uses `{{cursor}}`. */
        val followWithTrigger: Boolean,
    ) : ExpansionOutcome
}

object ExpansionAction {
    /**
     * False when the shell must not read the editor at all: expansion off, this key not opted in, a sensitive field,
     * a selection, or a modified Enter/Tab. The active operating mode is applied before this, by
     * [ModeExpansionPolicy.triggers], which decides [ExpansionAttempt.enabled] and [ExpansionAttempt.triggers].
     */
    fun shouldConsider(attempt: ExpansionAttempt): Boolean {
        if (!attempt.enabled || attempt.sensitiveField || attempt.selectionActive) return false
        if (!attempt.triggers.allows(attempt.trigger)) return false
        if (attempt.trigger != ExpansionTrigger.SPACE && attempt.modifierHeld) return false
        return true
    }

    /**
     * Decides the key. [plan] is called only after a snippet actually matched, so the shell can read the clipboard
     * inside it and skip that read on every ordinary Enter or Tab.
     */
    fun decide(attempt: ExpansionAttempt, plan: (ExpansionMatch) -> InsertionPlan): ExpansionOutcome {
        if (!shouldConsider(attempt)) return ExpansionOutcome.PassThrough
        val settings = ExpansionSettings(
            enabled = true,
            prefix = attempt.prefix,
            caseSensitive = attempt.caseSensitive,
            boundaries = attempt.triggers.boundaryChars(),
        )
        val match = ExpansionMatcher.match(
            textBeforeCursor = attempt.textBeforeCursor,
            typed = attempt.trigger.boundary,
            snippets = attempt.snippets,
            settings = settings,
            sensitiveField = attempt.sensitiveField,
        ) ?: return ExpansionOutcome.PassThrough
        return when (val rendered = plan(match)) {
            is InsertionPlan.Insert -> ExpansionOutcome.Replace(
                deleteCount = match.deleteCount,
                text = rendered.text,
                cursorBackCodePoints = rendered.cursorBackCodePoints,
                followWithTrigger = attempt.keepTrigger && rendered.cursorBackCodePoints == null,
            )
            InsertionPlan.TooLarge, InsertionPlan.NothingToInsert -> ExpansionOutcome.PassThrough
        }
    }
}
