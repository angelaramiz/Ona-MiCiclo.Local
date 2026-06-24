package com.ona.miciclo.core.di

import com.ona.miciclo.auth.data.AuthRepositoryImpl
import com.ona.miciclo.auth.domain.repository.AuthRepository
import com.ona.miciclo.calendar.data.CycleRepositoryImpl
import com.ona.miciclo.calendar.domain.repository.CycleRepository
import com.ona.miciclo.settings.data.ExportImportRepositoryImpl
import com.ona.miciclo.settings.domain.repository.ExportImportRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
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
}
