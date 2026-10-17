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

package com.victorious.fleet

/**
 * Shared discovery names. These are placeholders, not a list of fleet apps.
 *
 * TODO-confirm-with-hub
 *
 * [SERVICE_ACTION] is the intent action every fleet app exports.
 * [PERMISSION] is the shared signature permission those apps must hold.
 * Keep `app/src/main/AndroidManifest.xml` identical to both values.
 * The keyboard requests the permission and does not declare it.
 */
object FleetApiContract {
    const val SERVICE_ACTION = "com.victorious.fleet.KEYBOARD_PROVIDER"
    const val PERMISSION = "com.victorious.fleet.PERMISSION"
}
