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

package dev.patrickgold.florisboard.ime.snippets

/**
 * User-configurable text expansion. **Off by default.**
 *
 * With `prefix = ";"` and a snippet whose abbreviation is `sig`, typing `;sig` and then a boundary character (space,
 * newline, tab) replaces `;sig` with the snippet. The prefix keeps ordinary words from expanding by accident.
 */
data class ExpansionSettings(
    val enabled: Boolean = false,
    val prefix: String = ";",
    val caseSensitive: Boolean = true,
    val boundaries: Set<Char> = setOf(' ', '\n', '\t'),
) {
    val isValid: Boolean
        get() = prefix.length in 1..3 && prefix.none { it.isWhitespace() || it.isISOControl() } && boundaries.isNotEmpty()
}

/** [deleteCount] UTF-16 chars before the caret (the prefix plus abbreviation) to remove before inserting. */
data class ExpansionMatch(val snippet: Snippet, val deleteCount: Int)

object ExpansionMatcher {
    private const val MAX_TOKEN = SnippetLimits.MAX_ABBREVIATION + 3

    /**
     * Decides whether the character the user just typed ([typed], not yet committed) completes an expansion.
     *
     * Never matches in a sensitive field, when disabled, with invalid settings, for a non-boundary character, or for a
     * disabled snippet. The match is decided on [textBeforeCursor] only and the snippet body is **not** scanned again
     * afterwards, so expansions cannot chain or recurse.
     */
    fun match(
        textBeforeCursor: String,
        typed: Char,
        snippets: Collection<Snippet>,
        settings: ExpansionSettings,
        sensitiveField: Boolean,
    ): ExpansionMatch? {
        if (sensitiveField || !settings.enabled || !settings.isValid || typed !in settings.boundaries) return null
        val tail = textBeforeCursor.takeLast(MAX_TOKEN + 1)
        val token = tail.takeLastWhile { !it.isWhitespace() }
        if (token.length > MAX_TOKEN || token.length <= settings.prefix.length || !token.startsWith(settings.prefix)) return null
        val abbreviation = token.substring(settings.prefix.length)
        val snippet = snippets.firstOrNull {
            it.enabled && it.abbreviation != null && it.abbreviation.equals(abbreviation, ignoreCase = !settings.caseSensitive)
        } ?: return null
        return ExpansionMatch(snippet, token.length)
    }
}
