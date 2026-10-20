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

package com.victorious.keyboard.ime.clipboard

import com.victorious.keyboard.ime.text.key.KeyCode
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class ClipboardTileQueryTest : FunSpec({
    data class Row(val text: String, val pinned: Boolean, val favorite: Boolean)

    val rows = listOf(
        Row("Alpha note", pinned = true, favorite = false),
        Row("beta clip", pinned = false, favorite = true),
        Row("ALPHA extra", pinned = false, favorite = false),
        Row("gamma", pinned = true, favorite = true),
    )

    fun keep(query: String = "", pinnedOnly: Boolean = false, favoritesOnly: Boolean = false) =
        ClipboardTileQuery.filter(rows, { it.text }, { it.pinned }, { it.favorite }, query, pinnedOnly, favoritesOnly)
            .map { it.text }

    test("search keeps rows that contain every word, ignoring case") {
        keep("alpha") shouldBe listOf("Alpha note", "ALPHA extra")
        keep("alpha note") shouldBe listOf("Alpha note")
        keep("  ") shouldBe rows.map { it.text }
        keep("missing") shouldBe emptyList()
    }

    test("pin and favorite switches combine with the query") {
        keep(pinnedOnly = true) shouldBe listOf("Alpha note", "gamma")
        keep(favoritesOnly = true) shouldBe listOf("beta clip", "gamma")
        keep(pinnedOnly = true, favoritesOnly = true) shouldBe listOf("gamma")
        keep("alpha", pinnedOnly = true) shouldBe listOf("Alpha note")
    }

    test("keys edit the query and enter stops capture") {
        ClipboardTileQuery.consumes(KeyCode.DELETE, character = false) shouldBe true
        ClipboardTileQuery.consumes(KeyCode.SHIFT, character = false) shouldBe false
        ClipboardTileQuery.type("ab", KeyCode.DELETE, character = false, text = "") shouldBe
            ClipboardTileQuery.Typed("a", stop = false)
        ClipboardTileQuery.type("a", KeyCode.SPACE, character = false, text = "") shouldBe
            ClipboardTileQuery.Typed("a ", stop = false)
        ClipboardTileQuery.type("a", 0, character = true, text = "b") shouldBe
            ClipboardTileQuery.Typed("ab", stop = false)
        ClipboardTileQuery.type("find", KeyCode.ENTER, character = false, text = "") shouldBe
            ClipboardTileQuery.Typed("find", stop = true)
        val full = "x".repeat(ClipboardTileQuery.MAX_QUERY_CHARS)
        ClipboardTileQuery.type(full, 0, character = true, text = "y") shouldBe
            ClipboardTileQuery.Typed(full, stop = false)
    }
})
