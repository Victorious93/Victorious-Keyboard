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

package com.victorious.keyboard.app.manual

import com.victorious.keyboard.app.ProjectUrls
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import java.io.File

class ManualDocumentTest : FunSpec({
    val docs = File("../docs/MANUAL.md")
    val asset = File("src/main/assets/manual/MANUAL.md")

    test("the bundled asset is the docs manual") {
        asset.isFile shouldBe true
        asset.readText() shouldBe docs.readText()
    }

    test("the manual has every required section and loads as blocks") {
        val text = asset.readText()
        val headings = ManualDocument.headingTexts(text)
        ManualDocument.requiredHeadings.forEach { headings shouldContain it }
        val blocks = ManualDocument.parse(text)
        blocks.filterIsInstance<ManualDocument.Block.Heading>().size shouldBe headings.size
        text shouldContain "only for the pack update check"
        text shouldNotContain "android.permission.INTERNET"
    }

    test("picture slots are TODO placeholders and the header is real art") {
        val images = ManualDocument.parse(docs.readText()).filterIsInstance<ManualDocument.Block.Image>()
        images.isNotEmpty() shouldBe true
        images.forEach { image ->
            val file = File("../docs/${image.path}")
            file.isFile shouldBe true
            if (ManualDocument.isHeaderArt(image.path)) {
                file.readText() shouldContain "linearGradient"
                file.readText() shouldContain "#FF2BD6"
                file.readText() shouldContain "#22F0FF"
            } else {
                file.readText() shouldContain "TODO"
                file.extension shouldNotContain "png"
            }
        }
    }

    test("inline markers and a fence that never closes") {
        ManualDocument.inlines("Hello **bold** and `code`") shouldBe listOf(
            ManualDocument.Span("Hello "),
            ManualDocument.Span("bold", bold = true),
            ManualDocument.Span(" and "),
            ManualDocument.Span("code", code = true),
        )
        ManualDocument.inlines("a **unclosed")[0].text shouldBe "a **unclosed"
        ManualDocument.parse("```\nline").single() shouldBe ManualDocument.Block.Code("line")
        ManualDocument.parse("```\nline\n```").single() shouldBe ManualDocument.Block.Code("line")
        ManualDocument.parse("") shouldBe emptyList()
    }

    test("Help reads the asset and the old wiki links open this screen") {
        val screen = File("src/main/kotlin/com/victorious/keyboard/app/settings/manual/ManualScreen.kt").readText()
        screen shouldContain "ManualDocument.ASSET_PATH"
        screen shouldContain "loadTextAsset"
        screen shouldNotContain "launchUrl"
        val home = File("src/main/kotlin/com/victorious/keyboard/app/settings/HomeScreen.kt").readText()
        home shouldContain "Routes.Settings.Manual"
        val theme = File("src/main/kotlin/com/victorious/keyboard/app/settings/theme/ThemeEditorScreen.kt").readText()
        theme shouldContain "Routes.Settings.Manual"
        val typing = File("src/main/kotlin/com/victorious/keyboard/app/settings/typing/TypingScreen.kt").readText()
        typing shouldContain "Routes.Settings.Manual"
        ProjectUrls.SPELL_CHECKER_DOCS shouldBe ProjectUrls.MANUAL
        ProjectUrls.THEME_EDITOR_DOCS shouldBe ProjectUrls.MANUAL
        ProjectUrls.MANUAL shouldBe "ui://florisboard/settings/manual"
    }
})
