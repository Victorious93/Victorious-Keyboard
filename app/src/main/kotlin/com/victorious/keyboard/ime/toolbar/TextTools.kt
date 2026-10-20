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

package com.victorious.keyboard.ime.toolbar

/** Where the text being transformed came from. */
enum class TextToolSource {
    SELECTION,
    CLIPBOARD,
}

/** What the preview buttons do. Nothing is applied until the user picks one. */
enum class TextToolPlace {
    REPLACE,
    INSERT,
    COPY,
}

/**
 * A computed transform waiting for Replace, Insert, or Copy.
 * [input] and [output] are user text: [toString] leaves them out so a log of this object cannot carry them.
 */
data class TextToolPreview(
    val source: TextToolSource,
    val input: String,
    val op: TextTransform,
    val output: String?,
    val fromVault: Boolean,
) {
    val ok: Boolean get() = output != null

    override fun toString(): String =
        "TextToolPreview(source=$source, op=$op, ok=$ok, vault=$fromVault)"
}

sealed class TextToolWrite {
    data class IntoField(val text: String, val replaceSelection: Boolean) : TextToolWrite()
    data class OntoClipboard(val text: String) : TextToolWrite()
    data object Blocked : TextToolWrite()
}

/**
 * The Text Tools tile. Selection wins; otherwise the clipboard is the source. The result stays a preview until
 * [write] says where it goes. Vault text can be replaced or inserted inside the editor and cannot be copied out.
 */
object TextTools {
    val offered: List<TextTransform> = listOf(
        TextTransform.BASE64_ENCODE,
        TextTransform.BASE64_DECODE,
        TextTransform.URL_ENCODE,
        TextTransform.URL_DECODE,
        TextTransform.HEX_ENCODE,
        TextTransform.HEX_DECODE,
        TextTransform.JSON_PRETTY,
        TextTransform.JSON_MINIFY,
        TextTransform.MD5,
        TextTransform.SHA1,
        TextTransform.SHA256,
        TextTransform.UPPER,
        TextTransform.LOWER,
        TextTransform.TITLE,
        TextTransform.CAMEL,
        TextTransform.SNAKE,
        TextTransform.KEBAB,
        TextTransform.TRIM_LINES,
        TextTransform.SORT_LINES,
        TextTransform.DEDUPE_LINES,
        TextTransform.ROT13,
        TextTransform.EXTRACT_URL,
        TextTransform.EXTRACT_EMAIL,
        TextTransform.EXTRACT_IP,
        TextTransform.JWT_DECODE,
        TextTransform.UNIX_TO_DATE,
        TextTransform.DATE_TO_UNIX,
        TextTransform.SHELL_ESCAPE,
        TextTransform.JSON_ESCAPE,
        TextTransform.REGEX_ESCAPE,
    )

    fun open(
        selection: String,
        clipboard: String?,
        fromVault: Boolean,
        op: TextTransform = offered.first(),
        nowMillis: Long = System.currentTimeMillis(),
    ): TextToolPreview? {
        val (source, input) = when {
            selection.isNotEmpty() -> TextToolSource.SELECTION to selection
            !clipboard.isNullOrEmpty() -> TextToolSource.CLIPBOARD to clipboard
            else -> return null
        }
        return preview(source, input, op, fromVault && source == TextToolSource.SELECTION, nowMillis)
    }

    fun withOp(current: TextToolPreview, op: TextTransform, nowMillis: Long = System.currentTimeMillis()): TextToolPreview =
        preview(current.source, current.input, op, current.fromVault, nowMillis)

    fun write(preview: TextToolPreview, place: TextToolPlace): TextToolWrite {
        val text = preview.output ?: return TextToolWrite.Blocked
        return when (place) {
            TextToolPlace.COPY ->
                if (preview.fromVault) TextToolWrite.Blocked else TextToolWrite.OntoClipboard(text)
            TextToolPlace.INSERT -> TextToolWrite.IntoField(text, replaceSelection = false)
            TextToolPlace.REPLACE -> when (preview.source) {
                TextToolSource.SELECTION -> TextToolWrite.IntoField(text, replaceSelection = true)
                TextToolSource.CLIPBOARD -> TextToolWrite.OntoClipboard(text)
            }
        }
    }

    private fun preview(
        source: TextToolSource,
        input: String,
        op: TextTransform,
        fromVault: Boolean,
        nowMillis: Long,
    ): TextToolPreview {
        val chosen = if (op in offered) op else offered.first()
        val output = when (val result = TextTransforms.apply(chosen, input, nowMillis = nowMillis)) {
            is TransformResult.Ok -> result.text
            is TransformResult.Failed -> null
        }
        return TextToolPreview(source, input, chosen, output, fromVault)
    }
}
