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

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.time.LocalDateTime

private val NOW = LocalDateTime.of(2026, 3, 4, 5, 6)

private fun sn(body: String, id: String = "a", enabled: Boolean = true, tags: List<String> = emptyList(), title: String = "T$id") =
    Snippet(id, title, body, null, tags, enabled)

private fun req(
    body: String,
    clip: String? = null,
    sel: String? = null,
    sensitive: Boolean = false,
    enabled: Boolean = true,
) = InsertionRequest(sn(body, enabled = enabled), NOW, clip, sel, sensitive)

private fun lib(vararg s: Snippet): SnippetLibrary {
    var l = SnippetLibrary.EMPTY
    for (x in s) l = (l.add(x) as LibraryResult.Ok).library
    return l
}

class SnippetInsertionTest : FunSpec({
    test("plain body is inserted as is") {
        SnippetInsertion.plan(req("hello")) shouldBe InsertionPlan.Insert("hello", null)
    }

    test("selection and clipboard are substituted outside sensitive fields") {
        SnippetInsertion.plan(req("[{{selection}}|{{clipboard}}]", clip = "C", sel = "S")) shouldBe
            InsertionPlan.Insert("[S|C]", null)
    }

    test("sensitive field never receives selection or clipboard, whatever the caller passes") {
        SnippetInsertion.plan(req("[{{selection}}|{{clipboard}}]", clip = "secret", sel = "also secret", sensitive = true)) shouldBe
            InsertionPlan.Insert("[|]", null)
    }

    test("sensitive field still gets dates and literal text") {
        SnippetInsertion.plan(req("{{date}} x", sensitive = true)) shouldBe InsertionPlan.Insert("2026-03-04 x", null)
    }

    test("selected text is data: placeholders inside it are not expanded") {
        SnippetInsertion.plan(req("{{selection}}", sel = "{{clipboard}}{{cursor}}", clip = "C")) shouldBe
            InsertionPlan.Insert("{{clipboard}}{{cursor}}", null)
    }

    test("cursor placement is reported in code points from the end") {
        SnippetInsertion.plan(req("a{{cursor}}é😀")) shouldBe InsertionPlan.Insert("aé😀", 2)
    }

    test("wrapping a selection with the cursor after it") {
        SnippetInsertion.plan(req("({{selection}}){{cursor}}", sel = "x")) shouldBe InsertionPlan.Insert("(x)", 0)
    }

    test("date and time patterns come from the request") {
        val r = InsertionRequest(sn("{{date}} {{time}}"), NOW, datePattern = "dd.MM.yyyy", timePattern = "HH.mm")
        SnippetInsertion.plan(r) shouldBe InsertionPlan.Insert("04.03.2026 05.06", null)
    }

    test("a huge selection is refused, not truncated") {
        SnippetInsertion.plan(req("{{selection}}", sel = "x".repeat(SnippetLimits.MAX_RENDERED + 1))) shouldBe InsertionPlan.TooLarge
    }

    test("a selection exactly at the limit is accepted") {
        val sel = "x".repeat(SnippetLimits.MAX_RENDERED)
        SnippetInsertion.plan(req("{{selection}}", sel = sel)) shouldBe InsertionPlan.Insert(sel, null)
    }

    test("a huge selection is not read in a sensitive field, so it cannot trigger TooLarge either") {
        SnippetInsertion.plan(req("a{{selection}}", sel = "x".repeat(SnippetLimits.MAX_RENDERED + 1), sensitive = true)) shouldBe
            InsertionPlan.Insert("a", null)
    }

    test("disabled snippets, empty bodies and empty results insert nothing") {
        SnippetInsertion.plan(req("x", enabled = false)) shouldBe InsertionPlan.NothingToInsert
        SnippetInsertion.plan(req("")) shouldBe InsertionPlan.NothingToInsert
        SnippetInsertion.plan(req("{{selection}}")) shouldBe InsertionPlan.NothingToInsert
        SnippetInsertion.plan(req("{{clipboard}}", clip = "")) shouldBe InsertionPlan.NothingToInsert
    }

    test("an over-long body is refused even if it somehow got into memory") {
        SnippetInsertion.plan(req("x".repeat(SnippetLimits.MAX_BODY + 1))) shouldBe InsertionPlan.NothingToInsert
    }

    test("a body exactly at the size limit is accepted") {
        val body = "x".repeat(SnippetLimits.MAX_BODY)
        SnippetInsertion.plan(req(body)) shouldBe InsertionPlan.Insert(body, null)
    }

    test("a huge clipboard is refused, not truncated") {
        SnippetInsertion.plan(req("{{clipboard}}", clip = "c".repeat(SnippetLimits.MAX_RENDERED + 1))) shouldBe InsertionPlan.TooLarge
    }

    test("literal text plus a selection that together exceed the limit are refused") {
        SnippetInsertion.plan(req("abc{{selection}}", sel = "x".repeat(SnippetLimits.MAX_RENDERED - 2))) shouldBe InsertionPlan.TooLarge
    }

    test("a sensitive field with a disabled snippet still inserts nothing") {
        SnippetInsertion.plan(req("x", sensitive = true, enabled = false)) shouldBe InsertionPlan.NothingToInsert
    }

    test("an unusable date pattern in the request falls back to the text the user wrote") {
        val r = InsertionRequest(sn("{{date}}"), NOW, datePattern = "not a pattern ((")
        SnippetInsertion.plan(r) shouldBe InsertionPlan.Insert("{{date}}", null)
    }

    test("unknown placeholders stay literal") {
        SnippetInsertion.plan(req("{{exec:rm -rf /}}")) shouldBe InsertionPlan.Insert("{{exec:rm -rf /}}", null)
    }

    context("picker model") {
        val a = sn("A", id = "a", title = "Beta", tags = listOf("Git", "shell"))
        val b = sn("B", id = "b", title = "alpha", tags = listOf("git"))
        val c = sn("C", id = "c", title = "Disabled", enabled = false, tags = listOf("hidden"))
        val library = lib(a, b, c)

        test("only enabled snippets, sorted by title ignoring case") {
            SnippetPickerModel.items(library).map { it.id } shouldBe listOf("b", "a")
        }
        test("tag filter is case-insensitive") {
            SnippetPickerModel.items(library, "GIT").map { it.id } shouldBe listOf("b", "a")
            SnippetPickerModel.items(library, "shell").map { it.id } shouldBe listOf("a")
            SnippetPickerModel.items(library, "nope") shouldBe emptyList()
        }
        test("tags come from enabled snippets only, de-duplicated and sorted") {
            SnippetPickerModel.tags(library) shouldBe listOf("Git", "shell")
        }
        test("empty library") {
            SnippetPickerModel.items(SnippetLibrary.EMPTY) shouldBe emptyList()
            SnippetPickerModel.tags(SnippetLibrary.EMPTY) shouldBe emptyList()
        }
    }
})

