package com.ona.miciclo.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.ona.miciclo.data.local.dao.CycleRecordDao
import com.ona.miciclo.data.local.dao.DailyLogDao
import com.ona.miciclo.data.local.dao.UserPreferencesDao
import com.ona.miciclo.data.local.entity.CycleRecordEntity
import com.ona.miciclo.data.local.entity.DailyLogEntity
import com.ona.miciclo.data.local.entity.UserPreferencesEntity

/**
 * Base de datos Room principal de Ona-MiCiclo.
 *
 * SEGURIDAD: Esta base de datos se abre SIEMPRE con SQLCipher.
 * La configuración de encriptación está en DatabaseModule.kt.
 * Sin la passphrase correcta del Android Keystore, los datos son ilegibles.
 *
 * exportSchema = true: permite generar archivos de esquema para testing de migraciones.
 * Los archivos de esquema se guardan en app/schemas/ y deben incluirse en el repo.
 */
@Database(
    entities = [
        CycleRecordEntity::class,
        DailyLogEntity::class,
        UserPreferencesEntity::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class OnaDatabase : RoomDatabase() {
    abstract fun cycleRecordDao(): CycleRecordDao
    abstract fun dailyLogDao(): DailyLogDao
    abstract fun userPreferencesDao(): UserPreferencesDao
}
