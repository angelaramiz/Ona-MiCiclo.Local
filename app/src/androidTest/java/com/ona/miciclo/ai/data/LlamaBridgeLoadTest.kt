package com.ona.miciclo.ai.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Prueba instrumentada (corre en el dispositivo/emulador, no en JVM).
 * Verifica que libona_llama.so enlaza correctamente en la ABI del dispositivo
 * y que el puente JNI maneja un modelo inexistente sin crash nativo.
 * No requiere descargar el .gguf de 2.5 GB.
 */
@RunWith(AndroidJUnit4::class)
class LlamaBridgeLoadTest {

    @Test
    fun nativeLibLoads() {
        assertTrue(
            "libona_llama.so debe cargar en ${android.os.Build.SUPPORTED_ABIS.firstOrNull()}",
            LlamaCppBridge.ensureLoaded()
        )
    }

    @Test
    fun missingModelReturnsZeroHandleWithoutCrash() {
        assertTrue(LlamaCppBridge.ensureLoaded())
        val handle = LlamaCppBridge.loadModelNative("/data/local/tmp/no-existe.gguf")
        assertEquals(0L, handle)
        // unload de handle inválido no debe crashear
        LlamaCppBridge.unloadModelNative(0L)
    }
}
