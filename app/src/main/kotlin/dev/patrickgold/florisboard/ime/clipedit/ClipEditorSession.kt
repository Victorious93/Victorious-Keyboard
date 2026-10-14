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

package dev.patrickgold.florisboard.ime.clipedit

/** Where the text in the editor came from. A vault origin must be written back to the vault. */
enum class ClipEditorOrigin {
    REGULAR,
    VAULT,
    SNIPPET,
}

enum class EditorField { BODY, FIND, REPLACE }

/**
 * One open editor. [sensitive] is true for vault text: it must not be written to regular history,
 * shell history, or the suggestion learner.
 */
data class ClipEditorSession(
    val origin: ClipEditorOrigin,
    val sourceId: String?,
    val sensitive: Boolean,
    val body: EditorBuffer,
    val find: EditorBuffer = EditorBuffer(),
    val replace: EditorBuffer = EditorBuffer(),
    val focus: EditorField = EditorField.BODY,
    val revision: Long = 0,
) {
    fun focused(): EditorBuffer = when (focus) {
        EditorField.BODY -> body
        EditorField.FIND -> find
        EditorField.REPLACE -> replace
    }

    fun withFocused(buffer: EditorBuffer): ClipEditorSession {
        val next = when (focus) {
            EditorField.BODY -> copy(body = buffer)
            EditorField.FIND -> copy(find = buffer)
            EditorField.REPLACE -> copy(replace = buffer)
        }
        return next.copy(revision = revision + 1)
    }

    fun focus(field: EditorField): ClipEditorSession = copy(focus = field, revision = revision + 1)
}

enum class ClipEditorWrite {
    UPDATE_REGULAR,
    ADD_REGULAR,
    UPDATE_VAULT,
    ADD_VAULT,
    UPDATE_SNIPPET,
}

/** Save keeps a vault item in the vault. Save as new from the vault adds another vault row. */
object ClipEditorSave {
    fun target(origin: ClipEditorOrigin, asNew: Boolean, hasSource: Boolean): ClipEditorWrite = when (origin) {
        ClipEditorOrigin.VAULT -> if (asNew || !hasSource) ClipEditorWrite.ADD_VAULT else ClipEditorWrite.UPDATE_VAULT
        ClipEditorOrigin.SNIPPET -> if (asNew || !hasSource) ClipEditorWrite.ADD_REGULAR else ClipEditorWrite.UPDATE_SNIPPET
        ClipEditorOrigin.REGULAR -> if (asNew || !hasSource) ClipEditorWrite.ADD_REGULAR else ClipEditorWrite.UPDATE_REGULAR
    }
}
