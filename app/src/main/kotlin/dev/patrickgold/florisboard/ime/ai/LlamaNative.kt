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

package dev.patrickgold.florisboard.ime.ai

/** One streamed piece. Return false to stop. The piece is model output and must not be logged. */
fun interface TokenSink {
    fun onToken(piece: String): Boolean
}

/**
 * JNI entry to llama.cpp. The library is absent on the JVM test runtime and on a device
 * whose ABI we did not ship; both cases leave [available] false and the keyboard keeps working.
 */
object LlamaNative {
    const val PIN = "b11624"

    val available: Boolean = try {
        System.loadLibrary("victorious_llama")
        true
    } catch (_: UnsatisfiedLinkError) {
        false
    } catch (_: SecurityException) {
        false
    }

    @JvmStatic external fun nativeCancel()
    @JvmStatic external fun nativeClearCancel()
    @JvmStatic external fun nativeLoad(path: String, nCtx: Int, nThreads: Int): Boolean
    @JvmStatic external fun nativeUnload()
    @JvmStatic external fun nativeIsLoaded(): Boolean
    @JvmStatic external fun nativeGenerate(
        system: String,
        user: String,
        temperature: Float,
        maxTokens: Int,
        callback: TokenSink,
    ): Int
}

object LlamaRuntime {
    fun load(path: String, nCtx: Int, nThreads: Int): Boolean {
        if (!LlamaNative.available) return false
        return try {
            LlamaNative.nativeLoad(path, nCtx, nThreads)
        } catch (_: UnsatisfiedLinkError) {
            false
        }
    }

    fun unload() {
        if (!LlamaNative.available) return
        try {
            LlamaNative.nativeUnload()
        } catch (_: UnsatisfiedLinkError) {
            // The library never loaded.
        }
    }

    fun loaded(): Boolean {
        if (!LlamaNative.available) return false
        return try {
            LlamaNative.nativeIsLoaded()
        } catch (_: UnsatisfiedLinkError) {
            false
        }
    }

    fun cancel() {
        if (!LlamaNative.available) return
        try {
            LlamaNative.nativeCancel()
        } catch (_: UnsatisfiedLinkError) {
            // Nothing to cancel.
        }
    }

    fun clearCancel() {
        if (!LlamaNative.available) return
        try {
            LlamaNative.nativeClearCancel()
        } catch (_: UnsatisfiedLinkError) {
            // Nothing to clear.
        }
    }

    /** 5 means the library or the model is not loaded. */
    fun generate(system: String, user: String, temperature: Float, maxTokens: Int, sink: TokenSink): Int {
        if (!LlamaNative.available) return 5
        return try {
            LlamaNative.nativeGenerate(system, user, temperature, maxTokens, sink)
        } catch (_: UnsatisfiedLinkError) {
            5
        }
    }
}
