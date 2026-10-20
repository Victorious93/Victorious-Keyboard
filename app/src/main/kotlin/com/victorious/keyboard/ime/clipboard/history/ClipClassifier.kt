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

package com.victorious.keyboard.ime.clipboard.history

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

/**
 * Heuristic, fully local categorisation. These are best-effort guesses used to pre-fill a category the user can
 * change; they are not security decisions. No text leaves the device.
 */
object ClipClassifier {
    private const val MAX_JSON_PROBE_CHARS = 200_000

    private val urlLine = Regex("^(https?|ftp)://[^\\s]+$", RegexOption.IGNORE_CASE)

    private val shellCommands = setOf(
        "git", "adb", "fastboot", "ls", "cd", "cat", "grep", "find", "sed", "awk", "curl", "wget", "ssh", "scp",
        "rsync", "sudo", "apt", "apt-get", "pkg", "dnf", "yum", "pacman", "docker", "podman", "systemctl",
        "journalctl", "chmod", "chown", "mkdir", "rm", "cp", "mv", "tar", "unzip", "pip", "pip3", "npm", "npx",
        "yarn", "pnpm", "python", "python3", "node", "java", "gradle", "./gradlew", "ollama", "export", "echo",
        "df", "free", "top", "htop", "ps", "kill", "ping", "ip", "ss", "tmux", "vim", "nano", "make", "cargo",
    )

    private val codeMarkers = listOf(
        Regex("\\bfun\\s+\\w+\\s*\\("), Regex("\\bdef\\s+\\w+\\s*\\("), Regex("\\bclass\\s+\\w+"),
        Regex("\\bfunction\\s+\\w*\\s*\\("), Regex("=>"), Regex("\\bimport\\s+[\\w.{}*]+"),
        Regex("\\breturn\\b"), Regex("\\b(val|var|let|const)\\s+\\w+\\s*="), Regex("\\bpublic\\s+(static\\s+)?\\w+"),
        Regex("#include\\s*<"), Regex("\\bSELECT\\b.+\\bFROM\\b", RegexOption.IGNORE_CASE),
    )

    private val promptStarts = listOf(
        "you are ", "act as ", "as a ", "write a ", "write an ", "explain ", "summarize ", "summarise ",
        "generate ", "refactor ", "review this", "fix this", "create a ", "create an ", "translate ",
        "convert this", "give me ", "help me ", "can you ", "please ",
    )

    fun classify(text: String): ClipCategory {
        val t = text.trim()
        if (t.isEmpty()) return ClipCategory.GENERAL
        return when {
            isUrl(t) -> ClipCategory.URL
            isJson(t) -> ClipCategory.JSON
            isShell(t) -> ClipCategory.SHELL
            isMarkdown(t) -> ClipCategory.MARKDOWN
            isCode(t) -> ClipCategory.CODE
            isPrompt(t) -> ClipCategory.AI_PROMPT
            else -> ClipCategory.GENERAL
        }
    }

    private fun isUrl(t: String): Boolean {
        val lines = t.lines().map { it.trim() }.filter { it.isNotEmpty() }
        return lines.isNotEmpty() && lines.size <= 20 && lines.all { urlLine.matches(it) }
    }

    private fun isJson(t: String): Boolean {
        if (t.length > MAX_JSON_PROBE_CHARS) return false
        if (!(t.startsWith("{") && t.endsWith("}")) && !(t.startsWith("[") && t.endsWith("]"))) return false
        return try {
            val el = Json.parseToJsonElement(t)
            el is JsonObject || el is JsonArray
        } catch (_: Exception) {
            false
        }
    }

    private fun isShell(t: String): Boolean {
        if (t.startsWith("#!/")) return true
        val lines = t.lines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
        if (lines.isEmpty() || lines.size > 30) return false
        return lines.all { line ->
            val l = line.removePrefix("$ ").removePrefix("# ")
            val first = l.substringBefore(' ')
            first in shellCommands && !l.endsWith("{")
        }
    }

    private fun isMarkdown(t: String): Boolean {
        var score = 0
        if (Regex("(?m)^#{1,6}\\s+\\S").containsMatchIn(t)) score++
        if (t.contains("```")) score++
        if (Regex("\\*\\*[^*\\n]+\\*\\*").containsMatchIn(t)) score++
        if (Regex("\\[[^\\]\\n]+]\\([^)\\n]+\\)").containsMatchIn(t)) score++
        if (Regex("(?m)^\\s*([-*]|\\d+\\.)\\s+\\S").containsMatchIn(t)) score++
        return score >= 2
    }

    private fun isCode(t: String): Boolean {
        if (t.contains("```")) return true
        val markers = codeMarkers.count { it.containsMatchIn(t) }
        val punct = t.count { it == '{' || it == '}' || it == ';' || it == '(' || it == ')' }
        return markers >= 2 || (markers >= 1 && punct >= 4 && t.contains('\n'))
    }

    private fun isPrompt(t: String): Boolean {
        if (t.length < 20) return false
        val lower = t.lowercase()
        return promptStarts.any { lower.startsWith(it) }
    }
}
