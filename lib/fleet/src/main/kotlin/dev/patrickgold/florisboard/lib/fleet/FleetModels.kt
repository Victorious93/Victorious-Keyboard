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

package dev.patrickgold.florisboard.lib.fleet

enum class FleetLink {
    /** Hub package is not installed, or the link is switched off. Every fleet feature stays hidden. */
    ABSENT,

    /** Package is installed and the link is on, but ping has not succeeded. */
    INSTALLED,

    /** ping() succeeded. Commands, snippets, and Ask hub may be shown. */
    CONNECTED,
}

enum class FleetSnippetKind {
    ADB,
    FASTBOOT,
    TERMUX,
    OTHER,
}

data class FleetCommand(
    val name: String,
    val description: String,
)

data class FleetSnippet(
    val id: String,
    val label: String,
    val kind: FleetSnippetKind,
    val text: String,
    val command: String,
    val args: List<String>,
) {
    /** Text to insert. A blank body asks the hub for text via [FleetHub.runCommand]. The result is not executed. */
    fun insertText(hub: FleetHub): FleetOutcome {
        val raw = if (text.isNotBlank()) text else hub.runCommand(command, args)
        return FleetOutcome(raw.take(FleetPolicy.MAX_INSERT))
    }
}

/** Hub text waiting to be inserted. [execute] stays false: the keyboard never runs this string. */
data class FleetOutcome(val text: String) {
    val execute: Boolean get() = false
}

data class FleetSnapshot(
    val link: FleetLink,
    val advertised: List<FleetCommand>,
    val snippets: List<FleetSnippet>,
) {
    fun commands(reserved: Set<String>): List<FleetCommand> {
        if (link != FleetLink.CONNECTED) return emptyList()
        return FleetCommands.visible(advertised, reserved)
    }

    fun panel(): FleetPanelState = when (link) {
        FleetLink.ABSENT -> FleetPanelState.Hidden
        FleetLink.INSTALLED -> FleetPanelState(showDot = true, connected = false, snippets = emptyList())
        FleetLink.CONNECTED -> FleetPanelState(showDot = true, connected = true, snippets = snippets)
    }

    companion object {
        fun absent() = FleetSnapshot(FleetLink.ABSENT, emptyList(), emptyList())

        fun installed() = FleetSnapshot(FleetLink.INSTALLED, emptyList(), emptyList())
    }
}

data class FleetPanelState(
    val showDot: Boolean,
    val connected: Boolean,
    val snippets: List<FleetSnippet>,
) {
    companion object {
        val Hidden = FleetPanelState(showDot = false, connected = false, snippets = emptyList())
    }
}

object FleetCommands {
    val builtins: List<FleetCommand> = listOf(
        FleetCommand("build", "Ask the hub about a build. The reply is inserted and not run"),
        FleetCommand("status", "Ask the hub for status. The reply is inserted and not run"),
        FleetCommand("note", "Send the words after /note. The reply is inserted and not run"),
    )

    private val namePattern = Regex("[a-z][a-z0-9_-]{0,31}")

    fun visible(advertised: List<FleetCommand>, reserved: Set<String>): List<FleetCommand> {
        val blocked = reserved.map { it.lowercase() }.toMutableSet()
        val extra = ArrayList<FleetCommand>()
        for (command in builtins) {
            blocked.add(command.name)
        }
        for (command in advertised) {
            val name = command.name.lowercase()
            if (!namePattern.matches(name) || name in blocked) continue
            blocked.add(name)
            extra.add(command.copy(name = name))
            if (extra.size >= 40) break
        }
        return builtins + extra
    }
}

object FleetPolicy {
    const val MAX_SEND = 8_000
    const val MAX_INSERT = 8_000

    /** Typed slash argument only. Clipboard and vault text are not parameters of this call. */
    fun slashText(argument: String, passwordLike: Boolean): String? {
        if (passwordLike) return null
        return argument.take(MAX_SEND)
    }

    /**
     * Selection the user picked for Ask hub. Password fields are refused.
     * Vault text is refused unless [explicitVaultPick] is true (the user tapped Ask hub).
     */
    fun askText(
        selection: String,
        passwordLike: Boolean,
        selectionIsVault: Boolean,
        explicitVaultPick: Boolean,
    ): String? {
        if (passwordLike) return null
        if (selectionIsVault && !explicitVaultPick) return null
        if (selection.isBlank()) return null
        return selection.take(MAX_SEND)
    }
}
