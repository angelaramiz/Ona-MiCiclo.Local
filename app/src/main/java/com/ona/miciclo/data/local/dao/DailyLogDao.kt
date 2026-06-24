package com.ona.miciclo.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.ona.miciclo.data.local.entity.DailyLogEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * DAO para registros diarios de síntomas y flujo.
 */
@Dao
interface DailyLogDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: DailyLogEntity): Long

    @Update
    suspend fun update(log: DailyLogEntity)

    @Query("SELECT * FROM daily_logs WHERE user_id = :userId AND fecha = :date LIMIT 1")
    suspend fun getByDate(userId: String, date: LocalDate): DailyLogEntity?

    @Query("SELECT * FROM daily_logs WHERE user_id = :userId AND fecha BETWEEN :startDate AND :endDate ORDER BY fecha ASC")
    fun getByDateRange(userId: String, startDate: LocalDate, endDate: LocalDate): Flow<List<DailyLogEntity>>

    @Query("SELECT * FROM daily_logs WHERE user_id = :userId AND fecha BETWEEN :startDate AND :endDate ORDER BY fecha ASC")
    suspend fun getByDateRangeSync(userId: String, startDate: LocalDate, endDate: LocalDate): List<DailyLogEntity>

    @Query("SELECT * FROM daily_logs WHERE user_id = :userId ORDER BY fecha DESC")
    fun getAllByUser(userId: String): Flow<List<DailyLogEntity>>

    @Query("DELETE FROM daily_logs WHERE user_id = :userId")
    suspend fun deleteAllByUser(userId: String)

    @Query("DELETE FROM daily_logs WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** Para export — obtiene todos los registros sin Flow */
    @Query("SELECT * FROM daily_logs WHERE user_id = :userId ORDER BY fecha ASC")
    suspend fun getAllByUserSync(userId: String): List<DailyLogEntity>
}
