package com.ona.miciclo.ai.data

import android.util.Log
import java.io.File

object MnnLlmBridge {
    private const val TAG = "MnnLlmBridge"
    
    var isAvailable = false
        private set
        
    var lastInitError: String? = null
        private set

    fun tryLoadLibraries(): Boolean {
        if (isAvailable) return true
        
        try {
            // Cargar en orden de dependencias
            System.loadLibrary("c++_shared")
            System.loadLibrary("MNN")
            System.loadLibrary("llm")
            isAvailable = true
            lastInitError = null
            Log.d(TAG, "MNN Native libraries loaded successfully")
            return true
        } catch (t: Throwable) {
            lastInitError = t.localizedMessage ?: t.message ?: "Unknown UnsatisfiedLinkError"
            Log.e(TAG, "Failed to load MNN native libraries", t)
            isAvailable = false
            return false
        }
    }

    fun validateModelFiles(modelDir: File): String? {
        val requiredFiles = listOf(
            "config.json",
            "llm.mnn",
            "llm.mnn.weight",
            "embeddings_bf16.bin",
            "visual.mnn",
            "visual.mnn.weight",
            "tokenizer.txt"
        )
        
        if (!modelDir.exists() || !modelDir.isDirectory) {
            return "El directorio del modelo no existe o no es un directorio válido."
        }
        
        val missing = mutableListOf<String>()
        for (fileName in requiredFiles) {
            val file = File(modelDir, fileName)
            if (!file.exists() || file.length() == 0L) {
                missing.add(fileName)
            }
        }
        
        return if (missing.isNotEmpty()) {
            "Faltan los siguientes archivos del modelo: ${missing.joinToString(", ")}"
        } else {
            null
        }
    }
}
