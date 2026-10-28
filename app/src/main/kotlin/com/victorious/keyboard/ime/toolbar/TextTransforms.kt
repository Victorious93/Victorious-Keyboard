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

package com.victorious.keyboard.ime.toolbar

import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Base64
import java.util.Locale
import java.util.UUID
import kotlin.random.Random
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/** A local text rewrite. Failure is a value, never a thrown exception and never a log of the input. */
sealed class TransformResult {
    data class Ok(val text: String) : TransformResult()
    data class Failed(val reason: String) : TransformResult()
}

enum class TextTransform {
    CAMEL, SNAKE, KEBAB, PASCAL,
    INDENT, OUTDENT, TOGGLE_COMMENT,
    REGEX_ESCAPE,
    BASE64_ENCODE, BASE64_DECODE,
    URL_ENCODE, URL_DECODE,
    HEX_ENCODE, HEX_DECODE,
    JSON_PRETTY,
    UUID, TIMESTAMP, RANDOM_HEX,
    MD5, SHA1, SHA256,
    CODE_FENCE,
    JSON_MINIFY,
    UPPER, LOWER, TITLE,
    TRIM_LINES, SORT_LINES, DEDUPE_LINES,
    ROT13,
    EXTRACT_URL, EXTRACT_EMAIL, EXTRACT_IP,
    JWT_DECODE,
    UNIX_TO_DATE, DATE_TO_UNIX,
    SHELL_ESCAPE, JSON_ESCAPE,
}

data class TextCounts(val characters: Int, val words: Int, val tokens: Int)

