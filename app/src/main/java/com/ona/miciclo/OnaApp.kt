package com.ona.miciclo

import android.app.Application
import androidx.work.Configuration
import com.ona.miciclo.core.debug.DebugTelemetry
import com.ona.miciclo.core.di.OnaWorkerFactory
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Ona-MiCiclo Application class.
 * 
 * @HiltAndroidApp triggers Hilt's code generation for dependency injection.
 * No analytics SDKs, no tracking libraries — privacy by design.
 */
@HiltAndroidApp
class OnaApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: OnaWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        // Cargar librería nativa de SQLCipher antes de cualquier operación de base de datos
        System.loadLibrary("sqlcipher")
        // #region debug-session: app-update-crash
        // Captura de crash global: reporta la excepción fatal a la telemetría de debug
        // antes de delegar en el handler por defecto. Solo activo si TELEMETRY_ENABLED.
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            DebugTelemetry.emitException("OnaApp:uncaught", throwable)
            defaultHandler?.uncaughtException(thread, throwable)
        }
        // #endregion
    }
}

