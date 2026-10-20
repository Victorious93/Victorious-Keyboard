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

package com.victorious.keyboard.ime.clipboard.history

import com.victorious.keyboard.ime.clipboard.lanes.DetectedSecret

enum class SensitiveReason {
    PRIVATE_KEY,
    AWS_ACCESS_KEY,
    JWT,
    API_TOKEN,
    PASSWORD_ASSIGNMENT,
    CREDIT_CARD,
    IBAN,
    OTP,
}

/**
 * Best-effort detection of credentials so they are **not stored in history by default**.
 *
 * Limits (be honest in the UI): this is pattern matching. It will miss secrets that look like ordinary text and it
 * can flag harmless text. It reports only the *reason*, never the matched content, so a finding is safe to log.
 */
object SensitiveDetector {
    private val privateKey = Regex("-----BEGIN [A-Z ]*PRIVATE KEY-----")
    private val awsKey = Regex("\\b(AKIA|ASIA)[0-9A-Z]{16}\\b")
    private val jwt = Regex("\\beyJ[A-Za-z0-9_-]{8,}\\.eyJ[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{8,}\\b")
    private val apiToken = Regex(
        "\\b(gh[pousr]_[A-Za-z0-9]{30,}|github_pat_[A-Za-z0-9_]{30,}|xox[baprs]-[A-Za-z0-9-]{10,}" +
            "|sk_live_[A-Za-z0-9]{16,}|sk-[A-Za-z0-9_-]{20,}|AIza[0-9A-Za-z_-]{35})\\b"
    )
    private val passwordAssign = Regex(
        // No leading \\b on purpose: identifiers such as db_password, API_TOKEN or aws_secret_key must match too.
        "(pass(?:word|wd)?|pwd|secret|token|api[_-]?key)[A-Za-z0-9_]*\\s*[:=]\\s*[\"']?([A-Za-z0-9/+_.=-]{8,})",
        RegexOption.IGNORE_CASE,
    )
    private val cardCandidate = Regex("\\b(?:\\d[ -]?){13,19}\\b")
    private val ibanCandidate = Regex("\\b[A-Za-z]{2}\\d{2}(?:[ ]?[A-Za-z0-9]){11,30}\\b")
    private val otpCandidate = Regex(
        "(?i)\\b(?:otp|one[- ]time(?:[- ]code)?|verification code|totp)\\b[^\\d]{0,16}(\\d{4,8})\\b",
    )

    fun check(text: String): SensitiveReason? {
        if (text.isEmpty()) return null
        if (privateKey.containsMatchIn(text)) return SensitiveReason.PRIVATE_KEY
        if (awsKey.containsMatchIn(text)) return SensitiveReason.AWS_ACCESS_KEY
        if (jwt.containsMatchIn(text)) return SensitiveReason.JWT
        if (apiToken.containsMatchIn(text)) return SensitiveReason.API_TOKEN
        for (m in passwordAssign.findAll(text)) {
            val value = m.groupValues[2]
            // A secret-looking value mixes letters and digits; this avoids flagging `password = input_text`.
            if (value.any { it.isDigit() } && value.any { it.isLetter() }) return SensitiveReason.PASSWORD_ASSIGNMENT
        }
        for (m in cardCandidate.findAll(text)) {
            val digits = m.value.filter { it.isDigit() }
            if (digits.length in 13..19 && luhnValid(digits)) return SensitiveReason.CREDIT_CARD
        }
        for (m in ibanCandidate.findAll(text)) {
            if (ibanValid(m.value)) return SensitiveReason.IBAN
        }
        if (otpCandidate.containsMatchIn(text)) return SensitiveReason.OTP
        return null
    }

    /** Groups a finding into the kinds the vault detector offers. Null when [text] is not flagged. */
    fun detectedSecret(text: String): DetectedSecret? = when (check(text)) {
        null -> null
        SensitiveReason.PRIVATE_KEY -> DetectedSecret.PRIVATE_KEY
        SensitiveReason.CREDIT_CARD -> DetectedSecret.CARD
        SensitiveReason.IBAN -> DetectedSecret.IBAN
        SensitiveReason.OTP -> DetectedSecret.OTP
        else -> DetectedSecret.API_TOKEN
    }

    internal fun luhnValid(digits: String): Boolean {
        var sum = 0
        var alt = false
        for (i in digits.length - 1 downTo 0) {
            var n = digits[i] - '0'
            if (alt) {
                n *= 2
                if (n > 9) n -= 9
            }
            sum += n
            alt = !alt
        }
        return sum % 10 == 0
    }

    internal fun ibanValid(raw: String): Boolean {
        val compact = raw.filter { it.isLetterOrDigit() }.uppercase()
        if (compact.length !in 15..34) return false
        if (!compact[0].isLetter() || !compact[1].isLetter() || !compact[2].isDigit() || !compact[3].isDigit()) {
            return false
        }
        val rearranged = compact.drop(4) + compact.take(4)
        var remainder = 0
        for (c in rearranged) {
            val digits = if (c.isDigit()) c.toString() else (c.code - 'A'.code + 10).toString()
            for (d in digits) {
                remainder = (remainder * 10 + (d - '0')) % 97
            }
        }
        return remainder == 1
    }
}
