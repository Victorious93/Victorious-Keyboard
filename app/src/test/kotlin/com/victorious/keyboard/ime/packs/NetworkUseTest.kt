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
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import java.io.File

/**
 * The app has exactly one network use: the opt-in pack update check. These tests read the source and the manifest so
 * that a second use, a background trigger, or cleartext traffic cannot be added without a test failing and a person
 * deciding. They check source text, not behaviour; nothing here has been run on a device.
 */
class NetworkUseTest : FunSpec({
    val manifest = File("src/main/AndroidManifest.xml").readText()
    val mainSources = File("src/main/kotlin").walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
    val transport = "UrlConnectionTransport.kt"

    test("the manifest declares INTERNET once, and blocks cleartext traffic") {
        Regex("android.permission.INTERNET").findAll(manifest).count() shouldBe 1
        manifest shouldContain "android:usesCleartextTraffic=\"false\""
        manifest shouldContain "android:networkSecurityConfig=\"@xml/network_security_config\""
        val config = File("src/main/res/xml/network_security_config.xml").readText()
        config shouldContain "cleartextTrafficPermitted=\"false\""
        config shouldNotContain "cleartextTrafficPermitted=\"true\""
        config shouldNotContain "<domain-config"
        config shouldNotContain "trust-anchors"
    }

    test("no permission that would let the app watch or wake on the network is requested") {
        for (p in listOf("ACCESS_NETWORK_STATE", "CHANGE_NETWORK_STATE", "RECEIVE_BOOT_COMPLETED", "FOREGROUND_SERVICE", "WAKE_LOCK", "SCHEDULE_EXACT_ALARM", "REQUEST_INSTALL_PACKAGES")) {
            withClue(p) { manifest shouldNotContain "android.permission.$p" }
        }
    }

    test("only UrlConnectionTransport opens a connection, and no networking library is on the classpath") {
        val markers = listOf(
            "HttpURLConnection", "HttpsURLConnection", "openConnection", "openStream", "java.net.Socket", "SSLSocket",
            "okhttp", "HttpClient", "ktor", "Retrofit", "DownloadManager", "URLConnection",
        )
        for (file in mainSources) {
            val text = file.readText()
            for (m in markers) {
                if (file.name == transport) continue
                withClue("${file.path} uses $m") { text.contains(m) shouldBe false }
            }
        }
        val gradle = File("build.gradle.kts").readText() + File("../gradle/libs.versions.toml").readText()
        for (lib in listOf("okhttp", "retrofit", "ktor", "volley", "cronet", "workmanager")) {
            withClue(lib) { gradle.lowercase().contains(lib) shouldBe false }
        }
    }

    test("the connection class talks to the fixed address only, never follows redirects, and sends no identifiers") {
        val text = mainSources.first { it.name == transport }.readText()
        text shouldContain "require(url == PackCatalogEndpoint.URL)"
        text shouldContain "instanceFollowRedirects = false"
        text shouldContain "useCaches = false"
        text shouldContain "connectTimeout"
        text shouldContain "readTimeout"
        text shouldNotContain "setDoOutput"
        text shouldNotContain "outputStream"
        text shouldNotContain "CookieHandler"
        text shouldNotContain "Authorization"
        text shouldNotContain "Build."
        text shouldNotContain "Settings.Secure"
        text shouldNotContain "Log."
        val headers = Regex("setRequestProperty\\(\"([^\"]+)\"").findAll(text).map { it.groupValues[1] }.toSet()
        headers shouldBe setOf("User-Agent", "Accept")
        // GET only, and the user agent carries no version, device or user data.
        text shouldContain "requestMethod = \"GET\""
        text shouldContain "USER_AGENT = \"VictoriousKeyboard-pack-check\""
    }

    test("only the update screen starts a check, only from a button, and only while the switch is on") {
        val users = mainSources.filter { f ->
            val t = f.readText()
            t.contains("UrlConnectionTransport()") || t.contains("PackUpdateChecker.check(")
        }.map { it.name }.toSet()
        users shouldBe setOf("PackUpdatesScreen.kt")
        val screen = mainSources.first { it.name == "PackUpdatesScreen.kt" }.readText()
        screen shouldContain "enabledIf = { prefs.packs.updateCheckEnabled isEqualTo true }"
        screen shouldContain "!prefs.packs.updateCheckEnabled.get()"
        screen shouldContain "enabled = prefs.packs.updateCheckEnabled.get()"
        screen.split("PackUpdateChecker.check(").size shouldBe 2
        // Not a LaunchedEffect, a timer, or anything that runs when the screen opens.
        screen shouldNotContain "LaunchedEffect"
        screen shouldNotContain "DisposableEffect"
        screen shouldNotContain "SideEffect"
    }

    test("nothing schedules or repeats work in the background") {
        val markers = listOf("WorkManager", "AlarmManager", "JobScheduler", "PeriodicWorkRequest", "ForegroundService", "BOOT_COMPLETED")
        for (file in mainSources) {
            val text = file.readText()
            for (m in markers) withClue("${file.path} uses $m") { text.contains(m) shouldBe false }
        }
        manifest shouldNotContain "BOOT_COMPLETED"
        manifest shouldNotContain "android.net.conn.CONNECTIVITY_CHANGE"
    }

    test("the update check is off by default") {
        val prefs = mainSources.first { it.name == "AppPrefs.kt" }.readText()
        val block = prefs.substringAfter("key = \"packs__update_check_enabled\"").substringBefore(")")
        block.replace(Regex("\\s+"), " ") shouldContain "default = false"
    }

    test("the request and the reply are never logged") {
        for (name in listOf("UrlConnectionTransport.kt", "PackUpdateCheck.kt", "PackCatalog.kt", "PackUpdatesScreen.kt")) {
            val text = mainSources.first { it.name == name }.readText()
            for (m in listOf("Log.", "Timber", "println", "printStackTrace", "Logger")) {
                withClue("$name uses $m") { text.contains(m) shouldBe false }
            }
        }
    }
})
