#include <jni.h>
#include <android/log.h>
#include <algorithm>
#include <atomic>
#include <mutex>
#include <string>
#include <vector>
#include <chrono>
#include <sstream>
#include "llama.h"

#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "JarvisLLM", __VA_ARGS__)

static llama_model *g_model = nullptr;
static llama_context *g_context = nullptr;
static llama_sampler *g_sampler = nullptr;
static std::mutex g_mutex;
static std::atomic<bool> g_abort{false};
static bool g_backend_initialized = false;
static long long g_format_ms = 0;
static long long g_tokenize_ms = 0;
static long long g_prefill_ms = 0;
static long long g_decode_ms = 0;
static int g_prompt_tokens = 0;
static int g_generated_tokens = 0;
static int g_prefill_batches = 0;
static int g_trimmed_messages = 0;
static int g_cache_reused_tokens = 0;
static std::vector<llama_token> g_cached_tokens;
static bool g_cache_valid = false;
static std::string g_finish_reason = "not_started";

static void release_model() {
    g_cached_tokens.clear();
    g_cache_valid = false;
    g_cache_reused_tokens = 0;
    if (g_sampler) { llama_sampler_free(g_sampler); g_sampler = nullptr; }
    if (g_context) { llama_free(g_context); g_context = nullptr; }
    if (g_model) { llama_model_free(g_model); g_model = nullptr; }
}

// Length of the longest prefix of `s` that does not end in the middle of a
// UTF-8 sequence. Streamed chunks and the final answer are cut with this, so
// Kotlin always receives whole characters (Cyrillic is 2 bytes, emoji are 4,
// and a token boundary can fall inside either).
static size_t utf8_complete_len(const std::string &s) {
    const size_t n = s.size();
    for (size_t back = 1; back <= 4 && back <= n; ++back) {
        const unsigned char c = static_cast<unsigned char>(s[n - back]);
        if ((c & 0xC0) == 0x80) continue;            // continuation byte, keep looking for the lead
        size_t need = 1;
        if ((c & 0xE0) == 0xC0) need = 2;
        else if ((c & 0xF0) == 0xE0) need = 3;
        else if ((c & 0xF8) == 0xF0) need = 4;
        return back >= need ? n : n - back;           // incomplete tail is held back
    }
    return n;                                         // no lead byte found: let the Java decoder replace it
}

// Errors become Java exceptions instead of "answers", so they never end up in the chat history.
static jbyteArray fail(JNIEnv *env, const char *message) {
    jclass cls = env->FindClass("java/lang/IllegalStateException");
    if (cls) env->ThrowNew(cls, message);
    return nullptr;
}

static jbyteArray to_java_bytes(JNIEnv *env, const char *data, size_t len) {
    jbyteArray array = env->NewByteArray(static_cast<jsize>(len));
    if (array && len > 0) {
        env->SetByteArrayRegion(array, 0, static_cast<jsize>(len), reinterpret_cast<const jbyte *>(data));
    }
    return array;
}

static std::string from_java_bytes(JNIEnv *env, jbyteArray array) {
    if (!array) return std::string();
    const jsize len = env->GetArrayLength(array);
    std::string out(static_cast<size_t>(len), '\0');
    if (len > 0) env->GetByteArrayRegion(array, 0, len, reinterpret_cast<jbyte *>(&out[0]));
    return out;
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_stahij_jarvis_NativeLlm_nativeLoadModel(
        JNIEnv *env, jobject, jstring jpath, jint threads, jint context_size) {
    g_abort.store(true);  // let a running generation finish quickly instead of blocking the load
    std::lock_guard<std::mutex> lock(g_mutex);
    const char *path = env->GetStringUTFChars(jpath, nullptr);
    std::string model_path(path ? path : "");
    if (path) env->ReleaseStringUTFChars(jpath, path);

    release_model();
    if (!g_backend_initialized) {
        llama_backend_init();
        g_backend_initialized = true;
    }

    llama_model_params model_params = llama_model_default_params();
    model_params.n_gpu_layers = 0;
    g_model = llama_model_load_from_file(model_path.c_str(), model_params);
    if (!g_model) {
        return env->NewStringUTF("Не удалось загрузить GGUF-модель. Проверь файл и свободную память.");
    }

    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx = static_cast<uint32_t>(std::max(1024, static_cast<int>(context_size)));
    ctx_params.n_batch = 512;
    ctx_params.n_ubatch = 256;
    // Snapdragon 732G has a heterogeneous 2+6 CPU layout. Cap both pools at
    // four initially; benchmark before increasing them on mobile devices.
    const int cpu_threads = std::clamp(static_cast<int>(threads), 2, 4);
    ctx_params.n_threads = cpu_threads;
    ctx_params.n_threads_batch = cpu_threads;
    g_context = llama_init_from_model(g_model, ctx_params);
    if (!g_context) {
        release_model();
        return env->NewStringUTF("Модель открылась, но не удалось выделить память под контекст.");
    }

    auto sampler_params = llama_sampler_chain_default_params();
    g_sampler = llama_sampler_chain_init(sampler_params);
    llama_sampler_chain_add(g_sampler, llama_sampler_init_top_k(40));
    llama_sampler_chain_add(g_sampler, llama_sampler_init_top_p(0.90f, 1));
    llama_sampler_chain_add(g_sampler, llama_sampler_init_temp(0.70f));
    llama_sampler_chain_add(g_sampler, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));
    return nullptr;
}

