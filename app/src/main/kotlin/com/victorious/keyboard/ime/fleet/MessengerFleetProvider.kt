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

package com.victorious.keyboard.ime.fleet

import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.Message
import android.os.Messenger
import com.victorious.keyboard.lib.fleet.FleetMessages
import com.victorious.keyboard.lib.fleet.FleetProvider
import com.victorious.keyboard.lib.fleet.FleetReplyMailbox
import com.victorious.keyboard.lib.fleet.Listed
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Talks to one command provider with Messenger messages. Request and reply
 * bodies are not logged. A dead remote leaves this provider empty.
 */
class MessengerFleetProvider(private val remote: Messenger) : FleetProvider {
    private val thread = HandlerThread("fleet-reply").apply {
        isDaemon = true
        start()
    }
    private val turn = Any()
    private val mailbox = FleetReplyMailbox()
    private var cache: Listed? = null
    private val ids = AtomicInteger()

    private val replyTo = Messenger(object : Handler(thread.looper) {
        override fun handleMessage(msg: Message) {
            val body = if (msg.what == FleetMessages.WHAT_RESPONSE) {
                msg.data?.getString(FleetMessages.KEY_RESPONSE).orEmpty()
            } else {
                ""
            }
            mailbox.complete(FleetMessages.replyId(body), body)
        }
    })

    fun close() {
        try {
            thread.quitSafely()
        } catch (e: Exception) {
            // The thread is already gone.
        }
    }

    override fun ping(): Boolean = listed().ok

    override fun providerName(): String = listed().title

    override fun namespace(): String = listed().namespace

    override fun icon(): ByteArray = ByteArray(0)

    override fun slashCommands(): String = FleetMessages.legacyCommands(listed().commands)

    override fun snippets(): String = FleetMessages.legacySnippets(listed().snippets)

    override fun actions(): String = FleetMessages.legacyActions(listed().actions)

    override fun ask(action: String, text: String): String {
        return run(action.ifBlank { "ask" }, text)
    }

    override fun runCommand(name: String, args: List<String>): String {
        return run(name, args.joinToString(" "))
    }

    private fun run(name: String, argument: String): String {
        val id = ids.incrementAndGet().toString()
        val raw = transact(id, FleetMessages.runRequest(id, name, argument))
        return FleetMessages.parseRun(raw)
    }

    private fun listed(): Listed {
        cache?.let { return it }
        val id = ids.incrementAndGet().toString()
        val raw = transact(id, FleetMessages.listRequest(id))
        val parsed = FleetMessages.parseList(raw)
        if (parsed.ok) cache = parsed
        return parsed
    }

    private fun transact(id: String, request: String): String = synchronized(turn) {
        val future = CompletableFuture<String>()
        if (!mailbox.begin(id, future)) return ""
        val message = Message.obtain(null, FleetMessages.WHAT_REQUEST)
        message.data = Bundle().apply { putString(FleetMessages.KEY_REQUEST, request) }
        message.replyTo = replyTo
        try {
            remote.send(message)
            future.get(1500, TimeUnit.MILLISECONDS) ?: ""
        } catch (e: Exception) {
            ""
        } finally {
            mailbox.end(id)
        }
    }
}
