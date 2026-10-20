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

package com.victorious.keyboard.ime.nlp.prediction

import com.victorious.keyboard.ime.dictionary.packs.DictionaryPackReader
import com.victorious.keyboard.ime.dictionary.packs.PackEntry
import com.victorious.keyboard.ime.dictionary.packs.PackReadResult

/** Static words from the bundled dictionary packs. A pack that fails to read is skipped, not repaired. */
object BundledLexicon {
    fun fromEntries(entries: List<PackEntry>): Map<String, Int> {
        val out = HashMap<String, Int>()
        for (entry in entries) {
            val key = LearnedWords.normalize(entry.word) ?: continue
            out[key] = maxOf(out[key] ?: 0, entry.freq.coerceIn(1, 255))
        }
        return out
    }

    fun fromPackZips(packs: List<ByteArray>): Map<String, Int> {
        val entries = ArrayList<PackEntry>()
        for (bytes in packs) {
            val read = DictionaryPackReader.read(bytes)
            if (read is PackReadResult.Ok) entries += read.pack.entries
        }
        return fromEntries(entries)
    }
}