extern "C"
JNIEXPORT jbyteArray JNICALL
Java_com_stahij_jarvis_NativeLlm_nativeGenerate(
        JNIEnv *env, jobject, jobjectArray jroles, jobjectArray jcontents, jint max_tokens, jobject listener) {
    std::lock_guard<std::mutex> lock(g_mutex);
    g_abort.store(false);
    if (!g_model || !g_context || !g_sampler) {
        return fail(env, "Модель ещё не загружена.");
    }

    const jsize count = env->GetArrayLength(jroles);
    if (count <= 0 || count != env->GetArrayLength(jcontents)) {
        return fail(env, "История диалога пуста или повреждена.");
    }

    std::vector<std::string> role_storage;
    std::vector<std::string> content_storage;
    std::vector<llama_chat_message> chat;
    role_storage.reserve(count);
    content_storage.reserve(count);
    chat.reserve(count);

    for (jsize i = 0; i < count; ++i) {
        auto jr = static_cast<jstring>(env->GetObjectArrayElement(jroles, i));
        // Contents arrive as real UTF-8 bytes: GetStringUTFChars would hand llama.cpp
        // "modified UTF-8" (emoji as surrogate pairs, which is not valid UTF-8).
        auto jc = static_cast<jbyteArray>(env->GetObjectArrayElement(jcontents, i));
        std::string role = "user";
        if (jr) {
            const char *r = env->GetStringUTFChars(jr, nullptr);
            if (r) { role = r; env->ReleaseStringUTFChars(jr, r); }
            env->DeleteLocalRef(jr);
        }
        role_storage.emplace_back(std::move(role));
        content_storage.emplace_back(from_java_bytes(env, jc));
        if (jc) env->DeleteLocalRef(jc);
    }

    g_format_ms = 0; g_tokenize_ms = 0; g_prefill_ms = 0; g_decode_ms = 0;
    g_prompt_tokens = 0; g_generated_tokens = 0; g_prefill_batches = 0; g_trimmed_messages = 0; g_cache_reused_tokens = 0;
    g_finish_reason = "not_started";
    const char *tmpl = llama_model_chat_template(g_model, nullptr);
    std::string prompt;
    std::vector<llama_token> tokens;
    auto format_prompt = [&]() -> bool {
        chat.clear();
        for (size_t i = 0; i < role_storage.size(); ++i) {
            chat.push_back({ role_storage[i].c_str(), content_storage[i].c_str() });
        }
        prompt.clear();
        if (tmpl) {
            const int32_t needed = llama_chat_apply_template(tmpl, chat.data(), chat.size(), true, nullptr, 0);
            if (needed <= 0) return false;
            std::vector<char> formatted(static_cast<size_t>(needed) + 1);
            const int32_t written = llama_chat_apply_template(
                tmpl, chat.data(), chat.size(), true, formatted.data(), static_cast<int32_t>(formatted.size()));
            if (written < 0) return false;
            prompt.assign(formatted.data(), static_cast<size_t>(written));
        } else {
            for (size_t i = 0; i < role_storage.size(); ++i) {
                prompt += role_storage[i] + ":\n" + content_storage[i] + "\n";
            }
            prompt += "assistant:\n";
        }
        return true;
    };
    const llama_vocab *vocab = llama_model_get_vocab(g_model);
    auto tokenize_prompt = [&]() -> bool {
        const int32_t needed = -llama_tokenize(
            vocab, prompt.c_str(), static_cast<int32_t>(prompt.size()), nullptr, 0, true, true);
        if (needed <= 0) return false;
        tokens.resize(static_cast<size_t>(needed));
        const int32_t written = llama_tokenize(
            vocab, prompt.c_str(), static_cast<int32_t>(prompt.size()),
            tokens.data(), static_cast<int32_t>(tokens.size()), true, true);
        if (written <= 0) return false;
        tokens.resize(static_cast<size_t>(written));
        return true;
    };

    auto phase_start = std::chrono::steady_clock::now();
    if (!format_prompt()) return fail(env, "Не удалось применить chat template этой модели.");
    g_format_ms += std::chrono::duration_cast<std::chrono::milliseconds>(
        std::chrono::steady_clock::now() - phase_start).count();
    phase_start = std::chrono::steady_clock::now();
    if (!tokenize_prompt()) return fail(env, "Не удалось разбить запрос на токены.");
    g_tokenize_ms += std::chrono::duration_cast<std::chrono::milliseconds>(
        std::chrono::steady_clock::now() - phase_start).count();

    // Keep the system message and newest messages intact. Drop only complete oldest
    // conversation messages; never cut arbitrary tokens from the system instructions.
    // Reserve half of the 4096-token context for a full 1024-token answer.
    constexpr size_t MAX_PROMPT_TOKENS = 3072;
    while (tokens.size() > MAX_PROMPT_TOKENS && role_storage.size() > 2) {
        const size_t remove_count = std::min<size_t>(2, role_storage.size() - 2);
        role_storage.erase(role_storage.begin() + 1, role_storage.begin() + 1 + remove_count);
        content_storage.erase(content_storage.begin() + 1, content_storage.begin() + 1 + remove_count);
        g_trimmed_messages += static_cast<int>(remove_count);

        phase_start = std::chrono::steady_clock::now();
        if (!format_prompt()) return fail(env, "Не удалось повторно сформировать сокращённую историю.");
        g_format_ms += std::chrono::duration_cast<std::chrono::milliseconds>(
            std::chrono::steady_clock::now() - phase_start).count();
        phase_start = std::chrono::steady_clock::now();
        if (!tokenize_prompt()) return fail(env, "Не удалось повторно токенизировать сокращённую историю.");
        g_tokenize_ms += std::chrono::duration_cast<std::chrono::milliseconds>(
            std::chrono::steady_clock::now() - phase_start).count();
    }
    if (tokens.size() > MAX_PROMPT_TOKENS) {
        return fail(env, "Системная инструкция и последнее сообщение превышают лимит контекста. Сократи вложенный контекст или запрос.");
    }

    // Reuse the unchanged prefix of the previous prompt and generated answer.
    // The token prefix is compared exactly; any changed system prompt/history/model
    // automatically falls back to a clean context.
    llama_memory_t memory = llama_get_memory(g_context);
    size_t reused_tokens = 0;
    const bool memory_matches_cache =
        g_cache_valid &&
        !g_cached_tokens.empty() &&
        llama_memory_seq_pos_max(memory, 0) == static_cast<llama_pos>(g_cached_tokens.size() - 1);

    if (memory_matches_cache) {
        const size_t limit = std::min(tokens.size(), g_cached_tokens.size());
        while (reused_tokens < limit && tokens[reused_tokens] == g_cached_tokens[reused_tokens]) {
            ++reused_tokens;
        }
        // Keep at least one prompt token to recompute logits for the next generation.
        if (reused_tokens == tokens.size() && reused_tokens > 0) --reused_tokens;
        if (reused_tokens < g_cached_tokens.size() &&
            !llama_memory_seq_rm(memory, 0, static_cast<llama_pos>(reused_tokens), -1)) {
            reused_tokens = 0;
            g_cache_valid = false;
        }
    }

    if (!memory_matches_cache || !g_cache_valid) {
        llama_memory_clear(memory, true);
        g_cached_tokens.clear();
        reused_tokens = 0;
    } else {
        g_cached_tokens.resize(reused_tokens);
    }

    g_cache_reused_tokens = static_cast<int>(reused_tokens);
    g_prompt_tokens = static_cast<int>(tokens.size());
    const auto prefill_start = std::chrono::steady_clock::now();
    for (size_t offset = reused_tokens; offset < tokens.size();) {
        if (g_abort.load()) {
            // Everything decoded so far is still consistent with g_cached_tokens.
            g_finish_reason = "aborted";
            g_cache_valid = !g_cached_tokens.empty();
            return to_java_bytes(env, "", 0);
        }
        const int32_t batch_size = static_cast<int32_t>(
            std::min<size_t>(512, tokens.size() - offset));
        llama_batch batch = llama_batch_get_one(tokens.data() + offset, batch_size);
        if (llama_decode(g_context, batch) != 0) {
            g_cache_valid = false;
            llama_memory_clear(memory, true);
            g_cached_tokens.clear();
            return fail(env, "Не удалось обработать контекст. Попробуй сократить историю.");
        }
        g_cached_tokens.insert(g_cached_tokens.end(), tokens.begin() + offset, tokens.begin() + offset + batch_size);
        ++g_prefill_batches;
        offset += static_cast<size_t>(batch_size);
    }
    g_cache_valid = true;
    g_prefill_ms = std::chrono::duration_cast<std::chrono::milliseconds>(
        std::chrono::steady_clock::now() - prefill_start).count();

    llama_sampler_reset(g_sampler);
    const auto decode_start = std::chrono::steady_clock::now();
    std::string answer;
    answer.reserve(8192);
    size_t emitted = 0;  // bytes of `answer` already delivered to the listener
    jmethodID on_token = nullptr;
    if (listener) {
        jclass listener_class = env->GetObjectClass(listener);
        on_token = env->GetMethodID(listener_class, "onToken", "([B)V");
        env->DeleteLocalRef(listener_class);
        if (!on_token) env->ExceptionClear();
    }
    const int limit = std::clamp(static_cast<int>(max_tokens), 16, 1024);
    g_finish_reason = "token_limit";
    for (int i = 0; i < limit; ++i) {
        if (g_abort.load()) {
            g_finish_reason = "aborted";
            break;
        }
        const llama_token token = llama_sampler_sample(g_sampler, g_context, -1);
        if (llama_vocab_is_eog(vocab, token)) {
            g_finish_reason = "eog";
            break;
        }
        ++g_generated_tokens;

        char piece[1024];
        const int32_t piece_len = llama_token_to_piece(vocab, token, piece, sizeof(piece), 0, false);
        if (piece_len > 0) answer.append(piece, static_cast<size_t>(piece_len));

        if (on_token) {
            const size_t safe = utf8_complete_len(answer);
            if (safe > emitted) {
                jbyteArray chunk = to_java_bytes(env, answer.data() + emitted, safe - emitted);
                if (chunk) {
                    env->CallVoidMethod(listener, on_token, chunk);
                    env->DeleteLocalRef(chunk);
                }
                emitted = safe;
                if (env->ExceptionCheck()) {  // a throwing listener must not unwind through native code
                    env->ExceptionClear();
                    g_finish_reason = "listener_error";
                    break;
                }
            }
        }

        llama_batch batch = llama_batch_get_one(const_cast<llama_token *>(&token), 1);
        if (llama_decode(g_context, batch) != 0) {
            g_finish_reason = "decode_error";
            g_cache_valid = false;
            llama_memory_clear(memory, true);
            g_cached_tokens.clear();
            break;
        }
        g_cached_tokens.push_back(token);
    }
    g_decode_ms = std::chrono::duration_cast<std::chrono::milliseconds>(std::chrono::steady_clock::now() - decode_start).count();
    // Never return half a character (e.g. when cut by the token limit or by Stop).
    answer.resize(utf8_complete_len(answer));
    return to_java_bytes(env, answer.data(), answer.size());
}

