package com.ona.miciclo.calendar.domain.repository

import com.ona.miciclo.calendar.domain.model.CycleRecord
import com.ona.miciclo.calendar.domain.model.DailyLog
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * Interfaz del repositorio de ciclo menstrual.
 * Desacoplada de Room — permite testing con mocks.
 */
interface CycleRepository {

    // ── Cycle Records ──
    fun getAllCycleRecords(userId: String): Flow<List<CycleRecord>>
    suspend fun getLastCycleRecords(userId: String, limit: Int): List<CycleRecord>
    suspend fun getLatestCycleRecord(userId: String): CycleRecord?
    suspend fun getCycleRecordByDate(userId: String, date: LocalDate): CycleRecord?
    suspend fun saveCycleRecord(record: CycleRecord): Long
    suspend fun updateCycleRecord(record: CycleRecord)
    suspend fun deleteCycleRecord(id: Long)
    suspend fun getCycleRecordCount(userId: String): Int

    // ── Daily Logs ──
    fun getDailyLogsByDateRange(userId: String, startDate: LocalDate, endDate: LocalDate): Flow<List<DailyLog>>
    suspend fun getDailyLogByDate(userId: String, date: LocalDate): DailyLog?
    suspend fun saveDailyLog(log: DailyLog): Long
    suspend fun updateDailyLog(log: DailyLog)
    fun getAllDailyLogs(userId: String): Flow<List<DailyLog>>

    // ── Bulk operations (for export/import) ──
    suspend fun getAllCycleRecordsSync(userId: String): List<CycleRecord>
    suspend fun getAllDailyLogsSync(userId: String): List<DailyLog>
    suspend fun deleteAllData(userId: String)
}
