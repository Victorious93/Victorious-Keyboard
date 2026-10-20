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

package com.victorious.keyboard.app.settings.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.victorious.keyboard.R
import com.victorious.keyboard.ime.theme.colors.KeyboardColorMath
import com.victorious.keyboard.ime.window.rememberImeBottomClearance
import com.victorious.keyboard.ime.theme.colors.KeyboardColorRole
import com.victorious.keyboard.ime.theme.colors.KeyboardColorTokens
import com.victorious.keyboard.ime.theme.colors.KeyboardPalette
import com.victorious.keyboard.ime.theme.colors.KeyboardPaletteCodec
import com.victorious.keyboard.ime.theme.colors.KeyboardPalettes
import com.victorious.keyboard.lib.compose.FlorisScreen
import dev.patrickgold.jetpref.datastore.model.observeAsState
import dev.patrickgold.jetpref.datastore.ui.ExperimentalJetPrefDatastoreUi
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlinx.coroutines.launch
import org.florisboard.lib.compose.stringRes

@OptIn(ExperimentalJetPrefDatastoreUi::class)
@Composable
fun KeyboardColorsScreen() = FlorisScreen {
    title = stringRes(R.string.settings__keyboard_colors__title)
    previewFieldVisible = true

    content {
        val stored by prefs.theme.keyboardColors.observeAsState()
        var palette by remember { mutableStateOf(KeyboardPaletteCodec.decode(stored)) }
        var picking by remember { mutableStateOf<KeyboardColorRole?>(null) }
        val scope = rememberCoroutineScope()
        LaunchedEffect(stored) {
            palette = KeyboardPaletteCodec.decode(stored)
        }
        fun save(next: KeyboardPalette) {
            palette = next
            scope.launch { prefs.theme.keyboardColors.set(KeyboardPaletteCodec.encode(next)) }
        }

        Text(
            text = stringRes(R.string.settings__keyboard_colors__summary),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        KeyboardPreview(palette)
        Text(
            text = stringRes(R.string.settings__keyboard_colors__presets),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            KeyboardPalettes.presets.forEach { preset ->
                PresetChip(
                    label = stringRes(presetLabel(preset.id)),
                    palette = preset.palette,
                    selected = palette == preset.palette,
                    onClick = { save(preset.palette) },
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        KeyboardColorRole.entries.forEach { role ->
            val shown = if (role == KeyboardColorRole.KEY_TEXT) palette.resolved().keyText else palette.color(role)
            ColorRow(
                label = stringRes(roleLabel(role)),
                color = shown,
                caption = if (role == KeyboardColorRole.KEY_TEXT && shown != palette.keyText) {
                    stringRes(R.string.settings__keyboard_colors__contrast_note)
                } else {
                    null
                },
                onClick = { picking = role },
            )
        }
        TextButton(
            onClick = { save(KeyboardPalettes.Neon) },
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Text(stringRes(R.string.settings__keyboard_colors__reset))
        }
        Text(
            text = stringRes(R.string.settings__keyboard_colors__scope),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )

        picking?.let { role ->
            ColorPickerDialog(
                title = stringRes(roleLabel(role)),
                initial = palette.color(role),
                onDismiss = { picking = null },
                onApply = { color ->
                    save(palette.with(role, color))
                    picking = null
                },
            )
        }
    }
}

@Composable
private fun KeyboardPreview(palette: KeyboardPalette) {
    val resolved = palette.resolved()
    val tokens = KeyboardColorTokens.from(resolved)
    val bg = resolved.background.compose()
    val key = resolved.key.compose()
    val text = resolved.keyText.compose()
    val accent = resolved.primary.compose()
    val highlight = resolved.secondary.compose()
    val pill = resolved.suggestion.compose()
    val pillText = tokens.suggestionText.compose()
    val onAccent = tokens.defines.getValue("--on-primary").compose()
    Column(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .padding(10.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("termux", "search").forEachIndexed { index, word ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(pill)
                        .border(1.dp, accent.copy(alpha = 0.7f), RoundedCornerShape(50))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Text(word, color = pillText, style = MaterialTheme.typography.labelLarge)
                }
                if (index == 0) Spacer(Modifier.width(0.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("Tab", "Esc", "Ctrl").forEach { label ->
                KeyCap(
                    label = label,
                    fill = if (label == "Ctrl") accent else key,
                    ink = if (label == "Ctrl") onAccent else text,
                    border = accent,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("q", "w", "e", "r", "t").forEach { label ->
                KeyCap(label, key, text, accent, Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            KeyCap("123", key, text, accent, Modifier.weight(1.1f))
            Box(
                modifier = Modifier
                    .weight(2.4f)
                    .height(36.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(key)
                    .border(1.dp, highlight, RoundedCornerShape(18.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text("English", color = text.copy(alpha = 0.8f), style = MaterialTheme.typography.labelMedium)
            }
            KeyCap("↵", accent, onAccent, accent, Modifier.weight(1.2f), filled = true)
        }
        val bottomGap = rememberImeBottomClearance(allowFallback = false).effectiveGapDp
        if (bottomGap > 0) {
            Spacer(Modifier.height(bottomGap.dp))
        }
    }
}

@Composable
private fun KeyCap(
    label: String,
    fill: Color,
    ink: Color,
    border: Color,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
) {
    Box(
        modifier = modifier
            .height(36.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(fill)
            .border(1.dp, if (filled) Color.Transparent else border.copy(alpha = 0.55f), RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = ink, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
    }
}

@Composable
private fun PresetChip(label: String, palette: KeyboardPalette, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier = Modifier
            .width(88.dp)
            .clip(shape)
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            listOf(palette.background, palette.key, palette.primary, palette.secondary).forEach { color ->
                Box(
                    Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(color.compose())
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), CircleShape),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, maxLines = 2)
    }
}

@Composable
private fun ColorRow(label: String, color: Int, caption: String?, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(color.compose())
                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
            )
            Column(Modifier.padding(start = 14.dp).weight(1f)) {
                Text(label, style = MaterialTheme.typography.titleSmall)
                Text(
                    KeyboardColorMath.hex(color),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (caption != null) {
                    Text(caption, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
private fun ColorPickerDialog(
    title: String,
    initial: Int,
    onDismiss: () -> Unit,
    onApply: (Int) -> Unit,
) {
    val start = KeyboardColorMath.toHsv(initial)
    var hue by remember { mutableFloatStateOf(start.first) }
    var sat by remember { mutableFloatStateOf(start.second) }
    var value by remember { mutableFloatStateOf(start.third) }
    var hex by remember { mutableStateOf(KeyboardColorMath.hex(initial)) }
    val color = KeyboardColorMath.hsv(hue, sat, value)
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(12.dp))
                HueSatWheel(hue, sat, value) { h, s ->
                    hue = h
                    sat = s
                    hex = KeyboardColorMath.hex(KeyboardColorMath.hsv(h, s, value))
                }
                Spacer(Modifier.height(12.dp))
                Slider(
                    value = value,
                    onValueChange = {
                        value = it
                        hex = KeyboardColorMath.hex(KeyboardColorMath.hsv(hue, sat, it))
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = hex,
                    onValueChange = { next ->
                        hex = next
                        KeyboardColorMath.parseHex(next)?.let { parsed ->
                            val hsv = KeyboardColorMath.toHsv(parsed)
                            hue = hsv.first
                            sat = hsv.second
                            value = hsv.third
                        }
                    },
                    singleLine = true,
                    label = { Text(stringRes(R.string.settings__keyboard_colors__hex)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text(stringRes(R.string.action__cancel)) }
                    TextButton(onClick = {
                        val parsed = KeyboardColorMath.parseHex(hex) ?: color
                        onApply(parsed or 0xFF000000.toInt())
                    }) { Text(stringRes(R.string.action__apply)) }
                }
            }
        }
    }
}

@Composable
private fun HueSatWheel(
    hue: Float,
    saturation: Float,
    value: Float,
    onChange: (Float, Float) -> Unit,
) {
    val hues = listOf(
        Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red,
    )
    Canvas(
        Modifier
            .size(220.dp)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    fun at(position: Offset) {
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val delta = position - center
                        val radius = minOf(size.width, size.height) / 2f
                        val dist = hypot(delta.x, delta.y).coerceAtMost(radius)
                        val angle = Math.toDegrees(atan2(delta.y.toDouble(), delta.x.toDouble())).toFloat()
                        val nextHue = (angle + 360f) % 360f
                        val nextSat = (dist / radius).coerceIn(0f, 1f)
                        onChange(nextHue, nextSat)
                    }
                    at(down.position)
                    drag(down.id) { change ->
                        at(change.position)
                        change.consume()
                    }
                }
            },
    ) {
        val radius = minOf(size.width, size.height) / 2f
        drawCircle(Brush.sweepGradient(hues))
        drawCircle(Brush.radialGradient(listOf(Color.White, Color.Transparent)))
        drawCircle(Color.Black.copy(alpha = 1f - value))
        val rad = Math.toRadians(hue.toDouble())
        val point = center + Offset(
            (cos(rad) * saturation * radius).toFloat(),
            (sin(rad) * saturation * radius).toFloat(),
        )
        drawCircle(Color.White, radius = 12.dp.toPx(), center = point)
        drawCircle(KeyboardColorMath.hsv(hue, saturation, value).compose(), radius = 8.dp.toPx(), center = point)
    }
}

private fun Int.compose(): Color {
    val a = ((this ushr 24) and 0xFF) / 255f
    val r = ((this ushr 16) and 0xFF) / 255f
    val g = ((this ushr 8) and 0xFF) / 255f
    val b = (this and 0xFF) / 255f
    return Color(r, g, b, a)
}

private fun roleLabel(role: KeyboardColorRole): Int = when (role) {
    KeyboardColorRole.BACKGROUND -> R.string.settings__keyboard_colors__background
    KeyboardColorRole.KEY -> R.string.settings__keyboard_colors__key
    KeyboardColorRole.KEY_TEXT -> R.string.settings__keyboard_colors__key_text
    KeyboardColorRole.PRIMARY -> R.string.settings__keyboard_colors__primary
    KeyboardColorRole.SECONDARY -> R.string.settings__keyboard_colors__secondary
    KeyboardColorRole.SUGGESTION -> R.string.settings__keyboard_colors__suggestion
}

private fun presetLabel(id: String): Int = when (id) {
    "black" -> R.string.settings__keyboard_colors__preset_black
    "neon" -> R.string.settings__keyboard_colors__preset_neon
    "cyber" -> R.string.settings__keyboard_colors__preset_cyber
    "matrix" -> R.string.settings__keyboard_colors__preset_matrix
    "purple" -> R.string.settings__keyboard_colors__preset_purple
    "red" -> R.string.settings__keyboard_colors__preset_red
    else -> R.string.settings__keyboard_colors__preset_mono
}
