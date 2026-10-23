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

/**
 * Every user-facing project URL. Settings, the crash dialog and "Search for Updates" read these
 * constants (the string resources in `strings_dont_translate.xml` are kept in lockstep and checked
 * by [com.victorious.keyboard.app.ProjectUrlsTest]).
 *
 * There is deliberately no link to FlorisBoard: the About screen's "Based on FlorisBoard" row is a plain
 * credit that opens the in-app licence screen.
 */
object ProjectUrls {
    const val REPO = "https://github.com/Victorious93/Victorious-Keyboard"
    const val RELEASES = "$REPO/releases"
    const val ISSUES = "$REPO/issues"
    const val PRIVACY = "$REPO/blob/main/PRIVACY.md"
    const val README = "$REPO/blob/main/README.md"
    const val CHANGELOG = "$REPO/blob/main/CHANGELOG.md"
    const val COMMIT = "$REPO/commit/{hash}"
    const val KEY_CODES = "$REPO/blob/main/app/src/main/kotlin/com/victorious/keyboard/ime/text/key/KeyCode.kt"

    /** In-app Help screen. The old spell-checker and theme-editor links open this, not a website. */
    const val MANUAL = "ui://florisboard/settings/manual"
    const val SPELL_CHECKER_DOCS = MANUAL
    const val THEME_EDITOR_DOCS = MANUAL

    /**
     * Where each "Get more" button goes. Until the pack catalog exists (ROADMAP items 9 to 13) these point at
     * pages that already exist in this repository; a test keeps every one on [REPO].
     */
    const val GET_EXTENSIONS = RELEASES
    const val GET_THEMES = RELEASES
    const val GET_LAYOUTS = RELEASES
    const val GET_LANGUAGE_PACKS = "$REPO/blob/main/LANGUAGEPACKS.md"
    const val GET_DICTIONARY_PACKS = "$REPO/blob/main/DICTIONARY_PACKS.md"

    const val UNICODE_TABLE = "https://unicode-table.com/en"
}
