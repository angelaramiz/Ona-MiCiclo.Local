package com.ona.miciclo.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.ona.miciclo.data.local.entity.CycleRecordEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * DAO para registros de ciclo menstrual.
 * Todas las consultas retornan Flow para reactividad con StateFlow en ViewModels.
 */
@Dao
interface CycleRecordDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: CycleRecordEntity): Long

    @Update
    suspend fun update(record: CycleRecordEntity)

    @Query("SELECT * FROM cycle_records WHERE user_id = :userId ORDER BY fecha_inicio_menstruacion DESC")
    fun getAllByUser(userId: String): Flow<List<CycleRecordEntity>>

    @Query("SELECT * FROM cycle_records WHERE user_id = :userId ORDER BY fecha_inicio_menstruacion DESC LIMIT :limit")
    suspend fun getLastRecords(userId: String, limit: Int): List<CycleRecordEntity>

    @Query("SELECT * FROM cycle_records WHERE user_id = :userId AND fecha_inicio_menstruacion = :date LIMIT 1")
    suspend fun getByDate(userId: String, date: LocalDate): CycleRecordEntity?

    @Query("SELECT * FROM cycle_records WHERE user_id = :userId ORDER BY fecha_inicio_menstruacion DESC LIMIT 1")
    suspend fun getLatestRecord(userId: String): CycleRecordEntity?

    @Query("SELECT COUNT(*) FROM cycle_records WHERE user_id = :userId")
    suspend fun getRecordCount(userId: String): Int

    @Query("DELETE FROM cycle_records WHERE user_id = :userId")
    suspend fun deleteAllByUser(userId: String)

    @Query("DELETE FROM cycle_records WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(records: List<CycleRecordEntity>)

    @androidx.room.Transaction
    suspend fun clearAndInsertCycles(userId: String, records: List<CycleRecordEntity>) {
        deleteAllByUser(userId)
        insertAll(records)
    }

    /** Para export — obtiene todos los registros sin Flow */
    @Query("SELECT * FROM cycle_records WHERE user_id = :userId ORDER BY fecha_inicio_menstruacion ASC")
    suspend fun getAllByUserSync(userId: String): List<CycleRecordEntity>
}
