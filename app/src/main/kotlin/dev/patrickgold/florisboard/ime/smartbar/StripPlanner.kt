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

package dev.patrickgold.florisboard.ime.smartbar

import dev.patrickgold.florisboard.ime.nlp.custom.CustomSuggestion
import dev.patrickgold.florisboard.ime.nlp.custom.CustomSuggestionMatcher
import dev.patrickgold.florisboard.ime.slash.CustomSlashCommand
import dev.patrickgold.florisboard.ime.slash.SlashCatalog
import dev.patrickgold.florisboard.ime.slash.SlashMatcher
import dev.patrickgold.florisboard.ime.slash.SlashOffer

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
    ): StripPlan {
        if (!passwordLike) {
            val token = SlashMatcher.token(textBeforeCursor, passwordLike = false)
            if (token != null && slashEnabled) {
                return StripPlan.Slash(
                    offers = SlashCatalog.match(token.name, commands, enabled = true),
                    replaceLength = token.replaceLength,
                    argument = token.argument,
                )
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
