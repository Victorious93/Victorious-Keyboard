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

package dev.patrickgold.florisboard.ime.toolbar

/**
 * The smartbar's pages. Names are stored in the toolbar file. Add names only at the end.
 */
enum class ToolbarPage(val title: String) {
    TERMINAL("Terminal"),
    CODE("Code"),
    AI("AI"),
    EDIT("Edit"),
    CLIPBOARD("Clipboard"),
    PRIVACY("Privacy"),
}

/**
 * One toolbar button. Names are stored when the user hides or reorders a button. Do not rename them.
 */
enum class ToolbarActionId {
    CTRL, ALT, ESC, TAB, UP, DOWN, LEFT, RIGHT, HOME, END, PGUP, PGDN,
    CTRL_C, CTRL_D, CTRL_Z, CTRL_L, CTRL_R,
    PIPE, AMP, SEMI, GT, LT, TILDE, SLASH, BACKSLASH, BACKTICK, DOLLAR, STAR,
    BRACE, BRACKET, PAREN, QUOTE, DASHDASH, ANDAND, OROR,
    CAMEL, SNAKE, KEBAB, PASCAL, INDENT, OUTDENT, COMMENT,
    REGEX, B64E, B64D, URLE, URLD, HEXE, HEXD, JSON, UUID, TIME, RAND,
    MD5, SHA1, SHA256, PAIR,
    EXPLAIN, FIX, BASH, SUMMARIZE, FENCE, COUNT, PASTE_CODE,
    SEL_WORD, SEL_LINE, SEL_ALL, UNDO, REDO, CUT, COPY, PASTE,
    OPEN_CLIPBOARD, OPEN_VAULT, OPEN_SNIPPETS, EDIT_CLIP, PINNED, ZIP,
    INCOGNITO, LEARNING, BOTTOM_GAP, ONE_HAND, THEME, VOICE, EMOJI, SETTINGS, DEVROW,
    AI_REWRITE, AI_GRAMMAR, AI_CONTINUE, AI_SUMMARIZE, AI_TRANSLATE, AI_SHELL, AI_ASK,
}

data class ToolbarAction(val id: ToolbarActionId, val page: ToolbarPage, val label: String)

data class SavedCommand(
    val id: String,
    val label: String,
    val text: String,
    val tag: String,
)

