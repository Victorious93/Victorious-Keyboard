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

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * Regression for the stacked keyboards: the framework calls onCreateInputView repeatedly (every time the input
 * view is shown, and after configuration changes) while the service returns null to it.
 */
class SingleInstanceHostTest : FunSpec({
    class FakeWindow {
        val children = mutableListOf<Any>()
        val host = SingleInstanceHost(
            create = { Any() },
            isAttached = { it in children },
            attach = { children += it },
            detach = { children -= it },
        )
    }

    test("repeated show requests keep exactly one root in the window") {
        val w = FakeWindow()
        val first = w.host.ensureInstalled()
        repeat(50) { (w.host.ensureInstalled() === first) shouldBe true }
        w.children.size shouldBe 1
        w.host.createdCount shouldBe 1
    }

    test("a root that was removed from the window is replaced, never doubled") {
        val w = FakeWindow()
        val first = w.host.ensureInstalled()
        w.children.clear()
        val second = w.host.ensureInstalled()
        (second === first) shouldBe false
        w.children shouldBe listOf(second)
        w.host.createdCount shouldBe 2
    }

    test("release removes the root and a later request installs a fresh single one") {
        val w = FakeWindow()
        w.host.ensureInstalled()
        w.host.release()
        w.host.release()
        w.children.size shouldBe 0
        w.host.current shouldBe null
        w.host.ensureInstalled()
        w.children.size shouldBe 1
    }
})
