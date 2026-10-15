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

enum class FleetSnippetKind {
    ADB,
    FASTBOOT,
    TERMUX,
    OTHER,
}

/** A slash name advertised by one provider, before namespacing. */
data class FleetAdvertised(
    val name: String,
    val description: String,
)

/**
 * A slash command the strip can show. [name] is what the user sees (`status` or `edl:status`).
 * [rawName] is what the provider advertised and what `ask` receives.
 */
data class FleetCommand(
    val name: String,
    val rawName: String,
    val description: String,
    val packageName: String,
)

data class FleetAction(
    val name: String,
    val label: String,
)

data class FleetSnippet(
    val id: String,
    val label: String,
    val kind: FleetSnippetKind,
    val text: String,
    val command: String,
    val args: List<String>,
) {
    /** Text to insert. A blank body asks the provider for text. The result is not executed. */
    fun insertText(provider: FleetProvider): FleetOutcome {
        val raw = if (text.isNotBlank()) text else provider.runCommand(command, args)
        return FleetOutcome(raw.take(FleetPolicy.MAX_INSERT))
    }
}

/** Provider text waiting to be inserted. [execute] stays false: the keyboard never runs this string. */
data class FleetOutcome(val text: String) {
    val execute: Boolean get() = false
}

data class FleetEndpoint(
    val packageName: String,
    val provider: FleetProvider?,
)

data class FleetApp(
    val packageName: String,
    val title: String,
    val namespace: String,
    val connected: Boolean,
    val iconPng: ByteArray?,
    val commands: List<FleetAdvertised>,
    val snippets: List<FleetSnippet>,
    val actions: List<FleetAction>,
)

data class FleetSnapshot(
    val apps: List<FleetApp>,
) {
    fun commands(reserved: Set<String>): List<FleetCommand> = FleetAggregate.commands(apps, reserved)

    fun panel(): FleetPanelState = FleetAggregate.panel(apps)

    companion object {
        fun absent(): FleetSnapshot = FleetSnapshot(emptyList())
    }
}

data class FleetAppStatus(
    val packageName: String,
    val title: String,
    val connected: Boolean,
    val iconPng: ByteArray?,
)

data class FleetAppSection(
    val packageName: String,
    val title: String,
    val snippets: List<FleetSnippet>,
    val actions: List<FleetAction>,
)

data class FleetPanelState(
    val statuses: List<FleetAppStatus>,
    val sections: List<FleetAppSection>,
) {
    companion object {
        val Hidden = FleetPanelState(emptyList(), emptyList())
    }
}

/** A service `PackageManager` reported. The keyboard fills this; discovery does not name apps. */
data class FleetServiceCandidate(
    val packageName: String,
    val className: String,
    val permissionGranted: Boolean,
    val certificates: List<ByteArray>,
)

data class FleetServiceTarget(
    val packageName: String,
    val className: String,
)

object FleetTrust {
    /** Same signature permission and the same set of signing certificates. Order does not matter. */
    fun accept(
        permissionGranted: Boolean,
        keyboardCertificates: List<ByteArray>,
        providerCertificates: List<ByteArray>,
    ): Boolean {
        if (!permissionGranted) return false
        if (keyboardCertificates.isEmpty() || providerCertificates.isEmpty()) return false
        return keyboardCertificates.map { it.toList() }.toSet() == providerCertificates.map { it.toList() }.toSet()
    }
}

object FleetDiscovery {
    const val MAX_PROVIDERS = 16

    /**
     * Keeps services the keyboard may bind. Skips this app, a missing permission, and a different
     * signer. Two services in one package collapse to the first class name in sorted order.
     */
    fun select(
        ownPackage: String,
        keyboardCertificates: List<ByteArray>,
        candidates: List<FleetServiceCandidate>,
    ): List<FleetServiceTarget> {
        return candidates
            .asSequence()
            .filter { it.packageName.isNotBlank() && it.className.isNotBlank() && it.packageName != ownPackage }
            .sortedWith(compareBy({ it.packageName }, { it.className }))
            .filter { FleetTrust.accept(it.permissionGranted, keyboardCertificates, it.certificates) }
            .distinctBy { it.packageName }
            .take(MAX_PROVIDERS)
            .map { FleetServiceTarget(it.packageName, it.className) }
            .toList()
    }
}

