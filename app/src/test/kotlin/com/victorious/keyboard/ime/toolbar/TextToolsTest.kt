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
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.types.shouldBeInstanceOf

class TextToolsTest : FunSpec({
    test("case, lines, rot13, and escapes") {
        TextTransforms.apply(TextTransform.UPPER, "caf\u00e9").text() shouldBe "CAF\u00c9"
        TextTransforms.apply(TextTransform.LOWER, "CAF\u00c9").text() shouldBe "caf\u00e9"
        TextTransforms.apply(TextTransform.TITLE, "hello world").text() shouldBe "Hello World"
        TextTransforms.apply(TextTransform.TRIM_LINES, " a \n b ").text() shouldBe "a\nb"
        TextTransforms.apply(TextTransform.SORT_LINES, "b\na").text() shouldBe "a\nb"
        TextTransforms.apply(TextTransform.DEDUPE_LINES, "a\na\nb").text() shouldBe "a\nb"
        TextTransforms.apply(TextTransform.ROT13, "Hello").text() shouldBe "Uryyb"
        TextTransforms.apply(TextTransform.ROT13, "Uryyb").text() shouldBe "Hello"
        TextTransforms.apply(TextTransform.SHELL_ESCAPE, "a'b").text() shouldBe "'a'\\''b'"
        TextTransforms.apply(TextTransform.JSON_ESCAPE, "a\"b\n").text() shouldBe "\"a\\\"b\\n\""
        TextTransforms.apply(TextTransform.MD5, "").text() shouldBe "d41d8cd98f00b204e9800998ecf8427e"
    }

    test("json minify, extracts, jwt, and unix time fail closed") {
        TextTransforms.apply(TextTransform.JSON_MINIFY, "{\n  \"b\": 1\n}").text() shouldBe """{"b":1}"""
        TextTransforms.apply(TextTransform.JSON_MINIFY, "{").shouldBeInstanceOf<TransformResult.Failed>()
        val pulled = TextTransforms.apply(
            TextTransform.EXTRACT_URL,
            "see https://example.com/a. and mail",
        ).text()
        pulled shouldBe "https://example.com/a"
        TextTransforms.apply(TextTransform.EXTRACT_EMAIL, "ask a@b.co today").text() shouldBe "a@b.co"
        TextTransforms.apply(TextTransform.EXTRACT_IP, "at 1.2.3.4 and 999.1.1.1").text() shouldBe "1.2.3.4"
        TextTransforms.apply(TextTransform.EXTRACT_URL, "none here").shouldBeInstanceOf<TransformResult.Failed>()
        val jwt = TextTransforms.apply(
            TextTransform.JWT_DECODE,
            "eyJhbGciOiJub25lIn0.eyJzdWIiOiJhIn0.sig",
        ).text()
        jwt shouldContain "alg"
        jwt shouldContain "sub"
        jwt shouldNotContain "sig"
        TextTransforms.apply(TextTransform.JWT_DECODE, "not-a-jwt").shouldBeInstanceOf<TransformResult.Failed>()
        TextTransforms.apply(TextTransform.UNIX_TO_DATE, "0").text() shouldBe "1970-01-01T00:00:00Z"
        TextTransforms.apply(TextTransform.DATE_TO_UNIX, "1970-01-01T00:00:00Z").text() shouldBe "0"
        TextTransforms.apply(TextTransform.DATE_TO_UNIX, "not a date").shouldBeInstanceOf<TransformResult.Failed>()
        TextTransforms.apply(TextTransform.UNIX_TO_DATE, "soon").shouldBeInstanceOf<TransformResult.Failed>()
    }

    test("preview uses the selection, else the clipboard, and waits to write") {
        val selected = TextTools.open("hi", "clip", fromVault = false)
        selected!!.source shouldBe TextToolSource.SELECTION
        selected.output shouldBe "aGk="
        val fromClip = TextTools.open("", "hi", fromVault = false)
        fromClip!!.source shouldBe TextToolSource.CLIPBOARD
        TextTools.open("", null, fromVault = false) shouldBe null
        TextTools.open("   ", "   ", fromVault = false)!!.source shouldBe TextToolSource.SELECTION
        val upper = TextTools.withOp(selected, TextTransform.UPPER)
        upper.output shouldBe "HI"
        upper.input shouldBe "hi"
        TextTools.write(upper, TextToolPlace.REPLACE) shouldBe TextToolWrite.IntoField("HI", replaceSelection = true)
        TextTools.write(upper, TextToolPlace.INSERT) shouldBe TextToolWrite.IntoField("HI", replaceSelection = false)
        TextTools.write(upper, TextToolPlace.COPY) shouldBe TextToolWrite.OntoClipboard("HI")
        TextTools.write(fromClip, TextToolPlace.REPLACE) shouldBe TextToolWrite.OntoClipboard("aGk=")
        val vault = TextTools.open("hi", "clip", fromVault = true)!!
        vault.fromVault shouldBe true
        TextTools.write(vault, TextToolPlace.COPY) shouldBe TextToolWrite.Blocked
        TextTools.write(vault, TextToolPlace.REPLACE) shouldBe TextToolWrite.IntoField("aGk=", replaceSelection = true)
        val broken = TextTools.withOp(selected, TextTransform.BASE64_DECODE)
        broken.ok shouldBe false
        TextTools.write(broken, TextToolPlace.INSERT) shouldBe TextToolWrite.Blocked
        selected.toString() shouldNotContain "hi"
        selected.toString() shouldNotContain "aGk="
        ToolsCatalog.actions(ToolsTab.CODE).first() shouldBe ToolbarActionId.TEXT_TOOLS
    }
})

private fun TransformResult.text(): String = (this as TransformResult.Ok).text
