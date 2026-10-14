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

#include <jni.h>

#include <algorithm>
#include <atomic>
#include <cstdint>
#include <mutex>
#include <string>
#include <vector>

#include <android/log.h>

#include "llama.h"

namespace {

std::mutex g_mu;
llama_model * g_model = nullptr;
llama_context * g_ctx = nullptr;
std::atomic<bool> g_cancel{false};
bool g_backend = false;

void silent_log(ggml_log_level, const char *, void *) {}

bool abort_requested(void *) {
    return g_cancel.load();
}

void free_locked() {
    if (g_ctx != nullptr) {
        llama_free(g_ctx);
        g_ctx = nullptr;
    }
    if (g_model != nullptr) {
        llama_model_free(g_model);
        g_model = nullptr;
    }
}

std::string utf8_complete(std::string & pending, const char * data, int32_t n) {
    pending.append(data, data + n);
    int tail = 0;
    for (int i = static_cast<int>(pending.size()) - 1; i >= 0 && tail < 4; --i, ++tail) {
        const auto c = static_cast<unsigned char>(pending[i]);
        if ((c & 0xC0) != 0x80) {
            int need = 1;
            if ((c & 0xE0) == 0xC0) need = 2;
            else if ((c & 0xF0) == 0xE0) need = 3;
            else if ((c & 0xF8) == 0xF0) need = 4;
            if (tail + 1 < need) {
                std::string ready = pending.substr(0, pending.size() - (tail + 1));
                pending = pending.substr(pending.size() - (tail + 1));
                return ready;
            }
            break;
        }
    }
    std::string ready;
    ready.swap(pending);
    return ready;
}

std::string apply_template(const std::string & system, const std::string & user) {
    const char * tmpl = llama_model_chat_template(g_model, nullptr);
    llama_chat_message msgs[2];
    size_t n = 0;
    if (!system.empty()) {
        msgs[n].role = "system";
        msgs[n].content = system.c_str();
        n++;
    }
    msgs[n].role = "user";
    msgs[n].content = user.c_str();
    n++;
    if (tmpl != nullptr && tmpl[0] != '\0') {
        const int32_t need = llama_chat_apply_template(tmpl, msgs, n, true, nullptr, 0);
        if (need > 0 && need < 1000000) {
            std::string formatted(static_cast<size_t>(need), '\0');
            const int32_t got = llama_chat_apply_template(tmpl, msgs, n, true, formatted.data(), need);
            if (got > 0) {
                formatted.resize(static_cast<size_t>(got));
                return formatted;
            }
        }
    }
    std::string plain;
    if (!system.empty()) {
        plain += system;
        plain += "\n\n";
    }
    plain += user;
    plain += "\n";
    return plain;
}

bool emit_piece(JNIEnv * env, jobject callback, jmethodID method, const std::string & piece) {
    if (piece.empty()) return true;
    jstring text = env->NewStringUTF(piece.c_str());
    if (text == nullptr) return false;
    const jboolean keep = env->CallBooleanMethod(callback, method, text);
    env->DeleteLocalRef(text);
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
        return false;
    }
    return keep == JNI_TRUE;
}

} // namespace

extern "C" JNIEXPORT void JNICALL
Java_dev_patrickgold_florisboard_ime_ai_LlamaNative_nativeCancel(JNIEnv *, jclass) {
    g_cancel.store(true);
}

