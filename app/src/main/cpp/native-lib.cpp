#include <jni.h>
#include <android/log.h>
#include <algorithm>
#include <mutex>
#include <string>
#include <vector>
#include "llama.h"

#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "JarvisLLM", __VA_ARGS__)

static llama_model *g_model = nullptr;
static llama_context *g_context = nullptr;
static llama_sampler *g_sampler = nullptr;
static std::mutex g_mutex;
static bool g_backend_initialized = false;

static void release_model() {
    if (g_sampler) { llama_sampler_free(g_sampler); g_sampler = nullptr; }
    if (g_context) { llama_free(g_context); g_context = nullptr; }
    if (g_model) { llama_model_free(g_model); g_model = nullptr; }
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_stahij_jarvis_NativeLlm_nativeLoadModel(
        JNIEnv *env, jobject, jstring jpath, jint threads, jint context_size) {
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
    ctx_params.n_ubatch = 512;
    ctx_params.n_threads = std::max(2, static_cast<int>(threads));
    ctx_params.n_threads_batch = std::max(2, static_cast<int>(threads));
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
JNIEXPORT jstring JNICALL
Java_com_stahij_jarvis_NativeLlm_nativeGenerate(
        JNIEnv *env, jobject, jobjectArray jroles, jobjectArray jcontents, jint max_tokens) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (!g_model || !g_context || !g_sampler) {
        return env->NewStringUTF("Модель ещё не загружена.");
    }

    const jsize count = env->GetArrayLength(jroles);
    if (count <= 0 || count != env->GetArrayLength(jcontents)) {
        return env->NewStringUTF("История диалога пуста или повреждена.");
    }

    std::vector<std::string> role_storage;
    std::vector<std::string> content_storage;
    std::vector<llama_chat_message> chat;
    role_storage.reserve(count);
    content_storage.reserve(count);
    chat.reserve(count);

    for (jsize i = 0; i < count; ++i) {
        auto jr = static_cast<jstring>(env->GetObjectArrayElement(jroles, i));
        auto jc = static_cast<jstring>(env->GetObjectArrayElement(jcontents, i));
        const char *r = env->GetStringUTFChars(jr, nullptr);
        const char *c = env->GetStringUTFChars(jc, nullptr);
        role_storage.emplace_back(r ? r : "user");
        content_storage.emplace_back(c ? c : "");
        if (r) env->ReleaseStringUTFChars(jr, r);
        if (c) env->ReleaseStringUTFChars(jc, c);
        env->DeleteLocalRef(jr);
        env->DeleteLocalRef(jc);
    }

    for (size_t i = 0; i < role_storage.size(); ++i) {
        chat.push_back({ role_storage[i].c_str(), content_storage[i].c_str() });
    }

    const char *tmpl = llama_model_chat_template(g_model, nullptr);
    std::string prompt;
    if (tmpl) {
        const int32_t needed = llama_chat_apply_template(tmpl, chat.data(), chat.size(), true, nullptr, 0);
        if (needed <= 0) return env->NewStringUTF("Не удалось применить chat template этой модели.");
        std::vector<char> formatted(static_cast<size_t>(needed) + 1);
        const int32_t written = llama_chat_apply_template(
            tmpl, chat.data(), chat.size(), true, formatted.data(), static_cast<int32_t>(formatted.size()));
        if (written < 0) return env->NewStringUTF("Ошибка форматирования истории диалога.");
        prompt.assign(formatted.data(), static_cast<size_t>(written));
    } else {
        for (size_t i = 0; i < role_storage.size(); ++i) {
            prompt += role_storage[i] + ":\n" + content_storage[i] + "\n";
        }
        prompt += "assistant:\n";
    }

    llama_memory_clear(llama_get_memory(g_context), true);
    const llama_vocab *vocab = llama_model_get_vocab(g_model);
    const int32_t token_count = -llama_tokenize(
        vocab, prompt.c_str(), static_cast<int32_t>(prompt.size()), nullptr, 0, true, true);
    if (token_count <= 0) return env->NewStringUTF("Не удалось разбить запрос на токены.");

    std::vector<llama_token> tokens(static_cast<size_t>(token_count));
    const int32_t tokenized = llama_tokenize(
        vocab, prompt.c_str(), static_cast<int32_t>(prompt.size()),
        tokens.data(), static_cast<int32_t>(tokens.size()), true, true);
    if (tokenized < 0) return env->NewStringUTF("Ошибка токенизации запроса.");

    for (size_t offset = 0; offset < tokens.size();) {
        const int32_t batch_size = static_cast<int32_t>(
            std::min<size_t>(512, tokens.size() - offset));
        llama_batch batch = llama_batch_get_one(tokens.data() + offset, batch_size);
        if (llama_decode(g_context, batch) != 0) {
            return env->NewStringUTF("Не удалось обработать контекст. Попробуй сократить историю.");
        }
        offset += static_cast<size_t>(batch_size);
    }

    llama_sampler_reset(g_sampler);
    std::string answer;
    answer.reserve(4096);
    const int limit = std::clamp(static_cast<int>(max_tokens), 16, 1024);
    for (int i = 0; i < limit; ++i) {
        const llama_token token = llama_sampler_sample(g_sampler, g_context, -1);
        if (llama_vocab_is_eog(vocab, token)) break;

        char piece[1024];
        const int32_t piece_len = llama_token_to_piece(vocab, token, piece, sizeof(piece), 0, false);
        if (piece_len > 0) answer.append(piece, static_cast<size_t>(piece_len));

        llama_batch batch = llama_batch_get_one(const_cast<llama_token *>(&token), 1);
        if (llama_decode(g_context, batch) != 0) break;
    }
    return env->NewStringUTF(answer.c_str());
}

extern "C"
JNIEXPORT void JNICALL
Java_com_stahij_jarvis_NativeLlm_nativeUnload(JNIEnv *, jobject) {
    std::lock_guard<std::mutex> lock(g_mutex);
    release_model();
}
