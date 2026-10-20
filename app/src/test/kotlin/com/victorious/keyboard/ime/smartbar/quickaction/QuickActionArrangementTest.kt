/*
 * Copyright (C) 2025 The FlorisBoard Contributors
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

package com.victorious.keyboard.ime.smartbar.quickaction

import com.victorious.keyboard.ime.text.key.KeyCode
import com.victorious.keyboard.ime.text.keyboard.TextKeyData
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.shouldBe

class QuickActionArrangementTest : FunSpec({
    context("contains behavior") {
        withData(
            Triple(
                QuickActionArrangement(
                    stickyAction = null,
                    dynamicActions = listOf(),
                    hiddenActions = listOf(),
                ),
                QuickAction.InsertKey(TextKeyData.SETTINGS),
                false,
            ),
            Triple(
                QuickActionArrangement(
                    stickyAction = QuickAction.InsertKey(TextKeyData.SETTINGS),
                    dynamicActions = listOf(),
                    hiddenActions = listOf(),
                ),
                QuickAction.InsertKey(TextKeyData.SETTINGS),
                true,
            ),
            Triple(
                QuickActionArrangement(
                    stickyAction = null,
                    dynamicActions = listOf(QuickAction.InsertKey(TextKeyData.SETTINGS)),
                    hiddenActions = listOf(),
                ),
                QuickAction.InsertKey(TextKeyData.SETTINGS),
                true,
            ),
            Triple(
                QuickActionArrangement(
                    stickyAction = null,
                    dynamicActions = listOf(),
                    hiddenActions = listOf(QuickAction.InsertKey(TextKeyData.SETTINGS)),
                ),
                QuickAction.InsertKey(TextKeyData.SETTINGS),
                true,
            ),
        ) { (arrangement, action, expectedContains) ->
            arrangement.contains(action) shouldBe expectedContains
        }
    }

    context("distinct behavior") {
        withData(
            QuickActionArrangement(
                stickyAction = null,
                dynamicActions = listOf(),
                hiddenActions = listOf(),
            ) to QuickActionArrangement(
                stickyAction = null,
                dynamicActions = listOf(),
                hiddenActions = listOf(),
            ),
            QuickActionArrangement(
                stickyAction = QuickAction.InsertKey(TextKeyData.SETTINGS),
                dynamicActions = listOf(),
                hiddenActions = listOf(),
            ) to QuickActionArrangement(
                stickyAction = QuickAction.InsertKey(TextKeyData.SETTINGS),
                dynamicActions = listOf(),
                hiddenActions = listOf(),
            ),
            QuickActionArrangement(
                stickyAction = QuickAction.InsertKey(TextKeyData.SETTINGS),
                dynamicActions = listOf(
                    QuickAction.InsertKey(TextKeyData.SETTINGS),
                ),
                hiddenActions = listOf(),
            ) to QuickActionArrangement(
                stickyAction = QuickAction.InsertKey(TextKeyData.SETTINGS),
                dynamicActions = listOf(),
                hiddenActions = listOf(),
            ),
            QuickActionArrangement(
                stickyAction = QuickAction.InsertKey(TextKeyData.SETTINGS),
                dynamicActions = listOf(
                    QuickAction.InsertKey(TextKeyData.SETTINGS),
                    QuickAction.InsertKey(TextKeyData.SETTINGS),
                ),
                hiddenActions = listOf(),
            ) to QuickActionArrangement(
                stickyAction = QuickAction.InsertKey(TextKeyData.SETTINGS),
                dynamicActions = listOf(),
                hiddenActions = listOf(),
            ),
            QuickActionArrangement(
                stickyAction = QuickAction.InsertKey(TextKeyData.SETTINGS),
                dynamicActions = listOf(
                    QuickAction.InsertKey(TextKeyData.CLIPBOARD_SELECT_ALL),
                ),
                hiddenActions = listOf(
                    QuickAction.InsertKey(TextKeyData.VIEW_SYMBOLS),
                ),
            ) to QuickActionArrangement(
                stickyAction = QuickAction.InsertKey(TextKeyData.SETTINGS),
                dynamicActions = listOf(
                    QuickAction.InsertKey(TextKeyData.CLIPBOARD_SELECT_ALL),
                ),
                hiddenActions = listOf(
                    QuickAction.InsertKey(TextKeyData.VIEW_SYMBOLS),
                ),
            ),
            QuickActionArrangement(
                stickyAction = null,
                dynamicActions = listOf(
                    QuickAction.InsertKey(TextKeyData.CLIPBOARD_SELECT_ALL),
                ),
                hiddenActions = listOf(
                    QuickAction.InsertKey(TextKeyData.CLIPBOARD_SELECT_ALL),
                    QuickAction.InsertKey(TextKeyData.VIEW_SYMBOLS),
                ),
            ) to QuickActionArrangement(
                stickyAction = null,
                dynamicActions = listOf(
                    QuickAction.InsertKey(TextKeyData.CLIPBOARD_SELECT_ALL),
                ),
                hiddenActions = listOf(
                    QuickAction.InsertKey(TextKeyData.VIEW_SYMBOLS),
                ),
            ),
            QuickActionArrangement(
                stickyAction = null,
                dynamicActions = listOf(
                    QuickAction.InsertKey(TextKeyData.CLIPBOARD_SELECT_ALL),
                ),
                hiddenActions = listOf(
                    QuickAction.InsertKey(TextKeyData.VIEW_SYMBOLS),
                    QuickAction.InsertKey(TextKeyData.CLIPBOARD_SELECT_ALL),
                ),
            ) to QuickActionArrangement(
                stickyAction = null,
                dynamicActions = listOf(
                    QuickAction.InsertKey(TextKeyData.CLIPBOARD_SELECT_ALL),
                ),
                hiddenActions = listOf(
                    QuickAction.InsertKey(TextKeyData.VIEW_SYMBOLS),
                ),
            ),
        ) { (beforeDistinct, afterDistinct) ->
            beforeDistinct.distinct() shouldBe afterDistinct
        }
    }
    context("snippet picker action (Victorious Keyboard)") {
        val snippets = QuickAction.InsertKey(TextKeyData.IME_UI_MODE_SNIPPETS)
        val old = QuickActionArrangement(
            stickyAction = QuickAction.InsertKey(TextKeyData.VOICE_INPUT),
            dynamicActions = listOf(QuickAction.InsertKey(TextKeyData.UNDO)),
            hiddenActions = listOf(QuickAction.InsertKey(TextKeyData.REDO)),
        )

        test("the default arrangement offers it, exactly once") {
            QuickActionArrangement.Default.contains(snippets) shouldBe true
            QuickActionArrangement.Default.dynamicActions.count { it == snippets } shouldBe 1
            QuickActionArrangement.Default.distinct() shouldBe QuickActionArrangement.Default
        }
        test("its key data is distinct from the clipboard action's and keeps the stored code") {
            TextKeyData.IME_UI_MODE_SNIPPETS.code shouldBe KeyCode.IME_UI_MODE_SNIPPETS
            TextKeyData.IME_UI_MODE_SNIPPETS.label shouldBe "ime_ui_mode_snippets"
            (TextKeyData.IME_UI_MODE_SNIPPETS.label == TextKeyData.IME_UI_MODE_CLIPBOARD.label) shouldBe false
            (KeyCode.IME_UI_MODE_SNIPPETS == KeyCode.IME_UI_MODE_CLIPBOARD) shouldBe false
            // Stored in users' quick-action preference: this number must never change.
            KeyCode.IME_UI_MODE_SNIPPETS shouldBe -214
        }
        test("an existing arrangement gets it appended to the dynamic actions, nothing else moves") {
            val migrated = old.withActionIfAbsent(snippets)
            migrated.dynamicActions shouldBe old.dynamicActions + snippets
            migrated.stickyAction shouldBe old.stickyAction
            migrated.hiddenActions shouldBe old.hiddenActions
        }
        test("migrating twice changes nothing more (it runs on every preference load)") {
            val once = old.withActionIfAbsent(snippets)
            once.withActionIfAbsent(snippets) shouldBe once
        }
        test("a user who hid it keeps it hidden") {
            val hidden = old.copy(hiddenActions = old.hiddenActions + snippets)
            hidden.withActionIfAbsent(snippets) shouldBe hidden
        }
        test("a user who made it sticky keeps it sticky") {
            val sticky = old.copy(stickyAction = snippets)
            sticky.withActionIfAbsent(snippets) shouldBe sticky
        }
        test("it survives the JSON round trip used by the preference") {
            val arrangement = old.withActionIfAbsent(snippets)
            val json = QuickActionJsonConfig.encodeToString(arrangement)
            QuickActionJsonConfig.decodeFromString<QuickActionArrangement>(json) shouldBe arrangement
        }
    }
})
