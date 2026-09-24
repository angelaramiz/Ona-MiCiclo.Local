package com.ona.miciclo.ai.data

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.flow.collect

/**
 * Worker que descarga el modelo GGUF (Qwen3-4B, ~2.5 GB) en segundo plano.
 *
 * WorkManager mantiene el proceso vivo (wakelock) mientras descarga, aunque la
 * app esté en background. Reporta progreso 0..1 vía `setProgress`, que la UI
 * observa con `getWorkInfosByTagFlow`. Si el proceso muere, WorkManager relanza
 * el worker (la descarga empieza de nuevo; ver GgufModelDownloader para la
 * validación de archivo incompleto).
 *
 * Construido por `OnaWorkerFactory` (Hilt), no por @HiltWorker (KSP no lo genera).
 */
class ModelDownloadWorker(
    appContext: Context,
    workerParams: WorkerParameters,
    private val downloader: GgufModelDownloader
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            downloader.downloadModel().collect { state ->
                when (state) {
                    is DownloadState.Downloading ->
                        setProgress(workDataOf(KEY_PROGRESS to state.progress))
                    DownloadState.Success ->
                        setProgress(workDataOf(KEY_PROGRESS to 1f))
                    DownloadState.Idle -> Unit
                    is DownloadState.Error -> throw state.error
                }
            }
            Result.success()
        } catch (e: Exception) {
            Result.failure(workDataOf(KEY_ERROR to (e.message ?: "Error de descarga")))
        }
    }

    companion object {
        const val KEY_PROGRESS = "progress"
        const val KEY_ERROR = "error"
    }
}