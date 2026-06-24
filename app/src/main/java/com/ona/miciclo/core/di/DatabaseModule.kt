package com.ona.miciclo.core.di

import android.content.Context
import androidx.room.Room
import com.ona.miciclo.core.security.KeystoreManager
import com.ona.miciclo.data.local.OnaDatabase
import com.ona.miciclo.data.local.dao.CycleRecordDao
import com.ona.miciclo.data.local.dao.DailyLogDao
import com.ona.miciclo.data.local.dao.UserPreferencesDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import javax.inject.Singleton

/**
 * Módulo Hilt para proveer la base de datos Room encriptada con SQLCipher.
 *
 * PUNTO CRÍTICO DE SEGURIDAD:
 * - La passphrase se obtiene del Android Keystore via KeystoreManager
 * - SQLCipher encripta TODA la base de datos (tablas, índices, WAL, temp files)
 * - Sin la passphrase correcta, un volcado del archivo .db es ilegible
 * - La clave NUNCA está hardcodeada ni se transmite a ningún servidor
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
        keystoreManager: KeystoreManager
    ): OnaDatabase {
        // SEGURIDAD: Obtener passphrase del Keystore.
        // Si es la primera vez, se genera una nueva passphrase aleatoria de 256 bits.
        // Si ya existe, se desencripta la passphrase almacenada.
        val passphrase = keystoreManager.getOrCreateDatabasePassphrase()

        // SQLCipher SupportOpenHelperFactory integra la encriptación transparentemente con Room.
        // Room no necesita saber que la BD está encriptada — SQLCipher lo maneja a nivel SQLite.
        val factory = SupportOpenHelperFactory(passphrase)

        return Room.databaseBuilder(
            context,
            OnaDatabase::class.java,
            "ona_miciclo.db"
        )
            .openHelperFactory(factory) // ← Aquí se activa la encriptación
            .fallbackToDestructiveMigration() // En Fase 1, si hay conflicto de esquema, recrear
            .build()
    }

    @Provides
    fun provideCycleRecordDao(database: OnaDatabase): CycleRecordDao {
        return database.cycleRecordDao()
    }

    @Provides
    fun provideDailyLogDao(database: OnaDatabase): DailyLogDao {
        return database.dailyLogDao()
    }

    @Provides
    fun provideUserPreferencesDao(database: OnaDatabase): UserPreferencesDao {
        return database.userPreferencesDao()
    }
}
