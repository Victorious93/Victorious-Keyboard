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

package dev.patrickgold.florisboard.lib.android

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import java.io.File

/**
 * The synchronous toast helpers used to call runBlocking on the main dispatcher. A caller that
 * already held the main thread deadlocked. This guards that pattern from coming back.
 */
class ToastSyncSourceTest : FunSpec({
    test("synchronous toasts are posted to the main looper and are not deprecated") {
        val source = File("../lib/android/src/main/kotlin/org/florisboard/lib/android/Toast.kt").readText()
        source shouldContain "fun Context.showShortToastSync"
        source shouldContain "fun Context.showLongToastSync"
        source shouldContain "Looper.getMainLooper()"
        source shouldNotContain "runBlocking"
        source shouldNotContain "@Deprecated"
    }
})
