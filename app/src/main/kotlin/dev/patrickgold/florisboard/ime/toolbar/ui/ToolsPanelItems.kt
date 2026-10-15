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

package dev.patrickgold.florisboard.ime.toolbar.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.FormatIndentDecrease
import androidx.compose.material.icons.automirrored.outlined.FormatIndentIncrease
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.ShortText
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.DataObject
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.EmojiEmotions
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.QuestionAnswer
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Spellcheck
import androidx.compose.material.icons.outlined.Summarize
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.VerticalAlignBottom
import androidx.compose.ui.graphics.vector.ImageVector
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.ime.toolbar.ToolbarActionId
import dev.patrickgold.florisboard.ime.toolbar.ToolsTab

/**
 * What sits above a tile's label: a vector icon, a drawable, or a short glyph such as `^C` or `→`. Glyphs stay within
 * ASCII and the basic arrows (U+2190..U+2193) that the developer row already uses, so they render in the default font.
 */
sealed interface ToolGlyph {
    data class Vector(val imageVector: ImageVector) : ToolGlyph
    data class Drawable(@param:DrawableRes val id: Int) : ToolGlyph
    data class Text(val text: String) : ToolGlyph
}

data class ToolItem(val glyph: ToolGlyph, @param:StringRes val label: Int)

/** Icons and translatable labels for the tools panel. Every id in `ToolsCatalog` has an entry (checked by a test). */
object ToolsPanelItems {
    @StringRes
    fun tabLabel(tab: ToolsTab): Int = when (tab) {
        ToolsTab.CLIPBOARD -> R.string.tools__tab__clipboard
        ToolsTab.AI -> R.string.tools__tab__ai
        ToolsTab.TERMINAL -> R.string.tools__tab__terminal
        ToolsTab.CODE -> R.string.tools__tab__code
        ToolsTab.QUICK -> R.string.tools__tab__quick
    }

    fun item(id: ToolbarActionId): ToolItem? = items[id]

    private fun v(imageVector: ImageVector, @StringRes label: Int) = ToolItem(ToolGlyph.Vector(imageVector), label)
    private fun t(text: String, @StringRes label: Int) = ToolItem(ToolGlyph.Text(text), label)
    private fun d(@DrawableRes id: Int, @StringRes label: Int) = ToolItem(ToolGlyph.Drawable(id), label)

