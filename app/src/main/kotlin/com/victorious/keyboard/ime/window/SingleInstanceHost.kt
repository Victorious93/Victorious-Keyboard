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

package com.victorious.keyboard.ime.window

/**
 * Keeps at most one live view of type [V] installed, however often the platform asks for it.
 *
 * Why this exists: [com.victorious.keyboard.FlorisImeService.onCreateInputView] adds the keyboard root to the
 * IME window itself and returns `null` to the framework. `InputMethodService` only remembers a view that is
 * returned, so it keeps asking (each time the input view becomes shown, and again after a configuration
 * change). Adding a new root per call stacks complete keyboards on top of each other, each with its own
 * composition and each reporting window insets to the same controller.
 *
 * Pure Kotlin so the rule is unit-tested without Android.
 */
class SingleInstanceHost<V : Any>(
    private val create: () -> V,
    private val isAttached: (V) -> Boolean,
    private val attach: (V) -> Unit,
    private val detach: (V) -> Unit,
) {
    var current: V? = null
        private set

    /** Number of views this host has created, for diagnostics and tests. */
    var createdCount: Int = 0
        private set

    /** Returns the live view, creating and attaching one only when there is none attached. */
    fun ensureInstalled(): V {
        current?.let { existing ->
            if (isAttached(existing)) return existing
            // Dropped from the window behind our back: clean up before replacing, never keep two.
            detach(existing)
            current = null
        }
        val view = create()
        createdCount++
        current = view
        attach(view)
        return view
    }

    /** Removes the view, if any. Safe to call repeatedly. */
    fun release() {
        val existing = current ?: return
        current = null
        detach(existing)
    }
}
