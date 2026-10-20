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

package com.victorious.keyboard.ime.theme.colors

import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * The six colors a person can choose. Stored as opaque ARGB ints.
 * [keyText] is what they asked for; [resolved] replaces it when it would be hard to read on [key].
 */
data class KeyboardPalette(
    val background: Int,
    val key: Int,
    val keyText: Int,
    val primary: Int,
    val secondary: Int,
    val suggestion: Int,
) {
    fun resolved(): KeyboardPalette {
        val text = if (KeyboardColorMath.contrast(keyText, key) >= MIN_TEXT_CONTRAST) {
            keyText
        } else {
            KeyboardColorMath.readableOn(key)
        }
        return copy(keyText = text)
    }

    fun with(role: KeyboardColorRole, color: Int): KeyboardPalette = when (role) {
        KeyboardColorRole.BACKGROUND -> copy(background = color)
        KeyboardColorRole.KEY -> copy(key = color)
        KeyboardColorRole.KEY_TEXT -> copy(keyText = color)
        KeyboardColorRole.PRIMARY -> copy(primary = color)
        KeyboardColorRole.SECONDARY -> copy(secondary = color)
        KeyboardColorRole.SUGGESTION -> copy(suggestion = color)
    }

    fun color(role: KeyboardColorRole): Int = when (role) {
        KeyboardColorRole.BACKGROUND -> background
        KeyboardColorRole.KEY -> key
        KeyboardColorRole.KEY_TEXT -> keyText
        KeyboardColorRole.PRIMARY -> primary
        KeyboardColorRole.SECONDARY -> secondary
        KeyboardColorRole.SUGGESTION -> suggestion
    }

    companion object {
        const val MIN_TEXT_CONTRAST = 4.5
    }
}

enum class KeyboardColorRole {
    BACKGROUND,
    KEY,
    KEY_TEXT,
    PRIMARY,
    SECONDARY,
    SUGGESTION,
}

data class KeyboardPreset(
    val id: String,
    val palette: KeyboardPalette,
)

object KeyboardPalettes {
    val Black = KeyboardPalette(
        background = 0xFF050505.toInt(),
        key = 0xFF161616.toInt(),
        keyText = 0xFFE8E8E8.toInt(),
        primary = 0xFF3A3A3A.toInt(),
        secondary = 0xFF8A8A8A.toInt(),
        suggestion = 0xFF1C1C1C.toInt(),
    )
    val Neon = KeyboardPalette(
        background = 0xFF010619.toInt(),
        key = 0xFF141C33.toInt(),
        keyText = 0xFFFFF8E7.toInt(),
        primary = 0xFFFFC107.toInt(),
        secondary = 0xFFFF4D8D.toInt(),
        suggestion = 0xFF141C33.toInt(),
    )
    val CyberBlue = KeyboardPalette(
        background = 0xFF020814.toInt(),
        key = 0xFF0B1E33.toInt(),
        keyText = 0xFFE8F7FF.toInt(),
        primary = 0xFF00E5FF.toInt(),
        secondary = 0xFF3D7EFF.toInt(),
        suggestion = 0xFF102A44.toInt(),
    )
    val MatrixGreen = KeyboardPalette(
        background = 0xFF020805.toInt(),
        key = 0xFF0A1A0E.toInt(),
        keyText = 0xFFD8FFD8.toInt(),
        primary = 0xFF39FF14.toInt(),
        secondary = 0xFF00C853.toInt(),
        suggestion = 0xFF0E2414.toInt(),
    )
    val Purple = KeyboardPalette(
        background = 0xFF120818.toInt(),
        key = 0xFF241433.toInt(),
        keyText = 0xFFF6E9FF.toInt(),
        primary = 0xFFC77DFF.toInt(),
        secondary = 0xFFFF4DCD.toInt(),
        suggestion = 0xFF2C1840.toInt(),
    )
    val Red = KeyboardPalette(
        background = 0xFF140608.toInt(),
        key = 0xFF2A1014.toInt(),
        keyText = 0xFFFFE8EA.toInt(),
        primary = 0xFFFF2D55.toInt(),
        secondary = 0xFFFFB300.toInt(),
        suggestion = 0xFF3A141C.toInt(),
    )
    val Monochrome = KeyboardPalette(
        background = 0xFF101114.toInt(),
        key = 0xFF2A2C31.toInt(),
        keyText = 0xFFF4F4F5.toInt(),
        primary = 0xFFF4F4F5.toInt(),
        secondary = 0xFF8E929A.toInt(),
        suggestion = 0xFF3A3D44.toInt(),
    )

    val presets = listOf(
        KeyboardPreset("black", Black),
        KeyboardPreset("neon", Neon),
        KeyboardPreset("cyber", CyberBlue),
        KeyboardPreset("matrix", MatrixGreen),
        KeyboardPreset("purple", Purple),
        KeyboardPreset("red", Red),
        KeyboardPreset("mono", Monochrome),
    )

