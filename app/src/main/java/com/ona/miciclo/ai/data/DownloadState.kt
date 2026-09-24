package com.ona.miciclo.ai.data

/**
 * Estado del proceso de descarga de un modelo (MNN legacy y GGUF/Qwen3).
 */
sealed interface DownloadState {
    object Idle : DownloadState
    data class Downloading(val progress: Float) : DownloadState
    object Success : DownloadState
    data class Error(val error: Throwable) : DownloadState
}