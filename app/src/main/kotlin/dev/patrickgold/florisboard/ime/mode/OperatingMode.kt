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

package dev.patrickgold.florisboard.ime.mode

import dev.patrickgold.florisboard.ime.developer.LanguageProfile

enum class ModeAvailability {
    /** Has real, implemented behaviour and can be selected. */
    AVAILABLE,

    /** On the roadmap only. Never selectable and never shown; a stored value falls back to [OperatingMode.STANDARD]. */
    PLANNED,
}

/**
 * The keyboard's operating mode (not to be confused with `ime.keyboard.KeyboardMode`, which is the character / symbol /
 * numeric page). All modes share one IME; a mode only changes which optional rows and presets are active.
 *
 * **The enum constant names are persisted in preferences. Never rename or reorder them**; only add.
 */
enum class OperatingMode(val displayName: String, val availability: ModeAvailability) {
    STANDARD("Standard", ModeAvailability.AVAILABLE),
    DEVELOPER("Developer", ModeAvailability.AVAILABLE),
    TERMUX("Termux / terminal", ModeAvailability.AVAILABLE),
    CLAUDE_CODE("Claude Code", ModeAvailability.PLANNED),
    KALI("Kali Linux", ModeAvailability.PLANNED),
    NETHUNTER_AI("NetHunter AI", ModeAvailability.PLANNED);

    val isSelectable: Boolean get() = availability == ModeAvailability.AVAILABLE

    companion object {
        val selectable: List<OperatingMode> get() = entries.filter { it.isSelectable }

        /** Anything unknown, null or not yet implemented becomes [STANDARD]. */
        fun sanitize(mode: OperatingMode?): OperatingMode = if (mode != null && mode.isSelectable) mode else STANDARD
    }
}

/** The user's developer-row preferences, as stored. */
data class DeveloperRowPrefs(
    val enabled: Boolean,
    val profile: LanguageProfile,
    val terminalKeys: Boolean,
    val navigationKeys: Boolean = true,
    val symbols: Boolean = true,
)

/** What the developer row should actually do right now. */
data class EffectiveDeveloperRow(
    val visible: Boolean,
    val profile: LanguageProfile,
    val terminalKeys: Boolean,
    val navigationKeys: Boolean = true,
    val symbols: Boolean = true,
)

object ModePresets {
    /**
     * * STANDARD: exactly the user's preferences (so existing behaviour is unchanged).
     * * DEVELOPER: the row is always shown, with the user's profile and terminal-key choice.
     * * TERMUX: the row is always shown with terminal keys and the Shell symbols (a terminal preset).
     * * Planned modes behave like STANDARD.
     */
    fun developerRow(mode: OperatingMode, prefs: DeveloperRowPrefs): EffectiveDeveloperRow = when (OperatingMode.sanitize(mode)) {
        OperatingMode.DEVELOPER ->
            EffectiveDeveloperRow(true, prefs.profile, prefs.terminalKeys, prefs.navigationKeys, prefs.symbols)
        OperatingMode.TERMUX -> EffectiveDeveloperRow(true, LanguageProfile.SHELL, true, true, true)
        else -> EffectiveDeveloperRow(prefs.enabled, prefs.profile, prefs.terminalKeys, prefs.navigationKeys, prefs.symbols)
    }
}

/**
 * Suggests a mode for the app being typed into, from the package name Android hands every keyboard in `EditorInfo`.
 * Nothing is monitored and no accessibility service is involved. Only a *suggestion*: whether it is applied is the
 * user's choice.
 */
object ModeSuggester {
    /** Only packages whose identity is certain. Everything else is up to user-defined [overrides]. */
    val builtIn: Map<String, OperatingMode> = mapOf(
        "com.termux" to OperatingMode.TERMUX,
        "org.connectbot" to OperatingMode.TERMUX,
        "com.offsec.nhterm" to OperatingMode.TERMUX,
        "com.offsec.nethunter" to OperatingMode.TERMUX,
        "com.offsec.nethunter.kex" to OperatingMode.TERMUX,
        "com.sonelli.juicessh" to OperatingMode.TERMUX,
        "com.server.auditor.ssh.client" to OperatingMode.TERMUX,
        "com.termoneplus" to OperatingMode.TERMUX,
    )

    fun suggest(packageName: String?, overrides: Map<String, OperatingMode> = emptyMap()): OperatingMode? {
        if (packageName.isNullOrEmpty()) return null
        val mode = overrides[packageName] ?: builtIn[packageName] ?: return null
        return mode.takeIf { it.isSelectable }
    }
}

object ActiveMode {
    /** The mode in effect: the suggestion when auto-detect is on and one exists, else the (sanitised) default. */
    fun resolve(
        default: OperatingMode,
        autoDetect: Boolean,
        packageName: String?,
        overrides: Map<String, OperatingMode> = emptyMap(),
    ): OperatingMode {
        if (autoDetect) ModeSuggester.suggest(packageName, overrides)?.let { return it }
        return OperatingMode.sanitize(default)
    }
}
