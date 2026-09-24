package com.ona.miciclo.core.sync

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

/**
 * Agenda y cancela el sync periódico en segundo plano.
 *
 * WorkManager garantiza el trabajo en background (con las restricciones de
 * Doze/batería de Android) aunque el proceso de la app esté muerto. El loop
 * de 15s de SupabaseSyncManager sigue cubriendo el sync inmediato mientras la
 * app está abierta; este es el respaldo cuando está cerrada.
 */
object SyncScheduler {

    private const val WORK_TAG = "ona-background-sync"
    private const val SYNC_INTERVAL_MINUTES = 15L

    private fun workName(userId: String) = "ona-sync-$userId"

    /** Agenda (o actualiza) el sync periódico del usuario. Idempotente. */
    fun schedulePeriodic(context: Context, userId: String) {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(SYNC_INTERVAL_MINUTES, TimeUnit.MINUTES)
            .addTag(WORK_TAG)
            .setInputData(workDataOf(SyncWorker.KEY_USER_ID to userId))
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            workName(userId),
            ExistingPeriodicWorkPolicy.REPLACE,
            request
        )
    }

    /** Cancela el sync periódico del usuario (logout). */
    fun cancel(context: Context, userId: String) {
        WorkManager.getInstance(context).cancelUniqueWork(workName(userId))
    }

    /** Cancela todos los syncs periódicos (logout global / limpieza). */
    fun cancelAll(context: Context) {
        WorkManager.getInstance(context).cancelAllWorkByTag(WORK_TAG)
    }
}