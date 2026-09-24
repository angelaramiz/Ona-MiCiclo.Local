package com.ona.miciclo.core.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * Worker de sincronización en segundo plano (WorkManager).
 *
 * Mantiene la comunicación hostess<->partner aunque la app esté cerrada:
 * - Partner: descarga los datos de la hostess (vista solo lectura).
 * - Hostess/solo: sube todos sus datos locales a la nube.
 *
 * El rol y el id de usuario vinculado se leen de la DB en cada ejecución, así que
 * no hay que reprogramar el worker cuando cambia el rol.
 *
 * NOTA: no usa @HiltWorker (el procesador KSP de Hilt no genera el binding
 * WorkerKey en este setup); la construcción la resuelve `OnaWorkerFactory`
 * (worker factory manual inyectado por Hilt en OnaApp).
 */
class SyncWorker(
    appContext: Context,
    workerParams: WorkerParameters,
    private val syncManager: SupabaseSyncManager
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val userId = inputData.getString(KEY_USER_ID) ?: return Result.success()
        return try {
            syncManager.runSyncOnce(userId)
            Result.success()
        } catch (e: Exception) {
            // Error transitorio (red, nube caída...): reintentar más adelante.
            Result.retry()
        }
    }

    companion object {
        const val KEY_USER_ID = "userId"
    }
}