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

package dev.patrickgold.florisboard.signing

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import java.io.File
import java.security.KeyStore
import java.security.MessageDigest

class DebugKeystoreTest : FunSpec({
    val fingerprint = "47:1E:AD:6B:3F:96:7E:F3:C8:63:EF:49:08:74:8B:3B:E3:63:F9:1D:75:0B:FD:B1:E9:28:05:BC:F1:6B:AF:4F"

    test("committed debug keystore certificate matches the published fingerprint") {
        val keystore = repoFile("signing/debug.keystore")
        val store = KeyStore.getInstance("PKCS12")
        keystore.inputStream().use { store.load(it, "android".toCharArray()) }
        val cert = store.getCertificate("victorious-debug")
        sha256(cert.encoded) shouldBe fingerprint
    }

    test("only the debug signing config points at the committed keystore") {
        val gradle = repoFile("app/build.gradle.kts").readText()
        gradle shouldContain "signing/debug.keystore"
        gradle shouldContain "victorious-debug"
        val release = gradle.substringAfter("named(\"release\")").substringBefore("create(\"benchmark\")")
        release shouldNotContain "debug.keystore"
        release shouldNotContain "storeFile"
        val benchmark = gradle.substringAfter("create(\"benchmark\")")
        benchmark shouldContain "signingConfigs.getByName(\"debug\")"
    }
})

private fun repoFile(relative: String): File {
    return generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
        .map { File(it, relative) }
        .first { it.isFile }
}

private fun sha256(encoded: ByteArray): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(encoded)
    return digest.joinToString(":") { "%02X".format(it) }
}