extern "C"
JNIEXPORT void JNICALL
Java_com_stahij_jarvis_NativeLlm_nativeAbort(JNIEnv *, jobject) {
    g_abort.store(true);  // deliberately lock-free: generation holds g_mutex the whole time
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_stahij_jarvis_NativeLlm_nativeGetLastPerf(JNIEnv *env, jobject) {
    std::lock_guard<std::mutex> lock(g_mutex);
    std::ostringstream out;
    out << "format_ms=" << g_format_ms << "; tokenize_ms=" << g_tokenize_ms
        << "; prefill_ms=" << g_prefill_ms << "; prompt_tokens=" << g_prompt_tokens
        << "; prefill_batches=" << g_prefill_batches << "; trimmed_messages=" << g_trimmed_messages
        << "; cache_reused_tokens=" << g_cache_reused_tokens
        << "; prefill_new_tokens=" << (g_prompt_tokens - g_cache_reused_tokens)
        << "; decode_ms=" << g_decode_ms << "; generated_tokens=" << g_generated_tokens
        << "; finish_reason=" << g_finish_reason;
    return env->NewStringUTF(out.str().c_str());
}

extern "C"
JNIEXPORT void JNICALL
Java_com_stahij_jarvis_NativeLlm_nativeUnload(JNIEnv *, jobject) {
    g_abort.store(true);
    std::lock_guard<std::mutex> lock(g_mutex);
    release_model();
}
