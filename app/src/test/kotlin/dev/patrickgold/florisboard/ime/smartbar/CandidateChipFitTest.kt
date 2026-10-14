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

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.floats.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import java.io.File
import org.florisboard.lib.snygg.SnyggStylesheet

class CandidateChipFitTest : FunSpec({
    test("a normal chip keeps the theme size") {
        CandidateChipFit.fontSp(availableHeightDp = 32f, requestedSp = 15f, fontScale = 1f) shouldBe 15f
    }

    test("font scales from 0.85 to 1.3 stay inside the chip") {
        val heights = listOf(24f, 32f, 40f, 56f)
        val scales = listOf(0.85f, 1f, 1.15f, 1.3f)
        for (height in heights) {
            for (scale in scales) {
                val sp = CandidateChipFit.fontSp(height, requestedSp = 15f, fontScale = scale)
                val lineDp = sp * scale * CandidateChipFit.LINE_BOX
                lineDp shouldBeLessThanOrEqual height + 0.01f
            }
        }
    }

    test("a zero-height chip does not ask for a huge font") {
        CandidateChipFit.fontSp(0f, 15f, 1.3f) shouldBe 1f
    }

    test("neon stylesheets leave vertical room and ellipsize horizontally") {
        val dir = File("src/main/assets/ime/theme/org.florisboard.themes/stylesheets")
        for (name in listOf("victorious_neon.json", "victorious_neon_light.json")) {
            val text = dir.resolve(name).readText()
            SnyggStylesheet.fromJson(text).isSuccess shouldBe true
            text shouldContainChip("smartbar-candidate-word", "\"padding\": \"0dp 10dp\"")
            text shouldContainChip("smartbar-candidate-word", "\"margin\": \"2dp 3dp\"")
            text shouldContainChip("smartbar-candidate-clip", "\"clip\": \"no\"")
        }
    }
})

private fun String.shouldContainChip(element: String, snippet: String) {
    val body = substringAfter("\"$element\"")
    val block = body.substringBefore("\n  },")
    check(block.contains(snippet)) { "$element is missing `$snippet`" }
}
