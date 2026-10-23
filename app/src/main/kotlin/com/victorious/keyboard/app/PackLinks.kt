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


package com.victorious.keyboard.app

/**
 * Default homepage and issue tracker for the extensions that ship with the keyboard. An extension that names its
 * own links keeps them; a third-party extension that names none shows none, so this keyboard's repository is never
 * presented as someone else's homepage.
 */
object PackLinks {
    private val builtInPrefixes = listOf("org.florisboard.", "com.victorious.")

    fun isBuiltIn(extensionId: String): Boolean = builtInPrefixes.any { extensionId.startsWith(it) }

    fun homepage(extensionId: String, declared: String?): String? =
        declared?.takeIf { it.isNotBlank() } ?: ProjectUrls.REPO.takeIf { isBuiltIn(extensionId) }

    fun issueTracker(extensionId: String, declared: String?): String? =
        declared?.takeIf { it.isNotBlank() } ?: ProjectUrls.ISSUES.takeIf { isBuiltIn(extensionId) }
}
