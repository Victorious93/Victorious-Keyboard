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

package com.victorious.keyboard.ime.fleettheme

import com.victorious.keyboard.ime.theme.colors.KeyboardPalette
import com.victorious.keyboard.ime.theme.colors.KeyboardPalettes
import com.victorious.keyboard.lib.fleet.CertDigest
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Read-only client of the fleet theme (schema 1).
 * VictorLaunch is the only writer. This keyboard never inserts, updates, or deletes.
 * A broadcast extra is a wake-up, not a theme: the colors come from a query.
 *
 * The published VictorLaunch pin is the debug certificate in that app's `FleetPins.KNOWN`.
 * No release certificate is published. A blank value or the word TBD is not a match.
 */
object FleetThemeContract {
    const val SCHEMA_VERSION = 1
    const val AUTHORITY = "com.victorious.launch.theme"
    const val PATH_CURRENT = "current"
    const val CONTENT_URI = "content://com.victorious.launch.theme/current"
    const val MIME = "vnd.android.cursor.item/vnd.com.victorious.launch.theme.current"
    const val ACTION_CHANGED = "com.victorious.launch.action.FLEET_THEME_CHANGED"
    const val PROVIDER_PACKAGE = "com.victorious.launch"

    /** SHA-256 of the VictorLaunch debug signing certificate, colon-separated. */
    const val PROVIDER_CERT_SHA256 =
        "B9:9A:AF:E2:C1:41:8B:5D:64:AA:0D:4B:AC:61:FC:F7:A7:BA:81:44:35:E0:EF:89:E8:AA:55:BE:56:4E:4A:69"

    const val COL_SCHEMA = "schemaVersion"
    const val COL_MODE = "mode"
    const val COL_ICON_STYLE = "iconStyle"
    const val COL_ICON_PACK = "iconPackPackage"
    const val COL_JSON = "json"
    const val COL_UPDATED = "updatedAtMs"

    val COLOR_COLUMNS = listOf(
        "background",
        "surface",
        "primary",
        "secondary",
        "onBackground",
        "muted",
        "danger",
        "stale",
        "tile",
    )

    /** Columns this client reads. Any other column on the row is ignored. */
    val COLUMNS = listOf(COL_SCHEMA, COL_MODE, COL_ICON_STYLE, COL_ICON_PACK, COL_JSON, COL_UPDATED) + COLOR_COLUMNS
}

/** Nine palette colors, as opaque or translucent ARGB ints. No raw payload text. */
data class FleetPalette(
    val background: Int,
    val surface: Int,
    val primary: Int,
    val secondary: Int,
    val onBackground: Int,
    val muted: Int,
    val danger: Int,
    val stale: Int,
    val tile: Int,
) {
    override fun toString(): String = "FleetPalette"
}

data class FleetThemeSnapshot(
    val schemaVersion: Int,
    val mode: String,
    val iconStyle: String,
    val iconPackPackage: String,
    val updatedAtMs: Long,
    val palette: FleetPalette,
    val keyboard: KeyboardPalette,
) {
    override fun toString(): String = "FleetThemeSnapshot(schemaVersion=$schemaVersion,mode=$mode)"
}

/** What the keyboard and Settings should show. [Absent] keeps the person's own theme. */
sealed class FleetThemeState {
    data object Absent : FleetThemeState()

    /** The provider is there, but it is not pinned, the query was refused, or the payload is not schema 1. */
    data object NotPinned : FleetThemeState()

    data class Applied(val snapshot: FleetThemeSnapshot) : FleetThemeState() {
        override fun toString(): String = "Applied(${snapshot})"
    }
}

/** Colors for Settings. [light] chooses the Material scheme constructor; the ints come from the payload. */
data class FleetPaint(
    val background: Int,
    val surface: Int,
    val primary: Int,
    val secondary: Int,
    val onBackground: Int,
    val muted: Int,
    val danger: Int,
    val stale: Int,
    val tile: Int,
    val light: Boolean,
) {
    companion object {
        /** The built-in black squared palette. Danger and stale have no separate token there, so they use secondary. */
        fun blackSquared(): FleetPaint {
            val black = KeyboardPalettes.Black
            return FleetPaint(
                background = black.background,
                surface = black.key,
                primary = black.primary,
                secondary = black.secondary,
                onBackground = black.keyText,
                muted = black.secondary,
                danger = black.secondary,
                stale = black.secondary,
                tile = black.suggestion,
                light = false,
            )
        }
    }
}