object TextTransforms {
    private val prettyJson = Json { prettyPrint = true }
    private val compactJson = Json { prettyPrint = false }
    private const val BASE64_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/="
    private const val BASE64URL_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
    private val isoSeconds: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneOffset.UTC)
    private val URL_PATTERN = Regex("""https?://[^\s<>"']+""", RegexOption.IGNORE_CASE)
    private val EMAIL_PATTERN = Regex("""[A-Za-z0-9._%+\-]+@[A-Za-z0-9.\-]+\.[A-Za-z]{2,}""")
    private val IPV4_PATTERN = Regex(
        """\b(?:(?:25[0-5]|2[0-4]\d|[01]?\d\d?)\.){3}(?:25[0-5]|2[0-4]\d|[01]?\d\d?)\b""",
    )
    private val IPV6_PATTERN = Regex(
        """(?i)(?<![0-9A-Fa-f:])(?:[0-9A-Fa-f]{1,4}:){2,7}[0-9A-Fa-f]{0,4}(?![0-9A-Fa-f:])""",
    )

    fun apply(
        op: TextTransform,
        input: String,
        language: String = "",
        nowMillis: Long = System.currentTimeMillis(),
        random: Random = Random.Default,
    ): TransformResult = try {
        when (op) {
            TextTransform.CAMEL -> TransformResult.Ok(recase(input, CaseStyle.CAMEL))
            TextTransform.SNAKE -> TransformResult.Ok(recase(input, CaseStyle.SNAKE))
            TextTransform.KEBAB -> TransformResult.Ok(recase(input, CaseStyle.KEBAB))
            TextTransform.PASCAL -> TransformResult.Ok(recase(input, CaseStyle.PASCAL))
            TextTransform.INDENT -> TransformResult.Ok(input.split('\n').joinToString("\n") { "  $it" })
            TextTransform.OUTDENT -> TransformResult.Ok(input.split('\n').joinToString("\n") { outdentLine(it) })
            TextTransform.TOGGLE_COMMENT -> TransformResult.Ok(toggleComment(input))
            TextTransform.REGEX_ESCAPE -> TransformResult.Ok(regexEscape(input))
            TextTransform.BASE64_ENCODE -> TransformResult.Ok(Base64.getEncoder().encodeToString(input.toByteArray(Charsets.UTF_8)))
            TextTransform.BASE64_DECODE -> decodeBase64(input)
            // The String-charset overloads: the Charset ones need API 33 and minSdk is 26.
            TextTransform.URL_ENCODE -> TransformResult.Ok(URLEncoder.encode(input, "UTF-8"))
            TextTransform.URL_DECODE -> TransformResult.Ok(URLDecoder.decode(input, "UTF-8"))
            TextTransform.HEX_ENCODE -> TransformResult.Ok(input.toByteArray(Charsets.UTF_8).toHex())
            TextTransform.HEX_DECODE -> decodeHex(input)
            TextTransform.JSON_PRETTY -> pretty(input)
            TextTransform.JSON_MINIFY -> minify(input)
            TextTransform.UPPER -> TransformResult.Ok(input.uppercase(Locale.ROOT))
            TextTransform.LOWER -> TransformResult.Ok(input.lowercase(Locale.ROOT))
            TextTransform.TITLE -> TransformResult.Ok(titleCase(input))
            TextTransform.TRIM_LINES -> TransformResult.Ok(input.split('\n').joinToString("\n") { it.trim() })
            TextTransform.SORT_LINES -> TransformResult.Ok(input.split('\n').sorted().joinToString("\n"))
            TextTransform.DEDUPE_LINES -> TransformResult.Ok(input.split('\n').distinct().joinToString("\n"))
            TextTransform.ROT13 -> TransformResult.Ok(rot13(input))
            TextTransform.EXTRACT_URL -> extract(input, ExtractKind.URL)
            TextTransform.EXTRACT_EMAIL -> extract(input, ExtractKind.EMAIL)
            TextTransform.EXTRACT_IP -> extract(input, ExtractKind.IP)
            TextTransform.JWT_DECODE -> jwtDecode(input)
            TextTransform.UNIX_TO_DATE -> unixToDate(input)
            TextTransform.DATE_TO_UNIX -> dateToUnix(input)
            TextTransform.SHELL_ESCAPE -> TransformResult.Ok(shellEscape(input))
            TextTransform.JSON_ESCAPE -> TransformResult.Ok(Json.encodeToString(String.serializer(), input))
            TextTransform.UUID -> TransformResult.Ok(UUID.randomUUID().toString())
            TextTransform.TIMESTAMP -> TransformResult.Ok(nowMillis.toString())
            TextTransform.RANDOM_HEX -> TransformResult.Ok(random.nextBytes(8).toHex())
            TextTransform.MD5 -> digest("MD5", input)
            TextTransform.SHA1 -> digest("SHA-1", input)
            TextTransform.SHA256 -> digest("SHA-256", input)
            TextTransform.CODE_FENCE -> TransformResult.Ok(fence(input, language))
        }
    } catch (_: Exception) {
        TransformResult.Failed("failed")
    }

    fun counts(text: String): TextCounts {
        val words = if (text.isBlank()) 0 else text.trim().split(Regex("\\s+")).size
        return TextCounts(characters = text.length, words = words, tokens = (text.length + 3) / 4)
    }

    /** Replaces `{{name}}` once. Values are not scanned again, so a value containing `{{name}}` stays literal. */
    fun fillPrompt(template: String, vars: Map<String, String>): String {
        val pattern = Regex("\\{\\{\\s*([A-Za-z0-9_]+)\\s*}}")
        return pattern.replace(template) { match ->
            vars[match.groupValues[1]] ?: match.value
        }
    }

    fun fence(text: String, language: String): String {
        val lang = language.filter { it.isLetterOrDigit() || it == '+' || it == '#' }.take(16)
        return "```$lang\n$text\n```"
    }

    private fun digest(algorithm: String, input: String): TransformResult {
        val bytes = MessageDigest.getInstance(algorithm).digest(input.toByteArray(Charsets.UTF_8))
        return TransformResult.Ok(bytes.toHex())
    }

    private fun decodeBase64(input: String): TransformResult {
        val cleaned = input.trim().replace(Regex("\\s+"), "")
        if (cleaned.isEmpty() || cleaned.length % 4 != 0) return TransformResult.Failed("bad base64")
        if (cleaned.any { it !in BASE64_ALPHABET }) return TransformResult.Failed("bad base64")
        return try {
            TransformResult.Ok(String(Base64.getDecoder().decode(cleaned), Charsets.UTF_8))
        } catch (_: IllegalArgumentException) {
            TransformResult.Failed("bad base64")
        }
    }

    private fun decodeHex(input: String): TransformResult {
        val cleaned = input.trim().removePrefix("0x")
        if (cleaned.isEmpty() || cleaned.length % 2 != 0) return TransformResult.Failed("bad hex")
        if (cleaned.any { it !in '0'..'9' && it !in 'a'..'f' && it !in 'A'..'F' }) return TransformResult.Failed("bad hex")
        val bytes = ByteArray(cleaned.length / 2) { index ->
            cleaned.substring(index * 2, index * 2 + 2).toInt(16).toByte()
        }
        return try {
            val text = Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(java.nio.ByteBuffer.wrap(bytes))
                .toString()
            TransformResult.Ok(text)
        } catch (_: CharacterCodingException) {
            TransformResult.Failed("bad hex")
        }
    }

    private fun pretty(input: String): TransformResult {
        val trimmed = input.trim()
        if (trimmed.isEmpty() || !jsonDelimitersBalanced(trimmed)) return TransformResult.Failed("invalid json")
        val element = try {
            Json.parseToJsonElement(trimmed)
        } catch (_: Exception) {
            return TransformResult.Failed("invalid json")
        }
        val text = prettyJson.encodeToString(JsonElement.serializer(), element)
        if (text.isEmpty()) return TransformResult.Failed("invalid json")
        return TransformResult.Ok(text)
    }

    /** `{` alone is not JSON. The parser in use accepts it, so the brackets are checked first. */
    private fun jsonDelimitersBalanced(input: String): Boolean {
        val stack = ArrayDeque<Char>()
        var inString = false
        var escape = false
        for (c in input) {
            if (inString) {
                when {
                    escape -> escape = false
                    c == '\\' -> escape = true
                    c == '"' -> inString = false
                }
            } else {
                when (c) {
                    '"' -> inString = true
                    '{', '[' -> stack.addLast(c)
                    '}' -> if (stack.removeLastOrNull() != '{') return false
                    ']' -> if (stack.removeLastOrNull() != '[') return false
                }
            }
        }
        return !inString && !escape && stack.isEmpty()
    }

    private enum class CaseStyle { CAMEL, SNAKE, KEBAB, PASCAL }

    private fun recase(input: String, style: CaseStyle): String {
        val parts = words(input)
        if (parts.isEmpty()) return ""
        return when (style) {
            CaseStyle.SNAKE -> parts.joinToString("_") { it.lowercase() }
            CaseStyle.KEBAB -> parts.joinToString("-") { it.lowercase() }
            CaseStyle.PASCAL -> parts.joinToString("") { capitalize(it) }
            CaseStyle.CAMEL -> parts.mapIndexed { index, part ->
                if (index == 0) part.lowercase() else capitalize(part)
            }.joinToString("")
        }
    }

    private fun words(input: String): List<String> {
        return input.split(Regex("[^\\p{L}\\p{M}\\p{N}]+"))
            .flatMap { piece -> piece.split(Regex("(?<=[\\p{Ll}\\p{N}])(?=\\p{Lu})|(?<=\\p{Lu})(?=\\p{Lu}\\p{Ll})")) }
            .filter { it.isNotEmpty() }
    }

    private fun capitalize(part: String): String =
        part.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }.let { head ->
            if (head.length <= 1) head else head.first() + head.substring(1).lowercase()
        }

    private fun outdentLine(line: String): String = when {
        line.startsWith("\t") -> line.substring(1)
        line.startsWith("  ") -> line.substring(2)
        line.startsWith(" ") -> line.substring(1)
        else -> line
    }

    private fun toggleComment(text: String): String {
        val lines = text.split('\n')
        val content = lines.filter { it.isNotBlank() }
        if (content.isEmpty()) return text
        val mark = when {
            content.all { it.trimStart().startsWith("//") } -> "//"
            content.all { it.trimStart().startsWith("# ") || it.trimStart() == "#" || it.trimStart().startsWith("#") && !it.trimStart().startsWith("#!") } ->
                if (content.all { it.trimStart().startsWith("#") }) "#" else null
            else -> null
        }
        if (mark != null && content.all { it.trimStart().startsWith(mark) }) {
            return lines.joinToString("\n") { line -> uncomment(line, mark) }
        }
        val comment = if (looksLikeShell(text)) "# " else "// "
        return lines.joinToString("\n") { line -> if (line.isBlank()) line else comment + line }
    }

    private fun uncomment(line: String, mark: String): String {
        val trimmed = line.trimStart()
        if (!trimmed.startsWith(mark)) return line
        val indent = line.substring(0, line.length - trimmed.length)
        var rest = trimmed.substring(mark.length)
        if (rest.startsWith(" ")) rest = rest.substring(1)
        return indent + rest
    }

    private fun looksLikeShell(text: String): Boolean {
        if (text.contains('{') || text.contains("fun ") || text.contains(';')) return false
        val trimmed = text.trimStart()
        return trimmed.startsWith("$") || trimmed.startsWith("#!") || trimmed.startsWith("apt ") || trimmed.startsWith("pkg ")
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private fun minify(input: String): TransformResult {
        val trimmed = input.trim()
        if (trimmed.isEmpty() || !jsonDelimitersBalanced(trimmed)) return TransformResult.Failed("invalid json")
        val element = try {
            Json.parseToJsonElement(trimmed)
        } catch (_: Exception) {
            return TransformResult.Failed("invalid json")
        }
        val text = compactJson.encodeToString(JsonElement.serializer(), element)
        if (text.isEmpty()) return TransformResult.Failed("invalid json")
        return TransformResult.Ok(text)
    }

    private fun titleCase(input: String): String {
        val parts = words(input)
        if (parts.isEmpty()) return ""
        return parts.joinToString(" ") { capitalize(it) }
    }

    private fun rot13(input: String): String = buildString(input.length) {
        for (ch in input) {
            append(
                when (ch) {
                    in 'a'..'z' -> 'a' + (ch - 'a' + 13) % 26
                    in 'A'..'Z' -> 'A' + (ch - 'A' + 13) % 26
                    else -> ch
                },
            )
        }
    }

    private enum class ExtractKind { URL, EMAIL, IP }

    private fun extract(input: String, kind: ExtractKind): TransformResult {
        val hits = when (kind) {
            ExtractKind.URL -> URL_PATTERN.findAll(input).map { match ->
                match.value.trimEnd('.', ',', ';', ')', ']')
            }.toList()
            ExtractKind.EMAIL -> EMAIL_PATTERN.findAll(input).map { it.value }.toList()
            ExtractKind.IP -> {
                val v4 = IPV4_PATTERN.findAll(input).map { it.value }
                val v6 = IPV6_PATTERN.findAll(input).map { it.value }.filter { candidate ->
                    candidate.count { it == ':' } >= 2
                }
                (v4 + v6).toList()
            }
        }.distinct()
        if (hits.isEmpty()) return TransformResult.Failed("none")
        return TransformResult.Ok(hits.joinToString("\n"))
    }

    /** Header and payload only. The signature is not decoded and is not checked. */
    private fun jwtDecode(input: String): TransformResult {
        val parts = input.trim().split('.')
        if (parts.size !in 2..3) return TransformResult.Failed("bad jwt")
        val header = decodeJwtPart(parts[0]) ?: return TransformResult.Failed("bad jwt")
        val payload = decodeJwtPart(parts[1]) ?: return TransformResult.Failed("bad jwt")
        return TransformResult.Ok("header\n${presentJson(header)}\n\npayload\n${presentJson(payload)}")
    }

    private fun presentJson(text: String): String {
        val parsed = pretty(text)
        return if (parsed is TransformResult.Ok) parsed.text else text
    }

    private fun decodeJwtPart(part: String): String? {
        val cleaned = part.trim()
        if (cleaned.isEmpty() || cleaned.any { it !in BASE64URL_ALPHABET }) return null
        var padded = cleaned.replace('-', '+').replace('_', '/')
        padded += when (padded.length % 4) {
            0 -> ""
            2 -> "=="
            3 -> "="
            else -> return null
        }
        return try {
            String(Base64.getDecoder().decode(padded), Charsets.UTF_8)
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    private fun unixToDate(input: String): TransformResult {
        val value = input.trim().toLongOrNull() ?: return TransformResult.Failed("bad time")
        val millis = if (kotlin.math.abs(value) >= 100_000_000_000L) value else value * 1000
        return try {
            TransformResult.Ok(isoSeconds.format(Instant.ofEpochMilli(millis)))
        } catch (_: Exception) {
            TransformResult.Failed("bad time")
        }
    }

    private fun dateToUnix(input: String): TransformResult {
        val raw = input.trim()
        if (raw.isEmpty()) return TransformResult.Failed("bad time")
        val instant = parseInstant(raw) ?: return TransformResult.Failed("bad time")
        return TransformResult.Ok(instant.epochSecond.toString())
    }

    private fun parseInstant(raw: String): Instant? {
        runCatching { Instant.parse(raw) }.getOrNull()?.let { return it }
        val formats = listOf(
            DateTimeFormatter.ISO_LOCAL_DATE_TIME,
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
        )
        for (format in formats) {
            runCatching { LocalDateTime.parse(raw, format).toInstant(ZoneOffset.UTC) }.getOrNull()?.let { return it }
        }
        return runCatching {
            LocalDate.parse(raw, DateTimeFormatter.ISO_LOCAL_DATE).atStartOfDay().toInstant(ZoneOffset.UTC)
        }.getOrNull()
    }

    private fun shellEscape(input: String): String = "'" + input.replace("'", "'\\''") + "'"

    private fun regexEscape(input: String): String {
        val special = "\\.^$|?*+()[]{}"
        return buildString(input.length * 2) {
            for (ch in input) {
                if (ch in special) append('\\')
                append(ch)
            }
        }
    }
}
