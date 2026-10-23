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

    /**
     * Where "Get more" opens. One page per pack type under `packs/` in this repository, so every pack link stays on
     * the owner's own host. The pages are plain Markdown; the catalog behind them is queued work (ROADMAP item 9).
     */
    const val PACKS = "$REPO/blob/main/packs/README.md"
    const val EXTENSIONS = "$REPO/blob/main/packs/extensions.md"
    const val THEMES = "$REPO/blob/main/packs/themes.md"
    const val LAYOUTS = "$REPO/blob/main/packs/layouts.md"
    const val LANGUAGE_PACKS = "$REPO/blob/main/packs/language-packs.md"
    const val DICTIONARY_PACKS = "$REPO/blob/main/packs/dictionary-packs.md"
    const val COMMAND_PROVIDERS = "$REPO/blob/main/packs/command-providers.md"

    /** In-app Help screen. The old spell-checker and theme-editor links open this, not a website. */
    const val MANUAL = "ui://florisboard/settings/manual"
    const val SPELL_CHECKER_DOCS = MANUAL
    const val THEME_EDITOR_DOCS = MANUAL

    const val UNICODE_TABLE = "https://unicode-table.com/en"
}
