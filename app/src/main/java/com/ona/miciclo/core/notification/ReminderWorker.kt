package com.ona.miciclo.core.notification

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ona.miciclo.calendar.domain.repository.CycleRepository
import com.ona.miciclo.calendar.domain.usecase.CalculateCyclePredictionUseCase
import com.ona.miciclo.data.local.dao.UserPreferencesDao
import java.time.LocalDate

/**
 * Worker diario de recordatorios (A2) + avisos al partner (B1, WorkManager).
 *
 * - Hostess/solo: recordatorios propios (periodo, fértil, registro).
 * - Partner: avisos sobre el ciclo de la hostess vinculada, SOLO si activó
 *   "Recibir avisos" en su teléfono (opt-in local, apagado por defecto).
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
            val reminderPrefs = ReminderPrefs(applicationContext)

            if (prefs?.userRole == "partner" && !prefs.linkedUserId.isNullOrEmpty()) {
                runPartnerAlerts(prefs.linkedUserId!!, reminderPrefs)
                return Result.success()
            }

            val prediction = predictionUseCase(userId)
            val lastLog = cycleRepository.getAllDailyLogsSync(userId)
                .maxOfOrNull { it.fecha }

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
            showAll(due, reminderPrefs)
            // Widget (A3): snapshot diario aunque no haya avisos.
            try {
                val todayLog = cycleRepository.getDailyLogByDate(userId, LocalDate.now())
                com.ona.miciclo.core.widget.OnaWidgetProvider.refresh(
                    applicationContext, prediction, isPartner = false, todayLog = todayLog
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.retry()
        }
    }

    /**
     * Avisos al partner sobre el ciclo de su hostess (B1).
     * Requiere opt-in explícito en SU teléfono; sin él, silencio total.
     */
    private suspend fun runPartnerAlerts(hostessId: String, reminderPrefs: ReminderPrefs) {
        if (!reminderPrefs.partnerAlertsEnabled) return
        val prediction = try {
            predictionUseCase(hostessId)
        } catch (e: Exception) {
            null
        }
        val due = ReminderPlanner.due(
            ReminderPlanner.Inputs(
                prediction = prediction,
                today = LocalDate.now(),
                periodEnabled = reminderPrefs.periodEnabled,
                fertileEnabled = reminderPrefs.fertileEnabled,
                logEnabled = false,
                lastLogDate = null,
                alreadyNotified = reminderPrefs.notifiedKeys(),
                partnerMode = true
            )
        )
        showAll(due, reminderPrefs)
        // Widget (A3): también en modo pareja, con etiqueta de pareja.
        try {
            val todayLog = cycleRepository.getDailyLogByDate(hostessId, LocalDate.now())
            com.ona.miciclo.core.widget.OnaWidgetProvider.refresh(
                applicationContext, prediction, isPartner = true, todayLog = todayLog
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun showAll(due: List<ReminderPlanner.Reminder>, reminderPrefs: ReminderPrefs) {
        if (due.isEmpty()) return
        val helper = NotificationHelper(applicationContext)
        due.forEach { helper.showReminder(it.key.hashCode(), it.title, it.text) }
        reminderPrefs.markNotified(due.map { it.key })
    }

    companion object {
        const val KEY_USER_ID = "userId"
    }
}
