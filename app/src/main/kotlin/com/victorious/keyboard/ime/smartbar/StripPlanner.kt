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

package com.victorious.keyboard.ime.smartbar

import com.victorious.keyboard.ime.nlp.custom.CustomSuggestion
import com.victorious.keyboard.ime.nlp.custom.CustomSuggestionMatcher
import com.victorious.keyboard.ime.slash.CustomSlashCommand
import com.victorious.keyboard.ime.slash.SlashCatalog
import com.victorious.keyboard.ime.slash.SlashMatcher
import com.victorious.keyboard.ime.slash.SlashOffer
import com.victorious.keyboard.lib.fleet.FleetCommand

/**
 * Decides what the suggestion strip shows. A slash token replaces the strip with commands.
 * Custom phrases sort ahead of the dictionary. Password fields never produce slash or custom rows.
 */
sealed class StripPlan {
    data class Slash(val offers: List<SlashOffer>, val replaceLength: Int, val argument: String) : StripPlan()

    data class Words(
        val custom: List<CustomSuggestion>,
        val showClipboard: Boolean,
        val showDictionary: Boolean,
        val selection: String?,
    ) : StripPlan()
}

object StripPrefix {
    fun of(textBeforeCursor: String): String {
        val token = textBeforeCursor.takeLastWhile { !it.isWhitespace() }
        if (token.isEmpty() || token[0] == '/') return ""
        return token.take(64)
    }
}

object StripPlanner {
    fun plan(
        passwordLike: Boolean,
        slashEnabled: Boolean,
        textBeforeCursor: String,
        selection: String,
        custom: List<CustomSuggestion>,
        commands: List<CustomSlashCommand>,
        clipboardAvailable: Boolean,
        fleetCommands: List<FleetCommand> = emptyList(),
    ): StripPlan {
        if (!passwordLike) {
            val known = commands.filter { it.enabled }.map { it.name }.toSet() +
                fleetCommands.map { it.rawName }
            val token = SlashMatcher.token(
                textBeforeCursor,
                passwordLike = false,
                argumentNames = fleetCommands.map { it.name }.toSet(),
                knownNames = known,
            )
            if (token != null && slashEnabled) {
                val local = SlashCatalog.match(token.name, commands, enabled = true)
                val fleetOffers = fleetCommands
                    .filter { token.name.isEmpty() || it.name.startsWith(token.name) || it.rawName.startsWith(token.name) }
                    .filter { command -> local.none { it.name == command.name } }
                    .map {
                        SlashOffer(
                            it.name,
                            it.description,
                            null,
                            null,
                            fleet = true,
                            fleetPackage = it.packageName,
                            fleetRawName = it.rawName,
                        )
                    }
                val offers = (local + fleetOffers).take(24)
                // A slash that matches nothing (`cd /usr/bin`) is a path, not a command list.
                if (token.name.isEmpty() || offers.isNotEmpty()) {
                    return StripPlan.Slash(
                        offers = offers,
                        replaceLength = token.replaceLength,
                        argument = token.argument,
                    )
                }
            }
        }
        val prefix = if (passwordLike) "" else StripPrefix.of(textBeforeCursor)
        val matched = if (prefix.isEmpty()) emptyList() else CustomSuggestionMatcher.rank(custom, prefix)
        val typing = prefix.isNotEmpty()
        val phrase = selection.takeIf { it.isNotBlank() && !passwordLike }
        return StripPlan.Words(
            custom = matched,
            showClipboard = !typing && matched.isEmpty() && clipboardAvailable,
            showDictionary = typing || matched.isNotEmpty() || !clipboardAvailable,
            selection = phrase,
        )
    }
}

/** Index of the chip to highlight: the auto-commit word, else the first word, else the first chip. */
object CandidateHighlight {
    fun indexOf(autoCommit: List<Boolean>, word: List<Boolean>): Int {
        val auto = autoCommit.indexOfFirst { it }
        if (auto >= 0) return auto
        val firstWord = word.indexOfFirst { it }
        if (firstWord >= 0) return firstWord
        return if (autoCommit.isEmpty()) -1 else 0
    }
}
