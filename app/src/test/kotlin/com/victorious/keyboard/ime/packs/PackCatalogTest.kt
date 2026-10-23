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

package com.victorious.keyboard.ime.packs

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import java.io.File

private const val SHA = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
private const val URL = "https://github.com/Victorious93/Victorious-Keyboard/releases/download/packs-demo-1.0.0/demo-1.0.0.zip"

private fun entry(
    type: String = "dictionary-pack",
    id: String = "com.victorious.demo",
    version: String = "1.0.0",
    extra: String = """"url":"$URL","sha256":"$SHA","sizeBytes":1234""",
    title: String = "Demo",
) = """{"type":"$type","id":"$id","version":"$version","title":"$title","description":"d","publisher":"Victorious"${if (extra.isEmpty()) "" else ",$extra"}}"""

private fun catalog(vararg entries: String, format: String = "victorious-pack-catalog", version: Int = 1) =
    """{"format":"$format","version":$version,"packs":[${entries.joinToString(",")}]}""".toByteArray()

private fun ok(bytes: ByteArray) = PackCatalogReader.read(bytes).shouldBeInstanceOf<CatalogResult.Ok>().catalog
private fun failed(bytes: ByteArray) = PackCatalogReader.read(bytes).shouldBeInstanceOf<CatalogResult.Failed>().error

