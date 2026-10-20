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

package com.victorious.keyboard.lib.ext

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Built-in extension manifests use Victorious ids, and the FlorisBoard ids still resolve.
 */
class ExtensionRebrandTest : FunSpec({
    val json = Json { ignoreUnknownKeys = true }
    val packs = listOf(
        "src/main/assets/ime/keyboard/org.florisboard.composers/extension.json" to ExtensionIds.COMPOSERS,
        "src/main/assets/ime/keyboard/org.florisboard.currencysets/extension.json" to ExtensionIds.CURRENCY_SETS,
        "src/main/assets/ime/keyboard/org.florisboard.layouts/extension.json" to ExtensionIds.LAYOUTS,
        "src/main/assets/ime/keyboard/org.florisboard.localization/extension.json" to ExtensionIds.LOCALIZATION,
        "src/main/assets/ime/languagepack/org.florisboard.hanshapebasedbasicpack/extension.json" to ExtensionIds.HAN_SHAPE,
        "src/main/assets/ime/languagepack/org.florisboard.languagepack/extension.json" to ExtensionIds.LANGUAGE_PACK,
        "src/main/assets/ime/theme/org.florisboard.themes/extension.json" to ExtensionIds.THEMES,
        "src/main/assets/ime/theme/org.florisboard.themes.my/extension.json" to ExtensionIds.THEMES_MATERIAL_YOU,
    )

    test("each built-in manifest is Victorious and keeps its FlorisBoard id as an alias") {
        packs.forEach { (path, id) ->
            val meta = json.parseToJsonElement(File(path).readText()).jsonObject.getValue("meta").jsonObject
            val decoded = json.decodeFromString(ExtensionMeta.serializer(), meta.toString())
            decoded.id shouldBe id
            decoded.aliases shouldBe listOf(ExtensionIds.legacyOf(id)!!)
            decoded.title.startsWith("Victorious") shouldBe true
            decoded.maintainers.map { it.name } shouldBe listOf("Victorious93")
            decoded.homepage shouldBe ExtensionIds.REPO
            decoded.issueTracker shouldBe ExtensionIds.ISSUES
            decoded.license shouldBe "apache-2.0"
            ExtensionIds.matches(decoded.id, decoded.aliases, ExtensionIds.legacyOf(id)!!) shouldBe true
            ExtensionIds.matches(decoded.id, decoded.aliases, id) shouldBe true
        }
    }

    test("a saved FlorisBoard theme id and the new id index the same theme") {
        val themes = mutableMapOf<ExtensionComponentName, String>()
        ExtensionIds.indexComponent(
            themes,
            ExtensionIds.THEMES,
            listOf(ExtensionIds.legacyOf(ExtensionIds.THEMES)!!),
            "victorious_black",
            "Victorious Black",
        )
        themes[ExtensionComponentName("org.florisboard.themes", "victorious_black")] shouldBe "Victorious Black"
        themes[ExtensionComponentName(ExtensionIds.THEMES, "victorious_black")] shouldBe "Victorious Black"
        ExtensionIds.same("org.florisboard.themes", ExtensionIds.THEMES) shouldBe true
        ExtensionIds.isLegacy("org.florisboard.themes") shouldBe true
        ExtensionIds.isLegacy(ExtensionIds.THEMES) shouldBe false
    }

    test("layout and subtype references that still use the old id keep resolving") {
        val layouts = mutableMapOf<ExtensionComponentName, String>()
        ExtensionIds.indexComponent(
            layouts,
            ExtensionIds.LAYOUTS,
            listOf("org.florisboard.layouts"),
            "qwerty",
            "qwerty",
        )
        val stored = ExtensionComponentName.from("org.florisboard.layouts:qwerty")
        val current = ExtensionComponentName.from("${ExtensionIds.LAYOUTS}:qwerty")
        layouts[stored] shouldBe "qwerty"
        layouts[current] shouldBe "qwerty"
        val source = File("src/main/assets/ime/keyboard/org.florisboard.localization/extension.json").readText()
        source shouldContain "org.florisboard.layouts:qwerty"
        source shouldContain ExtensionIds.LAYOUTS
    }

    test("the Chinese pack sends readers to Don's repository") {
        val text = File(
            "src/main/assets/ime/languagepack/org.florisboard.hanshapebasedbasicpack/extension.json",
        ).readText()
        text shouldContain "https://github.com/Victorious93/Victorious-Keyboard"
        text shouldContain "更多输入法请到 Victorious Keyboard 仓库下载"
        text shouldContain "更多輸入法請到 Victorious Keyboard 倉庫下載"
    }

    test("the manual tells users about dotted words and the new extension names") {
        val manual = File("../docs/MANUAL.md").readText()
        File("src/main/assets/manual/MANUAL.md").readText() shouldBe manual
        manual shouldContain "word.word"
        manual shouldContain "1.2.3"
        manual shouldContain "com.victorious.keyboard.themes"
        manual shouldContain "org.florisboard."
        manual shouldContain "https://github.com/Victorious93/Victorious-Keyboard"
        manual shouldContain "FlorisBoard"
    }

    test("the licence and about screens still credit FlorisBoard") {
        val about = File("src/main/res/values/strings.xml").readText()
        about shouldContain "Based on FlorisBoard"
        about shouldContain "Apache 2.0"
        File("src/main/kotlin/com/victorious/keyboard/app/settings/about/AboutScreen.kt").readText()
            .shouldContain("Routes.Settings.ProjectLicense")
    }
})
