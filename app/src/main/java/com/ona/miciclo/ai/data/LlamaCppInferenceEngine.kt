package com.ona.miciclo.ai.data

import com.ona.miciclo.ai.domain.IInferenceEngine
import com.ona.miciclo.ai.domain.Qwen3Prompt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import javax.inject.Inject

/**
 * Puente JNI hacia `libona_llama.so` (llama.cpp v0.5.0, compilado vía CMake/NDK).
 * Firmas EXACTAS del `llama_bridge.cpp` — un parámetro incorrecto = crash nativo.
 */
internal object LlamaCppBridge {
    private const val LIB_NAME = "ona_llama"

    @Volatile
    private var loaded: Boolean = false

    /**
     * Carga perezosa de la lib nativa. Captura Throwable (UnsatisfiedLinkError
     * incluido). Sin android.util.Log para seguir siendo testeable en JVM.
     */
    fun ensureLoaded(): Boolean {
        if (loaded) return true
        return try {
            System.loadLibrary(LIB_NAME)
            loaded = true
            true
        } catch (t: Throwable) {
            false
        }
    }

    external fun loadModelNative(path: String): Long
    external fun unloadModelNative(handle: Long)
    external fun generateNative(handle: Long, prompt: String, maxTokens: Int): String
}

/**
 * Motor de inferencia GGUF (Qwen3-4B) vía llama.cpp (libona_llama.so).
 * Es el motor activo de la app (binding en AppModule). La ruta del .gguf la
 * provee `GgufModelDownloader.modelFile`.
 */
class LlamaCppInferenceEngine @Inject constructor() : IInferenceEngine {

    private var handle: Long = 0L

    override fun isModelLoaded(): Boolean = handle != 0L

    override suspend fun loadModel(modelPath: String): Result<Unit> {
        val file = File(modelPath)
        if (!file.exists()) {
            return Result.failure(Exception("Archivo GGUF no encontrado: $modelPath"))
        }
        if (file.length() < GgufModelSpec.MIN_SIZE_BYTES) {
            return Result.failure(Exception("Archivo GGUF incompleto (${file.length()} bytes)."))
        }
        if (!LlamaCppBridge.ensureLoaded()) {
            return Result.failure(Exception("No se pudo cargar la librería nativa ona_llama."))
        }
        val newHandle = try {
            LlamaCppBridge.loadModelNative(file.absolutePath)
        } catch (t: Throwable) {
            return Result.failure(Exception("Fallo nativo al inicializar el modelo: ${t.message}"))
        }
        if (newHandle == 0L) {
            return Result.failure(Exception("llama.cpp no pudo inicializar el modelo (handle 0)."))
        }
        handle = newHandle
        return Result.success(Unit)
    }

    override fun generateResponse(prompt: String): Flow<String> = flow {
        if (!isModelLoaded()) {
            throw IllegalStateException("Modelo GGUF no cargado. Llama loadModel() primero.")
        }
        // Qwen3Prompt.format lo aplica el llamador; aquí se sanea por seguridad.
        val output = LlamaCppBridge.generateNative(handle, prompt, 512)
        emit(Qwen3Prompt.stripThinking(output))
    }.flowOn(Dispatchers.IO)

    override fun unloadModel() {
        if (handle != 0L) {
            try {
                LlamaCppBridge.unloadModelNative(handle)
            } catch (t: Throwable) {
                // Liberación best-effort.
            } finally {
                handle = 0L
            }
        }
    }
}
