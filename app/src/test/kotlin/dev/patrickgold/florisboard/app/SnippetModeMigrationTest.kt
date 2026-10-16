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

package dev.patrickgold.florisboard.app

import dev.patrickgold.florisboard.ime.snippets.ModeExpansion
import dev.patrickgold.jetpref.datastore.jetprefDataStoreOf
import dev.patrickgold.jetpref.datastore.runtime.DataStoreReader
import dev.patrickgold.jetpref.datastore.runtime.DataStoreWriter
import dev.patrickgold.jetpref.datastore.runtime.LoadStrategy
import dev.patrickgold.jetpref.datastore.runtime.PersistStrategy
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration.Companion.seconds

private const val LEGACY_KEY = "snippets__expand_in_terminal_mode"
private const val NEW_KEY = "snippets__mode_termux"

/** The preferences after loading a stored file, and the file jetpref would write back. */
private class Loaded(val prefs: FlorisPreferenceModel, val persisted: String)

/**
 * Loads a stored preference file the way the app does on its first start after an upgrade (and Restore does for an
 * old backup): jetpref reads each `type;key;value` line and passes it through `AppPrefs.migrate`. jetpref applies the
 * load on its own event queue, one event at a time, so the export queued after it is handled only once the values are
 * in place.
 */
private suspend fun load(vararg lines: String): Loaded {
    val store = jetprefDataStoreOf(FlorisPreferenceModel::class)
    val prefs by store
    val reader = object : DataStoreReader {
        override suspend fun read(): String = lines.joinToString("\n")
    }
    store.init(LoadStrategy.UseReader(reader), PersistStrategy.Disabled).getOrThrow()
    val written = CompletableDeferred<String>()
    val writer = object : DataStoreWriter {
        override suspend fun write(content: String) {
            written.complete(content)
        }
    }
    store.export(writer).getOrThrow()
    return Loaded(prefs, withTimeout(10.seconds) { written.await() })
}

private fun Loaded.persistedLine(key: String): String? = persisted.lines().singleOrNull { ";$key;" in it }

/** The retired "Expand snippets in Termux mode" switch, loaded through the real `AppPrefs.migrate` rule. */
class SnippetModeMigrationTest : FunSpec({
    test("a stored on becomes the Termux rule Follow; Standard and Developer keep their defaults") {
        val loaded = load("b;$LEGACY_KEY;true")
        loaded.prefs.snippets.modeTermux.get() shouldBe ModeExpansion.FOLLOW
        loaded.prefs.snippets.modeStandard.get() shouldBe ModeExpansion.FOLLOW
        loaded.prefs.snippets.modeDeveloper.get() shouldBe ModeExpansion.FOLLOW
    }

    test("a stored off becomes Off") {
        load("b;$LEGACY_KEY;false").prefs.snippets.modeTermux.get() shouldBe ModeExpansion.OFF
    }

    test("a damaged stored value becomes Off instead of failing the load") {
        load("b;$LEGACY_KEY;garbage").prefs.snippets.modeTermux.get() shouldBe ModeExpansion.OFF
    }

    test("the old key is not written back; the new one is, under the preference's own name") {
        val loaded = load("b;$LEGACY_KEY;true")
        loaded.persistedLine(LEGACY_KEY) shouldBe null
        (loaded.persistedLine(NEW_KEY)?.contains(ModeExpansion.FOLLOW.name)) shouldBe true
        loaded.prefs.snippets.modeTermux.key shouldBe NEW_KEY
    }

    test("other stored snippet settings load unchanged, and without the old key Termux keeps its default") {
        val loaded = load("b;snippets__expand_on_enter;true", "b;snippets__expansion_enabled;true")
        loaded.prefs.snippets.expandOnEnter.get() shouldBe true
        loaded.prefs.snippets.expansionEnabled.get() shouldBe true
        loaded.prefs.snippets.modeTermux.get() shouldBe ModeExpansion.OFF
    }
})
