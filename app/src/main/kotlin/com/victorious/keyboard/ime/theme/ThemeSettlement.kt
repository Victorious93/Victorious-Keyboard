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
package com.victorious.keyboard.ime.theme

/**
 * When may the keyboard start drawing with [ThemeManager.activeThemeInfo]?
 *
 * The extension index starts empty and is filled asynchronously on a background dispatcher. Looking a theme
 * up before that finishes finds nothing; treating that as "use the built-in fallback" paints the gray upstream
 * style under (or instead of) the selected Victorious theme.
 */
object ThemeSettlement {
    /**
     * A lookup came back empty. It is final only if the index has already been read; otherwise the theme
     * may simply not be listed yet and the caller must wait for the next index update.
     */
    fun isSettledWhenNotFound(indexInitialized: Boolean): Boolean = indexInitialized
}
