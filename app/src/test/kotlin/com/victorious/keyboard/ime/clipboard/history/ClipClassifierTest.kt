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

package com.victorious.keyboard.ime.clipboard.history

import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.shouldBe

class ClipClassifierTest : FunSpec({
    context("classification") {
        withData(
            nameFn = { (text, expected) -> "${expected.name}: ${text.take(40).replace("\n", "⏎")}" },
            "https://example.org/a?b=1" to ClipCategory.URL,
            "https://a.example\nhttps://b.example" to ClipCategory.URL,
            """{"a": 1, "b": [true, null]}""" to ClipCategory.JSON,
            "[1, 2, 3]" to ClipCategory.JSON,
            "git status" to ClipCategory.SHELL,
            "adb logcat -d | grep Victorious" to ClipCategory.SHELL,
            "\$ sudo apt update\n\$ sudo apt upgrade -y" to ClipCategory.SHELL,
            "#!/bin/bash\necho hi" to ClipCategory.SHELL,
            "# Title\n\nSome **bold** text and a [link](https://x.y)" to ClipCategory.MARKDOWN,
            "fun main() {\n    val x = 1\n    println(x)\n}" to ClipCategory.CODE,
            "def add(a, b):\n    return a + b" to ClipCategory.CODE,
            "You are a senior Android engineer. Review the following code." to ClipCategory.AI_PROMPT,
            "Just a normal sentence about lunch." to ClipCategory.GENERAL,
            "" to ClipCategory.GENERAL,
        ) { (text, expected) ->
            ClipClassifier.classify(text) shouldBe expected
        }
    }

    test("invalid JSON that merely looks like JSON is not JSON") {
        ClipClassifier.classify("{not json}") shouldBe ClipCategory.GENERAL
    }

    test("a bare number is not classified as JSON") {
        ClipClassifier.classify("12345") shouldBe ClipCategory.GENERAL
    }

    test("unknown category names from newer backups fall back to GENERAL") {
        ClipCategory.parse("FUTURE_CATEGORY") shouldBe ClipCategory.GENERAL
        ClipCategory.parse(null) shouldBe ClipCategory.GENERAL
        ClipCategory.parse("URL") shouldBe ClipCategory.URL
    }
})