    fun byId(id: String): KeyboardPalette? = presets.firstOrNull { it.id == id }?.palette
}

/**
 * Colors written into the Victorious Neon stylesheet. Derived from a palette so text stays readable.
 */
data class KeyboardColorTokens(
    val defines: Map<String, Int>,
    val pressedKey: Int,
    val pressedKeyText: Int,
    val suggestionText: Int,
) {
    companion object {
        fun from(raw: KeyboardPalette): KeyboardColorTokens {
            val palette = raw.resolved()
            val bg = palette.background
            val key = palette.key
            val text = palette.keyText
            val primary = palette.primary
            val secondary = palette.secondary
            val suggestion = palette.suggestion
            val onBackground = KeyboardColorMath.readableOn(bg)
            val onPrimary = KeyboardColorMath.readableOn(primary)
            val onSecondary = KeyboardColorMath.readableOn(secondary)
            val suggestionText = KeyboardColorMath.readableOn(suggestion)
            val pressed = KeyboardColorMath.mix(key, primary, 0.22f)
            val pressedKey = if (KeyboardColorMath.contrast(primary, pressed) >= 3.0) {
                pressed
            } else {
                KeyboardColorMath.mix(key, 0xFF000000.toInt(), 0.35f)
            }
            val pressedKeyText = if (KeyboardColorMath.contrast(primary, pressedKey) >= KeyboardPalette.MIN_TEXT_CONTRAST) {
                primary
            } else {
                KeyboardColorMath.readableOn(pressedKey)
            }
            val onSurfaceVariant = run {
                val mixed = KeyboardColorMath.mix(text, primary, 0.28f)
                if (KeyboardColorMath.contrast(mixed, key) >= 3.0) mixed else KeyboardColorMath.mix(text, bg, 0.15f)
            }
            val defines = mapOf(
                "--primary" to primary,
                "--primary-variant" to KeyboardColorMath.shade(primary, 0.86f),
                "--secondary" to secondary,
                "--secondary-variant" to KeyboardColorMath.shade(secondary, 0.82f),
                "--background" to bg,
                "--background-variant" to KeyboardColorMath.mix(bg, key, 0.55f),
                "--surface" to key,
                "--surface-variant" to KeyboardColorMath.mix(key, onBackground, 0.10f),
                "--popup-surface" to KeyboardColorMath.mix(bg, secondary, 0.16f),
                "--focused-popup-surface" to KeyboardColorMath.mix(key, primary, 0.28f),
                "--drag-marker" to secondary,
                "--spacer-color" to KeyboardColorMath.withAlpha(primary, 0.35f),
                "--one-hand-background" to bg,
                "--one-hand-foreground" to onBackground,
                "--incognito-icon-color" to KeyboardColorMath.withAlpha(primary, 0.13f),
                "--on-primary" to onPrimary,
                "--on-secondary" to onSecondary,
                "--on-background" to onBackground,
                "--on-background-disabled" to KeyboardColorMath.withAlpha(onBackground, 0.28f),
                "--on-surface" to text,
                "--on-surface-variant" to onSurfaceVariant,
                "--glow" to primary,
                "--key-border" to KeyboardColorMath.withAlpha(primary, 0.38f),
                "--pink-border" to KeyboardColorMath.withAlpha(secondary, 0.70f),
            )
            return KeyboardColorTokens(defines, pressedKey, pressedKeyText, suggestionText)
        }
    }
}

object KeyboardColorMath {
    fun contrast(a: Int, b: Int): Double {
        val l1 = luminance(a)
        val l2 = luminance(b)
        val lighter = maxOf(l1, l2)
        val darker = minOf(l1, l2)
        return (lighter + 0.05) / (darker + 0.05)
    }

    /** Near-black or warm off-white, whichever is easier to read on [background]. */
    fun readableOn(background: Int): Int {
        val ink = 0xFF1A1200.toInt()
        val paper = 0xFFF6F1E4.toInt()
        return if (contrast(ink, background) >= contrast(paper, background)) ink else paper
    }

    fun mix(a: Int, b: Int, t: Float): Int {
        val weight = t.coerceIn(0f, 1f)
        return argb(
            255,
            channel(a, 16) * (1f - weight) + channel(b, 16) * weight,
            channel(a, 8) * (1f - weight) + channel(b, 8) * weight,
            channel(a, 0) * (1f - weight) + channel(b, 0) * weight,
        )
    }

    /** Multiply the RGB channels. Alpha stays opaque. */
    fun shade(color: Int, factor: Float): Int {
        val f = factor.coerceIn(0f, 1f)
        return argb(255, channel(color, 16) * f, channel(color, 8) * f, channel(color, 0) * f)
    }

    fun withAlpha(color: Int, alpha: Float): Int {
        val a = (alpha.coerceIn(0f, 1f) * 255f).roundToInt()
        return argb(a, channel(color, 16), channel(color, 8), channel(color, 0))
    }

