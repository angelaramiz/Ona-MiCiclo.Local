package com.ona.miciclo.ai.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

sealed interface DownloadState {
    object Idle : DownloadState
    data class Downloading(val progress: Float) : DownloadState
    object Success : DownloadState
    data class Error(val error: Throwable) : DownloadState
}

@Singleton
class ModelDownloader @Inject constructor(
    @ApplicationContext private val context: Context
) {
    val modelFile: File
        get() = File(context.filesDir, "qwen2.5-1.5b-instruct-q4_k_m.gguf")

    private val modelUrl = "https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/qwen2.5-1.5b-instruct-q4_k_m.gguf"

    fun isModelDownloaded(): Boolean {
        return modelFile.exists() && modelFile.length() > 100 * 1024 * 1024 // Al menos 100MB
    }

    fun deleteModel(): Boolean {
        if (modelFile.exists()) {
            return modelFile.delete()
        }
        return false
    }

    fun downloadModel(): Flow<DownloadState> = flow {
        emit(DownloadState.Downloading(0f))

        // 1. Verificar espacio disponible en disco (requiere 1.5 GB libres)
        val requiredSpace = 1.5 * 1024 * 1024 * 1024 // 1.5 GB
        val usableSpace = context.filesDir.usableSpace
        if (usableSpace < requiredSpace) {
            emit(DownloadState.Error(Exception("Espacio insuficiente en disco. Se requieren al menos 1.5 GB libres.")))
            return@flow
        }

        try {
            // Asegurarse de que el archivo temporal o antiguo no interfiera
            val tempFile = File(context.filesDir, "qwen_model.gguf.tmp")
            if (tempFile.exists()) {
                tempFile.delete()
            }

            val url = URL(modelUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            connection.connect()

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                emit(DownloadState.Error(Exception("Error del servidor: HTTP ${connection.responseCode}")))
                return@flow
            }

            val fileLength = connection.contentLengthLong
            var totalBytesRead = 0L

            connection.inputStream.use { input ->
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        // Soporte para cancelación cooperativa de la coroutina
                        if (!kotlinx.coroutines.currentCoroutineContext().isActive) {
                            tempFile.delete()
                            emit(DownloadState.Idle)
                            return@flow
                        }

                        output.write(buffer, 0, bytesRead)
                        totalBytesRead += bytesRead
                        if (fileLength > 0) {
                            emit(DownloadState.Downloading(totalBytesRead.toFloat() / fileLength.toFloat()))
                        }
                    }
                }
            }

            // Descarga completada: renombrar archivo temporal al real
            if (tempFile.renameTo(modelFile)) {
                emit(DownloadState.Success)
            } else {
                emit(DownloadState.Error(Exception("Error al guardar el archivo del modelo en disco.")))
            }
        } catch (e: Exception) {
            emit(DownloadState.Error(e))
        }
    }.flowOn(Dispatchers.IO)
}
