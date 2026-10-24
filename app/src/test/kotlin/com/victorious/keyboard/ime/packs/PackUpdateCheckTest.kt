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
import io.kotest.matchers.types.shouldBeInstanceOf
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream

private const val HASH = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
private const val BASE = "https://github.com/Victorious93/Victorious-Keyboard/releases/download"

private fun entry(id: String, version: String, title: String = id, type: String = "theme") =
    """{"id":"$id","type":"$type","version":"$version","title":"$title","publisher":"victorious",""" +
        """"download":"$BASE/v1/$id.flex","sha256":"$HASH","size_bytes":10}"""

private fun catalog(vararg entries: String) =
    """{"format":"victorious-pack-catalog","version":1,"updated":"2026-10-24","packs":[${entries.joinToString(",")}]}"""

/** Records what was asked of it and whether the body was closed. */
private class FakeTransport(private val reply: () -> TransportResponse) : CatalogTransport {
    val urls = mutableListOf<String>()
    var closed = false

    override fun get(url: String): TransportResponse {
        urls += url
        return reply()
    }

    fun body(bytes: ByteArray): TransportResponse = TransportResponse.Ok(
        object : ByteArrayInputStream(bytes) {
            override fun close() {
                closed = true
                super.close()
            }
        },
    )
}

private fun ok(text: String): FakeTransport {
    lateinit var t: FakeTransport
    t = FakeTransport { t.body(text.toByteArray()) }
    return t
}

