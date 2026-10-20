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

package com.victorious.keyboard.app.manual

/**
 * Offline manual. [ASSET_PATH] is a copy of `docs/MANUAL.md` inside the APK.
 * Parsing stays in pure Kotlin so the Help screen can render it without a network library.
 */
object ManualDocument {
    const val ASSET_PATH = "manual/MANUAL.md"

    val requiredHeadings: List<String> = listOf(
        "What Victorious Keyboard is",
        "Install and first run",
        "Features",
        "Suggestion strip",
        "Tools panel",
        "Clipboard",
        "Clip editor",
        "Vault",
        "Snippets",
        "Export and import",
        "Slash commands",
        "Terminal key row",
        "Screenshot",
        "Theme",
        "Local AI",
        "Privacy mode",
        "Permissions",
        "Fleet",
        "Troubleshooting",
        "Privacy and your data",
    )

    sealed interface Block {
        data class Heading(val level: Int, val text: String) : Block
        data class Paragraph(val text: String) : Block
        data class Bullet(val text: String) : Block
        data class Numbered(val number: Int, val text: String) : Block
        data class Code(val text: String) : Block
        data class Image(val alt: String, val path: String) : Block
        data object Rule : Block
    }

    data class Span(val text: String, val bold: Boolean = false, val code: Boolean = false)

    private val heading = Regex("""^(#{1,6})\s+(.+)$""")
    private val image = Regex("""^!\[([^\]]*)]\(([^)]+)\)$""")
    private val bullet = Regex("""^[-*]\s+(.+)$""")
    private val numbered = Regex("""^(\d+)\.\s+(.+)$""")

    fun parse(markdown: String): List<Block> {
        val lines = markdown.replace("\r\n", "\n").lines()
        val out = ArrayList<Block>()
        val paragraph = StringBuilder()
        fun flush() {
            val text = paragraph.toString().trim()
            if (text.isNotEmpty()) out.add(Block.Paragraph(text))
            paragraph.clear()
        }
        var index = 0
        while (index < lines.size) {
            val trimmed = lines[index].trim()
            val imageMatch = image.matchEntire(trimmed)
            val headingMatch = heading.matchEntire(trimmed)
            val bulletMatch = bullet.matchEntire(trimmed)
            val numberedMatch = numbered.matchEntire(trimmed)
            when {
                trimmed.startsWith("```") -> {
                    flush()
                    val code = StringBuilder()
                    index++
                    while (index < lines.size && !lines[index].trim().startsWith("```")) {
                        if (code.isNotEmpty()) code.append('\n')
                        code.append(lines[index])
                        index++
                    }
                    out.add(Block.Code(code.toString()))
                    if (index < lines.size) index++
                }
                imageMatch != null -> {
                    flush()
                    out.add(Block.Image(imageMatch.groupValues[1], imageMatch.groupValues[2]))
                    index++
                }
                headingMatch != null -> {
                    flush()
                    out.add(Block.Heading(headingMatch.groupValues[1].length, headingMatch.groupValues[2].trim()))
                    index++
                }
                trimmed == "---" || trimmed == "***" -> {
                    flush()
                    out.add(Block.Rule)
                    index++
                }
                bulletMatch != null -> {
                    flush()
                    out.add(Block.Bullet(bulletMatch.groupValues[1].trim()))
                    index++
                }
                numberedMatch != null -> {
                    flush()
                    out.add(Block.Numbered(numberedMatch.groupValues[1].toInt(), numberedMatch.groupValues[2].trim()))
                    index++
                }
                trimmed.isEmpty() -> {
                    flush()
                    index++
                }
                else -> {
                    if (paragraph.isNotEmpty()) paragraph.append(' ')
                    paragraph.append(trimmed)
                    index++
                }
            }
        }
        flush()
        return out
    }

    fun headingTexts(markdown: String): List<String> =
        parse(markdown).filterIsInstance<Block.Heading>().map { it.text }

    fun isHeaderArt(path: String): Boolean = path.endsWith("header.svg")

    /** `**bold**` and `` `code` ``. An unmatched marker is left as text. Values are not re-scanned. */
    fun inlines(text: String): List<Span> {
        val out = ArrayList<Span>()
        val plain = StringBuilder()
        fun flushPlain() {
            if (plain.isNotEmpty()) {
                out.add(Span(plain.toString()))
                plain.clear()
            }
        }
        var index = 0
        while (index < text.length) {
            if (text.startsWith("**", index)) {
                val end = text.indexOf("**", index + 2)
                if (end > index) {
                    flushPlain()
                    out.add(Span(text.substring(index + 2, end), bold = true))
                    index = end + 2
                    continue
                }
            }
            if (text[index] == '`') {
                val end = text.indexOf('`', index + 1)
                if (end > index) {
                    flushPlain()
                    out.add(Span(text.substring(index + 1, end), code = true))
                    index = end + 1
                    continue
                }
            }
            plain.append(text[index])
            index++
        }
        flushPlain()
        return out
    }
}