object FleetNamespaces {
    private val token = Regex("[a-z][a-z0-9]{0,15}")

    fun tokenFor(declared: String, title: String, packageName: String, taken: Set<String>): String {
        val base = sequenceOf(declared, slug(title), packageSuffix(packageName))
            .map { it.lowercase() }
            .firstOrNull { token.matches(it) } ?: "app"
        if (base !in taken) return base
        var n = 2
        while (n < 100) {
            val suffix = n.toString()
            val candidate = base.take(16 - suffix.length) + suffix
            if (candidate !in taken && token.matches(candidate)) return candidate
            n++
        }
        return "app"
    }

    /** First token only, so "Swarm AI" becomes "swarm". */
    fun slug(title: String): String {
        val out = StringBuilder()
        for (ch in title.lowercase()) {
            if (ch in 'a'..'z' || (out.isNotEmpty() && ch in '0'..'9')) {
                out.append(ch)
            } else if (out.isNotEmpty()) {
                break
            }
            if (out.length >= 16) break
        }
        return out.toString()
    }

    fun packageSuffix(packageName: String): String = slug(packageName.substringAfterLast('.'))
}

object FleetIcons {
    const val MAX_BYTES = 48_000
    private val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)

    fun keep(bytes: ByteArray?): ByteArray? {
        if (bytes == null || bytes.size !in png.size..MAX_BYTES) return null
        if (!bytes.copyOfRange(0, png.size).contentEquals(png)) return null
        return bytes
    }
}

object FleetAggregate {
    private val namePattern = Regex("[a-z][a-z0-9_-]{0,31}")
    const val MAX_COMMANDS = 40

    fun commands(apps: List<FleetApp>, reserved: Set<String>): List<FleetCommand> {
        val blocked = reserved.map { it.lowercase() }.toSet()
        val connected = apps.filter { it.connected }
        val counts = HashMap<String, Int>()
        for (app in connected) {
            for (name in validNames(app.commands)) {
                counts[name] = (counts[name] ?: 0) + 1
            }
        }
        val used = HashSet<String>()
        val out = ArrayList<FleetCommand>()
        for (app in connected) {
            val seen = HashSet<String>()
            for (command in app.commands) {
                val name = command.name.lowercase()
                if (!namePattern.matches(name) || !seen.add(name)) continue
                val clash = (counts[name] ?: 0) > 1 || name in blocked
                val qualified = if (clash) "${app.namespace}:$name" else name
                if (!used.add(qualified)) continue
                out.add(FleetCommand(qualified, name, command.description, app.packageName))
                if (out.size >= MAX_COMMANDS) return out
            }
        }
        return out
    }

    fun panel(apps: List<FleetApp>): FleetPanelState {
        if (apps.isEmpty()) return FleetPanelState.Hidden
        return FleetPanelState(
            statuses = apps.map { FleetAppStatus(it.packageName, it.title, it.connected, it.iconPng) },
            sections = apps.filter { it.connected }.map {
                FleetAppSection(it.packageName, it.title, it.snippets, it.actions)
            },
        )
    }

    private fun validNames(commands: List<FleetAdvertised>): Set<String> {
        val seen = LinkedHashSet<String>()
        for (command in commands) {
            val name = command.name.lowercase()
            if (namePattern.matches(name)) seen.add(name)
        }
        return seen
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
     * Selection the user picked for Ask or an advertised action. Password fields are refused.
     * Vault text is refused unless [explicitVaultPick] is true (the user tapped that app).
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
