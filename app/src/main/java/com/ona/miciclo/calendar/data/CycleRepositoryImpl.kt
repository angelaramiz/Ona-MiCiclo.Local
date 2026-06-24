package com.ona.miciclo.calendar.data

import com.ona.miciclo.calendar.domain.model.CycleRecord
import com.ona.miciclo.calendar.domain.model.DailyLog
import com.ona.miciclo.calendar.domain.repository.CycleRepository
import com.ona.miciclo.data.local.dao.CycleRecordDao
import com.ona.miciclo.data.local.dao.DailyLogDao
import com.ona.miciclo.data.mapper.CycleMapper
import com.ona.miciclo.data.mapper.DailyLogMapper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementación del repositorio de ciclo.
 * Mapea entre entidades Room y modelos de dominio.
 */
@Singleton
class CycleRepositoryImpl @Inject constructor(
    private val cycleRecordDao: CycleRecordDao,
    private val dailyLogDao: DailyLogDao,
    private val syncManagerProvider: javax.inject.Provider<com.ona.miciclo.core.sync.FirestoreSyncManager>
) : CycleRepository {

    // Helper to trigger background sync if user is hostess
    private fun triggerSync(userId: String) {
        syncManagerProvider.get().syncHostessDataToCloud(userId)
    }

    // ── Cycle Records ──

    override fun getAllCycleRecords(userId: String): Flow<List<CycleRecord>> {
        return cycleRecordDao.getAllByUser(userId).map { entities ->
            entities.map { CycleMapper.entityToDomain(it) }
        }
    }

    override suspend fun getLastCycleRecords(userId: String, limit: Int): List<CycleRecord> {
        return cycleRecordDao.getLastRecords(userId, limit).map { CycleMapper.entityToDomain(it) }
    }

    override suspend fun getLatestCycleRecord(userId: String): CycleRecord? {
        return cycleRecordDao.getLatestRecord(userId)?.let { CycleMapper.entityToDomain(it) }
    }

    override suspend fun getCycleRecordByDate(userId: String, date: LocalDate): CycleRecord? {
        return cycleRecordDao.getByDate(userId, date)?.let { CycleMapper.entityToDomain(it) }
    }

    override suspend fun saveCycleRecord(record: CycleRecord): Long {
        val result = cycleRecordDao.insert(CycleMapper.domainToEntity(record))
        triggerSync(record.userId)
        return result
    }

    override suspend fun updateCycleRecord(record: CycleRecord) {
        cycleRecordDao.update(CycleMapper.domainToEntity(record))
        triggerSync(record.userId)
    }

    override suspend fun deleteCycleRecord(id: Long) {
        cycleRecordDao.deleteById(id)
        // Omitimos triggerSync aquí ya que no tenemos userId de forma directa,
        // o se sincronizará en el siguiente cambio
    }

    override suspend fun getCycleRecordCount(userId: String): Int {
        return cycleRecordDao.getRecordCount(userId)
    }

    // ── Daily Logs ──

    override fun getDailyLogsByDateRange(
        userId: String,
        startDate: LocalDate,
        endDate: LocalDate
    ): Flow<List<DailyLog>> {
        return dailyLogDao.getByDateRange(userId, startDate, endDate).map { entities ->
            entities.map { DailyLogMapper.entityToDomain(it) }
        }
    }

    override suspend fun getDailyLogByDate(userId: String, date: LocalDate): DailyLog? {
        return dailyLogDao.getByDate(userId, date)?.let { DailyLogMapper.entityToDomain(it) }
    }

    override suspend fun saveDailyLog(log: DailyLog): Long {
        val result = dailyLogDao.insert(DailyLogMapper.domainToEntity(log))
        triggerSync(log.userId)
        return result
    }

    override suspend fun updateDailyLog(log: DailyLog) {
        dailyLogDao.update(DailyLogMapper.domainToEntity(log))
        triggerSync(log.userId)
    }

    override fun getAllDailyLogs(userId: String): Flow<List<DailyLog>> {
        return dailyLogDao.getAllByUser(userId).map { entities ->
            entities.map { DailyLogMapper.entityToDomain(it) }
        }
    }

    // ── Bulk operations ──

    override suspend fun getAllCycleRecordsSync(userId: String): List<CycleRecord> {
        return cycleRecordDao.getAllByUserSync(userId).map { CycleMapper.entityToDomain(it) }
    }

    override suspend fun getAllDailyLogsSync(userId: String): List<DailyLog> {
        return dailyLogDao.getAllByUserSync(userId).map { DailyLogMapper.entityToDomain(it) }
    }

    override suspend fun deleteAllData(userId: String) {
        cycleRecordDao.deleteAllByUser(userId)
        dailyLogDao.deleteAllByUser(userId)
    }
}
