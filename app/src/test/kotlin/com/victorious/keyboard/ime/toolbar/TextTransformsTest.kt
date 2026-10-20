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

package com.victorious.keyboard.ime.toolbar

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlin.random.Random

class TextTransformsTest : FunSpec({
    test("case, indent, and comment transforms") {
        TextTransforms.apply(TextTransform.CAMEL, "hello world").text() shouldBe "helloWorld"
        TextTransforms.apply(TextTransform.SNAKE, "helloWorld").text() shouldBe "hello_world"
        TextTransforms.apply(TextTransform.KEBAB, "Hello World").text() shouldBe "hello-world"
        TextTransforms.apply(TextTransform.PASCAL, "hello_world").text() shouldBe "HelloWorld"
        TextTransforms.apply(TextTransform.INDENT, "a\nb").text() shouldBe "  a\n  b"
        TextTransforms.apply(TextTransform.OUTDENT, "  a").text() shouldBe "a"
        TextTransforms.apply(TextTransform.TOGGLE_COMMENT, "val x = 1").text() shouldBe "// val x = 1"
        TextTransforms.apply(TextTransform.TOGGLE_COMMENT, "// val x = 1").text() shouldBe "val x = 1"
        TextTransforms.apply(TextTransform.TOGGLE_COMMENT, "pkg update").text() shouldBe "# pkg update"
    }

    test("encodings fail closed and json pretty-prints") {
        TextTransforms.apply(TextTransform.BASE64_ENCODE, "hi").text() shouldBe "aGk="
        TextTransforms.apply(TextTransform.BASE64_DECODE, "aGk=").text() shouldBe "hi"
        TextTransforms.apply(TextTransform.BASE64_DECODE, "!!!!").shouldBeInstanceOf<TransformResult.Failed>()
        TextTransforms.apply(TextTransform.HEX_ENCODE, "A").text() shouldBe "41"
        TextTransforms.apply(TextTransform.HEX_DECODE, "41").text() shouldBe "A"
        TextTransforms.apply(TextTransform.HEX_DECODE, "4").shouldBeInstanceOf<TransformResult.Failed>()
        TextTransforms.apply(TextTransform.URL_ENCODE, "a b").text() shouldContain "a"
        TextTransforms.apply(TextTransform.URL_ENCODE, "a b&c=\u00fc").text() shouldBe "a+b%26c%3D%C3%BC"
        TextTransforms.apply(TextTransform.URL_DECODE, "a+b%26c%3D%C3%BC").text() shouldBe "a b&c=\u00fc"
        TextTransforms.apply(TextTransform.REGEX_ESCAPE, "a.b").text() shouldBe "a\\.b"
        val pretty = TextTransforms.apply(TextTransform.JSON_PRETTY, """{"b":1}""")
        pretty.shouldBeInstanceOf<TransformResult.Ok>()
        pretty.text shouldContain "\n"
        TextTransforms.apply(TextTransform.JSON_PRETTY, "{").shouldBeInstanceOf<TransformResult.Failed>()
    }

    test("hashes, counts, fences, and prompts do not rescan inserted values") {
        TextTransforms.apply(TextTransform.SHA256, "hi").text() shouldBe
            "8f434346648f6b96df89dda901c5176b10a6d83961dd3c1ac88b59b2dc327aa4"
        TextTransforms.apply(TextTransform.TIMESTAMP, "ignored", nowMillis = 42).text() shouldBe "42"
        TextTransforms.apply(TextTransform.RANDOM_HEX, "", random = Random(1)).text().length shouldBe 16
        TextTransforms.counts("one two").shouldBe(TextCounts(7, 2, 2))
        TextTransforms.fence("body", "kotlin") shouldBe "```kotlin\nbody\n```"
        val filled = TextTransforms.fillPrompt("Explain {{selection}}", mapOf("selection" to "use {{selection}}"))
        filled shouldBe "Explain use {{selection}}"
    }

    test("shell history stays off for incognito, passwords, vault text, and the editor") {
        val open = ShellHistoryPolicy.shouldRecord(true, false, false, false, false)
        open shouldBe true
        ShellHistoryPolicy.shouldRecord(false, false, false, false, false) shouldBe false
        ShellHistoryPolicy.shouldRecord(true, true, false, false, false) shouldBe false
        ShellHistoryPolicy.shouldRecord(true, false, true, false, false) shouldBe false
        ShellHistoryPolicy.shouldRecord(true, false, false, true, false) shouldBe false
        ShellHistoryPolicy.shouldRecord(true, false, false, false, true) shouldBe false
        ShellHistoryPolicy.commandFrom("ls\npwd") shouldBe "pwd"
        ShellHistoryPolicy.commandFrom("   ") shouldBe null
        ShellHistoryPolicy.remember(listOf("pwd", "ls"), "pwd") shouldBe listOf("pwd", "ls")
    }

    test("toolbar config hides, reorders, and rejects a broken file") {
        val hidden = ToolbarConfig().hiding(ToolbarActionId.CTRL.name)
        ToolbarCatalog.visible(ToolbarPage.TERMINAL, hidden.hiddenSet(), emptyList()).any { it.id == ToolbarActionId.CTRL } shouldBe false
        val moved = ToolbarConfig().movingEarlier(ToolbarPage.CODE, ToolbarActionId.SNAKE.name)
        moved.pageOrder(ToolbarPage.CODE).first() shouldBe ToolbarActionId.SNAKE.name
        val encoded = ToolbarConfigCodec.encode(hidden.copy(shellHistoryEnabled = true, bracketPairing = true))
        ToolbarConfigCodec.decode(encoded).shellHistoryEnabled shouldBe true
        ToolbarConfigCodec.decode("{").hidden shouldBe emptyList()
        ToolbarConfigCodec.decode(null).bracketPairing shouldBe false
        SavedCommandRules.normalize("  ", "ls", "pkg") shouldBe null
        SavedCommandRules.normalize("ls", "ls -la", "pkg")?.text shouldBe "ls -la"
    }
})

private fun TransformResult.text(): String = (this as TransformResult.Ok).text
