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

import java.util.concurrent.CompletableFuture

/**
 * Matches one provider reply to the request that is waiting for it.
 * A reply whose id is missing or belongs to an older request is ignored, so a late
 * `commands.list` cannot fill in the result of the next `command.run`.
 */
class FleetReplyMailbox {
    private val lock = Any()
    private var waitingId: String? = null
    private var future: CompletableFuture<String>? = null

    /** False when a request is already waiting. The caller must not replace it. */
    fun begin(id: String, future: CompletableFuture<String>): Boolean = synchronized(lock) {
        if (waitingId != null || id.isEmpty()) return false
        waitingId = id
        this.future = future
        true
    }

    /**
     * Completes the waiting request only when [replyId] is that request's id.
     * Returns false when nothing was waiting or the id does not match.
     */
    fun complete(replyId: String?, body: String): Boolean = synchronized(lock) {
        val waiting = waitingId ?: return false
        val pending = future ?: return false
        if (replyId == null || replyId != waiting) return false
        pending.complete(body)
        true
    }

    /** Drops [id] if it is still the waiting request. A newer request is left alone. */
    fun end(id: String) = synchronized(lock) {
        if (waitingId == id) {
            waitingId = null
            future = null
        }
    }
}
