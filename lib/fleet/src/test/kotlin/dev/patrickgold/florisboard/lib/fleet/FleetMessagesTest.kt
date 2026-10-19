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
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain

class FleetMessagesTest : FunSpec({
    test("commands.list and command.run round-trip and refuse a bad reply") {
        val listRequest = FleetMessages.listRequest("1")
        listRequest shouldContain "\"op\":\"commands.list\""
        listRequest shouldContain "\"protocol_major\":1"
        listRequest shouldNotContain "password"

        val listed = FleetMessages.parseList(
            """
            {"protocol_major":1,"op":"commands.list","id":"1","ok":true,"title":"EDL","namespace":"edl",
             "commands":[{"name":"status","label":"Status"}],
             "snippets":[{"name":"devices","label":"devices","text":"adb devices"}]}
            """.trimIndent(),
        )
        listed.ok shouldBe true
        listed.namespace shouldBe "edl"
        listed.commands.single().name shouldBe "status"
        listed.snippets.single().text shouldBe "adb devices"

        FleetMessages.parseList("""{"protocol_major":2,"op":"commands.list","ok":true}""") shouldBe Listed.Empty
        FleetMessages.parseList("{") shouldBe Listed.Empty
        FleetMessages.parseList("x".repeat(70_000)) shouldBe Listed.Empty

        val runRequest = FleetMessages.runRequest("2", "status", "hello")
        runRequest shouldContain "\"op\":\"command.run\""
        runRequest shouldContain "hello"
        FleetMessages.parseRun(
            """{"protocol_major":1,"op":"command.run","id":"2","ok":true,"text":"inserted"}""",
        ) shouldBe "inserted"
        FleetMessages.parseRun("""{"protocol_major":1,"op":"command.run","ok":false,"text":"no"}""") shouldBe ""
        FleetMessages.replyId("""{"protocol_major":1,"op":"command.run","id":"2","ok":true}""") shouldBe "2"
        FleetMessages.replyId("""{"protocol_major":1,"ok":true}""") shouldBe null
        FleetMessages.replyId("{") shouldBe null
        FleetMessages.replyId("""{"id":"${"a".repeat(65)}"}""") shouldBe null
    }

    test("a stored grant does not follow a replaced certificate") {
        val cert = "AB".repeat(32)
        val replacement = "CD".repeat(32)
        val grants = FleetGrants.parse(FleetGrants.render(FleetGrants.approve(emptyList(), "com.example.app", cert)))
        FleetTrust.access(cert, "", FleetGrants.find(grants, "com.example.app")) shouldBe FleetAccess.Active
        FleetTrust.access(replacement, "", FleetGrants.find(grants, "com.example.app")) shouldBe FleetAccess.NeedsApproval
        val off = FleetGrants.setEnabled(grants, "com.example.app", cert, enabled = false)
        FleetTrust.access(cert, "", FleetGrants.find(off, "com.example.app")) shouldBe FleetAccess.Off
    }
})
