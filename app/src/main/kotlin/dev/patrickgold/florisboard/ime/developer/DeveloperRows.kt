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

package dev.patrickgold.florisboard.ime.developer

import dev.patrickgold.florisboard.ime.terminal.SpecialKey
import dev.patrickgold.florisboard.ime.terminal.TerminalModifier

/** What a developer-row key does. Pure data; the Android shell turns it into input. */
sealed interface DevKeyAction {
    /** Insert [text], then move the cursor [cursorBack] characters left (0 = leave it after the text). */
    data class Insert(val text: String, val cursorBack: Int = 0) : DevKeyAction

    /** A terminal key (Esc, Tab, arrows, …) sent as a real key event. */
    data class Special(val key: SpecialKey) : DevKeyAction

    /** A sticky/lockable modifier key, see `ModifierStateMachine`. */
    data class Modifier(val modifier: TerminalModifier) : DevKeyAction

    /**
     * A saved sequence. Text is inserted. Enter and Ctrl plus a letter are real key events.
     * The keyboard does not run the text as a command.
     */
    data class Play(val steps: List<MacroStep>) : DevKeyAction
}

/** One step of a macro key. Persisted by [TerminalRowLayout], not by enum name. */
sealed interface MacroStep {
    data class Text(val text: String) : MacroStep
    data object Enter : MacroStep
    data class Ctrl(val letter: Char) : MacroStep
}

data class DevKey(val label: String, val action: DevKeyAction, val description: String = label)

enum class LanguageProfile(val id: String, val displayName: String) {
    SHELL("shell", "Shell"),
    PYTHON("python", "Python"),
    JS_TS("js_ts", "JavaScript / TypeScript"),
    KOTLIN_JAVA("kotlin_java", "Kotlin / Java"),
    JSON("json", "JSON"),
    YAML("yaml", "YAML"),
    MARKDOWN("markdown", "Markdown");

    companion object {
        fun fromId(id: String?): LanguageProfile? = entries.firstOrNull { it.id == id }
    }
}

/**
 * Key rows for developer / terminal mode.
 *
 * * [terminalRow]: always the same, so muscle memory carries across profiles.
 * * [symbolRow]: the symbols a language needs most, behind the number/symbol layer on a normal keyboard. Pair keys
 *   (`()`, `{}`, …) are explicit keys — nothing is auto-closed behind the user's back.
 *
 * No key here executes anything; they only produce key events or text.
 */
object DeveloperRows {
    private fun ins(text: String, back: Int = 0, desc: String = text) = DevKey(text, DevKeyAction.Insert(text, back), desc)
    private fun pair(open: String, close: String, desc: String) = DevKey(open + close, DevKeyAction.Insert(open + close, close.length), desc)
    private fun special(label: String, key: SpecialKey, desc: String = label) = DevKey(label, DevKeyAction.Special(key), desc)

    /** Esc, Tab, Ctrl, Alt. */
    val modifierRow: List<DevKey> = listOf(
        special("Esc", SpecialKey.ESCAPE, "Escape"),
        special("Tab", SpecialKey.TAB),
        DevKey("Ctrl", DevKeyAction.Modifier(TerminalModifier.CTRL), "Control (tap: next key, double tap: lock)"),
        DevKey("Alt", DevKeyAction.Modifier(TerminalModifier.ALT), "Alt (tap: next key, double tap: lock)"),
    )

    /** Arrows, Home, End, PgUp, PgDn. */
    val navigationRow: List<DevKey> = listOf(
        special("←", SpecialKey.LEFT, "Left"),
        special("↓", SpecialKey.DOWN, "Down"),
        special("↑", SpecialKey.UP, "Up"),
        special("→", SpecialKey.RIGHT, "Right"),
        special("Home", SpecialKey.HOME),
        special("End", SpecialKey.END),
        special("PgUp", SpecialKey.PAGE_UP, "Page up"),
        special("PgDn", SpecialKey.PAGE_DOWN, "Page down"),
    )

    /** Both groups, in the original order. */
    val terminalRow: List<DevKey> = modifierRow + navigationRow

