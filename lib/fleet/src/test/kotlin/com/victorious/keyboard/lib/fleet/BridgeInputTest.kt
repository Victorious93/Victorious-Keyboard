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


package com.victorious.keyboard.lib.fleet

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private class FakePort(
    var running: Boolean = true,
    var field: BridgeInput.FieldState = BridgeInput.FieldState(keyboardUp = true, hasField = true, sensitive = false),
    var performResult: Boolean = true,
    var failProbe: Boolean = false,
    var failPerform: Boolean = false,
) : BridgeInput.Port {
    val performed = mutableListOf<BridgeInput.Action>()
    var probes = 0

    override fun imeRunning() = running

    override fun field(): BridgeInput.FieldState {
        probes++
        if (failProbe) error("probe")
        return field
    }

    override fun perform(action: BridgeInput.Action): Boolean {
        if (failPerform) error("perform")
        performed += action
        return performResult
    }
}

private fun text(value: String) =
    """{"protocol_major":1,"op":"keyboard.input.text","text":${Json.encodeToString(kotlinx.serialization.serializer<String>(), value)}}"""

private fun key(body: String) = """{"protocol_major":1,"op":"keyboard.input.key",$body}"""

private fun reply(raw: String) = Json.parseToJsonElement(raw).jsonObject

private fun error(raw: String) = reply(raw)["error"]!!.jsonPrimitive.content

private fun ok(raw: String) = reply(raw)["ok"]!!.jsonPrimitive.boolean

