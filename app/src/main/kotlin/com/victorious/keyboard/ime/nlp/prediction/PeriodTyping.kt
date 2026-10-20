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

/**
 * A period typed against a letter or digit is part of a name: file.txt, example.com, 1.2.3,
 * user@mail.com, or a path. It is not a sentence boundary until the user types a space.
 */
object PeriodTyping {
    fun isTokenChar(ch: Char): Boolean =
        ch.isLetterOrDigit() || ch == '_' || ch == '.' || ch == '/' || ch == '+' || ch == '-' || ch == '@'

    /** The identifier under the caret. Stops at whitespace, so "hello. " is not a dotted token. */
    fun caretToken(textBeforeCursor: String): String =
        textBeforeCursor.takeLastWhile { isTokenChar(it) }

    fun caretTokenContainsPeriod(textBeforeCursor: String): Boolean =
        '.' in caretToken(textBeforeCursor)

    /** The key is '.' and the character before the caret is already part of a token. */
    fun periodExtendsToken(textBeforeCursor: String): Boolean {
        val prev = textBeforeCursor.lastOrNull() ?: return false
        return isTokenChar(prev)
    }

    /** Do not insert a space after a period that is glued to a token. "done!" can still auto-space. */
    fun blocksAutoSpace(textBeforeCursor: String, inserting: String): Boolean {
        if (inserting.isEmpty() || inserting[0] != '.') return false
        return periodExtendsToken(textBeforeCursor)
    }

    /** Do not put a phantom space between "word." and the next letter or digit. */
    fun blocksPhantomSpace(textBeforeCursor: String, inserting: String): Boolean {
        if (inserting.isEmpty() || !inserting[0].isLetterOrDigit()) return false
        if (textBeforeCursor.lastOrNull() != '.') return false
        return periodExtendsToken(textBeforeCursor.dropLast(1))
    }

    /**
     * Keep shift off at "word.|". "word. " and "word.\n" are sentence boundaries and are not suppressed.
     */
    fun suppressAutoCapitalization(textBeforeCursor: String): Boolean {
        if (textBeforeCursor.length < 2 || textBeforeCursor.last() != '.') return false
        return periodExtendsToken(textBeforeCursor.dropLast(1))
    }
}

/**
 * Whether a non-letter key may commit the auto-commit suggestion.
 * A caret already inside a dotted token must not: the composing word stops at the period,
 * so the suggestion would be inserted after it with no space (word.words).
 */
object SuggestionCommit {
    fun allows(textBeforeCursor: String, key: String): Boolean {
        if (key.isEmpty()) return false
        return !PeriodTyping.caretTokenContainsPeriod(textBeforeCursor)
    }

    /**
     * Field text after [key], if [candidate] is eligible to commit.
     * A null or empty [composing] means the caret is not inside a word, so a commit inserts
     * [candidate] instead of replacing one. A letter never commits.
     */
    fun apply(textBeforeCursor: String, key: String, candidate: String?, composing: String?): String {
        val letter = key.firstOrNull()?.isLetter() == true
        val commit = !candidate.isNullOrEmpty() && !letter && allows(textBeforeCursor, key)
        val base = when {
            !commit -> textBeforeCursor
            !composing.isNullOrEmpty() && textBeforeCursor.endsWith(composing) ->
                textBeforeCursor.dropLast(composing.length) + candidate
            else -> textBeforeCursor + candidate
        }
        return base + key
    }
}
