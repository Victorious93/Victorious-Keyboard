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

package dev.patrickgold.florisboard.ime.fleet

import dev.patrickgold.florisboard.lib.fleet.FleetHub
import dev.patrickgold.florisboard.lib.fleet.IFleetHub

/**
 * Adapts the generated binder stub to [FleetHub].
 * TODO-confirm-with-hub: when the hub publishes its AIDL, change this adapter and
 * `lib/fleet/src/main/aidl/.../IFleetHub.aidl` together. Callers insert the strings.
 * Do not call this on the main thread.
 */
class AidlFleetHub(private val remote: IFleetHub) : FleetHub {
    override fun askHub(action: String, text: String): String = remote.askHub(action, text).orEmpty()

    override fun runCommand(name: String, args: List<String>): String =
        remote.runCommand(name, args.toTypedArray()).orEmpty()

    override fun getSnippets(): String = remote.getSnippets().orEmpty()

    override fun ping(): Boolean = remote.ping()
}
