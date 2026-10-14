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

package dev.patrickgold.florisboard.ime.nlp.prediction

/**
 * Current-word, next-word, and autocorrect suggestions over a static lexicon plus an optional
 * learned lexicon. Pure Kotlin. Does not log text. A null [learned] lexicon means personalized
 * data must not be consulted.
 */
object WordPredictor {
    const val MAX_CANDIDATES = 8
    private const val AUTOCOMMIT_MIN_LENGTH = 4
    private val TOKEN = Regex("""[A-Za-z0-9_./+\-]+""")

    fun predict(
        textBeforeCursor: String,
        lexicon: Map<String, Int>,
        learned: LearnedLexicon?,
        maxCandidates: Int = MAX_CANDIDATES,
    ): List<Prediction> {
        if (lexicon.isEmpty() && (learned == null || learned.words.isEmpty())) return emptyList()
        val limit = maxCandidates.coerceIn(1, MAX_CANDIDATES)
        val (previous, current) = contextTokens(textBeforeCursor)
        return if (current == null) {
            if (previous == null) emptyList() else nextWords(previous, lexicon, learned, limit)
        } else {
            currentWord(current, lexicon, learned, limit)
        }
    }

    /** The token being typed, and the token before it. [current] is null when the cursor is after whitespace. */
    fun contextTokens(textBeforeCursor: String): Pair<String?, String?> {
        if (textBeforeCursor.isEmpty()) return null to null
        val atBoundary = textBeforeCursor.last().isWhitespace()
        val tokens = TOKEN.findAll(textBeforeCursor.trimEnd()).map { it.value }.toList()
        if (tokens.isEmpty()) return null to null
        return if (atBoundary) {
            tokens.last() to null
        } else {
            tokens.dropLast(1).lastOrNull() to tokens.last()
        }
    }

    private fun currentWord(
        typed: String,
        lexicon: Map<String, Int>,
        learned: LearnedLexicon?,
        limit: Int,
    ): List<Prediction> {
        val needle = typed.lowercase()
        val pool = merged(lexicon, learned)
        val completions = pool.asSequence()
            .filter { (word, _) -> word.startsWith(needle) }
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.key == needle }.thenByDescending { it.value }.thenBy { it.key })
            .take(limit)
            .map { (word, freq) ->
                Prediction(
                    text = applyCase(typed, word),
                    kind = Prediction.Kind.COMPLETION,
                    confidence = (0.55 + 0.45 * (freq / 255.0)).coerceIn(0.0, 1.0),
                    autoCommit = false,
                )
            }
            .toList()
        if (needle in pool || typed.length < 2) return completions
        val corrections = pool.asSequence()
            .map { (word, freq) -> Triple(word, freq, distance(needle, word, 2)) }
            .filter { it.third <= 2 && !it.first.startsWith(needle) }
            .sortedWith(compareBy<Triple<String, Int, Int>> { it.third }.thenByDescending { it.second }.thenBy { it.first })
            .take(limit)
            .toList()
        val best = corrections.firstOrNull()
        val auto = best != null && best.third == 1 && typed.length >= AUTOCOMMIT_MIN_LENGTH
        val correctionPreds = corrections.mapIndexed { index, (word, freq, dist) ->
            Prediction(
                text = applyCase(typed, word),
                kind = Prediction.Kind.CORRECTION,
                confidence = (if (dist == 1) 0.72 else 0.4) * (0.5 + 0.5 * freq / 255.0),
                autoCommit = auto && index == 0,
            )
        }
        return (correctionPreds + completions).distinctBy { it.text.lowercase() }.take(limit)
    }

    private fun nextWords(
        previous: String,
        lexicon: Map<String, Int>,
        learned: LearnedLexicon?,
        limit: Int,
    ): List<Prediction> {
        val prev = previous.lowercase()
        val fromBigrams = learned?.bigrams[prev].orEmpty().entries
            .sortedByDescending { it.value }
            .map { (word, count) ->
                Prediction(
                    text = word,
                    kind = Prediction.Kind.NEXT,
                    confidence = (0.5 + 0.5 * (count / 255.0)).coerceIn(0.0, 1.0),
                    autoCommit = false,
                )
            }
        val seen = fromBigrams.map { it.text }.toMutableSet()
        seen += prev
        val filler = merged(lexicon, learned).entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .asSequence()
            .filter { it.key !in seen }
            .take(limit)
            .map { (word, freq) ->
                Prediction(
                    text = word,
                    kind = Prediction.Kind.NEXT,
                    confidence = 0.25 + 0.25 * (freq / 255.0),
                    autoCommit = false,
                )
            }
        return (fromBigrams + filler).take(limit)
    }

    private fun merged(lexicon: Map<String, Int>, learned: LearnedLexicon?): Map<String, Int> {
        if (learned == null || learned.words.isEmpty()) return lexicon
        val out = HashMap<String, Int>(lexicon.size + learned.words.size)
        out.putAll(lexicon)
        for ((word, freq) in learned.words) {
            out[word] = maxOf(out[word] ?: 0, freq)
        }
        return out
    }

    fun applyCase(typed: String, dictionary: String): String {
        val letters = typed.filter { it.isLetter() }
        return when {
            letters.isNotEmpty() && letters.all { it.isUpperCase() } -> dictionary.uppercase()
            typed.firstOrNull()?.isUpperCase() == true ->
                dictionary.replaceFirstChar { it.titlecaseChar() }
            else -> dictionary
        }
    }

    /** Levenshtein distance, or [max] + 1 when the distance is greater than [max]. */
    fun distance(a: String, b: String, max: Int): Int {
        if (kotlin.math.abs(a.length - b.length) > max) return max + 1
        if (a == b) return 0
        var prev = IntArray(b.length + 1) { it }
        var curr = IntArray(b.length + 1)
        for (i in 1..a.length) {
            curr[0] = i
            var rowMin = curr[0]
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                curr[j] = minOf(curr[j - 1] + 1, prev[j] + 1, prev[j - 1] + cost)
                if (curr[j] < rowMin) rowMin = curr[j]
            }
            if (rowMin > max) return max + 1
            val swap = prev
            prev = curr
            curr = swap
        }
        return if (prev[b.length] > max) max + 1 else prev[b.length]
    }
}

data class Prediction(
    val text: String,
    val kind: Kind,
    val confidence: Double,
    val autoCommit: Boolean,
) {
    enum class Kind { COMPLETION, CORRECTION, NEXT }
}

/**
 * Whether prediction may run, and whether a typed word may be stored.
 * Password-like fields, incognito, and [noPersonalizedLearning] never store anything.
 */
object LearningGate {
    data class Field(
        val passwordLike: Boolean = false,
        val incognito: Boolean = false,
        val noPersonalizedLearning: Boolean = false,
        val noSuggestions: Boolean = false,
    )

    fun allowsPrediction(predictionEnabled: Boolean, field: Field): Boolean =
        predictionEnabled && !field.passwordLike && !field.noSuggestions

    fun allowsLearning(learningEnabled: Boolean, field: Field): Boolean =
        learningEnabled &&
            !field.passwordLike &&
            !field.incognito &&
            !field.noPersonalizedLearning &&
            !field.noSuggestions
}
