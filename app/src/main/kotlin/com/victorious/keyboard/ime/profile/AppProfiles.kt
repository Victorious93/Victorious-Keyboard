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

package com.victorious.keyboard.ime.profile

import com.victorious.keyboard.ime.mode.TerminalKeyRow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * What one app should use instead of the global keyboard settings. Null fields keep the global value.
 * A stored profile for the same package overrides the preset field by field.
 */
data class AppProfile(
    val packageName: String,
    val layoutSubtypeId: Long? = null,
    val themeId: String? = null,
    val terminalRow: TerminalKeyRow? = null,
    val autocorrectOff: Boolean? = null,
    val suggestions: Boolean? = null,
)

/** The overlay in effect for the focused app. Defaults leave the user's settings alone. */
data class ProfileEffect(
    val autocorrectOff: Boolean = false,
    val suggestions: Boolean? = null,
    val terminalRow: TerminalKeyRow? = null,
    val layoutSubtypeId: Long? = null,
    val themeId: String? = null,
) {
    companion object {
        val None = ProfileEffect()
    }
}

/** Values to put back when the focused app no longer wants a layout or theme override. */
data class ProfileHold(
    val layout: Long? = null,
    val day: String? = null,
    val night: String? = null,
    val active: Boolean = false,
)

sealed class ProfileShift {
    data object Stay : ProfileShift()
    data class Apply(val layout: Long?, val theme: String?) : ProfileShift()
    data class Restore(val layout: Long?, val day: String?, val night: String?) : ProfileShift()
}

object AppProfiles {
    private val terminals = setOf(
        "com.termux",
        "com.offsec.nhterm",
        "com.offsec.nethunter",
        "com.offsec.nethunter.kex",
        "com.sonelli.juicessh",
        "org.connectbot",
        "jackpal.androidterm",
        "io.neoterm",
        "com.termoneplus",
        "com.server.auditor.ssh.client",
    )

    /** Editors where autocorrect fights the code and the terminal row is useful. */
    private val editors = setOf(
        "com.aide.ui",
        "com.blacksquircle.ui",
        "com.foxdebug.acode",
        "com.itsaky.androidide",
        "io.github.rosemoe.sora",
        "org.gnu.emacs",
        "com.rhmsoft.code",
        "com.n0n3m4.droidc",
    )

    fun isPresetPackage(packageName: String?): Boolean {
        if (packageName.isNullOrEmpty()) return false
        if (packageName == "com.termux" || packageName.startsWith("com.termux.")) return true
        if (packageName in terminals || packageName in editors) return true
        return editors.any { packageName.startsWith("$it.") } || terminals.any { packageName.startsWith("$it.") }
    }

    /** Autocorrect off and the terminal row on. Layout, theme, and suggestions stay as the user set them. */
    fun preset(packageName: String): AppProfile? {
        if (!isPresetPackage(packageName)) return null
        return AppProfile(
            packageName = packageName,
            terminalRow = TerminalKeyRow.ALWAYS,
            autocorrectOff = true,
        )
    }

    fun effect(packageName: String?, stored: List<AppProfile>): ProfileEffect {
        if (packageName.isNullOrEmpty()) return ProfileEffect.None
        val saved = stored.firstOrNull { it.packageName == packageName }
        val base = preset(packageName)
        if (base == null && saved == null) return ProfileEffect.None
        val profile = if (base == null) {
            saved!!
        } else if (saved == null) {
            base
        } else {
            base.copy(
                layoutSubtypeId = saved.layoutSubtypeId ?: base.layoutSubtypeId,
                themeId = saved.themeId ?: base.themeId,
                terminalRow = saved.terminalRow ?: base.terminalRow,
                autocorrectOff = saved.autocorrectOff ?: base.autocorrectOff,
                suggestions = saved.suggestions ?: base.suggestions,
            )
        }
        return ProfileEffect(
            autocorrectOff = profile.autocorrectOff == true,
            suggestions = profile.suggestions,
            terminalRow = profile.terminalRow,
            layoutSubtypeId = profile.layoutSubtypeId,
            themeId = profile.themeId?.takeIf { it.isNotBlank() },
        )
    }
}

object ProfileHoldMachine {
    fun step(
        hold: ProfileHold,
        effect: ProfileEffect,
        currentLayout: Long,
        currentDay: String,
        currentNight: String,
    ): Pair<ProfileHold, ProfileShift> {
        val wants = effect.layoutSubtypeId != null || !effect.themeId.isNullOrBlank()
        if (!wants) {
            if (!hold.active) return hold to ProfileShift.Stay
            return ProfileHold() to ProfileShift.Restore(hold.layout, hold.day, hold.night)
        }
        val next = if (hold.active) {
            hold
        } else {
            ProfileHold(currentLayout, currentDay, currentNight, active = true)
        }
        return next to ProfileShift.Apply(effect.layoutSubtypeId, effect.themeId)
    }
}

/**
 * Which stored theme ids can be written back. A bad id is skipped on its own, so one broken
 * night theme does not keep the day theme from being restored.
 */
object ProfileThemePlan {
    fun kept(raw: String?, acceptable: (String) -> Boolean): String? {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty() || !acceptable(text)) return null
        return text
    }
}

/** The overlay the suggestion and terminal-row code read. Set from the editor-focus listener. */
object AppProfileRuntime {
    private val mutable = MutableStateFlow(ProfileEffect.None)
    val effect: StateFlow<ProfileEffect> = mutable

    fun set(value: ProfileEffect) {
        mutable.value = value
    }
}
