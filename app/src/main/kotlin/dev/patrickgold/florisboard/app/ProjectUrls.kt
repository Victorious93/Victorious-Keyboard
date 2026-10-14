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

/**
 * Every user-facing project URL. Settings, the crash dialog and "Search for Updates" read these
 * constants (the string resources in `strings_dont_translate.xml` are kept in lockstep and checked
 * by [dev.patrickgold.florisboard.app.ProjectUrlsTest]).
 *
 * [UPSTREAM_REPO] is attribution for FlorisBoard, the project this keyboard is derived from. It is
 * the link on the About screen's "Based on FlorisBoard" row and is not a product link.
 */
object ProjectUrls {
    const val REPO = "https://github.com/Victorious93/Victorious-Keyboard"
    const val RELEASES = "$REPO/releases"
    const val ISSUES = "$REPO/issues"
    const val PRIVACY = "$REPO/blob/main/PRIVACY.md"
    const val README = "$REPO/blob/main/README.md"
    const val CHANGELOG = "$REPO/blob/main/CHANGELOG.md"
    const val COMMIT = "$REPO/commit/{hash}"
    const val KEY_CODES = "$REPO/blob/main/app/src/main/kotlin/dev/patrickgold/florisboard/ime/text/key/KeyCode.kt"

    /** Spell-checker behaviour is documented in this project's README. */
    const val SPELL_CHECKER_DOCS = "$README#spell-checker"
    /** Theme editor behaviour is documented in this project's README. */
    const val THEME_EDITOR_DOCS = "$README#theme-editor"

    const val UPSTREAM_REPO = "https://github.com/florisboard/florisboard"
    const val UNICODE_TABLE = "https://unicode-table.com/en"
}
