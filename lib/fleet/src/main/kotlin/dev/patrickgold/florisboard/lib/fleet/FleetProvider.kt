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

/**
 * One command provider, as the keyboard sees it. The wire format is
 * [FleetMessages]. Returned strings are inserted. Nothing in this interface runs a command.
 */
interface FleetProvider {
    fun ping(): Boolean

    fun providerName(): String

    fun namespace(): String

    fun icon(): ByteArray

    fun slashCommands(): String

    fun snippets(): String

    fun actions(): String

    fun ask(action: String, text: String): String

    fun runCommand(name: String, args: List<String>): String
}
