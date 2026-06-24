package com.ona.miciclo.ai.domain

import kotlinx.coroutines.flow.Flow

/**
 * Interfaz genérica para el motor de inferencia local.
 */
interface IInferenceEngine {
    /**
     * Comprueba si el modelo local está cargado e inicializado.
     */
    fun isModelLoaded(): Boolean

    /**
     * Carga el modelo local desde el almacenamiento.
     * @param modelPath Ruta al archivo GGUF del modelo.
     */
    suspend fun loadModel(modelPath: String): Result<Unit>

    /**
     * Envía un prompt estructurado al modelo y recibe la respuesta de forma reactiva.
     */
    fun generateResponse(prompt: String): Flow<String>

    /**
     * Libera los recursos del modelo.
     */
    fun unloadModel()
}
