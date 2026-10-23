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

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private const val HASH = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
private const val CERT = "8C:1F:E3:C9:E5:34:F2:71:05:C0:68:8F:45:28:D7:7E:47:11:AE:0A:79:DD:08:D1:E8:59:30:B9:E9:48:85:F2"
private const val BASE = "https://github.com/Victorious93/Victorious-Keyboard/releases/download"

private fun doc(vararg entries: String, extra: String = "", date: String = "2026-10-23", version: String = "1") =
    """{"format":"victorious-pack-catalog","version":$version,"updated":"$date"$extra,"packs":[${entries.joinToString(",")}]}"""

private fun theme(
    id: String = "com.victorious.neon_plus",
    publisher: String = "victorious",
    download: String = "$BASE/v1/neon-plus.flex",
    extra: String = "",
    version: String = "1.0.0",
    sha: String = HASH,
    size: String = "2048",
    type: String = "theme",
) = """{"id":"$id","type":"$type","version":"$version","title":"Neon Plus","description":"A theme.",""" +
    """"publisher":"$publisher","download":"$download","sha256":"$sha","size_bytes":$size$extra}"""

private fun provider(
    pkg: String = "ai.victorious.filemanager",
    cert: String = CERT,
    id: String = "ai.victorious.filemanager",
    extra: String = "",
) = """{"id":"$id","type":"command-provider","version":"1.0.0","title":"File Manager","publisher":"victorious",""" +
    """"package":"$pkg","cert_sha256":"$cert"$extra}"""

private fun read(text: String, reserved: Set<String> = emptySet()) = PackCatalogReader.read(text.toByteArray(), reserved)

private fun failed(text: String, reserved: Set<String> = emptySet()) =
    read(text, reserved).shouldBeInstanceOf<CatalogResult.Failed>()