object ToolbarCatalog {
    val actions: List<ToolbarAction> = listOf(
        act(ToolbarActionId.CTRL, ToolbarPage.TERMINAL, "Ctrl"),
        act(ToolbarActionId.ALT, ToolbarPage.TERMINAL, "Alt"),
        act(ToolbarActionId.ESC, ToolbarPage.TERMINAL, "Esc"),
        act(ToolbarActionId.TAB, ToolbarPage.TERMINAL, "Tab"),
        act(ToolbarActionId.UP, ToolbarPage.TERMINAL, "Up"),
        act(ToolbarActionId.DOWN, ToolbarPage.TERMINAL, "Dn"),
        act(ToolbarActionId.LEFT, ToolbarPage.TERMINAL, "Lt"),
        act(ToolbarActionId.RIGHT, ToolbarPage.TERMINAL, "Rt"),
        act(ToolbarActionId.HOME, ToolbarPage.TERMINAL, "Home"),
        act(ToolbarActionId.END, ToolbarPage.TERMINAL, "End"),
        act(ToolbarActionId.PGUP, ToolbarPage.TERMINAL, "PgUp"),
        act(ToolbarActionId.PGDN, ToolbarPage.TERMINAL, "PgDn"),
        act(ToolbarActionId.CTRL_C, ToolbarPage.TERMINAL, "C-c"),
        act(ToolbarActionId.CTRL_D, ToolbarPage.TERMINAL, "C-d"),
        act(ToolbarActionId.CTRL_Z, ToolbarPage.TERMINAL, "C-z"),
        act(ToolbarActionId.CTRL_L, ToolbarPage.TERMINAL, "C-l"),
        act(ToolbarActionId.CTRL_R, ToolbarPage.TERMINAL, "C-r"),
        act(ToolbarActionId.PIPE, ToolbarPage.TERMINAL, "|"),
        act(ToolbarActionId.AMP, ToolbarPage.TERMINAL, "&"),
        act(ToolbarActionId.SEMI, ToolbarPage.TERMINAL, ";"),
        act(ToolbarActionId.GT, ToolbarPage.TERMINAL, ">"),
        act(ToolbarActionId.LT, ToolbarPage.TERMINAL, "<"),
        act(ToolbarActionId.TILDE, ToolbarPage.TERMINAL, "~"),
        act(ToolbarActionId.SLASH, ToolbarPage.TERMINAL, "/"),
        act(ToolbarActionId.BACKSLASH, ToolbarPage.TERMINAL, "\\"),
        act(ToolbarActionId.BACKTICK, ToolbarPage.TERMINAL, "`"),
        act(ToolbarActionId.DOLLAR, ToolbarPage.TERMINAL, "$"),
        act(ToolbarActionId.STAR, ToolbarPage.TERMINAL, "*"),
        act(ToolbarActionId.BRACE, ToolbarPage.TERMINAL, "{}"),
        act(ToolbarActionId.BRACKET, ToolbarPage.TERMINAL, "[]"),
        act(ToolbarActionId.PAREN, ToolbarPage.TERMINAL, "()"),
        act(ToolbarActionId.QUOTE, ToolbarPage.TERMINAL, "\"'"),
        act(ToolbarActionId.DASHDASH, ToolbarPage.TERMINAL, "--"),
        act(ToolbarActionId.ANDAND, ToolbarPage.TERMINAL, "&&"),
        act(ToolbarActionId.OROR, ToolbarPage.TERMINAL, "||"),
        act(ToolbarActionId.CAMEL, ToolbarPage.CODE, "camel"),
        act(ToolbarActionId.SNAKE, ToolbarPage.CODE, "snake"),
        act(ToolbarActionId.KEBAB, ToolbarPage.CODE, "kebab"),
        act(ToolbarActionId.PASCAL, ToolbarPage.CODE, "Pascal"),
        act(ToolbarActionId.INDENT, ToolbarPage.CODE, "Indent"),
        act(ToolbarActionId.OUTDENT, ToolbarPage.CODE, "Outdent"),
        act(ToolbarActionId.COMMENT, ToolbarPage.CODE, "Comment"),
        act(ToolbarActionId.REGEX, ToolbarPage.CODE, "Regex"),
        act(ToolbarActionId.B64E, ToolbarPage.CODE, "B64+"),
        act(ToolbarActionId.B64D, ToolbarPage.CODE, "B64-"),
        act(ToolbarActionId.URLE, ToolbarPage.CODE, "URL+"),
        act(ToolbarActionId.URLD, ToolbarPage.CODE, "URL-"),
        act(ToolbarActionId.HEXE, ToolbarPage.CODE, "Hex+"),
        act(ToolbarActionId.HEXD, ToolbarPage.CODE, "Hex-"),
        act(ToolbarActionId.JSON, ToolbarPage.CODE, "JSON"),
        act(ToolbarActionId.UUID, ToolbarPage.CODE, "UUID"),
        act(ToolbarActionId.TIME, ToolbarPage.CODE, "Time"),
        act(ToolbarActionId.RAND, ToolbarPage.CODE, "Hash"),
        act(ToolbarActionId.MD5, ToolbarPage.CODE, "MD5"),
        act(ToolbarActionId.SHA1, ToolbarPage.CODE, "SHA1"),
        act(ToolbarActionId.SHA256, ToolbarPage.CODE, "SHA256"),
        act(ToolbarActionId.PAIR, ToolbarPage.CODE, "Pair"),
        act(ToolbarActionId.EXPLAIN, ToolbarPage.AI, "Explain"),
        act(ToolbarActionId.FIX, ToolbarPage.AI, "Fix"),
        act(ToolbarActionId.BASH, ToolbarPage.AI, "Bash"),
        act(ToolbarActionId.SUMMARIZE, ToolbarPage.AI, "Summary"),
        act(ToolbarActionId.FENCE, ToolbarPage.AI, "```"),
        act(ToolbarActionId.COUNT, ToolbarPage.AI, "Count"),
        act(ToolbarActionId.PASTE_CODE, ToolbarPage.AI, "Clip```"),
        act(ToolbarActionId.AI_REWRITE, ToolbarPage.AI, "Rewrite"),
        act(ToolbarActionId.AI_GRAMMAR, ToolbarPage.AI, "Grammar"),
        act(ToolbarActionId.AI_CONTINUE, ToolbarPage.AI, "Continue"),
        act(ToolbarActionId.AI_SUMMARIZE, ToolbarPage.AI, "Brief"),
        act(ToolbarActionId.AI_TRANSLATE, ToolbarPage.AI, "Translate"),
        act(ToolbarActionId.AI_SHELL, ToolbarPage.AI, "Shell"),
        act(ToolbarActionId.AI_ASK, ToolbarPage.AI, "Ask"),
        act(ToolbarActionId.SEL_WORD, ToolbarPage.EDIT, "Word"),
        act(ToolbarActionId.SEL_LINE, ToolbarPage.EDIT, "Line"),
        act(ToolbarActionId.SEL_ALL, ToolbarPage.EDIT, "All"),
        act(ToolbarActionId.UNDO, ToolbarPage.EDIT, "Undo"),
        act(ToolbarActionId.REDO, ToolbarPage.EDIT, "Redo"),
        act(ToolbarActionId.CUT, ToolbarPage.EDIT, "Cut"),
        act(ToolbarActionId.COPY, ToolbarPage.EDIT, "Copy"),
        act(ToolbarActionId.PASTE, ToolbarPage.EDIT, "Paste"),
        act(ToolbarActionId.LEFT, ToolbarPage.EDIT, "Lt"),
        act(ToolbarActionId.RIGHT, ToolbarPage.EDIT, "Rt"),
        act(ToolbarActionId.UP, ToolbarPage.EDIT, "Up"),
        act(ToolbarActionId.DOWN, ToolbarPage.EDIT, "Dn"),
        act(ToolbarActionId.HOME, ToolbarPage.EDIT, "Home"),
        act(ToolbarActionId.END, ToolbarPage.EDIT, "End"),
        act(ToolbarActionId.OPEN_CLIPBOARD, ToolbarPage.CLIPBOARD, "Clips"),
        act(ToolbarActionId.OPEN_VAULT, ToolbarPage.CLIPBOARD, "Vault"),
        act(ToolbarActionId.OPEN_SNIPPETS, ToolbarPage.CLIPBOARD, "Snips"),
        act(ToolbarActionId.EDIT_CLIP, ToolbarPage.CLIPBOARD, "Edit"),
        act(ToolbarActionId.PINNED, ToolbarPage.CLIPBOARD, "Pin"),
        act(ToolbarActionId.ZIP, ToolbarPage.CLIPBOARD, "Zip"),
        act(ToolbarActionId.INCOGNITO, ToolbarPage.PRIVACY, "Incog"),
        act(ToolbarActionId.LEARNING, ToolbarPage.PRIVACY, "Learn"),
        act(ToolbarActionId.BOTTOM_GAP, ToolbarPage.PRIVACY, "Gap"),
        act(ToolbarActionId.ONE_HAND, ToolbarPage.PRIVACY, "1-hand"),
        act(ToolbarActionId.THEME, ToolbarPage.PRIVACY, "Theme"),
        act(ToolbarActionId.VOICE, ToolbarPage.PRIVACY, "Voice"),
        act(ToolbarActionId.EMOJI, ToolbarPage.PRIVACY, "Emoji"),
        act(ToolbarActionId.SETTINGS, ToolbarPage.PRIVACY, "Settings"),
        act(ToolbarActionId.DEVROW, ToolbarPage.PRIVACY, "Dev row"),
    )

