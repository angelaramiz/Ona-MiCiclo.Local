package com.ona.miciclo.ai.data

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

/**
 * Agenda y cancela la descarga del modelo de IA en segundo plano (WorkManager).
 * El worker sobrevive al cierre de la app y su progreso lo observa la UI vía tag.
 */
object ModelDownloadScheduler {

    const val TAG = "ona-model-download"
    private const val UNIQUE_NAME = "ona-model-download"

    /** Inicia (o reemplaza) la descarga en segundo plano. */
    fun start(context: Context) {
        val request = OneTimeWorkRequestBuilder<ModelDownloadWorker>()
            .addTag(TAG)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            UNIQUE_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    /** Cancela la descarga (el worker limpia su archivo temporal). */
    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_NAME)
    }
}