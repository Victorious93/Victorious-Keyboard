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

package com.victorious.keyboard.lib.ext

/**
 * Built-in extension ids, plus the FlorisBoard ids they replace.
 *
 * Settings shows [canonical] ids (`com.victorious.keyboard.*`). Preferences, subtypes, and
 * layout references that still say `org.florisboard.*` resolve to the same extension.
 * Asset folders keep the old names; the id users see is the `id` field in `extension.json`.
 */
object ExtensionIds {
    const val REPO = "https://github.com/Victorious93/Victorious-Keyboard"
    const val ISSUES = "https://github.com/Victorious93/Victorious-Keyboard/issues"
    const val MAINTAINER = "Victorious93 ($REPO)"

    const val COMPOSERS = "com.victorious.keyboard.composers"
    const val CURRENCY_SETS = "com.victorious.keyboard.currencysets"
    const val LAYOUTS = "com.victorious.keyboard.layouts"
    const val LOCALIZATION = "com.victorious.keyboard.localization"
    const val HAN_SHAPE = "com.victorious.keyboard.hanshapebasedbasicpack"
    const val LANGUAGE_PACK = "com.victorious.keyboard.languagepack"
    const val THEMES = "com.victorious.keyboard.themes"
    const val THEMES_MATERIAL_YOU = "com.victorious.keyboard.themes.my"

    private val legacyToCanonical = mapOf(
        "org.florisboard.composers" to COMPOSERS,
        "org.florisboard.currencysets" to CURRENCY_SETS,
        "org.florisboard.layouts" to LAYOUTS,
        "org.florisboard.localization" to LOCALIZATION,
        "org.florisboard.hanshapebasedbasicpack" to HAN_SHAPE,
        "org.florisboard.languagepack" to LANGUAGE_PACK,
        "org.florisboard.themes" to THEMES,
        "org.florisboard.themes.my" to THEMES_MATERIAL_YOU,
    )

    fun canonical(id: String): String = legacyToCanonical[id] ?: id

    fun isLegacy(id: String): Boolean = legacyToCanonical.containsKey(id)

    fun legacyOf(canonicalId: String): String? =
        legacyToCanonical.entries.firstOrNull { it.value == canonicalId }?.key

    fun same(a: String, b: String): Boolean = canonical(a) == canonical(b)

    /**
     * Every id under which a component of this extension must be indexed.
     * The canonical id is first. Declared aliases and the built-in legacy id follow.
     */
    fun indexIds(canonicalId: String, declaredAliases: List<String> = emptyList()): List<String> {
        val ids = LinkedHashSet<String>()
        ids.add(canonical(canonicalId))
        ids.add(canonicalId)
        declaredAliases.forEach { ids.add(it) }
        legacyOf(canonical(canonicalId))?.let { ids.add(it) }
        return ids.toList()
    }

    fun matches(metaId: String, aliases: List<String>, query: String): Boolean {
        if (same(metaId, query)) return true
        return aliases.any { same(it, query) || it == query }
    }

    fun <T> indexComponent(
        into: MutableMap<ExtensionComponentName, T>,
        metaId: String,
        aliases: List<String>,
        componentId: String,
        value: T,
    ) {
        for (id in indexIds(metaId, aliases)) {
            into[ExtensionComponentName(id, componentId)] = value
        }
    }
}