class PackCatalogTest : FunSpec({
    test("an empty catalog is valid") {
        val c = ok(catalog())
        c.entries shouldBe emptyList()
        c.rejectedEntries shouldBe 0
    }

    test("a download entry and a command provider entry are read") {
        val provider = entry(
            type = "command-provider", id = "com.victorious.provider",
            extra = """"packageName":"ai.example.app","certSha256":"$SHA"""",
        )
        val c = ok(catalog(entry(), provider))
        c.rejectedEntries shouldBe 0
        c.entries.map { it.type } shouldBe listOf(PackType.DICTIONARY_PACK, PackType.COMMAND_PROVIDER)
        c.entries[0].sha256 shouldBe SHA
        c.entries[1].packageName shouldBe "ai.example.app"
    }

    test("every pack type has a distinct wire name that round-trips") {
        PackType.entries.map { it.wire }.toSet().size shouldBe PackType.entries.size
        PackType.entries.forEach { PackType.fromWire(it.wire) shouldBe it }
        PackType.fromWire("plugin") shouldBe null
    }

    test("a wrong format or version is refused") {
        failed(catalog(format = "other")) shouldBe CatalogError.UNSUPPORTED_FORMAT
        failed(catalog(version = 2)) shouldBe CatalogError.UNSUPPORTED_VERSION
        failed(catalog(version = 0)) shouldBe CatalogError.UNSUPPORTED_VERSION
    }

    test("bad JSON, a wrong shape and an empty file are refused") {
        failed("not json".toByteArray()) shouldBe CatalogError.BAD_JSON
        failed("[]".toByteArray()) shouldBe CatalogError.BAD_JSON
        failed(ByteArray(0)) shouldBe CatalogError.BAD_JSON
        failed("""{"format":"victorious-pack-catalog","version":1,"packs":"x"}""".toByteArray()) shouldBe CatalogError.BAD_JSON
        failed(byteArrayOf(0xC3.toByte(), 0x28)) shouldBe CatalogError.BAD_JSON
    }

    test("a file over the size limit is refused before it is parsed") {
        failed(ByteArray(CatalogLimits.MAX_BYTES + 1) { ' '.code.toByte() }) shouldBe CatalogError.TOO_LARGE
    }

    test("more entries than the limit is refused") {
        val many = Array(CatalogLimits.MAX_ENTRIES + 1) { entry(id = "com.victorious.p$it") }
        failed(catalog(*many)) shouldBe CatalogError.TOO_MANY_ENTRIES
    }

    test("a newer catalog with extra fields is still read") {
        val bytes = """{"format":"victorious-pack-catalog","version":1,"future":true,"packs":[
            {"type":"theme","id":"com.victorious.t","version":"1.2.3","title":"T","description":"","publisher":"V",
             "url":"$URL","sha256":"$SHA","sizeBytes":10,"newField":[1,2]}]}""".toByteArray()
        ok(bytes).entries.size shouldBe 1
    }

    test("an entry that fails a check is dropped and counted, the rest are kept") {
        val c = ok(catalog(entry(), entry(id = "com.victorious.bad", version = "1.0"), entry(id = "com.victorious.fine")))
        c.entries.map { it.id } shouldBe listOf("com.victorious.demo", "com.victorious.fine")
        c.rejectedEntries shouldBe 1
    }

    test("a second entry with the same id is dropped, whatever its type") {
        val c = ok(catalog(entry(), entry(type = "theme")))
        c.entries.size shouldBe 1
        c.rejectedEntries shouldBe 1
    }

    test("ids must be an own com.victorious id") {
        listOf("org.florisboard.themes", "com.victorious", "com.victoriousx.a", "COM.victorious.a", "com.victorious.A", "com.victorious..a", "", "a")
            .forEach { ok(catalog(entry(id = it))).entries shouldBe emptyList() }
    }

    test("versions must be three plain numbers") {
        listOf("1", "1.0", "1.0.0.0", "01.0.0", "1.0.0-beta", "v1.0.0", "", "1.0.x", "1234567.0.0")
            .forEach { ok(catalog(entry(version = it))).entries shouldBe emptyList() }
    }

    test("download links must be a release asset of this repository, over https") {
        listOf(
            "http://github.com/Victorious93/Victorious-Keyboard/releases/download/t/f.zip",
            "https://github.com/Other/Victorious-Keyboard/releases/download/t/f.zip",
            "https://github.com/Victorious93/Victorious-Keyboard/releases/download/t/../f.zip",
            "https://github.com/Victorious93/Victorious-Keyboard/releases/download/t/f.zip?x=1",
            "https://github.com/Victorious93/Victorious-Keyboard/releases/download/t/f zip",
            "https://github.com/Victorious93/Victorious-Keyboard/releases/download/t",
            "https://github.com.evil.example/Victorious93/Victorious-Keyboard/releases/download/t/f.zip",
            "https://example.com/f.zip",
            "",
        ).forEach {
            ok(catalog(entry(extra = """"url":"$it","sha256":"$SHA","sizeBytes":5"""))).entries shouldBe emptyList()
        }
    }

    test("the hash must be 64 lower-case hex characters") {
        listOf(SHA.uppercase(), SHA.dropLast(1), SHA + "0", "g" + SHA.drop(1), "")
            .forEach { ok(catalog(entry(extra = """"url":"$URL","sha256":"$it","sizeBytes":5"""))).entries shouldBe emptyList() }
    }

    test("a download needs a size inside the limit") {
        listOf("0", "-1", (CatalogLimits.MAX_DOWNLOAD_BYTES + 1).toString())
            .forEach { ok(catalog(entry(extra = """"url":"$URL","sha256":"$SHA","sizeBytes":$it"""))).entries shouldBe emptyList() }
        ok(catalog(entry(extra = """"url":"$URL","sha256":"$SHA""""))).entries shouldBe emptyList()
        ok(catalog(entry(extra = """"url":"$URL","sizeBytes":5"""))).entries shouldBe emptyList()
        ok(catalog(entry(extra = """"sha256":"$SHA","sizeBytes":5"""))).entries shouldBe emptyList()
        ok(catalog(entry(extra = """"url":"$URL","sha256":"$SHA","sizeBytes":${CatalogLimits.MAX_DOWNLOAD_BYTES}"""))).entries.size shouldBe 1
    }

    test("a download must not carry provider fields, and a provider must not carry download fields") {
        ok(catalog(entry(extra = """"url":"$URL","sha256":"$SHA","sizeBytes":5,"packageName":"a.b""""))).entries shouldBe emptyList()
        val provider = """"packageName":"ai.example.app","certSha256":"$SHA""""
        ok(catalog(entry(type = "command-provider", extra = provider + ""","sha256":"$SHA""""))).entries shouldBe emptyList()
        ok(catalog(entry(type = "command-provider", extra = provider + ""","sizeBytes":5"""))).entries shouldBe emptyList()
    }

    test("a command provider needs a valid package name and certificate hash") {
        listOf(
            """"packageName":"nodots","certSha256":"$SHA"""",
            """"packageName":"Ai.Example.App","certSha256":"$SHA"""",
            """"packageName":"ai.example.app","certSha256":"${SHA.uppercase()}"""",
            """"packageName":"ai.example.app"""",
            """"certSha256":"$SHA"""",
            """"packageName":"ai..app","certSha256":"$SHA"""",
        ).forEach { ok(catalog(entry(type = "command-provider", extra = it))).entries shouldBe emptyList() }
    }

    test("a command provider information page must be on this repository") {
        val good = """"packageName":"ai.example.app","certSha256":"$SHA","url":"https://github.com/Victorious93/Victorious-Keyboard/blob/main/packs/command-providers.md""""
        ok(catalog(entry(type = "command-provider", extra = good))).entries.size shouldBe 1
        val bad = good.replace("Victorious93", "Other")
        ok(catalog(entry(type = "command-provider", extra = bad))).entries shouldBe emptyList()
    }

    test("titles and publishers are bounded text without control characters") {
        ok(catalog(entry(title = ""))).entries shouldBe emptyList()
        ok(catalog(entry(title = "x".repeat(CatalogLimits.MAX_TITLE + 1)))).entries shouldBe emptyList()
        ok(catalog(entry(title = "x".repeat(CatalogLimits.MAX_TITLE)))).entries.size shouldBe 1
        ok(catalog(entry(title = "a\\u0007b"))).entries shouldBe emptyList()
        ok(catalog(entry().replace(""""publisher":"Victorious"""", """"publisher":"""""))).entries shouldBe emptyList()
    }

    test("a description over the limit is dropped") {
        val long = "x".repeat(CatalogLimits.MAX_DESCRIPTION + 1)
        ok(catalog(entry().replace(""""description":"d"""", """"description":"$long""""))).entries shouldBe emptyList()
    }

    test("the repository's own catalog is valid and every entry in it is accepted") {
        val file = File("../packs/index.json")
        val c = ok(file.readBytes())
        c.rejectedEntries shouldBe 0
    }

    test("the documentation names the format, the version and every pack type") {
        val docs = File("../docs/PACKS.md").readText()
        docs shouldContain CatalogLimits.FORMAT
        docs shouldContain "\"version\": ${CatalogLimits.VERSION}"
        PackType.entries.forEach { docs shouldContain it.wire }
    }
})
