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

package com.victorious.keyboard.ime.clipboard.vault

import com.victorious.keyboard.ime.clipboard.history.ClipCategory
import com.victorious.keyboard.ime.clipboard.history.entry
import com.victorious.keyboard.ime.clipboard.provider.ClipboardItem
import com.victorious.keyboard.ime.clipboard.provider.ItemType
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotBeEmpty

class ClipboardMappingTest : FunSpec({
    test("a domain entry survives a trip through a database row") {
        val e = entry(
            7, "hello", pinned = true, favorite = true, tags = setOf("b", "a"),
            category = ClipCategory.URL, editedAtMs = 42L,
        ).copy(sensitive = true)
        val row = e.toNewClipboardItem()
        row.type shouldBe ItemType.TEXT
        row.tags shouldBe "a,b" // stored sorted, comma separated
        row.toClipEntry() shouldBe e.copy(tags = setOf("a", "b"))
    }

    test("tag column parsing and formatting") {
        parseTagColumn("") shouldBe emptySet()
        parseTagColumn("x,y,,z") shouldBe setOf("x", "y", "z")
        formatTagColumn(setOf("z", "a")) shouldBe "a,z"
    }

    test("media rows map to blank-text entries so they count toward the cap but are never exported") {
        val image = ClipboardItem(
            id = 3, type = ItemType.IMAGE, text = null, uri = null, creationTimestampMs = 5L,
            isPinned = false, mimeTypes = listOf("image/png"),
        )
        image.uuid.shouldNotBeEmpty()
        val asEntry = image.toClipEntry()
        asEntry.text shouldBe ""
        asEntry.id shouldBe image.uuid
        image.isTextItem shouldBe false
    }

    test("withEntryFields updates user data but keeps the database id") {
        val row = entry(1, "old").toNewClipboardItem().copy(id = 99)
        val updated = row.withEntryFields(entry(1, "new", pinned = true, tags = setOf("t")))
        updated.id shouldBe 99
        updated.text shouldBe "new"
        updated.isPinned shouldBe true
        updated.tags shouldBe "t"
    }

    test("new rows get distinct UUIDs") {
        val a = ClipboardItem.text("x")
        val b = ClipboardItem.text("x")
        (a.uuid != b.uuid) shouldBe true
    }
})