extern "C" JNIEXPORT void JNICALL
Java_dev_patrickgold_florisboard_ime_ai_LlamaNative_nativeClearCancel(JNIEnv *, jclass) {
    g_cancel.store(false);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_dev_patrickgold_florisboard_ime_ai_LlamaNative_nativeLoad(
        JNIEnv * env, jclass, jstring path, jint n_ctx, jint n_threads) {
    const char * c_path = env->GetStringUTFChars(path, nullptr);
    if (c_path == nullptr) return JNI_FALSE;
    std::string owned(c_path);
    env->ReleaseStringUTFChars(path, c_path);

    std::lock_guard<std::mutex> lock(g_mu);
    try {
        if (!g_backend) {
            llama_log_set(silent_log, nullptr);
            ggml_log_set(silent_log, nullptr);
            llama_backend_init();
            g_backend = true;
        }
        free_locked();
        llama_model_params model_params = llama_model_default_params();
        model_params.n_gpu_layers = 0;
        model_params.progress_callback = [](float, void *) { return !g_cancel.load(); };
        g_model = llama_model_load_from_file(owned.c_str(), model_params);
        if (g_model == nullptr) {
            __android_log_print(ANDROID_LOG_WARN, "victorious_llama", "load failed");
            return JNI_FALSE;
        }
        llama_context_params ctx_params = llama_context_default_params();
        const int ctx = n_ctx < 512 ? 512 : n_ctx;
        ctx_params.n_ctx = static_cast<uint32_t>(ctx);
        ctx_params.n_batch = static_cast<uint32_t>(ctx < 512 ? ctx : 512);
        ctx_params.n_ubatch = ctx_params.n_batch;
        ctx_params.n_threads = n_threads < 1 ? 1 : n_threads;
        ctx_params.n_threads_batch = ctx_params.n_threads;
        ctx_params.offload_kqv = false;
        ctx_params.abort_callback = abort_requested;
        g_ctx = llama_init_from_model(g_model, ctx_params);
        if (g_ctx == nullptr) {
            free_locked();
            __android_log_print(ANDROID_LOG_WARN, "victorious_llama", "context failed");
            return JNI_FALSE;
        }
        return JNI_TRUE;
    } catch (...) {
        free_locked();
        __android_log_print(ANDROID_LOG_WARN, "victorious_llama", "load exception");
        return JNI_FALSE;
    }
}

extern "C" JNIEXPORT void JNICALL
Java_dev_patrickgold_florisboard_ime_ai_LlamaNative_nativeUnload(JNIEnv *, jclass) {
    std::lock_guard<std::mutex> lock(g_mu);
    free_locked();
}

extern "C" JNIEXPORT jboolean JNICALL
Java_dev_patrickgold_florisboard_ime_ai_LlamaNative_nativeIsLoaded(JNIEnv *, jclass) {
    std::lock_guard<std::mutex> lock(g_mu);
    return g_ctx != nullptr ? JNI_TRUE : JNI_FALSE;
}

// 0 finished, 1 cancelled, 3 prompt does not fit, 4 decode failed, 5 not loaded
extern "C" JNIEXPORT jint JNICALL
Java_dev_patrickgold_florisboard_ime_ai_LlamaNative_nativeGenerate(
        JNIEnv * env, jclass, jstring system, jstring user, jfloat temperature, jint max_tokens, jobject callback) {
    const char * c_system = env->GetStringUTFChars(system, nullptr);
    const char * c_user = env->GetStringUTFChars(user, nullptr);
    if (c_system == nullptr || c_user == nullptr) {
        if (c_system != nullptr) env->ReleaseStringUTFChars(system, c_system);
        if (c_user != nullptr) env->ReleaseStringUTFChars(user, c_user);
        return 4;
    }
    std::string system_owned(c_system);
    std::string user_owned(c_user);
    env->ReleaseStringUTFChars(system, c_system);
    env->ReleaseStringUTFChars(user, c_user);

    jclass cls = env->GetObjectClass(callback);
    jmethodID method = env->GetMethodID(cls, "onToken", "(Ljava/lang/String;)Z");
    if (method == nullptr) {
        env->ExceptionClear();
        return 4;
    }

    std::lock_guard<std::mutex> lock(g_mu);
    if (g_model == nullptr || g_ctx == nullptr) return 5;
    try {
        const std::string prompt = apply_template(system_owned, user_owned);
        const llama_vocab * vocab = llama_model_get_vocab(g_model);
        int32_t cap = static_cast<int32_t>(prompt.size()) + 8;
        if (cap < 32) cap = 32;
        std::vector<llama_token> tokens(static_cast<size_t>(cap));
        int32_t n = llama_tokenize(vocab, prompt.c_str(), static_cast<int32_t>(prompt.size()),
                tokens.data(), cap, true, true);
        if (n == INT32_MIN) return 3;
        if (n < 0) {
            cap = -n + 8;
            tokens.resize(static_cast<size_t>(cap));
            n = llama_tokenize(vocab, prompt.c_str(), static_cast<int32_t>(prompt.size()),
                    tokens.data(), cap, true, true);
        }
        if (n <= 0) return 3;
        const int32_t n_ctx = static_cast<int32_t>(llama_n_ctx(g_ctx));
        const int32_t room = n_ctx - 8;
        if (n > room) {
            if (room < 16) return 3;
            tokens.erase(tokens.begin(), tokens.begin() + (n - room));
            n = room;
        }
        llama_memory_t mem = llama_get_memory(g_ctx);
        if (mem != nullptr) llama_memory_clear(mem, true);

        const int32_t n_batch = static_cast<int32_t>(llama_n_batch(g_ctx));
        for (int32_t i = 0; i < n; i += n_batch) {
            if (g_cancel.load()) return 1;
            const int32_t count = std::min(n_batch, n - i);
            llama_batch batch = llama_batch_get_one(tokens.data() + i, count);
            const int32_t rc = llama_decode(g_ctx, batch);
            if (rc != 0) return g_cancel.load() ? 1 : 4;
        }

        llama_sampler_chain_params sparams = llama_sampler_chain_default_params();
        sparams.no_perf = true;
        llama_sampler * smpl = llama_sampler_chain_init(sparams);
        const float temp = temperature < 0.f ? 0.f : temperature;
        if (temp <= 0.f) {
            llama_sampler_chain_add(smpl, llama_sampler_init_greedy());
        } else {
            llama_sampler_chain_add(smpl, llama_sampler_init_temp(temp));
            llama_sampler_chain_add(smpl, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));
        }
        const int limit = max_tokens < 1 ? 1 : max_tokens;
        std::string pending;
        int code = 0;
        for (int i = 0; i < limit; i++) {
            if (g_cancel.load()) {
                code = 1;
                break;
            }
            const llama_token id = llama_sampler_sample(smpl, g_ctx, -1);
            llama_sampler_accept(smpl, id);
            if (llama_vocab_is_eog(vocab, id)) break;
            char stack_buf[256];
            int32_t piece_n = llama_token_to_piece(vocab, id, stack_buf, sizeof(stack_buf), 0, true);
            std::string piece;
            if (piece_n < 0) {
                piece.resize(static_cast<size_t>(-piece_n));
                piece_n = llama_token_to_piece(vocab, id, piece.data(), piece_n < 0 ? -piece_n : piece_n, 0, true);
                if (piece_n > 0) piece.resize(static_cast<size_t>(piece_n));
            } else if (piece_n > 0) {
                piece.assign(stack_buf, stack_buf + piece_n);
            }
            if (piece_n > 0) {
                const std::string ready = utf8_complete(pending, piece.data(), piece_n);
                if (!emit_piece(env, callback, method, ready)) {
                    code = 1;
                    break;
                }
            }
            llama_token next = id;
            llama_batch batch = llama_batch_get_one(&next, 1);
            const int32_t rc = llama_decode(g_ctx, batch);
            if (rc != 0) {
                code = g_cancel.load() ? 1 : 4;
                break;
            }
        }
        if (code == 0 && !pending.empty()) {
            if (!emit_piece(env, callback, method, pending)) code = 1;
        }
        llama_sampler_free(smpl);
        return code;
    } catch (...) {
        __android_log_print(ANDROID_LOG_WARN, "victorious_llama", "generate exception");
        return 4;
    }
}
