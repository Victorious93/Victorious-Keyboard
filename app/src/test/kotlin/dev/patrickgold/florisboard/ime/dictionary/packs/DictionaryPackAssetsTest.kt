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

package dev.patrickgold.florisboard.ime.dictionary.packs

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import java.io.File

private fun firstExisting(vararg candidates: String): File =
    candidates.map { File(it) }.firstOrNull { it.isDirectory }
        ?: error("none of ${candidates.toList()} found from ${File(".").absolutePath}")

/** The committed zips must be exactly what `tools/build_dictionary_packs.py` makes from `dictionary-packs/`. */
class DictionaryPackAssetsTest : FunSpec({
    val sources = firstExisting("../dictionary-packs", "dictionary-packs")
    val assets = firstExisting("src/main/assets/ime/dictionary-packs", "app/src/main/assets/ime/dictionary-packs")
    val ids = sources.listFiles { f -> f.isDirectory }!!.map { it.name }.sorted()

    test("there is a zip for every source directory and no stray zips") {
        (ids.size >= 5) shouldBe true
        assets.listFiles { f -> f.name.endsWith(".zip") }!!.map { it.name.removeSuffix(".zip") }.sorted() shouldBe ids
    }

    for (id in ids) {
        test("$id: the zip passes the strict reader and matches its sources") {
            val zip = File(assets, "$id.zip").readBytes()
            (zip.size < PackLimits.MAX_ZIP_BYTES) shouldBe true
            val pack = DictionaryPackReader.read(zip).shouldBeInstanceOf<PackReadResult.Ok>().pack
            val dir = File(sources, id)
            val expected = dir.resolve("words.list").readLines().map { it.trim() }.filter { it.isNotEmpty() }
            pack.manifest.id shouldBe id
            pack.rejectedLines shouldBe 0
            pack.entries.map { it.word } shouldBe expected
            pack.entries.all { it.freq in PackWordList.FREQ_MIN..PackWordList.FREQ_MAX } shouldBe true
            expected.map { it.lowercase() }.toSet().size shouldBe expected.size
            (expected.size in 20..PackLimits.MAX_WORDS) shouldBe true
        }
    }

    test("no pack word looks like a command line, option, URL or address") {
        for (id in ids) {
            for (w in File(sources, id).resolve("words.list").readLines().filter { it.isNotBlank() }) {
                w.startsWith("-") shouldBe false
                w.contains("://") shouldBe false
                Regex("\\d+\\.\\d+\\.\\d+\\.\\d+").containsMatchIn(w) shouldBe false
            }
        }
    }
})
