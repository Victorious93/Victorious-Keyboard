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

package com.victorious.keyboard.ime.nlp.prediction

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class PeriodTypingTest : FunSpec({
    test("a non-letter after a dotted word does not insert the suggestion") {
        // The period is not a word, so composing is empty and a commit would insert.
        SuggestionCommit.apply("word.", "2", "words", composing = null) shouldBe "word.2"
        SuggestionCommit.apply("word.", ".", "words", composing = null) shouldBe "word.."
        SuggestionCommit.apply("file.txt", " ", "filet", composing = "file.txt") shouldBe "file.txt "
        SuggestionCommit.apply("com.", " ", "come", composing = null) shouldBe "com. "
        SuggestionCommit.apply("user@mail.com", " ", "come", composing = null) shouldBe "user@mail.com "
        SuggestionCommit.apply("https://example.com/a", "/", "example", composing = null) shouldBe "https://example.com/a/"
        SuggestionCommit.apply("1.2.3", ".", "123", composing = null) shouldBe "1.2.3."
        SuggestionCommit.apply("word.", "x", "words", composing = null) shouldBe "word.x"
    }

    test("a sentence period still corrects the word before it, and a following space still capitalizes") {
        SuggestionCommit.allows("helo", ".") shouldBe true
        SuggestionCommit.apply("helo", ".", "hello", composing = "helo") shouldBe "hello."
        SuggestionCommit.allows("hello", " ") shouldBe true
        SuggestionCommit.apply("helo", " ", "hello", composing = "helo") shouldBe "hello "
        PeriodTyping.suppressAutoCapitalization("word.") shouldBe true
        PeriodTyping.suppressAutoCapitalization("file.") shouldBe true
        PeriodTyping.suppressAutoCapitalization("1.") shouldBe true
        PeriodTyping.suppressAutoCapitalization("word. ") shouldBe false
        PeriodTyping.suppressAutoCapitalization("Hello.\n") shouldBe false
        PeriodTyping.blocksAutoSpace("word", ".") shouldBe true
        PeriodTyping.blocksAutoSpace("1.2", ".") shouldBe true
        PeriodTyping.blocksAutoSpace("user@mail", ".") shouldBe true
        PeriodTyping.blocksAutoSpace("done", "!") shouldBe false
        PeriodTyping.blocksPhantomSpace("word.", "W") shouldBe true
        PeriodTyping.blocksPhantomSpace("word. ", "W") shouldBe false
    }
})
