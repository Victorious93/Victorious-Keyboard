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

package dev.patrickgold.florisboard.app.apptheme

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.io.File
import org.florisboard.lib.snygg.SnyggStylesheet

/**
 * The neon themes have to parse as real stylesheets, stay the defaults, and leave the older
 * FlorisBoard themes selectable. Settings routes registered on the home screen must still be
 * wired in the navigation host.
 */
class BrandThemeTest : FunSpec({
    val assets = File("src/main/assets/ime/theme/org.florisboard.themes")

    test("neon stylesheets parse and use the brand colors") {
        val neon = (assets.resolve("stylesheets/victorious_neon.json")).readText()
        val light = (assets.resolve("stylesheets/victorious_neon_light.json")).readText()
        SnyggStylesheet.fromJson(neon).isSuccess shouldBe true
        SnyggStylesheet.fromJson(light).isSuccess shouldBe true
        neon shouldContain "\"--background\": \"#010619\""
        neon shouldContain "\"--primary\": \"#ffc107\""
        neon shouldContain "\"--secondary\": \"#ff4d8d\""
        neon shouldContain "rounded-corner(16dp, 16dp, 16dp, 16dp)"
        light shouldContain "\"--background\": \"#f4efe4\""
        light shouldContain "\"--primary\": \"#ffc107\""
    }

    test("old FlorisBoard themes stay registered beside the neon defaults") {
        val extension = assets.resolve("extension.json").readText()
        listOf(
            "floris_day",
            "floris_night",
            "floris_pure_night",
            "victorious_neon",
            "victorious_neon_light",
        ).forEach { id ->
            extension shouldContain "\"id\": \"$id\""
        }
        val prefs = File("src/main/kotlin/dev/patrickgold/florisboard/app/AppPrefs.kt").readText()
        prefs shouldContain "extCoreTheme(\"victorious_neon\")"
        prefs shouldContain "extCoreTheme(\"victorious_neon_light\")"
        prefs shouldContain "default = AppTheme.DARK"
    }

    test("every settings destination is still registered") {
        val routes = File("src/main/kotlin/dev/patrickgold/florisboard/app/Routes.kt").readText()
        val home = File("src/main/kotlin/dev/patrickgold/florisboard/app/settings/HomeScreen.kt").readText()
        listOf(
            "Home",
            "Localization",
            "SelectLocale",
            "LanguagePackManager",
            "SubtypeAdd",
            "SubtypeEdit",
            "Theme",
            "ThemeManager",
            "KeyboardColors",
            "Keyboard",
            "InputFeedback",
            "Smartbar",
            "Typing",
            "Dictionary",
            "UserDictionary",
            "DictionaryPacks",
            "Gestures",
            "Clipboard",
            "ClipboardManager",
            "ClipboardBackup",
            "Snippets",
            "AdvancedDeveloper",
            "Media",
            "Other",
            "PhysicalKeyboard",
            "Backup",
            "Restore",
            "About",
            "ProjectLicense",
            "ThirdPartyLicenses",
        ).forEach { name ->
            routes shouldContain "Settings.$name::class"
        }
        listOf(
            "Routes.Settings.Localization",
            "Routes.Settings.Theme",
            "Routes.Settings.KeyboardColors",
            "Routes.Settings.Keyboard",
            "Routes.Settings.Smartbar",
            "Routes.Settings.Typing",
            "Routes.Settings.Gestures",
            "Routes.Settings.Clipboard",
            "Routes.Settings.Media",
            "Routes.Ext.Home",
            "Routes.Settings.Other",
            "Routes.Settings.About",
        ).forEach { destination ->
            home shouldContain destination
        }
    }
})
