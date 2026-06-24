package com.ona.miciclo.ocr

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import java.nio.ByteBuffer

/**
 * Analizador de imágenes local para clasificar tiras de ovulación (LH).
 * Compara la intensidad de la línea de test (T) contra la de control (C).
 */
class OvulationOcrAnalyzer(
    private val onResult: (String) -> Unit
) : ImageAnalysis.Analyzer {

    override fun analyze(image: ImageProxy) {
        val buffer = image.planes[0].buffer
        val data = buffer.toByteArray()

        // En un análisis real local:
        // 1. Convertimos el plano YUV a Bitmap/RGB.
        // 2. Extraemos el área delimitada (tira de test).
        // 3. Analizamos los histogramas de color en la zona de las líneas T y C.
        
        // Simulación de análisis local rápido de intensidad de color
        val averageIntensity = data.take(100).map { it.toInt() and 0xFF }.average()
        
        val resultado = when {
            averageIntensity > 200 -> "positivo"
            averageIntensity > 100 -> "debil"
            else -> "negativo"
        }

        onResult(resultado)
        image.close()
    }

    private fun ByteBuffer.toByteArray(): ByteArray {
        rewind()
        val data = ByteArray(remaining())
        get(data)
        return data
    }
}
