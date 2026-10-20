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

package com.victorious.keyboard.ime.snippets

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import java.time.LocalDateTime
import kotlin.random.Random

private val NOW = LocalDateTime.of(2026, 3, 7, 9, 5, 0)
private fun ctx(clip: String? = null, sel: String? = null) = TemplateContext(NOW, clip, sel)
private fun text(body: String, c: TemplateContext = ctx()) =
    (SnippetTemplate.render(body, c) as RenderResult.Rendered).text

class SnippetTemplateTest : FunSpec({
    test("plain text passes through unchanged") {
        text("hello world\nline 2\t{ } } {") shouldBe "hello world\nline 2\t{ } } {"
    }

    test("date and time with defaults and patterns") {
        text("{{date}}") shouldBe "2026-03-07"
        text("{{time}}") shouldBe "09:05"
        text("{{datetime}}") shouldBe "2026-03-07 09:05"
        text("{{date:dd/MM/yyyy}}") shouldBe "07/03/2026"
        text("{{time:HH.mm.ss}}") shouldBe "09.05.00"
        text("{{DATE}} {{ time }}") shouldBe "2026-03-07 09:05"
    }

    test("bad or oversized date patterns stay literal instead of failing") {
        text("{{date:yyyy-QQQQQQ-bogus[}}") shouldBe "{{date:yyyy-QQQQQQ-bogus[}}"
        text("{{date:" + "y".repeat(40) + "}}") shouldBe "{{date:" + "y".repeat(40) + "}}"
        text("{{date:VV}}") shouldBe "{{date:VV}}" // zone id: a LocalDateTime has none
    }

    test("clipboard and selection are substituted; missing ones become empty") {
        text("[{{clipboard}}] [{{selection}}]", ctx("C", "S")) shouldBe "[C] [S]"
        text("[{{clipboard}}] [{{selection}}]", ctx()) shouldBe "[] []"
    }

    test("substituted text is never re-scanned (no recursion through the clipboard)") {
        text("{{clipboard}}", ctx(clip = "{{date}} {{selection}} {{cursor}}", sel = "x")) shouldBe
            "{{date}} {{selection}} {{cursor}}"
        (SnippetTemplate.render("{{clipboard}}", ctx(clip = "{{cursor}}")) as RenderResult.Rendered)
            .cursorBackCodePoints shouldBe null
    }

    test("cursor position is reported in code points from the end; the first cursor wins") {
        val r = SnippetTemplate.render("fun f({{cursor}}) {}", ctx()) as RenderResult.Rendered
        r.text shouldBe "fun f() {}"
        r.cursorBackCodePoints shouldBe 4
        val emoji = SnippetTemplate.render("x{{cursor}}😀😀", ctx()) as RenderResult.Rendered
        emoji.cursorBackCodePoints shouldBe 2 // two code points, four UTF-16 chars
        val two = SnippetTemplate.render("a{{cursor}}b{{cursor}}c", ctx()) as RenderResult.Rendered
        two.text shouldBe "abc"
        two.cursorBackCodePoints shouldBe 2
        (SnippetTemplate.render("none", ctx()) as RenderResult.Rendered).cursorBackCodePoints shouldBe null
        (SnippetTemplate.render("end{{cursor}}", ctx()) as RenderResult.Rendered).cursorBackCodePoints shouldBe 0
    }

    test("exact single-brace tokens expand and other braces stay literal") {
        text("{date} {time}") shouldBe "2026-03-07 09:05"
        text("{DATE}") shouldBe "2026-03-07"
        text("clip={clipboard}", ctx("PASTE")) shouldBe "clip=PASTE"
        text("{clipboard}", ctx()) shouldBe ""
        val caret = SnippetTemplate.render("fn({cursor})", ctx()) as RenderResult.Rendered
        caret.text shouldBe "fn()"
        caret.cursorBackCodePoints shouldBe 1
        val first = SnippetTemplate.render("{cursor}x{{cursor}}", ctx()) as RenderResult.Rendered
        first.text shouldBe "x"
        first.cursorBackCodePoints shouldBe 1
        text("{ return; }") shouldBe "{ return; }"
        text("{date:yyyy}") shouldBe "{date:yyyy}"
        text("{datetime}") shouldBe "{datetime}"
        text("{selection}") shouldBe "{selection}"
        text("{date}}") shouldBe "{date}}"
        text("\\{date}") shouldBe "{date}"
        text("{{date}}") shouldBe "2026-03-07"
        text("{clipboard}", ctx(clip = "{date} {cursor}")) shouldBe "{date} {cursor}"
        SnippetTemplate.usesPlaceholder("see {clipboard}", "clipboard") shouldBe true
        SnippetTemplate.usesPlaceholder("see \\{clipboard}", "clipboard") shouldBe false
        SnippetTemplate.usesPlaceholder("{ return clipboard }", "clipboard") shouldBe false
    }

    test("a backslash escapes the opening braces") {
        text("\\{{date}}") shouldBe "{{date}}"
        // There is no way to escape the backslash itself: only the three characters \{{ are special, so two backslashes
        // followed by braces are a backslash plus an escaped brace pair.
        text("\\\\{{date}}") shouldBe "\\{{date}}"
    }

    test("nothing can be executed: command-like placeholders are literal text and reported") {
        for (p in listOf("exec:ls", "shell:id", "run:rm -rf /", "env:HOME", "file:/etc/passwd", "url:http://x", "foo")) {
            text("{{$p}}") shouldBe "{{$p}}"
            SnippetTemplate.unknownPlaceholders("a {{$p}} b") shouldBe listOf(p)
        }
    }

    test("placeholders with arguments they do not accept are unknown") {
        text("{{cursor:1}}{{clipboard:x}}{{selection:y}}") shouldBe "{{cursor:1}}{{clipboard:x}}{{selection:y}}"
        SnippetTemplate.unknownPlaceholders("{{date}} {{cursor}} {{clipboard}}") shouldBe emptyList()
    }

    test("unterminated, nested and multi-line braces degrade to literal text, valid ones still work") {
        text("a {{date") shouldBe "a {{date"
        text("{{") shouldBe "{{"
        text("}} {{") shouldBe "}} {{"
        text("{{a {{date}}") shouldBe "{{a 2026-03-07"
        text("{{da\nte}}") shouldBe "{{da\nte}}"
        text("{{" + "x".repeat(100) + "}}") shouldBe "{{" + "x".repeat(100) + "}}"
    }

    test("results over the limit are refused instead of truncated") {
        SnippetTemplate.render("{{clipboard}}", ctx(clip = "x".repeat(SnippetLimits.MAX_RENDERED + 1))) shouldBe RenderResult.TooLarge
        SnippetTemplate.render("{{clipboard}}{{clipboard}}", ctx(clip = "x".repeat(12_000))) shouldBe RenderResult.TooLarge
        SnippetTemplate.render("{{clipboard}}", ctx(clip = "x".repeat(SnippetLimits.MAX_RENDERED)))
            .shouldBeInstanceOf<RenderResult.Rendered>()
    }

    test("random brace/backslash/colon soup never throws and never loses plain characters") {
        val rnd = Random(42)
        val alphabet = "{}\\:abcdtimeclipboardcursor \n$"
        repeat(5_000) {
            val body = buildString { repeat(rnd.nextInt(0, 60)) { append(alphabet[rnd.nextInt(alphabet.length)]) } }
            SnippetTemplate.render(body, ctx("c", "s")).shouldBeInstanceOf<RenderResult.Rendered>()
            SnippetTemplate.unknownPlaceholders(body)
        }
    }
})
