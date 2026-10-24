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

import com.victorious.keyboard.app.ProjectUrls
import java.io.IOException
import java.io.InputStream

/**
 * The opt-in pack update check (roadmap item 10). When the user has switched it on and taps "Check now", the keyboard
 * downloads `packs/index.json` from this project's repository and compares it with what is installed. That is the whole
 * feature: no pack file is downloaded, nothing is installed, nothing runs in the background, and nothing about the
 * phone, the user or their text is sent. The catalog is untrusted input and goes through [PackCatalogReader].
 *
 * Pure Kotlin around a [CatalogTransport]; the one class that opens a connection is `UrlConnectionTransport`.
 * Nothing here logs.
 */
object PackCatalogEndpoint {
    const val HOST = "raw.githubusercontent.com"

    /** The only address the keyboard ever contacts for this feature. Redirects are refused, so no other is reached. */
    const val URL = "https://$HOST/Victorious93/Victorious-Keyboard/main/packs/index.json"

    /** The page a person can read in a browser; shown next to the switch. */
    const val READABLE_PAGE = ProjectUrls.PACKS
}

/** A catalog version, three numbers. Anything else cannot be compared and is reported as such. */
data class PackVersion(val major: Int, val minor: Int, val patch: Int) : Comparable<PackVersion> {
    override fun compareTo(other: PackVersion): Int =
        compareValuesBy(this, other, PackVersion::major, PackVersion::minor, PackVersion::patch)

    companion object {
        private val PATTERN = Regex("""(\d{1,4})\.(\d{1,4})\.(\d{1,4})""")

        fun parse(text: String): PackVersion? {
            val m = PATTERN.matchEntire(text) ?: return null
            return PackVersion(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt())
        }
    }
}

/** What is on this phone, as far as the keyboard can tell without reading any pack file. */
data class InstalledPacks(
    /** Pack id to its version, for extensions the user imported. Built-ins are not listed; they are reserved. */
    val versionsById: Map<String, String> = emptyMap(),
    /** Android packages of installed command-provider apps. */
    val packages: Set<String> = emptySet(),
    /** Ids that ship in the app. A catalog entry with one of these is dropped from the result. */
    val reservedIds: Set<String> = emptySet(),
)

enum class OfferStatus {
    /** The catalog lists a newer version than the installed one. */
    UPDATE,

    /** Not installed, as far as the keyboard can tell. */
    NEW,

    /** Installed, and the versions cannot be compared (a provider app, or a version that is not three numbers). */
    CANNOT_COMPARE,

    /** Installed at the same or a newer version. */
    CURRENT,
}

data class PackOffer(val entry: CatalogEntry, val status: OfferStatus, val installedVersion: String?)

enum class UpdateFailure { DISABLED, NETWORK, REDIRECT_REFUSED, HTTP_STATUS, TOO_LARGE, BAD_CATALOG }

sealed interface UpdateCheckResult {
    data class Done(val updated: String, val offers: List<PackOffer>) : UpdateCheckResult
    data class Failed(
        val reason: UpdateFailure,
        val httpStatus: Int = 0,
        val catalogError: CatalogError? = null,
    ) : UpdateCheckResult
}

/** What the connection code returns. It never follows a redirect and never hands over a body for a non-200 reply. */
sealed interface TransportResponse {
    /** A 200 reply. The checker closes [stream]. */
    class Ok(val stream: InputStream) : TransportResponse
    data class Status(val code: Int) : TransportResponse
}

fun interface CatalogTransport {
    /** May throw [IOException]; any other failure is also treated as a network failure. */
    fun get(url: String): TransportResponse
}

object BoundedRead {
    /** Reads at most [max] bytes. Returns null when the stream holds more, without keeping the excess. */
    fun read(input: InputStream, max: Int): ByteArray? {
        val out = java.io.ByteArrayOutputStream(minOf(max, 16 * 1024))
        val buffer = ByteArray(8 * 1024)
        var total = 0
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            total += n
            if (total > max) return null
            out.write(buffer, 0, n)
        }
        return out.toByteArray()
    }
}

object PackUpdateChecker {
    /**
     * @param enabled the user's switch. When false nothing is contacted.
     */
    fun check(enabled: Boolean, transport: CatalogTransport, installed: InstalledPacks): UpdateCheckResult {
        if (!enabled) return UpdateCheckResult.Failed(UpdateFailure.DISABLED)
        val response = try {
            transport.get(PackCatalogEndpoint.URL)
        } catch (_: Exception) {
            return UpdateCheckResult.Failed(UpdateFailure.NETWORK)
        }
        val bytes = when (response) {
            is TransportResponse.Status -> return when (response.code) {
                in 300..399 -> UpdateCheckResult.Failed(UpdateFailure.REDIRECT_REFUSED, response.code)
                else -> UpdateCheckResult.Failed(UpdateFailure.HTTP_STATUS, response.code)
            }
            is TransportResponse.Ok -> try {
                response.stream.use { BoundedRead.read(it, PackCatalogLimits.MAX_INDEX_BYTES) }
            } catch (_: Exception) {
                return UpdateCheckResult.Failed(UpdateFailure.NETWORK)
            } ?: return UpdateCheckResult.Failed(UpdateFailure.TOO_LARGE)
        }
        return when (val parsed = PackCatalogReader.read(bytes, installed.reservedIds)) {
            is CatalogResult.Failed -> UpdateCheckResult.Failed(UpdateFailure.BAD_CATALOG, catalogError = parsed.error)
            is CatalogResult.Ok -> UpdateCheckResult.Done(parsed.catalog.updated, offers(parsed.catalog, installed))
        }
    }

    fun offers(catalog: PackCatalog, installed: InstalledPacks): List<PackOffer> {
        val offers = catalog.entries.map { entry ->
            if (entry.type == PackType.COMMAND_PROVIDER) {
                val here = entry.packageName in installed.packages
                PackOffer(entry, if (here) OfferStatus.CANNOT_COMPARE else OfferStatus.NEW, null)
            } else {
                val have = installed.versionsById[entry.id]
                val offered = PackVersion.parse(entry.version)
                val current = have?.let { PackVersion.parse(it) }
                val status = when {
                    have == null -> OfferStatus.NEW
                    current == null || offered == null -> OfferStatus.CANNOT_COMPARE
                    offered > current -> OfferStatus.UPDATE
                    else -> OfferStatus.CURRENT
                }
                PackOffer(entry, status, have)
            }
        }
        return offers.sortedWith(
            compareBy<PackOffer> { it.status.ordinal }.thenBy { it.entry.title.lowercase() }.thenBy { it.entry.id },
        )
    }
}

/** The page "Open page" leads to for an offer: the project's page for that kind of pack. Never the download itself. */
object PackPages {
    fun urlFor(type: PackType): String = when (type) {
        PackType.DICTIONARY_PACK -> ProjectUrls.DICTIONARY_PACKS
        PackType.LANGUAGE_PACK -> ProjectUrls.LANGUAGE_PACKS
        PackType.THEME -> ProjectUrls.THEMES
        PackType.LAYOUT -> ProjectUrls.LAYOUTS
        PackType.EXTENSION -> ProjectUrls.EXTENSIONS
        PackType.COMMAND_PROVIDER -> ProjectUrls.COMMAND_PROVIDERS
    }
}
