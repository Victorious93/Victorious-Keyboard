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
import java.time.LocalDateTime

private val AFTERNOON = LocalDateTime.of(2026, 3, 7, 14, 5, 9)
private fun r(body: String, date: String = "yyyy-MM-dd", time: String = "HH:mm") =
    (SnippetTemplate.render(body, TemplateContext(AFTERNOON, datePattern = date, timePattern = time)) as RenderResult.Rendered).text

class SnippetFormatsTest : FunSpec({
    test("every offered format is a valid pattern and renders what its label promises") {
        SnippetDateFormat.entries.map { r("{{date}}", date = it.pattern) } shouldBe
            listOf("2026-03-07", "07/03/2026", "03/07/2026", "07.03.2026")
        SnippetTimeFormat.entries.map { r("{{time}}", time = it.pattern) } shouldBe
            listOf("14:05", "14:05:09", "02:05 PM")
    }

    test("enum names are persisted: lists may only grow at the end") {
        SnippetDateFormat.entries.map { it.name } shouldBe listOf("ISO", "DAY_FIRST", "MONTH_FIRST", "DOTTED")
        SnippetTimeFormat.entries.map { it.name } shouldBe listOf("H24", "H24_SECONDS", "H12")
    }

    test("datetime combines the configured date and time patterns") {
        r("{{datetime}}", "dd/MM/yyyy", "hh:mm a") shouldBe "07/03/2026 02:05 PM"
    }

    test("an explicit pattern in the placeholder still wins over the setting") {
        r("{{date:yyyy}} {{time:HH}}", "dd/MM/yyyy", "hh:mm a") shouldBe "2026 14"
    }

    test("the defaults are unchanged when no patterns are configured") {
        (SnippetTemplate.render("{{date}} {{time}} {{datetime}}", TemplateContext(AFTERNOON)) as RenderResult.Rendered).text shouldBe
            "2026-03-07 14:05 2026-03-07 14:05"
    }

    test("a broken configured pattern degrades to literal text instead of failing") {
        r("{{date}}", date = "yyyy-QQQQQQ[") shouldBe "{{date}}"
        r("{{time:QQQQQQ[}}") shouldBe "{{time:QQQQQQ[}}"
    }
})
