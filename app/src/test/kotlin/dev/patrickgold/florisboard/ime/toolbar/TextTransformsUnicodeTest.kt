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

package dev.patrickgold.florisboard.ime.toolbar

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class TextTransformsUnicodeTest : FunSpec({
    fun run(op: TextTransform, input: String): String =
        (TextTransforms.apply(op, input) as TransformResult.Ok).text

    test("case conversion keeps non-ASCII letters") {
        run(TextTransform.SNAKE, "café au lait") shouldBe "café_au_lait"
        run(TextTransform.CAMEL, "größe der Straße") shouldBe "größeDerStraße"
        run(TextTransform.KEBAB, "naïve Bayes") shouldBe "naïve-bayes"
    }

    test("case conversion still splits camel humps and ASCII punctuation") {
        run(TextTransform.SNAKE, "XMLHttpRequest") shouldBe "xml_http_request"
        run(TextTransform.KEBAB, "a.b/c") shouldBe "a-b-c"
    }
})