class BridgeInputTest : FunSpec({
    fun run(raw: String?, port: FakePort = FakePort(), authorized: Boolean = true, enabled: Boolean = true) =
        BridgeInput.handle(raw, authorized, enabled, port)

    context("caller and switch") {
        test("an unknown caller gets unauthorized_caller and nothing happens, switch on or off") {
            for (enabled in listOf(true, false)) {
                val port = FakePort()
                val out = run(text("hi"), port, authorized = false, enabled = enabled)
                error(out) shouldBe BridgeProtocol.ERROR_UNAUTHORIZED
                ok(out) shouldBe false
                port.performed.size shouldBe 0
                port.probes shouldBe 0
            }
        }

        test("an allowed caller with the switch off gets external_control_disabled and nothing happens") {
            val port = FakePort()
            val out = run(key("\"key\":\"enter\""), port, enabled = false)
            error(out) shouldBe BridgeProtocol.ERROR_DISABLED
            ok(out) shouldBe false
            port.performed.size shouldBe 0
            port.probes shouldBe 0
        }

        test("an oversize request from a stranger is still unauthorized, not too_large") {
            error(run("x".repeat(100_000), authorized = false)) shouldBe BridgeProtocol.ERROR_UNAUTHORIZED
        }
    }

    context("typing text") {
        test("valid text reaches the keyboard once, unchanged") {
            val port = FakePort()
            val out = run(text("ls -la\n\tok ünï ☃ 😀"), port)
            ok(out) shouldBe true
            error(out) shouldBe ""
            port.performed shouldBe listOf(BridgeInput.Action.Text("ls -la\n\tok ünï ☃ 😀"))
        }

        test("the reply never repeats the text") {
            val out = run(text("hunter2-secret"), FakePort())
            out shouldNotContain "hunter2"
        }

        test("empty, missing, control-character and lone-surrogate text is invalid") {
            val bad = listOf(
                text(""),
                """{"protocol_major":1,"op":"keyboard.input.text"}""",
                text("a\u0000b"),
                text("a\u001bb"),
                text("a\rb"),
                text("a\u007fb"),
                text("a\uD800b"),
                text("a\uDC00b"),
                text("tail\uD83D"),
            )
            for (raw in bad) {
                val port = FakePort()
                error(run(raw, port)) shouldBe BridgeInput.ERROR_INVALID
                port.performed.size shouldBe 0
            }
        }

        test("the text limit is exact") {
            val port = FakePort()
            ok(run(text("a".repeat(BridgeInput.MAX_TEXT_CHARS)), port)) shouldBe true
            error(run(text("a".repeat(BridgeInput.MAX_TEXT_CHARS + 1)), port)) shouldBe BridgeInput.ERROR_TOO_LARGE
            port.performed.size shouldBe 1
        }

        test("a maximum text made entirely of escaped characters still fits the request limit") {
            val port = FakePort()
            val raw = text("\"".repeat(BridgeInput.MAX_TEXT_CHARS))
            (raw.length <= BridgeInput.MAX_REQUEST_CHARS) shouldBe true
            ok(run(raw, port)) shouldBe true
        }

        test("text cannot be mixed with key fields") {
            val raw = """{"protocol_major":1,"op":"keyboard.input.text","text":"a","key":"enter"}"""
            error(run(raw)) shouldBe BridgeInput.ERROR_INVALID
        }
    }

    context("pressing keys") {
        test("enter, tab and the arrows are accepted, case-insensitively") {
            val expected = mapOf(
                "enter" to BridgeInput.Key.ENTER,
                "TAB" to BridgeInput.Key.TAB,
                "Up" to BridgeInput.Key.UP,
                "down" to BridgeInput.Key.DOWN,
                "left" to BridgeInput.Key.LEFT,
                "right" to BridgeInput.Key.RIGHT,
            )
            for ((name, k) in expected) {
                val port = FakePort()
                ok(run(key("\"key\":\"$name\""), port)) shouldBe true
                port.performed shouldBe listOf(BridgeInput.Action.Press(k, null, ctrl = false, count = 1))
            }
        }

        test("ctrl plus a letter is accepted; a bare letter is not") {
            val port = FakePort()
            ok(run(key("\"key\":\"c\",\"ctrl\":true"), port)) shouldBe true
            port.performed shouldBe listOf(BridgeInput.Action.Press(null, 'c', ctrl = true, count = 1))
            error(run(key("\"key\":\"c\""), port)) shouldBe BridgeInput.ERROR_INVALID
            error(run(key("\"key\":\"c\",\"ctrl\":false"), port)) shouldBe BridgeInput.ERROR_INVALID
            port.performed.size shouldBe 1
        }

        test("ctrl can modify a named key, and count repeats it") {
            val port = FakePort()
            ok(run(key("\"key\":\"left\",\"ctrl\":true,\"count\":3"), port)) shouldBe true
            port.performed shouldBe listOf(BridgeInput.Action.Press(BridgeInput.Key.LEFT, null, ctrl = true, count = 3))
        }

        test("keys outside the allowed set are invalid") {
            val bad = listOf("escape", "backspace", "delete", "f1", "home", "1", "ab", "é", "", "ctrl", " enter", "enter ")
            for (name in bad) {
                val port = FakePort()
                error(run(key("\"key\":\"$name\",\"ctrl\":true"), port)) shouldBe BridgeInput.ERROR_INVALID
                port.performed.size shouldBe 0
            }
        }

        test("count must be 1..20") {
            val port = FakePort()
            for (n in listOf(0, -1, 21, 1_000_000)) {
                error(run(key("\"key\":\"enter\",\"count\":$n"), port)) shouldBe BridgeInput.ERROR_INVALID
            }
            ok(run(key("\"key\":\"enter\",\"count\":20"), port)) shouldBe true
            port.performed.size shouldBe 1
        }

        test("modifiers other than ctrl are refused, not ignored") {
            for (extra in listOf("\"alt\":true", "\"shift\":true", "\"meta\":true")) {
                val port = FakePort()
                error(run(key("\"key\":\"a\",\"ctrl\":true,$extra"), port)) shouldBe BridgeInput.ERROR_INVALID
                port.performed.size shouldBe 0
            }
        }

        test("a key request with text, or without a key, is invalid") {
            error(run(key("\"key\":\"enter\",\"text\":\"x\""))) shouldBe BridgeInput.ERROR_INVALID
            error(run(key("\"ctrl\":true"))) shouldBe BridgeInput.ERROR_INVALID
        }
    }

    context("sensitive fields and keyboard state") {
        test("a password-like field refuses text and keys and nothing is sent") {
            val port = FakePort(field = BridgeInput.FieldState(keyboardUp = true, hasField = true, sensitive = true))
            error(run(text("x"), port)) shouldBe BridgeInput.ERROR_SENSITIVE
            error(run(key("\"key\":\"enter\""), port)) shouldBe BridgeInput.ERROR_SENSITIVE
            error(run(key("\"key\":\"c\",\"ctrl\":true"), port)) shouldBe BridgeInput.ERROR_SENSITIVE
            port.performed.size shouldBe 0
        }

        test("no field and no keyboard are reported without sending") {
            val none = FakePort(field = BridgeInput.FieldState(keyboardUp = true, hasField = false, sensitive = false))
            error(run(text("x"), none)) shouldBe BridgeInput.ERROR_NO_FIELD
            val down = FakePort(
                running = false,
                field = BridgeInput.FieldState(keyboardUp = false, hasField = false, sensitive = false),
            )
            error(run(text("x"), down)) shouldBe BridgeInput.ERROR_UNAVAILABLE
            none.performed.size + down.performed.size shouldBe 0
        }

        test("a failing edit or a throwing keyboard becomes input_failed, never a crash") {
            error(run(text("x"), FakePort(performResult = false))) shouldBe BridgeInput.ERROR_FAILED
            error(run(text("x"), FakePort(failPerform = true))) shouldBe BridgeInput.ERROR_FAILED
            error(run(text("x"), FakePort(failProbe = true))) shouldBe BridgeInput.ERROR_FAILED
        }

        test("the field is checked after validation, so a bad request never touches the editor state") {
            val port = FakePort()
            run(text(""), port)
            port.probes shouldBe 0
        }
    }

    context("request shape") {
        test("null, blank, garbage and wrong-version requests are invalid") {
            for (raw in listOf(null, "", "   ", "not json", "[]", "{}", """{"op":"keyboard.input.text","text":"a"}""")) {
                val port = FakePort()
                ok(run(raw, port)) shouldBe false
                port.performed.size shouldBe 0
            }
            error(run("""{"protocol_major":2,"op":"keyboard.input.text","text":"a"}""")) shouldBe BridgeInput.ERROR_INVALID
            error(run(null)) shouldBe BridgeInput.ERROR_INVALID
        }

        test("unknown fields and bad types are refused") {
            error(run("""{"protocol_major":1,"op":"keyboard.input.text","text":"a","extra":1}""")) shouldBe BridgeInput.ERROR_INVALID
            error(run("""{"protocol_major":1,"op":"keyboard.input.text","text":5}""")) shouldBe BridgeInput.ERROR_INVALID
            error(run(key("\"key\":\"enter\",\"count\":\"2\""))) shouldBe BridgeInput.ERROR_INVALID
        }

        test("an id is accepted up to its bound and never echoed") {
            val good = """{"protocol_major":1,"op":"keyboard.input.text","text":"a","id":"req-1"}"""
            val port = FakePort()
            val out = run(good, port)
            ok(out) shouldBe true
            out shouldNotContain "req-1"
            val long = """{"protocol_major":1,"op":"keyboard.input.text","text":"a","id":"${"i".repeat(65)}"}"""
            error(run(long, port)) shouldBe BridgeInput.ERROR_INVALID
        }

        test("an oversize request is refused before it is parsed") {
            val port = FakePort()
            val raw = text("a") + " ".repeat(BridgeInput.MAX_REQUEST_CHARS)
            error(run(raw, port)) shouldBe BridgeInput.ERROR_TOO_LARGE
            error(run("{".repeat(1_000_000), port)) shouldBe BridgeInput.ERROR_TOO_LARGE
            port.performed.size shouldBe 0
            port.probes shouldBe 0
        }

        test("an unknown operation is unsupported and sends nothing") {
            val port = FakePort()
            val out = run("""{"protocol_major":1,"op":"keyboard.read","text":"a"}""", port)
            error(out) shouldBe BridgeInput.ERROR_UNSUPPORTED_OP
            port.performed.size shouldBe 0
        }

        test("there is no operation that reads the editor or runs a toolbar button") {
            val caps = reply(
                BridgeProtocol.response(BridgeProtocol.OP_CAPABILITIES, true, true, true),
            )["capabilities"].toString()
            caps shouldNotContain "read"
            caps shouldNotContain "toolbar"
            caps shouldNotContain "selection"
            caps shouldNotContain "clipboard"
        }
    }

    context("existing status operations through the same entry point") {
        test("status and capabilities answer an allowed caller and report the keyboard state") {
            val up = reply(run("""{"protocol_major":1,"op":"keyboard.status"}""", FakePort(running = true)))
            up["ok"]!!.jsonPrimitive.boolean shouldBe true
            up["ime_running"]!!.jsonPrimitive.boolean shouldBe true
            val down = reply(run("""{"protocol_major":1,"op":"keyboard.status"}""", FakePort(running = false)))
            down["ime_running"]!!.jsonPrimitive.boolean shouldBe false
            val caps = run("""{"protocol_major":1,"op":"keyboard.capabilities.get"}""")
            ok(caps) shouldBe true
            caps.contains(BridgeProtocol.OP_INPUT_TEXT) shouldBe true
            caps.contains(BridgeProtocol.OP_INPUT_KEY) shouldBe true
        }

        test("status keeps refusing a caller whose switch is off") {
            error(run("""{"protocol_major":1,"op":"keyboard.status"}""", enabled = false)) shouldBe
                BridgeProtocol.ERROR_DISABLED
        }
    }
})
