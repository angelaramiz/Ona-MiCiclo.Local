package com.ona.miciclo.core.notification

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ona.miciclo.calendar.domain.repository.CycleRepository
import com.ona.miciclo.calendar.domain.usecase.CalculateCyclePredictionUseCase
import com.ona.miciclo.data.local.dao.UserPreferencesDao
import java.time.LocalDate

/**
 * Worker diario de recordatorios (A2, WorkManager).
 *
 * Solo para hostess/solo (el partner es solo lectura; sus avisos llegan en B1):
 * calcula la predicción, pregunta al [ReminderPlanner] qué vence hoy, muestra
 * las notificaciones y marca las claves para no repetirlas.
 */
class ReminderWorker(
    appContext: Context,
    workerParams: WorkerParameters,
    private val predictionUseCase: CalculateCyclePredictionUseCase,
    private val cycleRepository: CycleRepository,
    private val userPreferencesDao: UserPreferencesDao
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val userId = inputData.getString(KEY_USER_ID) ?: return Result.success()
        return try {
            val prefs = userPreferencesDao.getByUserId(userId)
            // Solo hostess: el partner no tiene predicción propia ni registros.
            if (prefs?.userRole == "partner") return Result.success()

            val prediction = predictionUseCase(userId)
            val lastLog = cycleRepository.getAllDailyLogsSync(userId)
                .maxOfOrNull { it.fecha }
            val reminderPrefs = ReminderPrefs(applicationContext)

            val due = ReminderPlanner.due(
                ReminderPlanner.Inputs(
                    prediction = prediction,
                    today = LocalDate.now(),
                    periodEnabled = reminderPrefs.periodEnabled,
                    fertileEnabled = reminderPrefs.fertileEnabled,
                    logEnabled = reminderPrefs.logEnabled,
                    lastLogDate = lastLog,
                    alreadyNotified = reminderPrefs.notifiedKeys()
                )
            )
            if (due.isNotEmpty()) {
                val helper = NotificationHelper(applicationContext)
                due.forEach { helper.showReminder(it.key.hashCode(), it.title, it.text) }
                reminderPrefs.markNotified(due.map { it.key })
            }
            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.retry()
        }
    }

    companion object {
        const val KEY_USER_ID = "userId"
    }
}
