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

import java.net.HttpURLConnection
import java.net.URL

/**
 * The only class in the app that opens a network connection. It fetches one fixed HTTPS address
 * ([PackCatalogEndpoint.URL]) for the opt-in pack update check and nothing else.
 *
 * * Refuses any other address, so a bug elsewhere cannot point it at a different host.
 * * Never follows a redirect (a redirect would reach an address nobody approved).
 * * Short timeouts; no cache; no cookies; no credentials; only a generic user agent. The request carries nothing about
 *   the phone, the user, the installed packs or anything typed.
 * * The caller runs it off the main thread and reads at most [PackCatalogLimits.MAX_INDEX_BYTES] from the body.
 * * Logs nothing.
 */
class UrlConnectionTransport : CatalogTransport {
    override fun get(url: String): TransportResponse {
        require(url == PackCatalogEndpoint.URL) { "address not allowed" }
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.instanceFollowRedirects = false
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.useCaches = false
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", USER_AGENT)
            connection.setRequestProperty("Accept", "application/json")
            val code = connection.responseCode
            if (code != 200) {
                connection.disconnect()
                return TransportResponse.Status(code)
            }
            return TransportResponse.Ok(DisconnectingStream(connection))
        } catch (e: Exception) {
            connection.disconnect()
            throw e
        }
    }

    /** Closing the body also drops the connection. */
    private class DisconnectingStream(private val connection: HttpURLConnection) : java.io.FilterInputStream(
        connection.inputStream,
    ) {
        override fun close() {
            try {
                super.close()
            } finally {
                connection.disconnect()
            }
        }
    }

    private companion object {
        const val TIMEOUT_MS = 8_000
        const val USER_AGENT = "VictoriousKeyboard-pack-check"
    }
}
