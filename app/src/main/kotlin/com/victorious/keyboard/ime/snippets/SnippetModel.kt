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

package com.victorious.keyboard.ime.snippets

import kotlinx.serialization.Serializable

/**
 * A reusable piece of text. [body] may contain template placeholders (see [SnippetTemplate]); nothing in a snippet
 * is ever executed. [abbreviation] is the optional short name typed after the user's trigger prefix.
 */
@Serializable
data class Snippet(
    val id: String,
    val title: String,
    val body: String,
    val abbreviation: String? = null,
    val tags: List<String> = emptyList(),
    val enabled: Boolean = true,
    val updatedAtMillis: Long = 0L,
)

object SnippetLimits {
    const val MAX_SNIPPETS = 500
    const val MAX_ID = 64
    const val MAX_TITLE = 80
    const val MAX_BODY = 10_000
    const val MIN_ABBREVIATION = 2
    const val MAX_ABBREVIATION = 32
    const val MAX_TAGS = 10
    const val MAX_TAG = 32

    /** Upper bound for any rendered text, including substituted clipboard/selection. */
    const val MAX_RENDERED = 20_000

    /** Upper bound for an imported or stored snippets file. Larger input is rejected unread. */
    const val MAX_FILE_BYTES = 8 * 1024 * 1024
}

enum class SnippetIssue {
    BAD_ID, BLANK_TITLE, TITLE_TOO_LONG, BAD_TITLE_CHARS, EMPTY_BODY, BODY_TOO_LONG, BAD_BODY_CHARS,
    BAD_ABBREVIATION, TOO_MANY_TAGS, BAD_TAG,
}

data class SnippetValidation(
    val errors: List<SnippetIssue>,
    /** Placeholders like `{{foo}}` that are not recognised and will be inserted literally. */
    val unknownPlaceholders: List<String>,
) {
    val isValid: Boolean get() = errors.isEmpty()
}

object SnippetValidator {
    private val ID = Regex("[A-Za-z0-9_-]{1,${SnippetLimits.MAX_ID}}")

    fun validate(s: Snippet): SnippetValidation {
        val e = mutableListOf<SnippetIssue>()
        if (!ID.matches(s.id)) e += SnippetIssue.BAD_ID
        if (s.title.isBlank()) e += SnippetIssue.BLANK_TITLE
        if (s.title.length > SnippetLimits.MAX_TITLE) e += SnippetIssue.TITLE_TOO_LONG
        if (s.title.any { it.isISOControl() }) e += SnippetIssue.BAD_TITLE_CHARS
        if (s.body.isEmpty()) e += SnippetIssue.EMPTY_BODY
        if (s.body.length > SnippetLimits.MAX_BODY) e += SnippetIssue.BODY_TOO_LONG
        if (s.body.any { it.isISOControl() && it != '\n' && it != '\r' && it != '\t' }) e += SnippetIssue.BAD_BODY_CHARS
        s.abbreviation?.let { a ->
            if (a.length !in SnippetLimits.MIN_ABBREVIATION..SnippetLimits.MAX_ABBREVIATION ||
                a.any { it.isWhitespace() || it.isISOControl() }
            ) e += SnippetIssue.BAD_ABBREVIATION
        }
        if (s.tags.size > SnippetLimits.MAX_TAGS) e += SnippetIssue.TOO_MANY_TAGS
        if (s.tags.any { it.isBlank() || it.length > SnippetLimits.MAX_TAG || it.any(Char::isISOControl) }) {
            e += SnippetIssue.BAD_TAG
        }
        val unknown = if (s.body.length <= SnippetLimits.MAX_BODY) SnippetTemplate.unknownPlaceholders(s.body) else emptyList()
        return SnippetValidation(e, unknown)
    }
}