class PackCatalogTest : FunSpec({
    context("well-formed catalogs") {
        test("an empty catalog is valid") {
            val ok = read(doc()).shouldBeInstanceOf<CatalogResult.Ok>()
            ok.catalog.entries.size shouldBe 0
            ok.catalog.updated shouldBe "2026-10-23"
        }

        test("a downloadable pack and a command provider both parse, with every field kept") {
            val ok = read(doc(theme(), provider())).shouldBeInstanceOf<CatalogResult.Ok>()
            val (t, p) = ok.catalog.entries
            t.type shouldBe PackType.THEME
            t.download shouldBe "$BASE/v1/neon-plus.flex"
            t.sha256 shouldBe HASH
            t.sizeBytes shouldBe 2048L
            t.packageName shouldBe null
            p.type shouldBe PackType.COMMAND_PROVIDER
            p.packageName shouldBe "ai.victorious.filemanager"
            p.certSha256 shouldBe CERT
            p.download shouldBe null
            p.sizeBytes shouldBe null
        }

        test("every downloadable type is accepted, and a dictionary pack uses its own slug pattern") {
            val kinds = listOf("language-pack", "theme", "layout", "extension")
            for (k in kinds) read(doc(theme(id = "com.victorious.x_${k.replace('-', '_')}", type = k))).shouldBeInstanceOf<CatalogResult.Ok>()
            val dict = theme(id = "kali-extra", type = "dictionary-pack", download = "$BASE/v1/kali-extra.zip")
            read(doc(dict)).shouldBeInstanceOf<CatalogResult.Ok>()
        }

        test("another publisher can list a pack outside the com.victorious namespace") {
            val other = theme(id = "org.example.pack", publisher = "someone else")
            read(doc(other)).shouldBeInstanceOf<CatalogResult.Ok>()
        }
    }

    context("the document itself") {
        test("too large, not JSON, wrong shape") {
            PackCatalogReader.read(ByteArray(PackCatalogLimits.MAX_INDEX_BYTES + 1)).shouldBeInstanceOf<CatalogResult.Failed>()
                .error shouldBe CatalogError.TOO_LARGE
            failed("not json").error shouldBe CatalogError.NOT_JSON
            failed("").error shouldBe CatalogError.NOT_JSON
            failed("[]").error shouldBe CatalogError.BAD_SHAPE
            failed("""{"format":"victorious-pack-catalog","version":1,"updated":"2026-10-23"}""").error shouldBe
                CatalogError.BAD_SHAPE
        }

        test("wrong format, unsupported version, bad date, extra field") {
            failed(doc().replace("victorious-pack-catalog", "other")).error shouldBe CatalogError.WRONG_FORMAT
            failed(doc(version = "2")).error shouldBe CatalogError.UNSUPPORTED_VERSION
            failed(doc(version = "\"1\"")).error shouldBe CatalogError.BAD_SHAPE
            failed(doc(date = "yesterday")).error shouldBe CatalogError.BAD_DATE
            failed(doc(extra = ""","note":"hi"""")).error shouldBe CatalogError.UNEXPECTED_FIELD
        }

        test("the entry cap is exact") {
            val many = (1..PackCatalogLimits.MAX_ENTRIES).map { theme(id = "com.victorious.p$it") }
            read(doc(*many.toTypedArray())).shouldBeInstanceOf<CatalogResult.Ok>()
            val tooMany = many + theme(id = "com.victorious.extra")
            failed(doc(*tooMany.toTypedArray())).error shouldBe CatalogError.TOO_MANY_ENTRIES
        }
    }

    context("ids") {
        test("a bad id, a built-in id, a reserved id and a duplicate are refused, with the entry position") {
            failed(doc(theme(), theme(id = "Bad Id"))).let { it.error shouldBe CatalogError.BAD_ID; it.entry shouldBe 1 }
            failed(doc(theme(id = "org.florisboard.themes", publisher = "x"))).error shouldBe CatalogError.RESERVED_ID
            failed(doc(theme(id = "com.victorious.keyboard.themes"))).error shouldBe CatalogError.RESERVED_ID
            failed(doc(theme(id = "com.victorious.keyboard"))).error shouldBe CatalogError.RESERVED_ID
            read(doc(theme(id = "com.victorious.keyboardx"))).shouldBeInstanceOf<CatalogResult.Ok>()
            failed(doc(theme(id = "com.victorious.mine")), reserved = setOf("com.victorious.mine")).error shouldBe
                CatalogError.RESERVED_ID
            val dict = theme(id = "termux", type = "dictionary-pack", download = "$BASE/v1/t.zip")
            failed(doc(dict), reserved = setOf("termux")).error shouldBe CatalogError.RESERVED_ID
            failed(doc(theme(), theme())).let { it.error shouldBe CatalogError.DUPLICATE_ID; it.entry shouldBe 1 }
        }

        test("a dictionary slug cannot be an extension id, and an extension id cannot be a slug") {
            val slugAsExt = theme(id = "my-pack")
            failed(doc(slugAsExt)).error shouldBe CatalogError.BAD_ID
            val extAsSlug = theme(id = "com.victorious.words", type = "dictionary-pack", download = "$BASE/v1/w.zip")
            failed(doc(extAsSlug)).error shouldBe CatalogError.BAD_ID
        }

        test("only the project publishes under com.victorious, and the project stays inside it") {
            failed(doc(theme(publisher = "someone else"))).error shouldBe CatalogError.BAD_PUBLISHER
            failed(doc(theme(id = "org.example.pack", publisher = "victorious"))).error shouldBe CatalogError.BAD_PUBLISHER
        }

        test("ids and publishers are bounded") {
            failed(doc(theme(id = "com.victorious." + "a".repeat(PackCatalogLimits.MAX_ID)))).error shouldBe
                CatalogError.BAD_ID
            failed(doc(theme(publisher = "Upper Case"))).error shouldBe CatalogError.BAD_PUBLISHER
            failed(doc(theme(publisher = ""))).error shouldBe CatalogError.BAD_PUBLISHER
        }
    }

    context("downloads") {
        test("only release assets of this repository are accepted") {
            val bad = listOf(
                "http://github.com/Victorious93/Victorious-Keyboard/releases/download/v1/a.flex",
                "https://example.com/a.flex",
                "https://github.com/Other/Victorious-Keyboard/releases/download/v1/a.flex",
                "https://github.com/Victorious93/Victorious-Keyboard/releases/download.evil/v1/a.flex",
                "$BASE/v1/a.flex?x=1",
                "$BASE/v1/a.flex#frag",
                "$BASE/v1/../a.flex",
                "$BASE/../v1/a.flex",
                "$BASE//a.flex",
                "$BASE/v1/a.flex/extra",
                "$BASE/v1/a.exe",
                "$BASE/v1/a b.flex",
                "$BASE/v1/%2e%2e.flex",
                "$BASE/v1/a@evil.flex",
                "$BASE/v1/a\\b.flex",
                "$BASE/" + "a".repeat(PackCatalogLimits.MAX_DOWNLOAD_URL) + "/a.flex",
            )
            for (url in bad) {
                withClue(url) { failed(doc(theme(download = url))).error shouldBe CatalogError.BAD_DOWNLOAD }
            }
            read(doc(theme(download = "$BASE/v1.2/a.zip"))).shouldBeInstanceOf<CatalogResult.Ok>()
        }

        test("a downloadable pack needs its hash and size, with exact bounds") {
            failed(doc(theme(sha = HASH.uppercase()))).error shouldBe CatalogError.BAD_HASH
            failed(doc(theme(sha = HASH.dropLast(1)))).error shouldBe CatalogError.BAD_HASH
            failed(doc(theme(sha = "g".repeat(64)))).error shouldBe CatalogError.BAD_HASH
            failed(doc(theme(size = "0"))).error shouldBe CatalogError.BAD_SIZE
            failed(doc(theme(size = "-5"))).error shouldBe CatalogError.BAD_SIZE
            failed(doc(theme(size = (PackCatalogLimits.MAX_PACK_BYTES + 1).toString()))).error shouldBe CatalogError.BAD_SIZE
            read(doc(theme(size = PackCatalogLimits.MAX_PACK_BYTES.toString()))).shouldBeInstanceOf<CatalogResult.Ok>()
            failed(doc(theme(size = "12.5"))).error shouldBe CatalogError.BAD_SIZE
            failed(doc(theme(size = "\"2048\""))).error shouldBe CatalogError.BAD_SHAPE
        }

        test("a downloadable pack cannot carry a package or certificate") {
            failed(doc(theme(extra = ""","package":"a.b""""))).error shouldBe CatalogError.UNEXPECTED_FIELD
            failed(doc(theme(extra = ""","cert_sha256":"$CERT""""))).error shouldBe CatalogError.UNEXPECTED_FIELD
        }
    }

    context("command providers") {
        test("a provider needs a package and a certificate and cannot carry a download") {
            failed(doc(provider(pkg = "NotAPackage"))).error shouldBe CatalogError.BAD_PACKAGE
            failed(doc(provider(pkg = "single"))).error shouldBe CatalogError.BAD_PACKAGE
            failed(doc(provider(cert = CERT.lowercase()))).error shouldBe CatalogError.BAD_CERT
            failed(doc(provider(cert = CERT.replace(":", "")))).error shouldBe CatalogError.BAD_CERT
            failed(doc(provider(cert = CERT.dropLast(3)))).error shouldBe CatalogError.BAD_CERT
            failed(doc(provider(extra = ""","download":"$BASE/v1/a.zip""""))).error shouldBe CatalogError.UNEXPECTED_FIELD
            failed(doc(provider(extra = ""","sha256":"$HASH""""))).error shouldBe CatalogError.UNEXPECTED_FIELD
            failed(doc(provider(extra = ""","size_bytes":5"""))).error shouldBe CatalogError.UNEXPECTED_FIELD
        }
    }

    context("text and shape") {
        test("versions must be three numbers") {
            for (v in listOf("1", "1.0", "1.0.0.0", "v1.0.0", "1.0.0-beta", "", "12345.0.0")) {
                withClue(v) { failed(doc(theme(version = v))).error shouldBe CatalogError.BAD_VERSION }
            }
        }

        test("titles and descriptions are bounded and free of control characters") {
            fun withTitle(t: String) = theme().replace("Neon Plus", t)
            failed(doc(withTitle(""))).error shouldBe CatalogError.BAD_TEXT
            failed(doc(withTitle("a".repeat(PackCatalogLimits.MAX_TITLE + 1)))).error shouldBe CatalogError.BAD_TEXT
            failed(doc(withTitle("bad\\u0000title"))).error shouldBe CatalogError.BAD_TEXT
            failed(doc(withTitle("line\\nbreak"))).error shouldBe CatalogError.BAD_TEXT
            read(doc(withTitle("a".repeat(PackCatalogLimits.MAX_TITLE)))).shouldBeInstanceOf<CatalogResult.Ok>()
            val longDesc = theme().replace("A theme.", "a".repeat(PackCatalogLimits.MAX_DESCRIPTION + 1))
            failed(doc(longDesc)).error shouldBe CatalogError.BAD_TEXT
            read(doc(theme().replace("A theme.", "two\\nlines"))).shouldBeInstanceOf<CatalogResult.Ok>()
        }

        test("an unknown type, an unknown field, a non-object entry and a wrong value type are refused") {
            failed(doc(theme(type = "plugin"))).error shouldBe CatalogError.UNKNOWN_TYPE
            failed(doc(theme(extra = ""","homepage":"https://example.com""""))).error shouldBe CatalogError.UNEXPECTED_FIELD
            failed(doc("42")).error shouldBe CatalogError.BAD_SHAPE
            failed(doc(""""x"""")).error shouldBe CatalogError.BAD_SHAPE
            failed(doc(theme().replace("\"title\":\"Neon Plus\"", "\"title\":7"))).error shouldBe CatalogError.BAD_SHAPE
            failed(doc(theme().replace("\"title\":\"Neon Plus\"", "\"title\":null"))).error shouldBe CatalogError.BAD_SHAPE
            failed(doc(theme(extra = ""","note":{"a":1}"""))).error shouldBe CatalogError.UNEXPECTED_FIELD
        }
    }

    context("hash check") {
        test("a file matches only its own SHA-256") {
            val bytes = "pack".toByteArray()
            val hex = PackHash.sha256Hex(bytes)
            hex.length shouldBe 64
            PackHash.matches(bytes, hex) shouldBe true
            PackHash.matches(bytes, hex.uppercase()) shouldBe true
            PackHash.matches("pack!".toByteArray(), hex) shouldBe false
            PackHash.matches(bytes, hex.dropLast(1)) shouldBe false
            PackHash.matches(bytes, "") shouldBe false
        }
    }

    context("this repository's own packs/index.json") {
        val index = listOf("../packs/index.json", "packs/index.json").map { File(it) }.first { it.isFile }
        // Bundled dictionary packs ship inside the app, so the catalog may not list a pack with the same id.
        val assets = listOf("src/main/assets/ime/dictionary-packs", "app/src/main/assets/ime/dictionary-packs")
            .map { File(it) }.first { it.isDirectory }
        val bundledDictionaries = assets.listFiles { f -> f.name.endsWith(".zip") }!!.map { it.name.removeSuffix(".zip") }.toSet()
        // Every built-in extension id and alias, read from the extension.json files that ship in the app.
        val imeAssets = File(assets.parentFile.path)
        val builtInExtensions = imeAssets.walkTopDown().filter { it.name == "extension.json" }.flatMap { file ->
            val meta = Json.parseToJsonElement(file.readText()).jsonObject["meta"]!!.jsonObject
            listOf(meta["id"]!!.jsonPrimitive.content) +
                (meta["aliases"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList())
        }.toSet()
        val bundled = bundledDictionaries + builtInExtensions

        test("it passes the strict reader") {
            (bundledDictionaries.size >= 5) shouldBe true
            (builtInExtensions.size >= 5) shouldBe true
            val result = PackCatalogReader.read(index.readBytes(), bundled)
            withClue(result.toString()) { result.shouldBeInstanceOf<CatalogResult.Ok>() }
        }

        test("it lists no bundled pack, and every entry has a page in packs/") {
            val ok = PackCatalogReader.read(index.readBytes(), bundled).shouldBeInstanceOf<CatalogResult.Ok>()
            ok.catalog.entries.none { it.id in bundled } shouldBe true
            for (type in PackType.entries) {
                val page = when (type) {
                    PackType.DICTIONARY_PACK -> "dictionary-packs.md"
                    PackType.LANGUAGE_PACK -> "language-packs.md"
                    PackType.THEME -> "themes.md"
                    PackType.LAYOUT -> "layouts.md"
                    PackType.EXTENSION -> "extensions.md"
                    PackType.COMMAND_PROVIDER -> "command-providers.md"
                }
                File(index.parentFile, page).isFile shouldBe true
            }
        }
    }
})
