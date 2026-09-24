package com.ona.miciclo.ai.domain

/**
 * Formato de prompt Qwen3 (ChatML) para el motor GGUF/llama.cpp.
 *
 * Qwen3-4B base es un modelo *thinking*: sin `/no_think` emite bloques
 * `<think>...</think>` antes de responder. La app espera respuestas directas
 * (JSON en PromptBuilder), así que se desactiva el razonamiento por defecto.
 */
object Qwen3Prompt {
    const val NO_THINK_SUFFIX = " /no_think"

    fun format(system: String, user: String, enableThinking: Boolean = false): String {
        val userText = if (enableThinking) user else user.trimEnd() + NO_THINK_SUFFIX
        return buildString {
            append("<|im_start|>system\n")
            append(system)
            append("<|im_end|>\n")
            append("<|im_start|>user\n")
            append(userText)
            append("<|im_end|>\n")
            append("<|im_start|>assistant\n")
        }
    }

    /** Quita bloques <think> residuales por si el modelo razonó de todos modos. */
    fun stripThinking(text: String): String {
        return text.replace(Regex("(?s)<think>.*?</think>"), "").trim()
    }
}
