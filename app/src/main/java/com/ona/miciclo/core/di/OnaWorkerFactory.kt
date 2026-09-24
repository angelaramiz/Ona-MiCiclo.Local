package com.ona.miciclo.core.di

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import com.ona.miciclo.ai.data.GgufModelDownloader
import com.ona.miciclo.ai.data.ModelDownloadWorker
import com.ona.miciclo.core.sync.SupabaseSyncManager
import com.ona.miciclo.core.sync.SyncWorker
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/**
 * WorkerFactory manual de Ona que construye los workers con sus dependencias Hilt.
 *
 * Sustituye al `HiltWorkerFactory` porque el procesador KSP de Hilt no genera los
 * bindings `@WorkerKey` de `@HiltWorker` en este setup. `OnaApp` lo registra en
 * la `Configuration` de WorkManager.
 */
@Singleton
class OnaWorkerFactory @Inject constructor(
    private val syncManagerProvider: Provider<SupabaseSyncManager>,
    private val ggufModelDownloaderProvider: Provider<GgufModelDownloader>
) : WorkerFactory() {

    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters
    ): ListenableWorker? {
        return when (workerClassName) {
            SyncWorker::class.java.name ->
                SyncWorker(appContext, workerParameters, syncManagerProvider.get())
            ModelDownloadWorker::class.java.name ->
                ModelDownloadWorker(appContext, workerParameters, ggufModelDownloaderProvider.get())
            else -> null
        }
    }
}