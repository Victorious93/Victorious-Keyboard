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

import java.time.DateTimeException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** What the shell knows at insertion time. Pass `null` for [clipboard]/[selection] when unavailable or the field is sensitive. */
data class TemplateContext(
    val now: LocalDateTime,
    val clipboard: String? = null,
    val selection: String? = null,
    /** What `{{date}}`, `{{time}}` and `{{datetime}}` print when no pattern is given. Must be valid `java.time` patterns. */
    val datePattern: String = "yyyy-MM-dd",
    val timePattern: String = "HH:mm",
)

sealed interface RenderResult {
    /**
     * @property cursorBackCodePoints how many code points the caret must be moved left from the end of [text] to
     * land on the first `{{cursor}}`; `null` if the template has none (caret stays at the end).
     */
    data class Rendered(val text: String, val cursorBackCodePoints: Int?) : RenderResult

    /** The result would exceed [SnippetLimits.MAX_RENDERED]; nothing must be inserted. */
    data object TooLarge : RenderResult
}

/**
 * The snippet template language. Deliberately tiny and **inert**: there is no way to run a command, read a file, call a
 * URL or loop. Placeholders are `{{name}}` or `{{name:argument}}`:
 *
 * | placeholder | result |
 * |---|---|
 * | `{{date}}` / `{{date:PATTERN}}` | local date, `yyyy-MM-dd` or a `java.time` pattern |
 * | `{{time}}` / `{{time:PATTERN}}` | local time, `HH:mm` or a pattern |
 * | `{{datetime}}` / `{{datetime:PATTERN}}` | `yyyy-MM-dd HH:mm` or a pattern |
 * | `{{clipboard}}` | the current clipboard text (empty if none) |
 * | `{{selection}}` | the selected text (empty if none) |
 * | `{{cursor}}` | where the caret ends up (first one wins, others are removed) |
 *
 * The same four names also work as exact single-brace tokens: `{date}`, `{time}`, `{clipboard}`, `{cursor}`.
 * `{selection}` and `{datetime}` stay double-brace only. A token with an argument (`{date:yyyy}`) or any other
 * braces (`{ return; }`) stay literal, so a code snippet is not rewritten. `\{{` writes a literal `{{`, and a
 * backslash before a single-brace token (`\{date}`) writes that token unchanged.
 *
 * Anything unrecognised, malformed or unterminated stays literal text. Substituted values (clipboard, selection)
 * are **never** re-scanned, so their content cannot trigger further placeholders.
 */
object SnippetTemplate {
    private const val MAX_PLACEHOLDER = 64
    private const val MAX_PATTERN = 32
    private val NO_ARG = setOf("clipboard", "selection", "cursor")
    private val WITH_ARG = setOf("date", "time", "datetime")
    /** Exact `{name}` tokens. `{datetime}` and `{selection}` are not in this list. */
    private val SINGLE_BRACE = listOf("date", "time", "clipboard", "cursor")

    private sealed interface Part {
        data class Literal(val text: String) : Part
        data class Value(val name: String, val arg: String?) : Part
    }

    private class Parsed(val parts: List<Part>, val unknown: List<String>)

    private fun parse(body: String): Parsed {
        val parts = mutableListOf<Part>()
        val unknown = mutableListOf<String>()
        val lit = StringBuilder()
        fun flush() { if (lit.isNotEmpty()) { parts += Part.Literal(lit.toString()); lit.setLength(0) } }
        var i = 0
        while (i < body.length) {
            when {
                body.startsWith("\\{{", i) -> { lit.append("{{"); i += 3 }
                body.startsWith("{{", i) -> {
                    val end = body.indexOf("}}", i + 2)
                    val inner = if (end < 0) null else body.substring(i + 2, end)
                    if (inner == null || inner.length > MAX_PLACEHOLDER || inner.contains('\n') || inner.contains("{{")) {
                        lit.append("{{"); i += 2 // unterminated or implausible: literal, keep scanning after the braces
                    } else {
                        val name = inner.substringBefore(':').trim().lowercase(Locale.ROOT)
                        val arg = if (inner.contains(':')) inner.substringAfter(':') else null
                        val ok = (name in NO_ARG && arg == null) || (name in WITH_ARG)
                        if (ok) { flush(); parts += Part.Value(name, arg) } else { lit.append("{{").append(inner).append("}}"); unknown += inner }
                        i = end + 2
                    }
                }
                else -> {
                    val single = singleBraceAt(body, i)
                    if (single == null) {
                        lit.append(body[i]); i++
                    } else {
                        val tokenLen = single.length + 2
                        if (lit.isNotEmpty() && lit.last() == '\\') {
                            lit.deleteCharAt(lit.lastIndex)
                            lit.append(body, i, i + tokenLen)
                        } else {
                            flush()
                            parts += Part.Value(single, null)
                        }
                        i += tokenLen
                    }
                }
            }
        }
        flush()
        return Parsed(parts, unknown)
    }

    /** `{date}` and the other exact tokens, or null. An extra `}` (as in `{date}}`) does not match. */
    private fun singleBraceAt(body: String, i: Int): String? {
        if (i >= body.length || body[i] != '{') return null
        for (name in SINGLE_BRACE) {
            val token = "{$name}"
            if (!body.regionMatches(i, token, 0, token.length, ignoreCase = true)) continue
            val after = i + token.length
            if (after < body.length && body[after] == '}') continue
            return name
        }
        return null
    }

    fun unknownPlaceholders(body: String): List<String> = parse(body).unknown.distinct()

    /**
     * Whether [body] really contains the placeholder [name] (`clipboard`, `selection`, ...), so the shell reads the
     * clipboard or the selection only when a snippet uses it. An escaped `\{{selection}}` is literal text and does not count.
     */
    fun usesPlaceholder(body: String, name: String): Boolean =
        parse(body).parts.any { it is Part.Value && it.name == name }

    fun render(body: String, ctx: TemplateContext): RenderResult {
        val out = StringBuilder()
        var cursorAt = -1
        for (part in parse(body).parts) {
            when (part) {
                is Part.Literal -> out.append(part.text)
                is Part.Value -> when (part.name) {
                    "clipboard" -> out.append(ctx.clipboard.orEmpty())
                    "selection" -> out.append(ctx.selection.orEmpty())
                    "cursor" -> if (cursorAt < 0) cursorAt = out.length
                    else -> out.append(formatTime(part.name, part.arg, ctx))
                }
            }
            if (out.length > SnippetLimits.MAX_RENDERED) return RenderResult.TooLarge
        }
        val text = out.toString()
        val back = if (cursorAt < 0) null else text.codePointCount(cursorAt, text.length)
        return RenderResult.Rendered(text, back)
    }

    private fun formatTime(name: String, arg: String?, ctx: TemplateContext): String {
        val now = ctx.now
        val default = when (name) {
            "date" -> ctx.datePattern
            "time" -> ctx.timePattern
            else -> ctx.datePattern + " " + ctx.timePattern
        }
        // What to print if the pattern cannot be used: exactly the text the user wrote.
        val literal = if (arg == null) "{{$name}}" else "{{$name:$arg}}"
        val pattern = arg?.takeIf { it.isNotBlank() } ?: default
        if (pattern.length > MAX_PATTERN) return literal
        return try {
            now.format(DateTimeFormatter.ofPattern(pattern, Locale.ROOT))
        } catch (_: IllegalArgumentException) {
            literal
        } catch (_: DateTimeException) {
            literal // e.g. a date pattern asking for a zone the value does not have
        }
    }
}
