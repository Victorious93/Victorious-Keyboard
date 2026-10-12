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

package dev.patrickgold.florisboard.ime.clipboard.history

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe

class ClipSearchTest : FunSpec({
    val data = listOf(
        entry(1, "git status --short", tags = setOf("git"), category = ClipCategory.SHELL),
        entry(2, "Meeting notes: Alpha Beta", favorite = true),
        entry(3, "https://example.org/Alpha", category = ClipCategory.URL, pinned = true),
        entry(4, "alpha release checklist", tags = setOf("work", "release")),
    )

    fun ids(r: List<ClipEntry>) = r.map { it.id.removePrefix("id-").toInt() }

    test("empty query returns everything newest first") {
        ids(ClipSearch.search(data, "")) shouldBe listOf(4, 3, 2, 1)
    }

    test("matching is case-insensitive") {
        ids(ClipSearch.search(data, "ALPHA")) shouldBe listOf(4, 3, 2)
    }

    test("all terms must match (AND), in any order") {
        ids(ClipSearch.search(data, "beta alpha")) shouldBe listOf(2)
        ids(ClipSearch.search(data, "alpha missing")) shouldBe emptyList()
    }

    test("tags are searchable") {
        ids(ClipSearch.search(data, "release")) shouldBe listOf(4)
        ids(ClipSearch.search(data, "git")) shouldBe listOf(1)
    }

    test("filters combine with the query") {
        ids(ClipSearch.search(data, "alpha", ClipFilter(pinnedOnly = true))) shouldBe listOf(3)
        ids(ClipSearch.search(data, "", ClipFilter(favoritesOnly = true))) shouldBe listOf(2)
        ids(ClipSearch.search(data, "", ClipFilter(category = ClipCategory.SHELL))) shouldBe listOf(1)
        ids(ClipSearch.search(data, "", ClipFilter(tag = " Work "))) shouldBe listOf(4)
    }

    test("an unusable tag filter matches nothing") {
        ClipSearch.search(data, "", ClipFilter(tag = "!!!")).shouldBeEmpty()
    }

    test("special regex characters are treated literally") {
        val d = listOf(entry(1, "a.b*c(d"), entry(2, "axbxc"))
        ids(ClipSearch.search(d, "a.b*c(")) shouldBe listOf(1)
    }

    test("500 long entries are searched correctly") {
        val big = (0 until 500).map { entry(it, "x".repeat(DEFAULT_MAX_ITEM_CHARS - 10) + " n$it") }
        ids(ClipSearch.search(big, "n499")) shouldBe listOf(499)
        ClipSearch.search(big, "").size shouldBe 500
    }

    test("tags are normalised") {
        ClipTags.normalize("  Hello World! ") shouldBe "hello-world"
        ClipTags.normalize("***") shouldBe null
        ClipTags.normalize("a".repeat(100))!!.length shouldBe ClipTags.MAX_TAG_LENGTH
        ClipTags.normalizeAll((1..40).map { "tag$it" }).size shouldBe ClipTags.MAX_TAGS_PER_ENTRY
    }
})