enum class FleetThemeTrust {
    Absent,
    Untrusted,
    Trusted,
}

object FleetThemeWake {
    /** The action is a wake-up. Extras are not a theme and are not read. */
    fun isWake(action: String?): Boolean = action == FleetThemeContract.ACTION_CHANGED
}

object FleetThemeAccess {
    fun trust(packageName: String?, certs: List<String>): FleetThemeTrust {
        if (packageName.isNullOrBlank()) return FleetThemeTrust.Absent
        if (packageName != FleetThemeContract.PROVIDER_PACKAGE) return FleetThemeTrust.Untrusted
        val pin = CertDigest.normalize(FleetThemeContract.PROVIDER_CERT_SHA256)
        if (pin.isEmpty()) return FleetThemeTrust.Untrusted
        val match = certs.any { cert ->
            val normalized = CertDigest.normalize(cert)
            normalized.isNotEmpty() && normalized == pin
        }
        return if (match) FleetThemeTrust.Trusted else FleetThemeTrust.Untrusted
    }

    /**
     * @param columns the query row, or null when the trusted provider returned no row.
     * A row is ignored unless [trust] is [FleetThemeTrust.Trusted] and [queryThrewSecurity] is false.
     * Fields inside the row are never treated as authorization.
     */
    fun decide(
        packageName: String?,
        certs: List<String>,
        queryThrewSecurity: Boolean,
        columns: Map<String, String?>?,
    ): FleetThemeState {
        if (queryThrewSecurity) return FleetThemeState.NotPinned
        when (trust(packageName, certs)) {
            FleetThemeTrust.Absent -> return FleetThemeState.Absent
            FleetThemeTrust.Untrusted -> return FleetThemeState.NotPinned
            FleetThemeTrust.Trusted -> Unit
        }
        if (columns == null) return FleetThemeState.Absent
        val snapshot = FleetThemeCodec.decode(columns) ?: return FleetThemeState.NotPinned
        return FleetThemeState.Applied(snapshot)
    }
}

object FleetThemeCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        isLenient = false
    }

    fun decode(columns: Map<String, String?>): FleetThemeSnapshot? {
        val schema = columns[FleetThemeContract.COL_SCHEMA]?.trim()?.toIntOrNull()
        if (schema != FleetThemeContract.SCHEMA_VERSION) return null
        val rawJson = columns[FleetThemeContract.COL_JSON]?.trim().orEmpty()
        if (rawJson.isNotEmpty()) {
            return decodeJson(rawJson)
        }
        return decodeColumns(columns)
    }

    fun decodeJson(text: String): FleetThemeSnapshot? {
        val parsed = try {
            json.decodeFromString(FleetThemeJson.serializer(), text)
        } catch (_: Exception) {
            return null
        }
        if (parsed.schemaVersion != FleetThemeContract.SCHEMA_VERSION) return null
        val palette = parsed.palette.toPalette() ?: return null
        return snapshot(
            mode = parsed.mode,
            iconStyle = parsed.iconStyle,
            iconPackPackage = parsed.iconPackPackage,
            updatedAtMs = parsed.updatedAtMs,
            palette = palette,
        )
    }

    private fun decodeColumns(columns: Map<String, String?>): FleetThemeSnapshot? {
        val colors = LinkedHashMap<String, Int>()
        for (name in FleetThemeContract.COLOR_COLUMNS) {
            val parsed = parseHex(columns[name] ?: return null) ?: return null
            colors[name] = parsed
        }
        return snapshot(
            mode = columns[FleetThemeContract.COL_MODE] ?: return null,
            iconStyle = columns[FleetThemeContract.COL_ICON_STYLE] ?: return null,
            iconPackPackage = columns[FleetThemeContract.COL_ICON_PACK].orEmpty(),
            updatedAtMs = columns[FleetThemeContract.COL_UPDATED]?.trim()?.toLongOrNull() ?: 0L,
            palette = FleetPalette(
                background = colors.getValue("background"),
                surface = colors.getValue("surface"),
                primary = colors.getValue("primary"),
                secondary = colors.getValue("secondary"),
                onBackground = colors.getValue("onBackground"),
                muted = colors.getValue("muted"),
                danger = colors.getValue("danger"),
                stale = colors.getValue("stale"),
                tile = colors.getValue("tile"),
            ),
        )
    }

    /** `#RRGGBB` or `#AARRGGBB`. Anything else, including `#RGB`, is rejected. */
    fun parseHex(raw: String): Int? {
        val text = raw.trim()
        if (text.length != 7 && text.length != 9) return null
        if (text[0] != '#') return null
        val hex = text.substring(1)
        if (hex.any { it !in '0'..'9' && it !in 'a'..'f' && it !in 'A'..'F' }) return null
        val value = hex.toLongOrNull(16) ?: return null
        return if (hex.length == 6) (0xFF000000L or value).toInt() else value.toInt()
    }

    private fun snapshot(
        mode: String,
        iconStyle: String,
        iconPackPackage: String,
        updatedAtMs: Long,
        palette: FleetPalette,
    ): FleetThemeSnapshot {
        return FleetThemeSnapshot(
            schemaVersion = FleetThemeContract.SCHEMA_VERSION,
            mode = mode,
            iconStyle = iconStyle,
            iconPackPackage = iconPackPackage,
            updatedAtMs = updatedAtMs,
            palette = palette,
            keyboard = KeyboardPalette(
                background = palette.background,
                key = palette.surface,
                keyText = palette.onBackground,
                primary = palette.primary,
                secondary = palette.secondary,
                suggestion = palette.tile,
            ).resolved(),
        )
    }
}

