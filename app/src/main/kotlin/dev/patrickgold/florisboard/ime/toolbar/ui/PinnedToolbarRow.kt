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

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import dev.patrickgold.florisboard.FlorisImeService
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.ime.input.LocalInputFeedbackController
import dev.patrickgold.florisboard.ime.keyboard.FlorisImeSizing
import dev.patrickgold.florisboard.ime.text.keyboard.TextKeyData
import dev.patrickgold.florisboard.ime.theme.FlorisImeUi
import dev.patrickgold.florisboard.ime.toolbar.PinnedToolbar
import dev.patrickgold.florisboard.ime.toolbar.ToolbarActionId
import dev.patrickgold.florisboard.ime.toolbar.ToolbarRuntime
import dev.patrickgold.florisboard.keyboardManager
import dev.patrickgold.jetpref.datastore.model.observeAsState
import org.florisboard.lib.snygg.ui.SnyggIcon
import org.florisboard.lib.snygg.ui.SnyggIconButton
import org.florisboard.lib.snygg.ui.SnyggRow
import org.florisboard.lib.snygg.ui.SnyggText

/** Deep link of the screen where the row is customized (`Routes.Settings.Toolbar`). */
private const val CUSTOMIZE_PATH = "settings/smartbar/toolbar"

/**
 * The customizable toolbar row above the suggestion strip: the buttons the user pinned ([PinnedToolbar]), then a
 * button that opens the screen where they are chosen and ordered. A pinned button runs through the same path as its
 * tile in the tools panel. Drawn with the theme's smartbar elements, so every theme applies. Nothing here is logged.
 *
 * Draws nothing when the row is switched off or empty; [FlorisImeSizing.smartbarRowCountAsState] uses the same rule.
 */
@Composable
fun PinnedToolbarRow(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val keyboardManager by context.keyboardManager()
    val prefs by FlorisPreferenceStore
    val inputFeedbackController = LocalInputFeedbackController.current
    LaunchedEffect(Unit) { ToolbarRuntime.ensure(context) }
    val config by ToolbarRuntime.config.collectAsState()
    val showScreenshot by prefs.tools.screenshotTile.observeAsState()
    val actions = remember(config.pinned, showScreenshot) { config.pinnedActions(showScreenshot) }
    if (!PinnedToolbar.rowVisible(config.pinnedRow, actions)) return

    val buttonHeight = FlorisImeSizing.smartbarHeight
    SnyggRow(
        elementName = FlorisImeUi.Smartbar.elementName,
        modifier = modifier.fillMaxWidth().height(buttonHeight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f).fillMaxHeight().horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            for (id in actions) {
                PinnedButton(id = id) {
                    inputFeedbackController.keyPress(TextKeyData.UNSPECIFIED)
                    keyboardManager.setToolsOpen(false)
                    keyboardManager.performToolbarAction(id)
                }
            }
        }
        val customize = stringResource(R.string.settings__toolbar__title)
        SnyggIconButton(
            elementName = FlorisImeUi.SmartbarSharedActionsToggle.elementName,
            onClick = { FlorisImeService.launchSettingsAt(CUSTOMIZE_PATH) },
            modifier = Modifier
                .sizeIn(maxHeight = buttonHeight)
                .aspectRatio(1f)
                .semantics { contentDescription = customize },
        ) {
            SnyggIcon(imageVector = Icons.Outlined.Tune)
        }
    }
}

@Composable
private fun PinnedButton(id: ToolbarActionId, onClick: () -> Unit) {
    val item = ToolsPanelItems.item(id) ?: return
    val label = stringResource(item.label)
    val elementName = FlorisImeUi.SmartbarSharedActionsToggle.elementName
    SnyggIconButton(
        elementName = elementName,
        onClick = onClick,
        modifier = Modifier
            .sizeIn(maxHeight = FlorisImeSizing.smartbarHeight)
            .aspectRatio(1f)
            .semantics { contentDescription = label },
    ) {
        when (val glyph = item.glyph) {
            is ToolGlyph.Vector -> SnyggIcon(imageVector = glyph.imageVector)
            is ToolGlyph.Drawable -> SnyggIcon(painter = painterResource(glyph.id))
            is ToolGlyph.Text -> SnyggText(
                elementName = elementName,
                text = glyph.text,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Clip,
                softWrap = false,
            )
        }
    }
}