    fun hex(color: Int): String {
        val a = (color ushr 24) and 0xFF
        val rgb = color and 0xFFFFFF
        return if (a == 255) {
            "#%06x".format(rgb)
        } else {
            "#%08x".format(color.toLong() and 0xFFFFFFFFL)
        }
    }

    fun parseHex(raw: String): Int? {
        val text = raw.trim().removePrefix("#").removePrefix("0x").removePrefix("0X")
        if (text.any { it !in '0'..'9' && it !in 'a'..'f' && it !in 'A'..'F' }) return null
        val expanded = when (text.length) {
            3 -> text.map { "$it$it" }.joinToString("")
            6 -> text
            8 -> text
            else -> return null
        }
        val value = expanded.toLongOrNull(16) ?: return null
        return if (expanded.length == 6) {
            (0xFF000000L or value).toInt()
        } else {
            value.toInt()
        }
    }

    fun hsv(hue: Float, saturation: Float, value: Float): Int {
        val h = ((hue % 360f) + 360f) % 360f
        val s = saturation.coerceIn(0f, 1f)
        val v = value.coerceIn(0f, 1f)
        val c = v * s
        val x = c * (1f - kotlin.math.abs((h / 60f) % 2f - 1f))
        val m = v - c
        val (r1, g1, b1) = when {
            h < 60f -> Triple(c, x, 0f)
            h < 120f -> Triple(x, c, 0f)
            h < 180f -> Triple(0f, c, x)
            h < 240f -> Triple(0f, x, c)
            h < 300f -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        return argb(255, (r1 + m) * 255f, (g1 + m) * 255f, (b1 + m) * 255f)
    }

    fun toHsv(color: Int): Triple<Float, Float, Float> {
        val r = channel(color, 16) / 255f
        val g = channel(color, 8) / 255f
        val b = channel(color, 0) / 255f
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val delta = max - min
        val hue = when {
            delta == 0f -> 0f
            max == r -> 60f * (((g - b) / delta) % 6f)
            max == g -> 60f * (((b - r) / delta) + 2f)
            else -> 60f * (((r - g) / delta) + 4f)
        }
        val sat = if (max == 0f) 0f else delta / max
        return Triple((hue + 360f) % 360f, sat, max)
    }

    private fun luminance(color: Int): Double {
        fun channel(value: Float): Double {
            val s = value / 255.0
            return if (s <= 0.04045) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
        }
        val r = channel(channel(color, 16))
        val g = channel(channel(color, 8))
        val b = channel(channel(color, 0))
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    private fun channel(color: Int, shift: Int): Float = ((color ushr shift) and 0xFF).toFloat()

    private fun argb(a: Int, r: Float, g: Float, b: Float): Int {
        fun byte(v: Float) = v.roundToInt().coerceIn(0, 255)
        return (a.coerceIn(0, 255) shl 24) or (byte(r) shl 16) or (byte(g) shl 8) or byte(b)
    }
}

object KeyboardPaletteCodec {
    const val MAX_CHARS = 512
    private val field = Regex(""""([a-zA-Z]+)"\s*:\s*"(#[0-9A-Fa-f]{6})"""")

    fun encode(palette: KeyboardPalette): String = buildString {
        append("{\"v\":1")
        append(",\"background\":\"${KeyboardColorMath.hex(palette.background)}\"")
        append(",\"key\":\"${KeyboardColorMath.hex(palette.key)}\"")
        append(",\"keyText\":\"${KeyboardColorMath.hex(palette.keyText)}\"")
        append(",\"primary\":\"${KeyboardColorMath.hex(palette.primary)}\"")
        append(",\"secondary\":\"${KeyboardColorMath.hex(palette.secondary)}\"")
        append(",\"suggestion\":\"${KeyboardColorMath.hex(palette.suggestion)}\"}")
    }

    /** Hostile or incomplete text becomes the Victorious Neon palette. Never throws. */
    fun decode(raw: String?): KeyboardPalette {
        if (raw.isNullOrBlank() || raw.length > MAX_CHARS || "\"v\":1" !in raw.replace(" ", "")) return KeyboardPalettes.Neon
        val found = field.findAll(raw).associate { it.groupValues[1] to KeyboardColorMath.parseHex(it.groupValues[2]) }
        val background = found["background"] ?: return KeyboardPalettes.Neon
        val key = found["key"] ?: return KeyboardPalettes.Neon
        val keyText = found["keyText"] ?: return KeyboardPalettes.Neon
        val primary = found["primary"] ?: return KeyboardPalettes.Neon
        val secondary = found["secondary"] ?: return KeyboardPalettes.Neon
        val suggestion = found["suggestion"] ?: return KeyboardPalettes.Neon
        if (listOf(background, key, keyText, primary, secondary, suggestion).any { it == null }) {
            return KeyboardPalettes.Neon
        }
        return KeyboardPalette(background, key, keyText, primary, secondary, suggestion)
    }
}
