package com.ona.miciclo.core.notification

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

/**
 * Agenda el worker diario de recordatorios (A2).
 */
object ReminderScheduler {

    private const val WORK_TAG = "ona-reminders"
    private const val INTERVAL_HOURS = 24L

    private fun workName(userId: String) = "ona-reminders-$userId"

    /** Agenda (o actualiza) el chequeo diario. Idempotente. */
    fun scheduleDaily(context: Context, userId: String) {
        val request = PeriodicWorkRequestBuilder<ReminderWorker>(INTERVAL_HOURS, TimeUnit.HOURS)
            .addTag(WORK_TAG)
            .setInputData(workDataOf(ReminderWorker.KEY_USER_ID to userId))
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            workName(userId),
            ExistingPeriodicWorkPolicy.REPLACE,
            request
        )
    }

    /** Disparo único (verificación manual / E2E). */
    fun triggerNow(context: Context, userId: String) {
        val request = OneTimeWorkRequestBuilder<ReminderWorker>()
            .addTag(WORK_TAG)
            .setInputData(workDataOf(ReminderWorker.KEY_USER_ID to userId))
            .build()
        WorkManager.getInstance(context).enqueue(request)
    }

    fun cancel(context: Context, userId: String) {
        WorkManager.getInstance(context).cancelUniqueWork(workName(userId))
    }
}
