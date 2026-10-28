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
 * The keys a person can put on the terminal row, in the order they chose, plus macro keys.
 * A blank order means "keep the row the app already builds". Macro text is never executed and never logged.
 */
object TerminalRowLayout {
    const val MAX_KEYS = 24
    const val MAX_MACROS = 8
    const val MAX_LABEL = 12
    const val MAX_TEXT = 64
    const val MAX_STEPS = 6

    data class Slot(val id: String, val key: DevKey)

    val slots: List<Slot> = buildList {
        val terminal = listOf(
            "esc", "tab", "ctrl", "alt", "left", "down", "up", "right", "home", "end", "pgup", "pgdn",
        )
        terminal.zip(DeveloperRows.modifierRow + DeveloperRows.navigationRow).forEach { (id, key) ->
            add(Slot(id, key))
        }
        fun symbol(id: String, text: String) {
            add(Slot(id, DevKey(text, DevKeyAction.Insert(text), text)))
        }
        symbol("pipe", "|")
        symbol("tilde", "~")
        symbol("slash", "/")
        symbol("backslash", "\\")
        symbol("amp", "&")
        symbol("dollar", "$")
        symbol("semi", ";")
        symbol("colon", ":")
        symbol("hash", "#")
        symbol("bang", "!")
        symbol("minus", "-")
        symbol("underscore", "_")
        symbol("tick", "`")
        fun media(id: String, label: String, command: com.victorious.keyboard.ime.media.MediaCommand) {
            add(Slot(id, DevKey(label, DevKeyAction.Media(command), label)))
        }
        media("media_play", "Play", com.victorious.keyboard.ime.media.MediaCommand.PLAY_PAUSE)
        media("media_prev", "Prev", com.victorious.keyboard.ime.media.MediaCommand.PREVIOUS)
        media("media_next", "Next", com.victorious.keyboard.ime.media.MediaCommand.NEXT)
        media("media_rw", "Rew", com.victorious.keyboard.ime.media.MediaCommand.REWIND)
        media("media_ff", "FF", com.victorious.keyboard.ime.media.MediaCommand.FAST_FORWARD)
        media("media_vol_down", "Vol-", com.victorious.keyboard.ime.media.MediaCommand.VOLUME_DOWN)
        media("media_vol_up", "Vol+", com.victorious.keyboard.ime.media.MediaCommand.VOLUME_UP)
        add(Slot("hist_up", DevKey("H+", DevKeyAction.History(older = true), "Older command")))
        add(Slot("hist_dn", DevKey("H-", DevKeyAction.History(older = false), "Newer command")))
    }

    /** Esc, Tab, Ctrl, Alt, then the arrows and the other navigation keys, in today's order. */
    val defaultIds: List<String> = listOf(
        "esc", "tab", "ctrl", "alt", "left", "down", "up", "right", "home", "end", "pgup", "pgdn",
    )

    private val byId = slots.associateBy { it.id }

    /** Blank stays blank (the caller keeps the built row). Anything else is known ids, in order, without duplicates. */
    fun parseIds(raw: String): List<String> {
        if (raw.isBlank()) return emptyList()
        return raw.split(',').map { it.trim().lowercase() }.filter { it in byId }.distinct().take(MAX_KEYS)
    }

    fun keysFor(raw: String): List<DevKey> = parseIds(raw).map { byId.getValue(it).key }

    /**
     * [base] is today's row. A blank [orderRaw] keeps it. Macros are always added at the end.
     */
    fun arrange(base: List<DevKey>, orderRaw: String, macrosRaw: String): List<DevKey> {
        val chosen = if (orderRaw.isBlank()) emptyList() else parseIds(orderRaw)
        // A blank list, or a list that names no real keys, keeps the row the app already builds.
        val head = if (orderRaw.isBlank() || chosen.isEmpty()) base else keysFor(orderRaw)
        return head + macroKeys(macrosRaw)
    }

    data class MacroDef(val label: String, val steps: List<MacroStep>)

    fun parseMacros(raw: String): List<MacroDef> {
        if (raw.isBlank()) return emptyList()
        return raw.lineSequence().mapNotNull { parseMacroLine(it) }.take(MAX_MACROS).toList()
    }

    fun macroKeys(raw: String): List<DevKey> = parseMacros(raw).map { def ->
        DevKey(def.label, DevKeyAction.Play(def.steps), def.label)
    }

    fun renderMacros(macros: List<MacroDef>): String = macros.take(MAX_MACROS).joinToString("\n") { def ->
        (listOf(def.label.take(MAX_LABEL)) + def.steps.take(MAX_STEPS).map { renderStep(it) }).joinToString("|")
    }

    fun commandMacro(command: String): MacroDef? {
        val text = sanitizeText(command)
        if (text.isEmpty()) return null
        return MacroDef(text.take(MAX_LABEL), listOf(MacroStep.Text(text), MacroStep.Enter))
    }

    fun ctrlMacro(letter: Char): MacroDef? {
        if (!letter.isLetter()) return null
        val lower = letter.lowercaseChar()
        return MacroDef("Ctrl+" + lower.uppercaseChar(), listOf(MacroStep.Ctrl(lower)))
    }

    fun addMacro(raw: String, macro: MacroDef): String {
        val current = parseMacros(raw)
        if (current.size >= MAX_MACROS) return renderMacros(current)
        return renderMacros(current + macro)
    }

    private fun parseMacroLine(line: String): MacroDef? {
        val bits = line.split('|')
        if (bits.size < 2) return null
        val label = bits[0].trim().take(MAX_LABEL)
        if (label.isEmpty()) return null
        val steps = bits.drop(1).mapNotNull { parseStep(it) }.take(MAX_STEPS)
        if (steps.isEmpty()) return null
        return MacroDef(label, steps)
    }

    private fun parseStep(raw: String): MacroStep? {
        val step = raw.trim()
        return when {
            step == "enter" -> MacroStep.Enter
            step.startsWith("ctrl:") && step.length == 6 && step[5].isLetter() -> MacroStep.Ctrl(step[5].lowercaseChar())
            step.startsWith("text:") -> {
                val text = sanitizeText(step.removePrefix("text:"))
                if (text.isEmpty()) null else MacroStep.Text(text)
            }
            else -> null
        }
    }

    private fun renderStep(step: MacroStep): String = when (step) {
        is MacroStep.Text -> "text:" + sanitizeText(step.text)
        MacroStep.Enter -> "enter"
        is MacroStep.Ctrl -> "ctrl:" + step.letter.lowercaseChar()
    }

    private fun sanitizeText(raw: String): String =
        raw.replace("|", "").replace("\n", "").replace("\r", "").trim().take(MAX_TEXT)
}