    /** Everyday command names the user can insert. Nothing here is run, and none of it is an attack recipe. */
    val commands: List<SavedCommand> = listOf(
        cmd("pkg-update", "pkg update", "pkg update", "pkg"),
        cmd("pkg-upgrade", "pkg upgrade", "pkg upgrade", "pkg"),
        cmd("apt-update", "apt update", "apt update", "apt"),
        cmd("git-status", "git status", "git status", "git"),
        cmd("git-diff", "git diff", "git diff", "git"),
        cmd("git-log", "git log", "git log --oneline -20", "git"),
        cmd("ssh", "ssh", "ssh ", "ssh"),
        cmd("adb", "adb devices", "adb devices", "adb"),
        cmd("systemctl", "systemctl", "systemctl status ", "systemctl"),
        cmd("docker", "docker ps", "docker ps", "docker"),
        cmd("ls", "ls", "ls -la", "shell"),
        cmd("nmap", "nmap", "nmap ", "tools"),
        cmd("msfconsole", "msfconsole", "msfconsole ", "tools"),
        cmd("hydra", "hydra", "hydra ", "tools"),
        cmd("aircrack", "aircrack-ng", "aircrack-ng ", "tools"),
    )

    val prompts: List<PromptTemplate> = listOf(
        PromptTemplate("explain", "Explain this code", "Explain this code:\n{{selection}}"),
        PromptTemplate("fix", "Fix this error", "Fix this error:\n{{selection}}"),
        PromptTemplate("bash", "Bash one-liner", "Write a bash one-liner to {{selection}}"),
        PromptTemplate("summarize", "Summarize", "Summarize:\n{{selection}}"),
    )

