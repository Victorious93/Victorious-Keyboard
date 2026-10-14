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

package dev.patrickgold.florisboard.ime.nlp.prediction

import dev.patrickgold.florisboard.ime.dictionary.packs.PackEntry
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.io.File
import java.nio.file.Files

private val LEXICON = mapOf(
    "termux" to 200,
    "termux-setup-storage" to 80,
    "pkg" to 180,
    "install" to 160,
    "nmap" to 140,
    "search" to 100,
)

class WordPredictorTest : FunSpec({
    test("a prefix completes from the static lexicon and keeps the typed capital") {
        val preds = WordPredictor.predict("Ter", LEXICON, learned = null)
        preds.first { it.kind == Prediction.Kind.COMPLETION }.text shouldBe "Termux"
        preds.map { it.text.lowercase() } shouldContain "termux-setup-storage"
        preds.none { it.autoCommit } shouldBe true
    }

    test("a one-character edit of an unknown word is an autocorrect that can commit") {
        val preds = WordPredictor.predict("termx", LEXICON, learned = null)
        val correction = preds.first { it.kind == Prediction.Kind.CORRECTION }
        correction.text shouldBe "termux"
        correction.autoCommit shouldBe true
    }

    test("a word that is already in the lexicon is not autocorrected") {
        val preds = WordPredictor.predict("pkg", LEXICON, learned = null)
        preds.none { it.kind == Prediction.Kind.CORRECTION } shouldBe true
        preds.first().text shouldBe "pkg"
    }

    test("after a space, next-word uses learned bigrams and ignores them when learning data is absent") {
        val learned = LearnedLexicon().record("install", "pkg").record("install", "pkg")
        val withLearning = WordPredictor.predict("pkg ", LEXICON, learned)
        withLearning.first().text shouldBe "install"
        withLearning.first().kind shouldBe Prediction.Kind.NEXT
        val staticOnly = WordPredictor.predict("pkg ", LEXICON, learned = null)
        staticOnly.none { it.text == "install" && it.confidence > 0.5 } shouldBe true
        staticOnly.first().kind shouldBe Prediction.Kind.NEXT
    }

    test("learned words are invisible while the learned lexicon is not passed in") {
        val learned = LearnedLexicon().record("mycmd", null)
        WordPredictor.predict("myc", LEXICON, learned).map { it.text } shouldContain "mycmd"
        WordPredictor.predict("myc", LEXICON, learned = null).map { it.text.lowercase() }.none { it == "mycmd" } shouldBe true
    }

    test("an empty field suggests nothing") {
        WordPredictor.predict("", LEXICON, null).shouldBeEmpty()
        WordPredictor.predict("   ", LEXICON, null).shouldBeEmpty()
    }

    test("learning gate blocks passwords, incognito, the no-personalized-learning flag, and a disabled switch") {
        val open = LearningGate.Field()
        LearningGate.allowsLearning(learningEnabled = false, open) shouldBe false
        LearningGate.allowsLearning(true, open.copy(passwordLike = true)) shouldBe false
        LearningGate.allowsLearning(true, open.copy(incognito = true)) shouldBe false
        LearningGate.allowsLearning(true, open.copy(noPersonalizedLearning = true)) shouldBe false
        LearningGate.allowsLearning(true, open.copy(noSuggestions = true)) shouldBe false
        LearningGate.allowsLearning(true, open) shouldBe true
        LearningGate.allowsPrediction(true, open.copy(passwordLike = true)) shouldBe false
        LearningGate.allowsPrediction(true, open.copy(noSuggestions = true)) shouldBe false
        LearningGate.allowsPrediction(false, open) shouldBe false
        LearningGate.allowsPrediction(true, open) shouldBe true
    }

    test("a short or symbolic token is not stored") {
        LearnedLexicon().record("a", null).words.isEmpty() shouldBe true
        LearnedLexicon().record("hello world", null).words.isEmpty() shouldBe true
        LearnedLexicon().record("ok", "pkg").words.keys shouldContain "ok"
    }

    test("codec round trip, and a corrupt file does not throw or invent words") {
        val lexicon = LearnedLexicon().record("termux", "pkg")
        val back = LearnedLexiconCodec.decode(LearnedLexiconCodec.encode(lexicon))
        back.words["termux"] shouldNotBe null
        back.bigrams["pkg"]?.get("termux") shouldNotBe null
        LearnedLexiconCodec.decode("not json".encodeToByteArray()).words.isEmpty() shouldBe true
        LearnedLexiconCodec.decode(ByteArray(LearnedWords.MAX_FILE_BYTES + 1)).words.isEmpty() shouldBe true
    }

    test("the file store saves, reloads, and clears") {
        val dir = Files.createTempDirectory("learned-words").toFile()
        val store = LearnedWordFile(dir)
        store.load().words.isEmpty() shouldBe true
        store.save(LearnedLexicon().record("nmap", null))
        store.load().words.containsKey("nmap") shouldBe true
        store.clear()
        store.load().words.isEmpty() shouldBe true
        File(dir, "learned-words.json").exists() shouldBe false
    }

    test("bundled packs contribute real words and a bad zip is skipped") {
        val dir = File("src/main/assets/ime/dictionary-packs")
        val zips = dir.listFiles { f -> f.extension == "zip" }!!.map { it.readBytes() }
        val lexicon = BundledLexicon.fromPackZips(zips)
        lexicon.containsKey("termux") shouldBe true
        lexicon.containsKey("nmap") shouldBe true
        BundledLexicon.fromPackZips(listOf("nope".encodeToByteArray())).isEmpty() shouldBe true
        BundledLexicon.fromEntries(listOf(PackEntry("Pkg", 10), PackEntry("pkg", 40)))["pkg"] shouldBe 40
    }
})
