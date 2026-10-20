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

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain

private fun e(n: Int, name: String = "backup-$n.vkbk") =
    BackupLibraryEntry(uri = "content://provider/doc/$n", displayName = name, addedAtMs = n.toLong())

class BackupLibraryTest : FunSpec({
    test("add is newest-first and replaces an existing uri instead of duplicating it") {
        val lib = BackupLibrary.EMPTY.add(e(1)).add(e(2)).add(e(1, "renamed.vkbk"))
        lib.entries.map { it.uri.substringAfterLast('/') } shouldBe listOf("1", "2")
        lib.find("content://provider/doc/1")!!.displayName shouldBe "renamed.vkbk"
    }

    test("the list is bounded") {
        var lib = BackupLibrary.EMPTY
        repeat(BackupLibrary.MAX_ENTRIES + 20) { lib = lib.add(e(it)) }
        lib.entries shouldHaveSize BackupLibrary.MAX_ENTRIES
        lib.entries.first().addedAtMs shouldBe (BackupLibrary.MAX_ENTRIES + 19).toLong()
    }

    test("update and remove") {
        val lib = BackupLibrary.EMPTY.add(e(1)).add(e(2))
            .update("content://provider/doc/2") { it.copy(itemCount = 500, kind = "encrypted") }
        lib.find("content://provider/doc/2")!!.itemCount shouldBe 500
        lib.remove("content://provider/doc/1").entries shouldHaveSize 1
        lib.remove("content://nope").entries shouldHaveSize 2
    }

    test("JSON round trip") {
        val lib = BackupLibrary.EMPTY.add(e(1).copy(sizeBytes = 123, itemCount = 7, formatVersion = 1, createdAtMs = 99))
        BackupLibrary.fromJson(lib.toJson()).entries shouldBe lib.entries
    }

    test("the stored library holds metadata only") {
        val json = BackupLibrary.EMPTY.add(e(1)).toJson()
        json shouldNotContain "password"
        json shouldNotContain "text"
    }

    test("damaged or hostile input yields an empty library and never throws") {
        for (bad in listOf(null, "", "   ", "{", "[]", "not json", "{\"entries\": 5}", "x".repeat(2_000_000))) {
            BackupLibrary.fromJson(bad).entries shouldHaveSize 0
        }
    }

    test("entries with unusable uris are dropped, long names are truncated") {
        val json = """{"version":1,"entries":[
            {"uri":"","displayName":"blank","addedAtMs":1},
            {"uri":"content://ok/1","displayName":"${"n".repeat(1000)}","addedAtMs":2},
            {"uri":"content://ok/1","displayName":"dup","addedAtMs":3}
        ]}"""
        val lib = BackupLibrary.fromJson(json)
        lib.entries shouldHaveSize 1
        lib.entries[0].displayName.length shouldBe 255
    }
})
