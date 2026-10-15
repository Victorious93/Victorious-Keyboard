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

package dev.patrickgold.florisboard.ime.fleet

import com.victorious.fleet.IFleetKeyboardProvider
import dev.patrickgold.florisboard.lib.fleet.FleetProvider

/**
 * Adapts the generated binder stub to [FleetProvider].
 * TODO-confirm-with-hub: the action and permission live in `FleetApiContract`.
 * Callers insert the strings. Do not call this on the main thread.
 */
class AidlFleetProvider(private val remote: IFleetKeyboardProvider) : FleetProvider {
    override fun ping(): Boolean = remote.ping()

    override fun providerName(): String = remote.getProviderName().orEmpty()

    override fun namespace(): String = remote.getNamespace().orEmpty()

    override fun icon(): ByteArray = remote.getIcon() ?: ByteArray(0)

    override fun slashCommands(): String = remote.getSlashCommands().orEmpty()

    override fun snippets(): String = remote.getSnippets().orEmpty()

    override fun actions(): String = remote.getActions().orEmpty()

    override fun ask(action: String, text: String): String = remote.ask(action, text).orEmpty()

    override fun runCommand(name: String, args: List<String>): String =
        remote.runCommand(name, args.toTypedArray()).orEmpty()
}
