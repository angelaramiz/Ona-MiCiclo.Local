package com.ona.miciclo

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Ona-MiCiclo Application class.
 * 
 * @HiltAndroidApp triggers Hilt's code generation for dependency injection.
 * No analytics SDKs, no tracking libraries — privacy by design.
 */
@HiltAndroidApp
class OnaApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Cargar librería nativa de SQLCipher antes de cualquier operación de base de datos
        System.loadLibrary("sqlcipher")
    }
}

