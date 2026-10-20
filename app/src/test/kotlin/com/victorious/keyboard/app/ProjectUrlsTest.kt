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

package com.victorious.keyboard.app

import io.kotest.core.spec.style.FunSpec
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import java.io.File

class ProjectUrlsTest : FunSpec({
    test("product links point at this repository and releases") {
        ProjectUrls.RELEASES shouldBe "https://github.com/Victorious93/Victorious-Keyboard/releases"
        ProjectUrls.ISSUES shouldBe "https://github.com/Victorious93/Victorious-Keyboard/issues"
        ProjectUrls.PRIVACY shouldBe "https://github.com/Victorious93/Victorious-Keyboard/blob/main/PRIVACY.md"
        ProjectUrls.README shouldBe "https://github.com/Victorious93/Victorious-Keyboard/blob/main/README.md"
    }

    test("string resources stay locked to the constants") {
        val xml = File("src/main/res/values/strings_dont_translate.xml").readText()
        fun url(name: String) = Regex("""name="$name"[^>]*>([^<]+)<""").find(xml)!!.groupValues[1]
        url("florisboard__repo_url") shouldBe ProjectUrls.REPO
        url("florisboard__issue_tracker_url") shouldBe ProjectUrls.ISSUES
        url("florisboard__privacy_policy_url") shouldBe ProjectUrls.PRIVACY
        url("florisboard__changelog_url") shouldBe ProjectUrls.CHANGELOG
        url("florisboard__commit_by_hash_url") shouldBe ProjectUrls.COMMIT
        url("florisboard__internal_key_codes_url") shouldBe ProjectUrls.KEY_CODES
        url("florisboard__spell_checker_wiki_url") shouldBe ProjectUrls.SPELL_CHECKER_DOCS
        url("florisboard__theme_editor_wiki_url") shouldBe ProjectUrls.THEME_EDITOR_DOCS
        url("florisboard__character_key_codes_url") shouldBe ProjectUrls.UNICODE_TABLE
    }

    test("the update button source and the packaged strings do not name the FlorisBoard add-on store") {
        val addon = File("src/main/kotlin/com/victorious/keyboard/app/ext/AddonBox.kt").readText()
        val extension = File("src/main/kotlin/com/victorious/keyboard/lib/ext/Extension.kt").readText()
        val gradle = File("build.gradle.kts").readText()
        val strings = File("src/main/res/values/strings_dont_translate.xml").readText()
        addon shouldContain "ProjectUrls.RELEASES"
        addon shouldNotContain "generateUpdateUrl"
        extension shouldContain "ProjectUrls.RELEASES"
        extension shouldNotContain "florisboard.org"
        gradle shouldNotContain "florisboard.org"
        strings shouldNotContain "florisboard.org"
        strings shouldNotContain "beta.addons"
    }

    test("no tappable link or bundled metadata points at FlorisBoard") {
        val about = File("src/main/kotlin/com/victorious/keyboard/app/settings/about/AboutScreen.kt").readText()
        val strings = File("src/main/res/values/strings_dont_translate.xml").readText()
        about shouldNotContain "upstream_url"
        strings shouldNotContain "github.com/florisboard"
        val bundled = File("src/main/assets/ime").walkTopDown().filter { it.isFile && it.extension == "json" }
        bundled.forEach { file ->
            val text = file.readText()
            withClue(file.path) {
                text shouldNotContain "schemas.florisboard.org"
                text shouldNotContain "patrickgold.dev"
            }
        }
    }
})