    private val items: Map<ToolbarActionId, ToolItem> = mapOf(
        ToolbarActionId.OPEN_CLIPBOARD to v(Icons.Outlined.History, R.string.tools__action__open_clipboard),
        ToolbarActionId.PASTE to v(Icons.Outlined.ContentPaste, R.string.tools__action__paste),
        ToolbarActionId.COPY to v(Icons.Outlined.ContentCopy, R.string.tools__action__copy),
        ToolbarActionId.CUT to v(Icons.Outlined.ContentCut, R.string.tools__action__cut),
        ToolbarActionId.SEL_ALL to v(Icons.Outlined.SelectAll, R.string.tools__action__sel_all),
        ToolbarActionId.UNDO to v(Icons.AutoMirrored.Outlined.Undo, R.string.tools__action__undo),
        ToolbarActionId.REDO to v(Icons.AutoMirrored.Outlined.Redo, R.string.tools__action__redo),
        ToolbarActionId.OPEN_SNIPPETS to v(Icons.AutoMirrored.Outlined.ShortText, R.string.tools__action__open_snippets),
        ToolbarActionId.EDIT_CLIP to v(Icons.Outlined.EditNote, R.string.tools__action__edit_clip),
        ToolbarActionId.PINNED to v(Icons.Outlined.PushPin, R.string.tools__action__pinned),
        ToolbarActionId.OPEN_VAULT to v(Icons.Outlined.Lock, R.string.tools__action__open_vault),
        ToolbarActionId.ZIP to v(Icons.Outlined.FolderZip, R.string.tools__action__zip),

        ToolbarActionId.AI_ASK to v(Icons.Outlined.QuestionAnswer, R.string.tools__action__ai_ask),
        ToolbarActionId.AI_REWRITE to v(Icons.Outlined.AutoFixHigh, R.string.tools__action__ai_rewrite),
        ToolbarActionId.AI_GRAMMAR to v(Icons.Outlined.Spellcheck, R.string.tools__action__ai_grammar),
        ToolbarActionId.AI_CONTINUE to v(Icons.AutoMirrored.Outlined.Notes, R.string.tools__action__ai_continue),
        ToolbarActionId.AI_SUMMARIZE to v(Icons.Outlined.Summarize, R.string.tools__action__ai_summarize),
        ToolbarActionId.AI_TRANSLATE to v(Icons.Outlined.Translate, R.string.tools__action__ai_translate),
        ToolbarActionId.AI_SHELL to v(Icons.Outlined.Terminal, R.string.tools__action__ai_shell),

        ToolbarActionId.CTRL to t("Ctrl", R.string.tools__action__ctrl),
        ToolbarActionId.ALT to t("Alt", R.string.tools__action__alt),
        ToolbarActionId.ESC to t("Esc", R.string.tools__action__esc),
        ToolbarActionId.TAB to t("Tab", R.string.tools__action__tab),
        ToolbarActionId.LEFT to t("←", R.string.tools__action__left),
        ToolbarActionId.DOWN to t("↓", R.string.tools__action__down),
        ToolbarActionId.UP to t("↑", R.string.tools__action__up),
        ToolbarActionId.RIGHT to t("→", R.string.tools__action__right),
        ToolbarActionId.HOME to t("Home", R.string.tools__action__home),
        ToolbarActionId.END to t("End", R.string.tools__action__end),
        ToolbarActionId.PGUP to t("PgUp", R.string.tools__action__pgup),
        ToolbarActionId.PGDN to t("PgDn", R.string.tools__action__pgdn),
        ToolbarActionId.CTRL_C to t("^C", R.string.tools__action__ctrl_c),
        ToolbarActionId.CTRL_D to t("^D", R.string.tools__action__ctrl_d),
        ToolbarActionId.CTRL_Z to t("^Z", R.string.tools__action__ctrl_z),
        ToolbarActionId.CTRL_L to t("^L", R.string.tools__action__ctrl_l),
        ToolbarActionId.CTRL_R to t("^R", R.string.tools__action__ctrl_r),
        ToolbarActionId.DEVROW to v(Icons.Outlined.Keyboard, R.string.tools__action__devrow),

        ToolbarActionId.CAMEL to t("aB", R.string.tools__action__camel),
        ToolbarActionId.SNAKE to t("a_b", R.string.tools__action__snake),
        ToolbarActionId.KEBAB to t("a-b", R.string.tools__action__kebab),
        ToolbarActionId.PASCAL to t("AB", R.string.tools__action__pascal),
        ToolbarActionId.INDENT to v(Icons.AutoMirrored.Outlined.FormatIndentIncrease, R.string.tools__action__indent),
        ToolbarActionId.OUTDENT to v(Icons.AutoMirrored.Outlined.FormatIndentDecrease, R.string.tools__action__outdent),
        ToolbarActionId.COMMENT to t("//", R.string.tools__action__comment),
        ToolbarActionId.JSON to v(Icons.Outlined.DataObject, R.string.tools__action__json),
        ToolbarActionId.B64E to t("→b64", R.string.tools__action__b64e),
        ToolbarActionId.B64D to t("b64→", R.string.tools__action__b64d),
        ToolbarActionId.UUID to v(Icons.Outlined.Fingerprint, R.string.tools__action__uuid),
        ToolbarActionId.TIME to v(Icons.Outlined.Schedule, R.string.tools__action__time),

        ToolbarActionId.INCOGNITO to d(R.drawable.ic_incognito, R.string.tools__action__incognito),
        ToolbarActionId.LEARNING to v(Icons.Outlined.Psychology, R.string.tools__action__learning),
        ToolbarActionId.EMOJI to v(Icons.Outlined.EmojiEmotions, R.string.tools__action__emoji),
        ToolbarActionId.VOICE to v(Icons.Outlined.Mic, R.string.tools__action__voice),
        ToolbarActionId.ONE_HAND to d(R.drawable.ic_accessibility_one_handed, R.string.tools__action__one_hand),
        ToolbarActionId.BOTTOM_GAP to v(Icons.Outlined.VerticalAlignBottom, R.string.tools__action__bottom_gap),
        ToolbarActionId.THEME to v(Icons.Outlined.Palette, R.string.tools__action__theme),
        ToolbarActionId.SETTINGS to v(Icons.Outlined.Settings, R.string.tools__action__settings),
    )
}
