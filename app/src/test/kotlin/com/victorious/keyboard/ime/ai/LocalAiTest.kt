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

package com.victorious.keyboard.ime.ai

import com.victorious.keyboard.ime.nlp.prediction.LearningGate
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.types.shouldBeInstanceOf
import java.io.ByteArrayInputStream
import java.io.File

class LocalAiTest : FunSpec({
    test("gguf magic accepts only the four byte header") {
        GgufHeader.matches(byteArrayOf('G'.code.toByte(), 'G'.code.toByte(), 'U'.code.toByte(), 'F'.code.toByte(), 1)) shouldBe true
        GgufHeader.matches("gguf".encodeToByteArray()) shouldBe false
        GgufHeader.matches(byteArrayOf(1, 2, 3)) shouldBe false
    }

    test("import decisions cover size, space, count, and acknowledgement") {
        val small = 32L * 1024L * 1024L
        val free = 8L * 1024L * 1024L * 1024L
        ModelImport.decide(small, free, 0, false) shouldBe ImportDecision.ACCEPT
        ModelImport.decide(0, free, 0, true) shouldBe ImportDecision.REJECT_EMPTY
        ModelImport.decide(ModelImport.WARN_BYTES, free, 0, false) shouldBe ImportDecision.WARN
        ModelImport.decide(ModelImport.WARN_BYTES, free, 0, true) shouldBe ImportDecision.ACCEPT
        ModelImport.decide(null, free, 0, false) shouldBe ImportDecision.WARN
        ModelImport.decide(ModelImport.MAX_BYTES + 1, free, 0, true) shouldBe ImportDecision.REJECT_TOO_LARGE
        ModelImport.decide(small, small, 0, true) shouldBe ImportDecision.REJECT_NO_SPACE
        ModelImport.decide(small, free, ModelImport.MAX_MODELS, true) shouldBe ImportDecision.REJECT_TOO_MANY
        ModelImport.fileName("../etc/passwd") shouldBe null
        ModelImport.fileName("not-a-uuid") shouldBe null
        ModelImport.fileName("11111111-1111-1111-1111-111111111111") shouldBe "11111111-1111-1111-1111-111111111111.gguf"
    }

    test("copy rejects a bad header and keeps a real gguf") {
        val root = File("build/local-ai-test").apply { deleteRecursively(); mkdirs() }
        val files = AiFiles(root)
        val bad = files.import("x", 4, Long.MAX_VALUE, emptyList(), true, ByteArrayInputStream("XXXX".encodeToByteArray()))
        (bad as ImportOutcome.Blocked).decision shouldBe ImportDecision.REJECT_MAGIC
        root.walk().any { it.name.endsWith(".gguf") } shouldBe false
        val bytes = byteArrayOf('G'.code.toByte(), 'G'.code.toByte(), 'U'.code.toByte(), 'F'.code.toByte(), 9, 9, 9, 9)
        val ok = files.import("notes.gguf", bytes.size.toLong(), Long.MAX_VALUE, emptyList(), true, ByteArrayInputStream(bytes))
        val copied = ok.shouldBeInstanceOf<ImportOutcome.Copied>()
        File(files.modelsDir, copied.model.id + ".gguf").readBytes().copyOf(4) shouldBe bytes.copyOf(4)
        val capped = files.import("big", null, Long.MAX_VALUE, emptyList(), true, ByteArrayInputStream(bytes), streamCap = 4)
        (capped as ImportOutcome.Blocked).decision shouldBe ImportDecision.REJECT_TOO_LARGE
    }

    test("config and model index fail closed") {
        AiConfig().suggestionsEnabled shouldBe false
        val hostile = AiConfigCodec.decode("{")
        hostile.suggestionsEnabled shouldBe false
        hostile.contextSize shouldBe 2048
        AiConfigCodec.decode("""{"format":"victorious-local-ai","version":99}""").maxTokens shouldBe 256
        val saved = AiConfig(
            selectedModelId = "../secret",
            contextSize = 9999,
            threads = 0,
            temperature = 9f,
            maxTokens = 2,
            systemPrompt = "be brief\u0000 and local",
            suggestionsEnabled = true,
            translateLanguage = "French!",
        )
        val round = AiConfigCodec.decode(AiConfigCodec.encode(saved))
        round.selectedModelId shouldBe null
        round.contextSize shouldBe 8192
        round.threads shouldBe AiConfigRules.defaultThreads(4)
        round.temperature shouldBe 1.5f
        round.maxTokens shouldBe 16
        round.systemPrompt shouldBe "be brief and local"
        round.suggestionsEnabled shouldBe true
        round.translateLanguage shouldBe "French"
        ModelIndexCodec.decode("[]") shouldBe emptyList()
        ModelIndexCodec.decode("""{"format":"victorious-local-ai-models","version":1,"models":[{"id":"../x","displayName":"a","bytes":1,"importedAt":1}]}""") shouldBe emptyList()
    }

    test("vault, password, and clipboard are not sent unless chosen") {
        val vault = "vault-secret-token"
        val blocked = AiPrompt.plan(sample(source = AiSource.VAULT, explicitVault = false, editorIsVault = true, editorText = vault))
        blocked.shouldBeInstanceOf<AiPlan.Refuse>().reason shouldBe AiRefusal.VAULT
        val editor = AiPrompt.plan(sample(source = AiSource.EDITOR, editorIsVault = true, editorText = vault))
        editor.shouldBeInstanceOf<AiPlan.Refuse>().reason shouldBe AiRefusal.VAULT
        val picked = AiPrompt.plan(sample(source = AiSource.VAULT, explicitVault = true, editorIsVault = true, editorText = vault)).shouldBeInstanceOf<AiPlan.Ready>()
        picked.fromVault shouldBe true
        picked.user shouldContain vault
        val password = AiPrompt.plan(sample(source = AiSource.FIELD, passwordLike = true, fieldText = "s3cret-password"))
        password.shouldBeInstanceOf<AiPlan.Refuse>().reason shouldBe AiRefusal.PASSWORD
        AiPrompt.chooseDefault("", "hello", "", false, false, false) shouldBe AiSource.FIELD
        AiPrompt.chooseDefault("sel", "field", "edit", false, true, false) shouldBe AiSource.SELECTION
        AiPrompt.chooseDefault("", "", "edit", false, true, false) shouldBe AiSource.EDITOR
        AiPrompt.chooseDefault("", "field", "vault text", false, true, true) shouldBe AiSource.FIELD
        AiPrompt.chooseDefault("", "", "vault text", false, true, true) shouldBe null
    }

    test("shell prompt is one command and is not an attack recipe") {
        val plan = AiPrompt.plan(sample(task = AiTask.SHELL, source = AiSource.SELECTION, selection = "list files in the current directory")).shouldBeInstanceOf<AiPlan.Ready>()
        plan.user shouldContain AiPrompt.SHELL_RULE
        plan.user shouldContain "list files in the current directory"
        plan.user shouldNotContain "msfvenom"
        plan.user shouldNotContain "aircrack"
        plan.user shouldNotContain "hydra -"
        plan.maxTokens shouldBe 256
    }

    test("missing model and missing native engine refuse before any prompt") {
        AiPrompt.plan(sample(modelSelected = false)).shouldBeInstanceOf<AiPlan.Refuse>().reason shouldBe AiRefusal.NO_MODEL
        AiPrompt.plan(sample(nativeAvailable = false)).shouldBeInstanceOf<AiPlan.Refuse>().reason shouldBe AiRefusal.NATIVE_MISSING
        AiPrompt.plan(sample(task = AiTask.ASK, askText = "")).shouldBeInstanceOf<AiPlan.Refuse>().reason shouldBe AiRefusal.EMPTY
    }

    test("suggestions stay off unless learning and the field both allow them") {
        val open = LearningGate.Field()
        AiPrivacy.maySuggest(false, true, true, true, open) shouldBe false
        AiPrivacy.maySuggest(true, false, true, true, open) shouldBe false
        AiPrivacy.maySuggest(true, true, false, true, open) shouldBe false
        AiPrivacy.maySuggest(true, true, true, false, open) shouldBe false
        AiPrivacy.maySuggest(true, true, true, true, open.copy(noPersonalizedLearning = true)) shouldBe false
        AiPrivacy.maySuggest(true, true, true, true, open.copy(passwordLike = true)) shouldBe false
        AiPrivacy.maySuggest(true, true, true, true, open.copy(incognito = true)) shouldBe false
        AiPrivacy.maySuggest(true, true, true, true, open.copy(vaultHandoff = true)) shouldBe false
        AiPrivacy.maySuggest(true, true, true, true, open) shouldBe true
        AiSuggestionText.candidate("hel", "hello there") shouldBe "hello there"
        AiSuggestionText.candidate("hel", "lo") shouldBe "hello"
        AiSuggestionText.candidate("hel", "hel") shouldBe null
        AiSuggestionText.candidate("hel", "one two three four five") shouldBe null
        AiPrompt.suggestionUser("hel") shouldNotContain "clipboard"
    }

    test("stream cancel and the character cap stop the reply") {
        val stream = StreamAssembler(maxChars = 4)
        stream.push("ab") shouldBe true
        stream.push("cdef") shouldBe false
        stream.text() shouldBe "abcd"
        stream.push("z") shouldBe false
        val cancelled = StreamAssembler()
        cancelled.cancelled = true
        cancelled.push("nope") shouldBe false
        cancelled.text() shouldBe ""
    }

    test("idle and memory pressure unload only when the model is sitting unused") {
        AiLifecycle.shouldUnload(true, false, 200_000, 80_000) shouldBe true
        AiLifecycle.shouldUnload(true, false, 199_999, 80_000) shouldBe false
        AiLifecycle.shouldUnload(true, true, 400_000, 80_000) shouldBe false
        AiLifecycle.shouldUnload(false, false, 400_000, 80_000) shouldBe false
        AiLifecycle.shouldUnloadForMemory(5) shouldBe false
        AiLifecycle.shouldUnloadForMemory(10) shouldBe true
        AiLifecycle.shouldUnloadForMemory(80) shouldBe true
    }

    test("the manifest does not add an internet permission") {
        val manifest = File("src/main/AndroidManifest.xml").readText().lowercase()
        manifest.contains("android.permission.internet") shouldBe false
    }
})

private fun sample(
    task: AiTask = AiTask.REWRITE,
    source: AiSource = AiSource.SELECTION,
    explicitVault: Boolean = false,
    passwordLike: Boolean = false,
    editorIsVault: Boolean = false,
    selection: String = "hello there",
    fieldText: String = "",
    clipboard: String = "",
    editorText: String = "",
    askText: String = "what is this",
    modelSelected: Boolean = true,
    nativeAvailable: Boolean = true,
) = AiRequest(
    task = task,
    source = source,
    explicitVault = explicitVault,
    passwordLike = passwordLike,
    editorIsVault = editorIsVault,
    selection = selection,
    fieldText = fieldText,
    clipboard = clipboard,
    editorText = editorText,
    askText = askText,
    systemPrompt = "be brief",
    modelSelected = modelSelected,
    nativeAvailable = nativeAvailable,
)
