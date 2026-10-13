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

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import java.time.LocalDateTime

private fun sig(body: String = "Hello") =
    Snippet(id = "sig", title = "Signature", body = body, abbreviation = "sig", enabled = true)

private fun attempt(
    trigger: ExpansionTrigger,
    before: String = ";sig",
    triggers: ExpansionTriggers = ExpansionTriggers(space = true, enter = true, tab = true),
    enabled: Boolean = true,
    sensitive: Boolean = false,
    selection: Boolean = false,
    modifier: Boolean = false,
    terminalBlocked: Boolean = false,
    keep: Boolean = true,
    snippets: List<Snippet> = listOf(sig()),
) = ExpansionAttempt(
    enabled = enabled,
    triggers = triggers,
    trigger = trigger,
    textBeforeCursor = before,
    snippets = snippets,
    sensitiveField = sensitive,
    selectionActive = selection,
    modifierHeld = modifier,
    blockedInTerminalMode = terminalBlocked,
    keepTrigger = keep,
)

private val now = LocalDateTime.of(2026, 10, 13, 12, 0)

private fun decide(a: ExpansionAttempt, bodyPlan: ((ExpansionMatch) -> InsertionPlan)? = null): ExpansionOutcome =
    ExpansionAction.decide(a) { match ->
        bodyPlan?.invoke(match) ?: SnippetInsertion.plan(InsertionRequest(snippet = match.snippet, now = now))
    }

