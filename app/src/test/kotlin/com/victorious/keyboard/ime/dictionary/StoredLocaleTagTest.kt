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

package com.victorious.keyboard.ime.dictionary

import com.victorious.keyboard.lib.FlorisLocale
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

class StoredLocaleTagTest : FunSpec({
    test("null and the dictionary's all-languages sentinels stay null") {
        storedLocaleTagToFloris(null).shouldBeNull()
        storedLocaleTagToFloris("").shouldBeNull()
        storedLocaleTagToFloris("all").shouldBeNull()
        storedLocaleTagToFloris("null").shouldBeNull()
    }

    test("a real tag round-trips through FlorisLocale") {
        val locale = storedLocaleTagToFloris("en_US")
        locale shouldBe FlorisLocale.fromTag("en_US")
        locale?.localeTag() shouldBe "en_US"
    }
})
