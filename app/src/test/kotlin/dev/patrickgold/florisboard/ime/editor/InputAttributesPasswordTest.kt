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

package dev.patrickgold.florisboard.ime.editor

import android.text.InputType
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.shouldBe

class InputAttributesPasswordTest : FunSpec({
    context("password-like fields are recognised, so copy/cut and clipboard capture are refused") {
        withData(
            nameFn = { (_, name) -> name },
            (InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD) to "text password",
            (InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD) to "visible password",
            (InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD) to "web password",
            (InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD) to "numeric password",
        ) { (raw, _) ->
            InputAttributes.wrap(raw).isPasswordLike() shouldBe true
        }
    }

    context("ordinary fields are not treated as passwords") {
        withData(
            nameFn = { (_, name) -> name },
            InputType.TYPE_CLASS_TEXT to "plain text",
            (InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS) to "email",
            (InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI) to "uri",
            (InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE) to "multiline",
            InputType.TYPE_CLASS_NUMBER to "number",
            InputType.TYPE_NULL to "null",
        ) { (raw, _) ->
            InputAttributes.wrap(raw).isPasswordLike() shouldBe false
        }
    }
})