class ExpansionActionTest : FunSpec({
    test("enter and tab are off unless the user opts in; space stays on") {
        val defaults = ExpansionTriggers()
        defaults.space shouldBe true
        defaults.enter shouldBe false
        defaults.tab shouldBe false
        defaults.boundaryChars() shouldBe setOf(' ')
        ExpansionAction.shouldConsider(attempt(ExpansionTrigger.ENTER, triggers = defaults)) shouldBe false
        ExpansionAction.shouldConsider(attempt(ExpansionTrigger.TAB, triggers = defaults)) shouldBe false
        ExpansionAction.shouldConsider(attempt(ExpansionTrigger.SPACE, triggers = defaults)) shouldBe true
    }

    test("a matching enter expands and can still send enter afterwards") {
        val out = decide(attempt(ExpansionTrigger.ENTER))
        out shouldBe ExpansionOutcome.Replace(
            deleteCount = 4,
            text = "Hello",
            cursorBackCodePoints = null,
            followWithTrigger = true,
        )
    }

    test("a matching tab expands the same way") {
        val out = decide(attempt(ExpansionTrigger.TAB, before = "hello ;sig"))
        out.shouldBeInstanceOf<ExpansionOutcome.Replace>()
        out.deleteCount shouldBe 4
        out.text shouldBe "Hello"
        out.followWithTrigger shouldBe true
    }

    test("enter is not swallowed when nothing matches") {
        val no = decide(attempt(ExpansionTrigger.ENTER, before = ";nope"))
        val plain = decide(attempt(ExpansionTrigger.ENTER, before = "hello"))
        val empty = decide(attempt(ExpansionTrigger.ENTER, before = ""))
        no shouldBe ExpansionOutcome.PassThrough
        plain shouldBe ExpansionOutcome.PassThrough
        empty shouldBe ExpansionOutcome.PassThrough
    }

    test("tab is not swallowed when nothing matches") {
        decide(attempt(ExpansionTrigger.TAB, before = "sig")) shouldBe ExpansionOutcome.PassThrough
        decide(attempt(ExpansionTrigger.TAB, before = ";sig", snippets = listOf(sig().copy(enabled = false)))) shouldBe
            ExpansionOutcome.PassThrough
    }

    test("opting out of one key leaves the others alone") {
        val enterOnly = ExpansionTriggers(space = false, enter = true, tab = false)
        decide(attempt(ExpansionTrigger.ENTER, triggers = enterOnly)).shouldBeInstanceOf<ExpansionOutcome.Replace>()
        decide(attempt(ExpansionTrigger.SPACE, triggers = enterOnly)) shouldBe ExpansionOutcome.PassThrough
        decide(attempt(ExpansionTrigger.TAB, triggers = enterOnly)) shouldBe ExpansionOutcome.PassThrough
        enterOnly.boundaryChars() shouldBe setOf('\n')
    }

    test("never in a sensitive field, and the editor text is not consulted") {
        var planned = false
        val out = ExpansionAction.decide(attempt(ExpansionTrigger.ENTER, sensitive = true)) {
            planned = true
            error("must not render")
        }
        out shouldBe ExpansionOutcome.PassThrough
        planned shouldBe false
        ExpansionAction.shouldConsider(attempt(ExpansionTrigger.TAB, sensitive = true)) shouldBe false
        ExpansionAction.shouldConsider(attempt(ExpansionTrigger.SPACE, sensitive = true)) shouldBe false
    }

    test("a selection is left for the real key") {
        decide(attempt(ExpansionTrigger.ENTER, selection = true)) shouldBe ExpansionOutcome.PassThrough
        decide(attempt(ExpansionTrigger.TAB, selection = true)) shouldBe ExpansionOutcome.PassThrough
    }

    test("shift ctrl or alt held: enter and tab pass through, space still expands") {
        decide(attempt(ExpansionTrigger.ENTER, modifier = true)) shouldBe ExpansionOutcome.PassThrough
        decide(attempt(ExpansionTrigger.TAB, modifier = true)) shouldBe ExpansionOutcome.PassThrough
        decide(attempt(ExpansionTrigger.SPACE, modifier = true)).shouldBeInstanceOf<ExpansionOutcome.Replace>()
    }

    test("expansion off, or off in terminal mode, never takes the key") {
        decide(attempt(ExpansionTrigger.ENTER, enabled = false)) shouldBe ExpansionOutcome.PassThrough
        decide(attempt(ExpansionTrigger.TAB, terminalBlocked = true)) shouldBe ExpansionOutcome.PassThrough
        decide(attempt(ExpansionTrigger.SPACE, terminalBlocked = true)) shouldBe ExpansionOutcome.PassThrough
    }

    test("a snippet that cannot be inserted does not swallow enter or tab") {
        val huge = "x".repeat(SnippetLimits.MAX_RENDERED + 1)
        decide(attempt(ExpansionTrigger.ENTER, snippets = listOf(sig(huge)))) shouldBe ExpansionOutcome.PassThrough
        decide(attempt(ExpansionTrigger.TAB, snippets = listOf(sig("")))) shouldBe ExpansionOutcome.PassThrough
        decide(attempt(ExpansionTrigger.ENTER)) { InsertionPlan.TooLarge } shouldBe ExpansionOutcome.PassThrough
        decide(attempt(ExpansionTrigger.TAB)) { InsertionPlan.NothingToInsert } shouldBe ExpansionOutcome.PassThrough
    }

    test("keep-trigger off consumes the key only after a real insertion") {
        val out = decide(attempt(ExpansionTrigger.ENTER, keep = false))
        out.shouldBeInstanceOf<ExpansionOutcome.Replace>()
        out.followWithTrigger shouldBe false
        decide(attempt(ExpansionTrigger.ENTER, before = "nope", keep = false)) shouldBe ExpansionOutcome.PassThrough
    }

    test("a cursor placeholder does not also send enter or tab") {
        val out = decide(attempt(ExpansionTrigger.ENTER, snippets = listOf(sig("Hi {{cursor}} there"))))
        out.shouldBeInstanceOf<ExpansionOutcome.Replace>()
        out.followWithTrigger shouldBe false
        out.cursorBackCodePoints shouldBe 6
        val tab = decide(attempt(ExpansionTrigger.TAB, snippets = listOf(sig("Hi {{cursor}}"))))
        tab.shouldBeInstanceOf<ExpansionOutcome.Replace>()
        tab.followWithTrigger shouldBe false
    }

    test("the renderer is not called when nothing matches, so a failure there cannot hide the key") {
        shouldThrow<IllegalStateException> {
            ExpansionAction.decide(attempt(ExpansionTrigger.ENTER, before = ";sig")) { error("render failed") }
        }
        var called = false
        val out = ExpansionAction.decide(attempt(ExpansionTrigger.TAB, before = "plain")) {
            called = true
            error("no")
        }
        called shouldBe false
        out shouldBe ExpansionOutcome.PassThrough
    }
})
