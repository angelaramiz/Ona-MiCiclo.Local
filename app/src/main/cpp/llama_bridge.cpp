// Puente JNI mínimo hacia llama.cpp (core C API, greedy decoding).
// No usa `common/` para mantener la superficie pequeña y estable.
// REGLA: ninguna excepción C++ cruza JNI (todo se captura y devuelve error).
#include <jni.h>
#include <string>
#include <vector>
#include <thread>
#include "llama.h"

struct OnaLlamaHandle {
    llama_model* model = nullptr;
    llama_context* ctx = nullptr;
    llama_sampler* sampler = nullptr;
};

static void free_handle(OnaLlamaHandle* h) {
    if (!h) return;
    if (h->sampler) { llama_sampler_free(h->sampler); h->sampler = nullptr; }
    if (h->ctx) { llama_free(h->ctx); h->ctx = nullptr; }
    if (h->model) { llama_model_free(h->model); h->model = nullptr; }
    delete h;
}

static std::string jstring_to_std(JNIEnv* env, jstring s) {
    if (!s) return {};
    const char* c = env->GetStringUTFChars(s, nullptr);
    std::string out(c ? c : "");
    if (c) env->ReleaseStringUTFChars(s, c);
    return out;
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_ona_miciclo_ai_data_LlamaCppBridge_loadModelNative(
        JNIEnv* env, jobject /*thiz*/, jstring jpath) {
    try {
        const std::string path = jstring_to_std(env, jpath);
        if (path.empty()) return 0;

        llama_backend_init();

        llama_model_params mparams = llama_model_default_params();
        llama_model* model = llama_model_load_from_file(path.c_str(), mparams);
        if (!model) return 0;

        llama_context_params cparams = llama_context_default_params();
        cparams.n_ctx = 2048;
        unsigned threads = std::thread::hardware_concurrency();
        cparams.n_threads = (int)(threads == 0 ? 4 : (threads > 4 ? 4 : threads));
        cparams.n_threads_batch = cparams.n_threads;

        llama_context* ctx = llama_init_from_model(model, cparams);
        if (!ctx) { llama_model_free(model); return 0; }

        llama_sampler* sampler = llama_sampler_init_greedy();
        if (!sampler) { llama_free(ctx); llama_model_free(model); return 0; }

        auto* h = new (std::nothrow) OnaLlamaHandle();
        if (!h) { llama_sampler_free(sampler); llama_free(ctx); llama_model_free(model); return 0; }
        h->model = model;
        h->ctx = ctx;
        h->sampler = sampler;
        return reinterpret_cast<jlong>(h);
    } catch (...) {
        return 0;
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_ona_miciclo_ai_data_LlamaCppBridge_unloadModelNative(
        JNIEnv* /*env*/, jobject /*thiz*/, jlong handle) {
    try {
        free_handle(reinterpret_cast<OnaLlamaHandle*>(handle));
    } catch (...) {
        // Nunca propagar a JNI.
    }
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_ona_miciclo_ai_data_LlamaCppBridge_generateNative(
        JNIEnv* env, jobject /*thiz*/, jlong handle, jstring jprompt, jint maxTokens) {
    try {
        auto* h = reinterpret_cast<OnaLlamaHandle*>(handle);
        if (!h || !h->model || !h->ctx || !h->sampler) return env->NewStringUTF("");

        const std::string prompt = jstring_to_std(env, jprompt);
        if (prompt.empty()) return env->NewStringUTF("");

        const llama_vocab* vocab = llama_model_get_vocab(h->model);
        if (!vocab) return env->NewStringUTF("");

        std::vector<llama_token> tokens(prompt.size() + 16);
        int n_tokens = llama_tokenize(vocab, prompt.c_str(), (int)prompt.size(),
                                      tokens.data(), (int)tokens.size(), true, true);
        if (n_tokens < 0) {
            tokens.resize((size_t)-n_tokens);
            n_tokens = llama_tokenize(vocab, prompt.c_str(), (int)prompt.size(),
                                      tokens.data(), (int)tokens.size(), true, true);
        }
        if (n_tokens <= 0) return env->NewStringUTF("");
        tokens.resize((size_t)n_tokens);

        const int max_new = maxTokens > 0 ? (int)maxTokens : 256;
        const int n_ctx = llama_n_ctx(h->ctx);
        if (n_tokens + max_new + 8 >= n_ctx) return env->NewStringUTF(""); // Prompt fuera de ventana.
        std::string output;
        output.reserve(1024);

        // Prefill del prompt en un solo batch (prompts de salud < 512 tokens).
        {
            llama_batch batch = llama_batch_get_one(tokens.data(), n_tokens);
            if (llama_decode(h->ctx, batch) != 0) return env->NewStringUTF("");
        }
        int n_cur = n_tokens;

        char piece[256];
        for (int i = 0; i < max_new; ++i) {
            llama_token next = llama_sampler_sample(h->sampler, h->ctx, -1);
            llama_sampler_accept(h->sampler, next);
            if (llama_vocab_is_eog(vocab, next)) break;
            int n_piece = llama_detokenize(vocab, &next, 1, piece, sizeof(piece), false, true);
            if (n_piece > 0) output.append(piece, (size_t)n_piece);
            if (++n_cur >= llama_n_ctx(h->ctx) - 4) break;
            llama_batch batch = llama_batch_get_one(&next, 1);
            if (llama_decode(h->ctx, batch) != 0) break;
        }
        return env->NewStringUTF(output.c_str());
    } catch (...) {
        return env->NewStringUTF("");
    }
}
