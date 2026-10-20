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

package dev.patrickgold.florisboard.ime.media.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.ime.media.FileManagerReply
import dev.patrickgold.florisboard.ime.media.MediaCommand
import dev.patrickgold.florisboard.ime.media.MediaRuntime
import dev.patrickgold.florisboard.ime.theme.FlorisImeUi
import dev.patrickgold.florisboard.keyboardManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.florisboard.lib.compose.stringRes
import org.florisboard.lib.snygg.ui.SnyggChip
import org.florisboard.lib.snygg.ui.SnyggText

/**
 * Play, skip, seek, and volume. The title is shown only when File Manager seek is connected.
 * The title is not logged.
 */
@Composable
fun MediaPanel(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val keyboardManager by context.keyboardManager()
    var title by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        while (true) {
            val bridge = keyboardManager.mediaBridgePackage()
            val state = withContext(Dispatchers.IO) { MediaRuntime.nowPlaying(context, bridge) }
            title = if (state is FileManagerReply.State) state.title else ""
            delay(2_000)
        }
    }
    Column(modifier = modifier.fillMaxWidth().padding(8.dp)) {
        Row(modifier = Modifier.fillMaxWidth()) {
            SnyggText(
                elementName = FlorisImeUi.SmartbarActionsEditorSubheader.elementName,
                text = title.ifBlank { stringRes(R.string.tools__action__media) },
                modifier = Modifier.weight(1f),
            )
            SnyggChip(
                elementName = FlorisImeUi.ClipboardFilterChip.elementName,
                onClick = { keyboardManager.setMediaOpen(false) },
                text = stringRes(R.string.media__close),
            )
        }
        MediaRow(
            listOf(
                R.string.media__prev to MediaCommand.PREVIOUS,
                R.string.media__play to MediaCommand.PLAY_PAUSE,
                R.string.media__next to MediaCommand.NEXT,
            ),
        )
        MediaRow(
            listOf(
                R.string.media__rewind to MediaCommand.REWIND,
                R.string.media__forward to MediaCommand.FAST_FORWARD,
            ),
        )
        MediaRow(
            listOf(
                R.string.media__vol_down to MediaCommand.VOLUME_DOWN,
                R.string.media__vol_up to MediaCommand.VOLUME_UP,
            ),
        )
    }
}

@Composable
private fun MediaRow(buttons: List<Pair<Int, MediaCommand>>) {
    val context = LocalContext.current
    val keyboardManager by context.keyboardManager()
    Row(modifier = Modifier.fillMaxWidth()) {
        for ((label, command) in buttons) {
            SnyggChip(
                elementName = FlorisImeUi.ClipboardFilterChip.elementName,
                onClick = { keyboardManager.performMedia(command) },
                text = stringRes(label),
                modifier = Modifier.padding(top = 8.dp, end = 8.dp),
            )
        }
    }
}