    fun visible(page: ToolbarPage, hidden: Set<String>, order: List<String>): List<ToolbarAction> {
        val all = actions.filter { it.page == page && it.id.name !in hidden }
        if (order.isEmpty()) return all
        val byId = all.associateBy { it.id.name }
        val ordered = order.mapNotNull { byId[it] }
        val rest = all.filter { it.id.name !in order.toSet() }
        return ordered + rest
    }

    fun visibleCommands(hidden: Set<String>, extra: List<SavedCommand>): List<SavedCommand> {
        return (commands + extra).filter { commandId(it.id) !in hidden && it.text.isNotBlank() }
    }

    fun commandId(id: String) = "cmd:$id"

    fun symbol(id: ToolbarActionId): String? = when (id) {
        ToolbarActionId.PIPE -> "|"
        ToolbarActionId.AMP -> "&"
        ToolbarActionId.SEMI -> ";"
        ToolbarActionId.GT -> ">"
        ToolbarActionId.LT -> "<"
        ToolbarActionId.TILDE -> "~"
        ToolbarActionId.SLASH -> "/"
        ToolbarActionId.BACKSLASH -> "\\"
        ToolbarActionId.BACKTICK -> "`"
        ToolbarActionId.DOLLAR -> "$"
        ToolbarActionId.STAR -> "*"
        ToolbarActionId.BRACE -> "{}"
        ToolbarActionId.BRACKET -> "[]"
        ToolbarActionId.PAREN -> "()"
        ToolbarActionId.QUOTE -> "\""
        ToolbarActionId.DASHDASH -> "--"
        ToolbarActionId.ANDAND -> "&&"
        ToolbarActionId.OROR -> "||"
        else -> null
    }

