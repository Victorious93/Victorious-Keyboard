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

package com.victorious.keyboard.ime.developer

/**
 * Parses the user's own developer-row keys: whitespace-separated tokens, each becoming an insert key. Untrusted text:
 * at most [MAX_KEYS] keys of 1 to [MAX_LENGTH] code points, no control characters, duplicates dropped. Bracket and
 * quote pairs such as `()` or `""` put the caret between the two halves. Anything not accepted is returned in
 * [Parsed.rejected] (never silently dropped, never executed).
 */
object CustomSymbols {
    const val MAX_KEYS = 40
    const val MAX_LENGTH = 8

    private val AUTO_PAIRS = setOf("()", "[]", "{}", "<>", "\"\"", "''", "``")

    data class Parsed(val keys: List<DevKey>, val rejected: List<String>)

    fun parse(text: String): Parsed {
        val keys = mutableListOf<DevKey>()
        val rejected = mutableListOf<String>()
        val seen = HashSet<String>()
        for (token in text.split(Regex("\\s+")).filter { it.isNotEmpty() }) {
            val length = token.codePointCount(0, token.length)
            when {
                length > MAX_LENGTH || token.any { it.isISOControl() } -> rejected += token
                !seen.add(token) -> Unit // a repeated token is harmless, not an error
                keys.size >= MAX_KEYS -> rejected += token
                else -> keys += DevKey(token, DevKeyAction.Insert(token, if (token in AUTO_PAIRS) 1 else 0), token)
            }
        }
        return Parsed(keys, rejected)
    }
}