class PackUpdateCheckTest : FunSpec({
    context("versions") {
        test("only three numbers parse, and they compare numerically") {
            PackVersion.parse("1.2.3") shouldBe PackVersion(1, 2, 3)
            for (bad in listOf("1", "1.2", "1.2.3.4", "v1.2.3", "1.2.3-beta", "", "1.x.3", "12345.0.0", "-1.0.0")) {
                PackVersion.parse(bad) shouldBe null
            }
            (PackVersion.parse("1.10.0")!! > PackVersion.parse("1.9.9")!!) shouldBe true
            (PackVersion.parse("2.0.0")!! > PackVersion.parse("1.99.99")!!) shouldBe true
            (PackVersion.parse("1.0.0")!! == PackVersion.parse("1.0.0")!!) shouldBe true
        }
    }

    context("what is offered") {
        val cat = (PackCatalogReader.read(
            catalog(
                entry("com.victorious.old", "2.0.0", "Old"),
                entry("com.victorious.same", "1.0.0", "Same"),
                entry("com.victorious.fresh", "1.0.0", "Fresh"),
                entry("com.victorious.odd", "1.0.0", "Odd"),
                entry("com.victorious.newer", "1.0.0", "Newer"),
            ).toByteArray(),
        ) as CatalogResult.Ok).catalog

        test("statuses, and the order: updates, new, not comparable, current") {
            val installed = InstalledPacks(
                versionsById = mapOf(
                    "com.victorious.old" to "1.0.0",
                    "com.victorious.same" to "1.0.0",
                    "com.victorious.odd" to "weird",
                    "com.victorious.newer" to "3.0.0",
                ),
            )
            val offers = PackUpdateChecker.offers(cat, installed)
            offers.map { it.entry.id to it.status } shouldBe listOf(
                "com.victorious.old" to OfferStatus.UPDATE,
                "com.victorious.fresh" to OfferStatus.NEW,
                "com.victorious.odd" to OfferStatus.CANNOT_COMPARE,
                "com.victorious.newer" to OfferStatus.CURRENT,
                "com.victorious.same" to OfferStatus.CURRENT,
            )
            offers.first().installedVersion shouldBe "1.0.0"
            offers.first { it.entry.id == "com.victorious.fresh" }.installedVersion shouldBe null
        }

        test("a command provider is new unless its package is installed, and then it cannot be compared") {
            val provider = """{"id":"ai.victorious.filemanager","type":"command-provider","version":"1.0.0",""" +
                """"title":"File Manager","publisher":"victorious","package":"ai.victorious.filemanager",""" +
                """"cert_sha256":"${List(32) { "AB" }.joinToString(":")}"}"""
            val c = (PackCatalogReader.read(catalog(provider).toByteArray()) as CatalogResult.Ok).catalog
            PackUpdateChecker.offers(c, InstalledPacks()).single().status shouldBe OfferStatus.NEW
            PackUpdateChecker.offers(c, InstalledPacks(packages = setOf("ai.victorious.filemanager")))
                .single().status shouldBe OfferStatus.CANNOT_COMPARE
        }
    }

    context("the switch") {
        test("with the switch off nothing is contacted") {
            val t = ok(catalog())
            val r = PackUpdateChecker.check(enabled = false, transport = t, installed = InstalledPacks())
            r shouldBe UpdateCheckResult.Failed(UpdateFailure.DISABLED)
            t.urls.size shouldBe 0
        }

        test("with the switch on, exactly the one fixed address is asked for, once") {
            val t = ok(catalog())
            PackUpdateChecker.check(true, t, InstalledPacks()).shouldBeInstanceOf<UpdateCheckResult.Done>()
            t.urls shouldBe listOf(PackCatalogEndpoint.URL)
            PackCatalogEndpoint.URL shouldBe
                "https://raw.githubusercontent.com/Victorious93/Victorious-Keyboard/main/packs/index.json"
            PackCatalogEndpoint.URL.startsWith("https://") shouldBe true
        }
    }

    context("a good answer") {
        test("it turns into offers, the body is closed, and the date is kept") {
            val t = ok(catalog(entry("com.victorious.a", "1.0.0")))
            val r = PackUpdateChecker.check(true, t, InstalledPacks()).shouldBeInstanceOf<UpdateCheckResult.Done>()
            r.updated shouldBe "2026-10-24"
            r.offers.single().status shouldBe OfferStatus.NEW
            t.closed shouldBe true
        }

        test("an empty catalog is a good answer with no offers") {
            val r = PackUpdateChecker.check(true, ok(catalog()), InstalledPacks())
                .shouldBeInstanceOf<UpdateCheckResult.Done>()
            r.offers.size shouldBe 0
        }
    }

    context("failures") {
        test("an exception from the connection is a network failure, not a crash") {
            for (boom in listOf<() -> TransportResponse>(
                { throw IOException("down") },
                { throw IllegalStateException("odd") },
                { throw SecurityException("no") },
            )) {
                PackUpdateChecker.check(true, FakeTransport(boom), InstalledPacks()) shouldBe
                    UpdateCheckResult.Failed(UpdateFailure.NETWORK)
            }
        }

        test("a redirect is refused and never followed") {
            for (code in listOf(301, 302, 307, 308)) {
                val t = FakeTransport { TransportResponse.Status(code) }
                PackUpdateChecker.check(true, t, InstalledPacks()) shouldBe
                    UpdateCheckResult.Failed(UpdateFailure.REDIRECT_REFUSED, httpStatus = code)
                t.urls.size shouldBe 1
            }
        }

        test("any other status is reported with its code") {
            for (code in listOf(204, 400, 403, 404, 429, 500, 503)) {
                PackUpdateChecker.check(true, FakeTransport { TransportResponse.Status(code) }, InstalledPacks()) shouldBe
                    UpdateCheckResult.Failed(UpdateFailure.HTTP_STATUS, httpStatus = code)
            }
        }

        test("a body over the limit is refused without being parsed; exactly the limit is read") {
            val over = ByteArray(PackCatalogLimits.MAX_INDEX_BYTES + 1) { ' '.code.toByte() }
            val t = FakeTransport { TransportResponse.Ok(ByteArrayInputStream(over)) }
            PackUpdateChecker.check(true, t, InstalledPacks()) shouldBe
                UpdateCheckResult.Failed(UpdateFailure.TOO_LARGE)
            val exact = ByteArray(PackCatalogLimits.MAX_INDEX_BYTES) { ' '.code.toByte() }
            val r = PackUpdateChecker.check(
                true,
                FakeTransport { TransportResponse.Ok(ByteArrayInputStream(exact)) },
                InstalledPacks(),
            )
            r.shouldBeInstanceOf<UpdateCheckResult.Failed>().reason shouldBe UpdateFailure.BAD_CATALOG
        }

        test("a stream that ends in an error is a network failure and is still closed") {
            var closed = false
            val broken = object : InputStream() {
                override fun read(): Int = throw IOException("cut")
                override fun close() {
                    closed = true
                }
            }
            PackUpdateChecker.check(true, FakeTransport { TransportResponse.Ok(broken) }, InstalledPacks()) shouldBe
                UpdateCheckResult.Failed(UpdateFailure.NETWORK)
            closed shouldBe true
        }

        test("a catalog the reader refuses is reported with the reason and nothing is offered") {
            val bad = listOf(
                "not json" to CatalogError.NOT_JSON,
                catalog().replace("victorious-pack-catalog", "x") to CatalogError.WRONG_FORMAT,
                catalog(entry("com.victorious.a", "1.0.0").replace("$BASE/v1", "https://evil.example/v1")) to
                    CatalogError.BAD_DOWNLOAD,
            )
            for ((text, error) in bad) {
                val r = PackUpdateChecker.check(true, ok(text), InstalledPacks())
                r shouldBe UpdateCheckResult.Failed(UpdateFailure.BAD_CATALOG, catalogError = error)
            }
        }

        test("a catalog entry that reuses a built-in id makes the whole catalog refused") {
            val text = catalog(entry("com.victorious.themes_builtin", "1.0.0"))
            val r = PackUpdateChecker.check(
                true,
                ok(text),
                InstalledPacks(reservedIds = setOf("com.victorious.themes_builtin")),
            )
            r shouldBe UpdateCheckResult.Failed(UpdateFailure.BAD_CATALOG, catalogError = CatalogError.RESERVED_ID)
        }
    }

    context("the connection class") {
        test("it refuses any address except the fixed one, before opening anything") {
            val t = UrlConnectionTransport()
            for (url in listOf(
                "http://raw.githubusercontent.com/Victorious93/Victorious-Keyboard/main/packs/index.json",
                "https://example.com/packs/index.json",
                PackCatalogEndpoint.URL + "?x=1",
                PackCatalogEndpoint.URL + "/",
                "",
            )) {
                try {
                    t.get(url)
                    error("should have refused $url")
                } catch (_: IllegalArgumentException) {
                    // expected: refused before any connection
                }
            }
        }
    }

    context("pages") {
        test("every pack type opens a page in this repository's packs folder") {
            for (type in PackType.entries) {
                PackPages.urlFor(type).contains("/blob/main/packs/") shouldBe true
            }
        }
    }
})