fun FleetThemeState.forcesBlackSquared(): Boolean = this !is FleetThemeState.Absent

/** Null means keep the palette the person already chose. */
fun FleetThemeState.keyboardPalette(): KeyboardPalette? = when (this) {
    FleetThemeState.Absent -> null
    FleetThemeState.NotPinned -> KeyboardPalettes.Black
    is FleetThemeState.Applied -> snapshot.keyboard
}

/** Null means keep the Settings colors the person already chose. */
fun FleetThemeState.settingsPaint(): FleetPaint? = when (this) {
    FleetThemeState.Absent -> null
    FleetThemeState.NotPinned -> FleetPaint.blackSquared()
    is FleetThemeState.Applied -> FleetPaint(
        background = snapshot.palette.background,
        surface = snapshot.palette.surface,
        primary = snapshot.palette.primary,
        secondary = snapshot.palette.secondary,
        onBackground = snapshot.palette.onBackground,
        muted = snapshot.palette.muted,
        danger = snapshot.palette.danger,
        stale = snapshot.palette.stale,
        tile = snapshot.palette.tile,
        light = snapshot.mode == "LIGHT",
    )
}

@Serializable
private data class FleetThemeJson(
    val schemaVersion: Int,
    val mode: String,
    val iconStyle: String = "",
    val iconPackPackage: String = "",
    val updatedAtMs: Long = 0,
    val palette: FleetPaletteJson,
)

@Serializable
private data class FleetPaletteJson(
    val background: String,
    val surface: String,
    val primary: String,
    val secondary: String,
    val onBackground: String,
    val muted: String,
    val danger: String,
    val stale: String,
    val tile: String,
) {
    fun toPalette(): FleetPalette? {
        val background = FleetThemeCodec.parseHex(background) ?: return null
        val surface = FleetThemeCodec.parseHex(surface) ?: return null
        val primary = FleetThemeCodec.parseHex(primary) ?: return null
        val secondary = FleetThemeCodec.parseHex(secondary) ?: return null
        val onBackground = FleetThemeCodec.parseHex(onBackground) ?: return null
        val muted = FleetThemeCodec.parseHex(muted) ?: return null
        val danger = FleetThemeCodec.parseHex(danger) ?: return null
        val stale = FleetThemeCodec.parseHex(stale) ?: return null
        val tile = FleetThemeCodec.parseHex(tile) ?: return null
        return FleetPalette(background, surface, primary, secondary, onBackground, muted, danger, stale, tile)
    }
}