    fun symbolRow(profile: LanguageProfile): List<DevKey> = when (profile) {
        LanguageProfile.SHELL -> listOf(
            ins("|"), ins("&"), ins(";"), ins("$"), ins(">"), ins("<"), ins("*"), ins("~"), ins("/"), ins("\\"),
            ins("-"), ins("_"), ins("!"), ins("#"), pair("\"", "\"", "Double quotes"), pair("'", "'", "Single quotes"),
            ins("`"),
        )
        LanguageProfile.PYTHON -> listOf(
            ins(":"), ins("_"), pair("(", ")", "Parentheses"), pair("[", "]", "Brackets"), pair("{", "}", "Braces"),
            ins("="), ins("#"), pair("\"", "\"", "Double quotes"), pair("'", "'", "Single quotes"), ins("*"), ins("->"),
            ins("."), ins(","), ins("@"), ins("\\"),
        )
        LanguageProfile.JS_TS -> listOf(
            ins("=>"), ins(";"), ins(":"), pair("(", ")", "Parentheses"), pair("[", "]", "Brackets"),
            pair("{", "}", "Braces"), pair("\"", "\"", "Double quotes"), pair("'", "'", "Single quotes"),
            pair("`", "`", "Template literal"), ins("\${", 0, "Template placeholder start"), ins("==="), ins("!=="),
            ins("&&"), ins("||"), ins("?."), ins("??"), ins("<"), ins(">"),
        )
        LanguageProfile.KOTLIN_JAVA -> listOf(
            ins(";"), ins(":"), pair("(", ")", "Parentheses"), pair("[", "]", "Brackets"), pair("{", "}", "Braces"),
            pair("\"", "\"", "Double quotes"), ins("->"), ins("::"), ins("?."), ins("?:"), ins("!!"), ins("&&"),
            ins("||"), ins("<"), ins(">"), ins("@"), ins("\$"),
        )
        LanguageProfile.JSON -> listOf(
            pair("{", "}", "Braces"), pair("[", "]", "Brackets"), pair("\"", "\"", "Double quotes"), ins(":"), ins(","),
            ins("true"), ins("false"), ins("null"), ins("-"), ins("."),
        )
        LanguageProfile.YAML -> listOf(
            ins(":"), ins("- ", 0, "List item"), ins("#"), ins("|"), ins(">"), pair("\"", "\"", "Double quotes"),
            pair("'", "'", "Single quotes"), pair("[", "]", "Brackets"), pair("{", "}", "Braces"), ins("&"), ins("*"),
            ins("!"),
        )
        LanguageProfile.MARKDOWN -> listOf(
            ins("#"), ins("*"), ins("_"), ins("-"), ins(">"), ins("`"), pair("[", "]", "Brackets"),
            pair("(", ")", "Parentheses"), ins("```\n\n```", 4, "Code block"), ins("!"), ins("|"),
        )
    }

    /** The full row as shown: terminal keys first (if enabled), then the profile's symbols. */
    fun row(profile: LanguageProfile, includeTerminalKeys: Boolean): List<DevKey> =
        row(profile, RowOptions(terminalKeys = includeTerminalKeys, navigationKeys = includeTerminalKeys))

    /**
     * The row for [options]: Esc/Tab/Ctrl/Alt, then the navigation keys, then symbols. Custom keys are appended to the
     * profile's symbols, or replace them when [CustomSymbolsMode.REPLACE] is chosen and there is at least one custom key
     * (an empty custom list never blanks the row by accident).
     */
    fun row(profile: LanguageProfile, options: RowOptions): List<DevKey> {
        val symbols = if (options.customMode == CustomSymbolsMode.REPLACE && options.custom.isNotEmpty()) {
            options.custom
        } else {
            (if (options.symbols) symbolRow(profile) else emptyList()) + options.custom
        }
        return (if (options.terminalKeys) modifierRow else emptyList()) +
            (if (options.navigationKeys) navigationRow else emptyList()) + symbols
    }
}

enum class CustomSymbolsMode { APPEND, REPLACE }

data class RowOptions(
    val terminalKeys: Boolean = true,
    val navigationKeys: Boolean = true,
    val symbols: Boolean = true,
    val custom: List<DevKey> = emptyList(),
    val customMode: CustomSymbolsMode = CustomSymbolsMode.APPEND,
)

/** Persisted by name: only add at the end. */
enum class DevRowSize(val label: String, val scale: Float) {
    COMPACT("Compact", 0.8f),
    NORMAL("Normal", 1.0f),
    LARGE("Large", 1.25f),
}

/** Bounds and defaults for the developer row's timing settings (also used to clamp stored values). */
object DeveloperRowTiming {
    const val HOLD_DEFAULT = 350
    const val HOLD_MIN = 150
    const val HOLD_MAX = 800
    const val REPEAT_DEFAULT = 60
    const val REPEAT_MIN = 20
    const val REPEAT_MAX = 200
    const val LOCK_WINDOW_DEFAULT = 400
    const val LOCK_WINDOW_MIN = 200
    const val LOCK_WINDOW_MAX = 800

    fun hold(v: Int): Long = v.coerceIn(HOLD_MIN, HOLD_MAX).toLong()
    fun repeat(v: Int): Long = v.coerceIn(REPEAT_MIN, REPEAT_MAX).toLong()
    fun lockWindow(v: Int): Long = v.coerceIn(LOCK_WINDOW_MIN, LOCK_WINDOW_MAX).toLong()
}
