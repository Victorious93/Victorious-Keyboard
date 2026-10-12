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

package dev.patrickgold.florisboard.ime.snippets

/** Default output of `{{date}}`. **Persisted by name: only add at the end.** */
enum class SnippetDateFormat(val label: String, val pattern: String) {
    ISO("2026-03-07", "yyyy-MM-dd"),
    DAY_FIRST("07/03/2026", "dd/MM/yyyy"),
    MONTH_FIRST("03/07/2026", "MM/dd/yyyy"),
    DOTTED("07.03.2026", "dd.MM.yyyy"),
}

/** Default output of `{{time}}`. **Persisted by name: only add at the end.** */
enum class SnippetTimeFormat(val label: String, val pattern: String) {
    H24("24-hour (14:05)", "HH:mm"),
    H24_SECONDS("24-hour with seconds (14:05:09)", "HH:mm:ss"),
    H12("12-hour (02:05 PM)", "hh:mm a"),
}
