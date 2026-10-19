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

package dev.patrickgold.florisboard.lib.fleet

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

class FleetReplyMailboxTest : FunSpec({
    test("a reply completes only the request with the same id") {
        val box = FleetReplyMailbox()
        val first = CompletableFuture<String>()
        box.begin("1", first) shouldBe true
        box.begin("2", CompletableFuture()) shouldBe false
        box.complete("2", """{"id":"2"}""") shouldBe false
        first.isDone shouldBe false
        box.complete(null, """{"ok":true}""") shouldBe false
        first.isDone shouldBe false
        box.complete("1", """{"id":"1","text":"ok"}""") shouldBe true
        first.get(1, TimeUnit.SECONDS) shouldBe """{"id":"1","text":"ok"}"""
    }

    test("a late reply cannot fill the next request") {
        val box = FleetReplyMailbox()
        val first = CompletableFuture<String>()
        box.begin("1", first) shouldBe true
        box.end("1")
        box.complete("1", "stale") shouldBe false
        first.isDone shouldBe false

        val second = CompletableFuture<String>()
        box.begin("2", second) shouldBe true
        box.complete("1", "stale") shouldBe false
        second.isDone shouldBe false
        box.complete("2", "fresh") shouldBe true
        second.get(1, TimeUnit.SECONDS) shouldBe "fresh"
        box.end("2")
        box.begin("", CompletableFuture()) shouldBe false
    }
})
