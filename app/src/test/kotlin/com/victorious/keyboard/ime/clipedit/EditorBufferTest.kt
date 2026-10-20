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

package com.victorious.keyboard.ime.clipedit

import com.victorious.keyboard.ime.text.key.KeyCode
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class EditorBufferTest : FunSpec({
    test("typing, backspace, and a selection replace go through the buffer") {
        val typed = EditorBuffer.of("").insert("hello").insert(" ").insert("world")
        typed.text shouldBe "hello world"
        typed.deleteBackward(words = false).text shouldBe "hello worl"
        val selected = EditorBuffer.of("hello world").setCaret(6).selectWord()
        selected.selectedText shouldBe "world"
        selected.insert("there").text shouldBe "hello there"
    }

    test("undo and redo are bounded and redo is dropped after a new edit") {
        var buffer = EditorBuffer.of("")
        repeat(60) { buffer = buffer.insert("x") }
        buffer.text.length shouldBe 60
        buffer.undo.size shouldBe EditorBuffer.UNDO_CAP
        repeat(50) { buffer = buffer.undo() }
        buffer.text.length shouldBe 10
        buffer.canUndo shouldBe false
        buffer.undo().text.length shouldBe 10
        val restored = buffer.redo()
        restored.text.length shouldBe 11
        restored.insert("!").canRedo shouldBe false
    }

    test("arrows, lines, and select all stay inside the text") {
        val buffer = EditorBuffer.of("ab\ncd").setCaret(0)
        val down = buffer.moveVertical(1, extend = false)
        down.end shouldBe 3
        val end = down.moveLineEdge(toEnd = true, extend = true)
        end.selectedText shouldBe "cd"
        buffer.selectAll().selectedText shouldBe "ab\ncd"
        buffer.moveHorizontal(-1, words = false, extend = false).end shouldBe 0
    }

    test("find and replace do not loop on a replacement that contains the query") {
        val buffer = EditorBuffer.of("a a a").setCaret(0)
        val first = buffer.findNext("a")
        first.found shouldBe true
        first.buffer.selectedText shouldBe "a"
        val replaced = first.buffer.replaceMatch("a", "aa")
        replaced.text shouldBe "aa a a"
        val (all, count) = EditorBuffer.of("a a a").replaceAll("a", "aa")
        count shouldBe 3
        all.text shouldBe "aa aa aa"
        EditorBuffer.of("hello").findNext("").found shouldBe false
        EditorBuffer.of("hello").replaceAll("", "x").second shouldBe 0
    }

    test("the key router writes into the buffer and can auto-pair") {
        val start = EditorBuffer.of("")
        val input = EditorKeyInput(code = 'h'.code, character = true, text = "h")
        EditorKeyRouter.consumes(input.code, character = true) shouldBe true
        EditorKeyRouter.apply(start, input).text shouldBe "h"
        val paired = EditorKeyInput(code = '('.code, character = true, text = "(", pairBrackets = true)
        val after = EditorKeyRouter.apply(start, paired)
        val between = EditorKeyRouter.caretAfterPair(start, after, paired)
        between.text shouldBe "()"
        between.end shouldBe 1
        EditorKeyRouter.consumes(KeyCode.SETTINGS, character = false) shouldBe false
        EditorKeyRouter.apply(EditorBuffer.of("ab").setCaret(2), EditorKeyInput(KeyCode.DELETE, character = false)).text shouldBe "a"
    }

    test("a vault edit saves back to the vault and save-as-new adds a vault row") {
        ClipEditorSave.target(ClipEditorOrigin.VAULT, asNew = false, hasSource = true) shouldBe ClipEditorWrite.UPDATE_VAULT
        ClipEditorSave.target(ClipEditorOrigin.VAULT, asNew = true, hasSource = true) shouldBe ClipEditorWrite.ADD_VAULT
        ClipEditorSave.target(ClipEditorOrigin.REGULAR, asNew = false, hasSource = true) shouldBe ClipEditorWrite.UPDATE_REGULAR
        ClipEditorSave.target(ClipEditorOrigin.REGULAR, asNew = true, hasSource = true) shouldBe ClipEditorWrite.ADD_REGULAR
        ClipEditorSave.target(ClipEditorOrigin.SNIPPET, asNew = false, hasSource = true) shouldBe ClipEditorWrite.UPDATE_SNIPPET
        ClipEditorSave.target(ClipEditorOrigin.SNIPPET, asNew = true, hasSource = true) shouldBe ClipEditorWrite.ADD_REGULAR
    }
})
