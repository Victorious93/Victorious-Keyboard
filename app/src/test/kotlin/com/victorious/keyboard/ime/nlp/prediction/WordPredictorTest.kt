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

import com.victorious.keyboard.ime.dictionary.packs.PackEntry
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.io.File
import java.nio.file.Files
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

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

    test("a period glued to a word does not auto-commit a dictionary word") {
        // Don: typing word. and then more characters produced word.<some word> with no space.
        // "word." is one edit from "word" and "words", so the period was arming an autocorrect.
        val lexicon = mapOf(
            "word" to 241,
            "words" to 241,
            "world" to 251,
            "file" to 229,
            "files" to 220,
            "filet" to 146,
            "come" to 250,
            "com" to 210,
            "the" to 255,
        )
        val afterPeriod = WordPredictor.predict("word.", lexicon, learned = null)
        afterPeriod.none { it.autoCommit } shouldBe true
        afterPeriod.none { it.kind == Prediction.Kind.CORRECTION } shouldBe true
        val fileSuffix = WordPredictor.predict("file.t", lexicon, learned = null)
        fileSuffix.none { it.autoCommit } shouldBe true
        fileSuffix.none { it.text.equals("filet", ignoreCase = true) } shouldBe true
        val domain = WordPredictor.predict("com.", lexicon, learned = null)
        domain.none { it.autoCommit } shouldBe true
        domain.none { it.text.equals("come", ignoreCase = true) } shouldBe true
        val version = WordPredictor.predict("1.2.3", lexicon, learned = null)
        version.none { it.autoCommit } shouldBe true
        version.none { it.kind == Prediction.Kind.CORRECTION } shouldBe true
        val email = WordPredictor.predict("user@mail.co", lexicon, learned = null)
        email.none { it.autoCommit } shouldBe true
        val url = WordPredictor.predict("https://example.com/a", lexicon, learned = null)
        url.none { it.autoCommit } shouldBe true
        url.none { it.kind == Prediction.Kind.CORRECTION } shouldBe true
        val learned = LearnedLexicon().record("file.txt", null)
        val continued = WordPredictor.predict("file.t", lexicon, learned)
        continued.none { it.autoCommit } shouldBe true
        continued.map { it.text } shouldContain "file.txt"
        val sentence = WordPredictor.predict("word. ", lexicon, learned = null)
        sentence.none { it.autoCommit } shouldBe true
        sentence.first().kind shouldBe Prediction.Kind.NEXT
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
        LearningGate.allowsLearning(true, open.copy(vaultHandoff = true)) shouldBe false
        LearningGate.allowsPrediction(true, open.copy(vaultHandoff = true)) shouldBe false
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

    test("hope is not replaced by rope when hope is a dictionary word") {
        val preds = WordPredictor.predict(
            "hope",
            lexicon = mapOf("rope" to 220),
            learned = null,
            knownWords = setOf("hope"),
        )
        preds.none { it.autoCommit } shouldBe true
        preds.none { it.kind == Prediction.Kind.CORRECTION } shouldBe true
        preds.first().text shouldBe "hope"
    }

    test("home, the, code, some, and with stay when they are dictionary words") {
        val lexicon = mapOf("tee" to 200, "node" to 200, "codex" to 180, "rope" to 180)
        for (word in listOf("home", "the", "code", "some", "with", "hope")) {
            val preds = WordPredictor.predict(word, lexicon, learned = null, knownWords = setOf(word))
            preds.none { it.autoCommit } shouldBe true
            preds.none { it.text.equals("rope", ignoreCase = true) && it.kind == Prediction.Kind.CORRECTION } shouldBe true
        }
    }

    test("a user-dictionary word and a learned word are not autocorrected") {
        val lexicon = mapOf("nmap" to 200, "rope" to 200)
        val user = WordPredictor.predict("namp", lexicon, learned = null, knownWords = setOf("namp"))
        user.none { it.autoCommit } shouldBe true
        user.first().text shouldBe "namp"
        val learned = LearnedLexicon().record("hope", null)
        val fromTyping = WordPredictor.predict("hope", lexicon, learned)
        fromTyping.none { it.autoCommit } shouldBe true
        fromTyping.none { it.kind == Prediction.Kind.CORRECTION } shouldBe true
    }

    test("an unknown word can still be corrected, and h is not treated as next to r") {
        KeyProximity.adjacent('h', 'r') shouldBe false
        KeyProximity.adjacent('h', 'g') shouldBe true
        KeyProximity.adjacent('u', 'i') shouldBe true
        val unknown = WordPredictor.predict("hope", mapOf("rope" to 220), learned = null)
        unknown.first { it.kind == Prediction.Kind.CORRECTION }.text shouldBe "rope"
        unknown.first().autoCommit shouldBe true
        val mild = WordPredictor.predict(
            "hope",
            mapOf("rope" to 220),
            learned = null,
            level = AutocorrectLevel.MILD,
        )
        mild.none { it.autoCommit } shouldBe true
        val neighbor = WordPredictor.predict(
            "termix",
            LEXICON,
            learned = null,
            level = AutocorrectLevel.MILD,
        )
        neighbor.first { it.autoCommit }.text shouldBe "termux"
    }

    test("a nearer key outranks a more frequent far key") {
        val preds = WordPredictor.predict(
            "helo",
            mapOf("hero" to 255, "help" to 10),
            learned = null,
        )
        preds.first { it.kind == Prediction.Kind.CORRECTION }.text shouldBe "help"
        preds.first().autoCommit shouldBe true
    }

    test("off never auto-commits, including a one-edit unknown word") {
        val preds = WordPredictor.predict(
            "termx",
            LEXICON,
            learned = null,
            level = AutocorrectLevel.OFF,
        )
        preds.any { it.text == "termux" } shouldBe true
        preds.none { it.autoCommit } shouldBe true
    }

    test("backspace after an autocorrect restores the typed word") {
        val armed = AutocorrectReplacement(typed = "hope", committed = "rope", withSpace = true)
        val step = AutocorrectUndo.plan("say rope ", armed)
        step shouldNotBe null
        step!!.deleteCount shouldBe "rope ".length
        step.restore shouldBe "hope"
        AutocorrectUndo.plan("say rope", armed) shouldBe null
        AutocorrectUndo.plan("say rope ", armed.copy(typed = "rope")) shouldBe null
        val noSpace = AutocorrectUndo.plan("rope", AutocorrectReplacement("termx", "termux", withSpace = false))
        noSpace shouldBe null
        val restored = AutocorrectUndo.plan("termux", AutocorrectReplacement("termx", "termux", withSpace = false))
        restored!!.restore shouldBe "termx"
        restored.deleteCount shouldBe "termux".length
    }

    test("reverting the same word twice marks it for the user dictionary") {
        val once = RevertCounts().note("namp")
        once.second shouldBe false
        val twice = once.first.note("Namp")
        twice.second shouldBe true
        twice.first.protectedWords() shouldContain "namp"
        twice.first.note("namp").second shouldBe true
        RevertCounts().note("a").second shouldBe false
        RevertPrivacy.mayRemember(LearningGate.Field()) shouldBe true
        RevertPrivacy.mayRemember(LearningGate.Field(passwordLike = true)) shouldBe false
        RevertPrivacy.mayRemember(LearningGate.Field(incognito = true)) shouldBe false
        RevertPrivacy.mayRemember(LearningGate.Field(vaultHandoff = true)) shouldBe false
        RevertPrivacy.mayRemember(LearningGate.Field(noPersonalizedLearning = true)) shouldBe false
    }

    test("revert counts round-trip and a corrupt file stores nothing") {
        val counts = RevertCounts().note("namp").first.note("namp").first
        val back = RevertCountCodec.decode(RevertCountCodec.encode(counts))
        back.protectedWords() shouldContain "namp"
        RevertCountCodec.decode("nope".encodeToByteArray()).counts.isEmpty() shouldBe true
        RevertCountCodec.decode(ByteArray(RevertCountCodec.MAX_FILE_BYTES + 1)).counts.isEmpty() shouldBe true
        val dir = Files.createTempDirectory("revert-counts").toFile()
        val file = RevertCountFile(dir)
        file.load().counts.isEmpty() shouldBe true
        file.save(counts)
        file.load().protectedWords() shouldContain "namp"
    }

    test("the english word list keeps hope, and the pack still contains rope") {
        val dir = File("src/main/assets/ime/dictionary-packs")
        val zips = dir.listFiles { f -> f.extension == "zip" }!!.map { it.readBytes() }
        val lexicon = BundledLexicon.fromPackZips(zips)
        lexicon.containsKey("rope") shouldBe true
        val english = Json.decodeFromString(
            MapSerializer(String.serializer(), Int.serializer()),
            File("src/main/assets/ime/dict/data.json").readText(),
        )
        english.containsKey("hope") shouldBe true
        english.containsKey("the") shouldBe true
        english.containsKey("code") shouldBe true
        val hope = WordPredictor.predict("hope", lexicon, learned = null, knownWords = english.keys)
        hope.none { it.autoCommit } shouldBe true
        hope.none { it.text.equals("rope", ignoreCase = true) } shouldBe true
        val the = WordPredictor.predict("the", lexicon, learned = null, knownWords = english.keys)
        the.none { it.autoCommit } shouldBe true
        val code = WordPredictor.predict("code", lexicon, learned = null, knownWords = english.keys)
        code.none { it.autoCommit } shouldBe true
        val termx = WordPredictor.predict("termx", lexicon, learned = null, knownWords = english.keys)
        termx.first { it.autoCommit }.text shouldBe "termux"
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
