package com.ona.miciclo.core.di

import android.content.Context
import com.google.firebase.firestore.FirebaseFirestore
import com.ona.miciclo.auth.data.AuthRepositoryImpl
import com.ona.miciclo.auth.domain.repository.AuthRepository
import com.ona.miciclo.calendar.data.CycleRepositoryImpl
import com.ona.miciclo.calendar.domain.repository.CycleRepository
import com.ona.miciclo.core.update.UpdateManager
import com.ona.miciclo.data.local.OnaDatabase
import com.ona.miciclo.data.local.dao.CycleRecordDao
import com.ona.miciclo.data.local.dao.DailyLogDao
import com.ona.miciclo.data.local.dao.UserPreferencesDao
import com.ona.miciclo.settings.data.ExportImportRepositoryImpl
import com.ona.miciclo.settings.domain.repository.ExportImportRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Módulo Hilt principal para bindings de interfaces a implementaciones.
 * Facilita el testing al permitir sustituir implementaciones con mocks.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {

    @Binds
    @Singleton
    abstract fun bindAuthRepository(impl: AuthRepositoryImpl): AuthRepository

    @Binds
    @Singleton
    abstract fun bindCycleRepository(impl: CycleRepositoryImpl): CycleRepository

    @Binds
    @Singleton
    abstract fun bindExportImportRepository(impl: ExportImportRepositoryImpl): ExportImportRepository

    companion object {
        @Provides
        @Singleton
        fun provideUpdateManager(
            @ApplicationContext context: Context
        ): UpdateManager {
            return UpdateManager(context)
        }

        @Provides
        @Singleton
        fun provideFirebaseFirestore(): FirebaseFirestore {
            return FirebaseFirestore.getInstance()
        }

        @Provides
        @Singleton
        fun provideFirestoreSyncManager(
            db: FirebaseFirestore,
            database: OnaDatabase,
            cycleRecordDao: CycleRecordDao,
            dailyLogDao: DailyLogDao,
            userPreferencesDao: UserPreferencesDao
        ): com.ona.miciclo.core.sync.FirestoreSyncManager {
            return com.ona.miciclo.core.sync.FirestoreSyncManager(db, database, cycleRecordDao, dailyLogDao, userPreferencesDao)
        }
    }
}
