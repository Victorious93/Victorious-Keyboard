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

package dev.patrickgold.florisboard.ime.clipboard.history

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * Fixtures are assembled at runtime from fragments so that no secret-looking literal sits in the repository (and
 * secret scanners stay quiet). None of these are real credentials.
 */
class SensitiveDetectorTest : FunSpec({
    test("PEM private key header") {
        SensitiveDetector.check("-----BEGIN " + "OPENSSH PRIVATE KEY-----\nabc") shouldBe SensitiveReason.PRIVATE_KEY
        SensitiveDetector.check("-----BEGIN " + "PRIVATE KEY-----") shouldBe SensitiveReason.PRIVATE_KEY
    }

    test("AWS documentation example access key id") {
        SensitiveDetector.check("key: " + "AKIA" + "IOSFODNN7EXAMPLE") shouldBe SensitiveReason.AWS_ACCESS_KEY
    }

    test("JWT-shaped token") {
        val jwt = "eyJ" + "hbGciOiJIUzI1NiJ9" + "." + "eyJ" + "zdWIiOiIxMjM0NTY3ODkwIn0" + "." + "abcdefghijkl"
        SensitiveDetector.check("Authorization: Bearer $jwt") shouldBe SensitiveReason.JWT
    }

    test("GitHub and other API tokens") {
        SensitiveDetector.check("gh" + "p_" + "a".repeat(36)) shouldBe SensitiveReason.API_TOKEN
        SensitiveDetector.check("sk" + "-" + "A1b2C3d4E5f6G7h8I9j0") shouldBe SensitiveReason.API_TOKEN
    }

    test("password assignments with a secret-looking value") {
        SensitiveDetector.check("db_password = hunter2hunter2") shouldBe SensitiveReason.PASSWORD_ASSIGNMENT
        SensitiveDetector.check("api_key: \"abc12345def\"") shouldBe SensitiveReason.PASSWORD_ASSIGNMENT
    }

    test("ordinary code that mentions a password variable is not flagged") {
        SensitiveDetector.check("password = input_text") shouldBe null
        SensitiveDetector.check("val token = getToken()") shouldBe null
        SensitiveDetector.check("Reset your password: please use the link") shouldBe null
    }

    test("credit cards are detected only when the Luhn check passes") {
        SensitiveDetector.check("pay with 4111 1111 1111 1111 today") shouldBe SensitiveReason.CREDIT_CARD
        SensitiveDetector.check("4111-1111-1111-1111") shouldBe SensitiveReason.CREDIT_CARD
        SensitiveDetector.check("1234 5678 9012 3456") shouldBe null
        SensitiveDetector.check("order number 12345678901234") shouldBe null
    }

    test("Luhn implementation") {
        SensitiveDetector.luhnValid("4111111111111111") shouldBe true
        SensitiveDetector.luhnValid("4111111111111112") shouldBe false
    }

    test("plain text and empty input are fine") {
        SensitiveDetector.check("") shouldBe null
        SensitiveDetector.check("Meet at 5pm, bring the laptop.") shouldBe null
    }

    test("findings never contain the matched text") {
        // The result is an enum; this guards against someone changing it to carry the match.
        SensitiveReason.entries.forEach { it.name.contains("hunter") shouldBe false }
    }
})