class SnippetTemplateUsageTest : FunSpec({
    test("usesPlaceholder finds real placeholders only") {
        SnippetTemplate.usesPlaceholder("a {{selection}} b", "selection") shouldBe true
        SnippetTemplate.usesPlaceholder("{{ Selection }}", "selection") shouldBe true
        SnippetTemplate.usesPlaceholder("a {{clipboard}}", "selection") shouldBe false
        SnippetTemplate.usesPlaceholder("x{{clipboard}}", "clipboard") shouldBe true
        SnippetTemplate.usesPlaceholder("\\{{clipboard}}", "clipboard") shouldBe false
        SnippetTemplate.usesPlaceholder("\\{{selection}}", "selection") shouldBe false
        SnippetTemplate.usesPlaceholder("{{selection:x}}", "selection") shouldBe false
        SnippetTemplate.usesPlaceholder("{{selection", "selection") shouldBe false
        SnippetTemplate.usesPlaceholder("", "selection") shouldBe false
    }
})

class CaretPlacementTest : FunSpec({
    test("no cursor, or nothing after it, means no key presses") {
        CaretPlacement.leftPresses("abc", null) shouldBe 0
        CaretPlacement.leftPresses("abc", 0) shouldBe 0
        CaretPlacement.leftPresses("", 3) shouldBe 0
    }
    test("plain text: one press per character") {
        CaretPlacement.leftPresses("Hi () there", 8) shouldBe 8
    }
    test("a combining accent is one cluster, not two code points") {
        CaretPlacement.leftPresses("ab" + "é" + "c", 3) shouldBe 2
    }
    test("CRLF is one cluster") {
        CaretPlacement.leftPresses("x\r\ny", 3) shouldBe 2
    }
    test("a skin-tone emoji is one cluster, not two code points") {
        CaretPlacement.leftPresses("a " + "👍🏽", 3) shouldBe 2
    }
    test("a flag is one cluster, not two code points") {
        CaretPlacement.leftPresses("a" + "🇩🇪", 2) shouldBe 1
    }
    test("a count larger than the text is clamped, not an error") {
        CaretPlacement.leftPresses("abc", 99) shouldBe 3
    }
    test("a huge tail is not attempted at all") {
        val text = "x".repeat(CaretPlacement.MAX_KEY_PRESSES + 1)
        CaretPlacement.leftPresses(text, text.length) shouldBe 0
        val ok = "x".repeat(CaretPlacement.MAX_KEY_PRESSES)
        CaretPlacement.leftPresses(ok, ok.length) shouldBe CaretPlacement.MAX_KEY_PRESSES
    }
})