    fun transform(id: ToolbarActionId): TextTransform? = when (id) {
        ToolbarActionId.CAMEL -> TextTransform.CAMEL
        ToolbarActionId.SNAKE -> TextTransform.SNAKE
        ToolbarActionId.KEBAB -> TextTransform.KEBAB
        ToolbarActionId.PASCAL -> TextTransform.PASCAL
        ToolbarActionId.INDENT -> TextTransform.INDENT
        ToolbarActionId.OUTDENT -> TextTransform.OUTDENT
        ToolbarActionId.COMMENT -> TextTransform.TOGGLE_COMMENT
        ToolbarActionId.REGEX -> TextTransform.REGEX_ESCAPE
        ToolbarActionId.B64E -> TextTransform.BASE64_ENCODE
        ToolbarActionId.B64D -> TextTransform.BASE64_DECODE
        ToolbarActionId.URLE -> TextTransform.URL_ENCODE
        ToolbarActionId.URLD -> TextTransform.URL_DECODE
        ToolbarActionId.HEXE -> TextTransform.HEX_ENCODE
        ToolbarActionId.HEXD -> TextTransform.HEX_DECODE
        ToolbarActionId.JSON -> TextTransform.JSON_PRETTY
        ToolbarActionId.UUID -> TextTransform.UUID
        ToolbarActionId.TIME -> TextTransform.TIMESTAMP
        ToolbarActionId.RAND -> TextTransform.RANDOM_HEX
        ToolbarActionId.MD5 -> TextTransform.MD5
        ToolbarActionId.SHA1 -> TextTransform.SHA1
        ToolbarActionId.SHA256 -> TextTransform.SHA256
        ToolbarActionId.FENCE -> TextTransform.CODE_FENCE
        else -> null
    }

    fun prompt(id: ToolbarActionId): PromptTemplate? = when (id) {
        ToolbarActionId.EXPLAIN -> prompts[0]
        ToolbarActionId.FIX -> prompts[1]
        ToolbarActionId.BASH -> prompts[2]
        ToolbarActionId.SUMMARIZE -> prompts[3]
        else -> null
    }

    private fun act(id: ToolbarActionId, page: ToolbarPage, label: String) = ToolbarAction(id, page, label)

    private fun cmd(id: String, label: String, text: String, tag: String) = SavedCommand(id, label, text, tag)
}

data class PromptTemplate(val id: String, val title: String, val body: String)

object ShellHistoryPolicy {
    const val CAP = 30
    const val MAX_COMMAND = 500

    fun shouldRecord(
        enabled: Boolean,
        incognito: Boolean,
        passwordLike: Boolean,
        vaultHold: Boolean,
        editorOpen: Boolean,
    ): Boolean = enabled && !incognito && !passwordLike && !vaultHold && !editorOpen

    fun commandFrom(textBeforeCursor: String): String? {
        val line = textBeforeCursor.substringAfterLast('\n').trim()
        if (line.isEmpty() || line.length > MAX_COMMAND) return null
        if (line.any { it.isISOControl() && it != '\t' }) return null
        return line
    }

    fun remember(history: List<String>, command: String): List<String> {
        if (command.isBlank() || command.length > MAX_COMMAND) return history
        return (listOf(command) + history.filter { it != command }).take(CAP)
    }
}

object SavedCommandRules {
    const val MAX_COMMANDS = 40
    const val MAX_LABEL = 40
    const val MAX_TEXT = 500
    const val MAX_TAG = 24

    fun normalize(label: String, text: String, tag: String, id: String = ""): SavedCommand? {
        val cleanLabel = label.trim().take(MAX_LABEL)
        val cleanText = text.trim().take(MAX_TEXT)
        val cleanTag = tag.trim().take(MAX_TAG).ifEmpty { "custom" }
        if (cleanLabel.isEmpty() || cleanText.isEmpty()) return null
        if (cleanText.any { it.isISOControl() && it != '\n' && it != '\t' }) return null
        val cleanId = id.ifBlank { "c" + cleanLabel.hashCode().toUInt().toString(16) }
        if (cleanId.any { !it.isLetterOrDigit() && it != '-' && it != '_' }) return null
        return SavedCommand(cleanId, cleanLabel, cleanText, cleanTag)
    }
}
