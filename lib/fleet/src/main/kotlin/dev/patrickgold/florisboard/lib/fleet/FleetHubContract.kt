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

/**
 * Hub identity. The three identity values are placeholders, not a guessed package.
 *
 * TODO-confirm-with-hub
 *
 * Replace [PACKAGE_NAME], [SERVICE_ACTION], and [PERMISSION] with the values published by the
 * Droidcommand-AI hub, and keep `app/src/main/AndroidManifest.xml` (queries package, queries
 * intent action, and uses-permission) identical to them. [PERMISSION] is the hub's
 * signature-level permission. This keyboard only requests it. It does not declare one.
 */
object FleetHubContract {
    const val PACKAGE_NAME = "todo.confirm.with.hub"
    const val SERVICE_ACTION = "todo.confirm.with.hub.SERVICE"
    const val PERMISSION = "todo.confirm.with.hub.PERMISSION"

    /** TODO-confirm-with-hub: askHub action that lists commands the hub advertises. */
    const val ACTION_LIST_COMMANDS = "list-commands"

    /** TODO-confirm-with-hub: askHub action for text the user explicitly picked. */
    const val ACTION_ASK = "ask"
}
