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

package dev.patrickgold.florisboard.app.settings.manual

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.manual.ManualDocument
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import dev.patrickgold.florisboard.lib.io.FlorisRef
import dev.patrickgold.florisboard.lib.io.loadTextAsset
import org.florisboard.lib.compose.florisVerticalScroll
import org.florisboard.lib.compose.stringRes

/**
 * Settings → Help. Reads the bundled manual. Nothing is fetched.
 */
@Composable
fun ManualScreen() = FlorisScreen {
    title = stringRes(R.string.settings__manual__title)
    previewFieldVisible = false
    scrollable = false
    iconSpaceReserved = false

    val context = LocalContext.current
    val markdown = remember {
        FlorisRef.assets(ManualDocument.ASSET_PATH).loadTextAsset(context).getOrElse { "" }
    }
    val blocks = remember(markdown) { ManualDocument.parse(markdown) }

    content {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .florisVerticalScroll()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            ManualHeaderArt(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp),
            )
            Spacer(modifier = Modifier.height(16.dp))
            if (markdown.isBlank()) {
                Text(text = stringRes(R.string.settings__manual__missing))
            } else {
                blocks.forEach { block ->
                    when (block) {
                        is ManualDocument.Block.Image -> if (!ManualDocument.isHeaderArt(block.path)) {
                            ManualPictureSlot(block)
                        }
                        else -> ManualBlock(block)
                    }
                }
            }
        }
    }
}

@Composable
private fun ManualHeaderArt(modifier: Modifier = Modifier) {
    val cyan = Color(0xFF22F0FF)
    val magenta = Color(0xFFFF2BD6)
    Canvas(modifier = modifier) {
        drawRoundRect(color = Color(0xFF010619), cornerRadius = androidx.compose.ui.geometry.CornerRadius(28f, 28f))
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = size.minDimension * 0.28f
        drawCircle(
            brush = Brush.sweepGradient(listOf(cyan, magenta, cyan), center = center),
            radius = radius,
            center = center,
            style = Stroke(width = radius * 0.16f, cap = StrokeCap.Round),
        )
        drawCircle(
            color = Color(0xFF7DF9FF).copy(alpha = 0.45f),
            radius = radius * 0.55f,
            center = center,
            style = Stroke(width = 3f),
        )
    }
}

@Composable
private fun ManualPictureSlot(image: ManualDocument.Block.Image) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
            .padding(12.dp),
    ) {
        Text(
            text = stringRes(R.string.settings__manual__picture_todo),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        if (image.alt.isNotEmpty()) {
            Text(text = image.alt, style = MaterialTheme.typography.bodyMedium)
        }
        Text(
            text = image.path,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
        )
    }
}

@Composable
private fun ManualBlock(block: ManualDocument.Block) {
    when (block) {
        is ManualDocument.Block.Heading -> {
            Spacer(modifier = Modifier.height(if (block.level <= 2) 16.dp else 10.dp))
            Text(
                text = inlineText(block.text),
                style = when (block.level) {
                    1 -> MaterialTheme.typography.headlineMedium
                    2 -> MaterialTheme.typography.headlineSmall
                    else -> MaterialTheme.typography.titleMedium
                },
            )
        }
        is ManualDocument.Block.Paragraph -> {
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = inlineText(block.text), style = MaterialTheme.typography.bodyLarge)
        }
        is ManualDocument.Block.Bullet -> {
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = inlineText("• ${block.text}"), style = MaterialTheme.typography.bodyLarge)
        }
        is ManualDocument.Block.Numbered -> {
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = inlineText("${block.number}. ${block.text}"), style = MaterialTheme.typography.bodyLarge)
        }
        is ManualDocument.Block.Code -> {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = block.text,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        is ManualDocument.Block.Rule -> Spacer(modifier = Modifier.height(12.dp))
        is ManualDocument.Block.Image -> Unit
    }
}

@Composable
private fun inlineText(text: String) = buildAnnotatedString {
    val codeStyle = SpanStyle(
        fontFamily = FontFamily.Monospace,
        background = MaterialTheme.colorScheme.surfaceVariant,
    )
    ManualDocument.inlines(text).forEach { span ->
        when {
            span.bold -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(span.text) }
            span.code -> withStyle(codeStyle) { append(span.text) }
            else -> append(span.text)
        }
    }
}
