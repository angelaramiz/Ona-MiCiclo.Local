package com.ona.miciclo.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.ona.miciclo.data.local.entity.UserPreferencesEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO para preferencias del usuario.
 */
@Dao
interface UserPreferencesDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(preferences: UserPreferencesEntity)

    @Update
    suspend fun update(preferences: UserPreferencesEntity)

    @Query("SELECT * FROM user_preferences WHERE user_id = :userId LIMIT 1")
    suspend fun getByUserId(userId: String): UserPreferencesEntity?

    @Query("SELECT * FROM user_preferences WHERE user_id = :userId LIMIT 1")
    fun observeByUserId(userId: String): Flow<UserPreferencesEntity?>

    @Query("DELETE FROM user_preferences WHERE user_id = :userId")
    suspend fun deleteByUserId(userId: String)
}
