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

package dev.patrickgold.florisboard.app

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.io.File

/** The manual shipped in the app is the same file as `docs/MANUAL.md`. */
class ManualCopyTest : FunSpec({
    test("the asset copy matches the docs manual and describes media keys") {
        val manual = File("../docs/MANUAL.md").readText()
        File("src/main/assets/manual/MANUAL.md").readText() shouldBe manual
        manual shouldContain "File Manager seek"
        manual shouldContain "Media tile"
        manual shouldContain "Pin up to four tools"
    }
})
