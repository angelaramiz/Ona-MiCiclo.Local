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
    val modelDir: File
        get() = File(context.filesDir, "qwen_ocr_model")

    private val modelFiles = listOf(
        "config.json",
        "llm.mnn",
        "llm.mnn.weight",
        "embeddings_bf16.bin",
        "visual.mnn",
        "visual.mnn.weight",
        "tokenizer.txt"
    )

    private val baseUrl = "https://modelscope.cn/api/v1/models/MNN/Qwen2-VL-2B-Instruct-MNN/repo?FilePath="

    fun isModelDownloaded(): Boolean {
        return MnnLlmBridge.validateModelFiles(modelDir) == null
    }

    fun deleteModel(): Boolean {
        if (modelDir.exists()) {
            return modelDir.deleteRecursively()
        }
        return false
    }

    fun downloadModel(): Flow<DownloadState> = flow {
        emit(DownloadState.Downloading(0f))

        // Verificar espacio libre
        val requiredSpace = 1.5 * 1024 * 1024 * 1024 // 1.5 GB
        val usableSpace = context.filesDir.usableSpace
        if (usableSpace < requiredSpace) {
            emit(DownloadState.Error(Exception("Espacio insuficiente. Se requieren al menos 1.5 GB libres.")))
            return@flow
        }

        if (!modelDir.exists()) {
            modelDir.mkdirs()
        }

        try {
            for (i in modelFiles.indices) {
                val fileName = modelFiles[i]
                val targetFile = File(modelDir, fileName)
                
                // Si el archivo ya existe y tiene contenido, omitir descarga
                if (targetFile.exists() && targetFile.length() > 0) {
                    emit(DownloadState.Downloading((i + 1) / modelFiles.size.toFloat()))
                    continue
                }

                val tempFile = File(modelDir, "$fileName.tmp")
                if (tempFile.exists()) {
                    tempFile.delete()
                }

                val url = URL(baseUrl + fileName)
                val connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 15000
                connection.readTimeout = 15000
                connection.connect()

                if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                    emit(DownloadState.Error(Exception("Error al descargar $fileName: HTTP ${connection.responseCode}")))
                    return@flow
                }

                val fileLength = connection.contentLengthLong
                var totalBytesRead = 0L

                connection.inputStream.use { input ->
                    FileOutputStream(tempFile).use { output ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            if (!kotlinx.coroutines.currentCoroutineContext().isActive) {
                                tempFile.delete()
                                emit(DownloadState.Idle)
                                return@flow
                            }
                            output.write(buffer, 0, bytesRead)
                            totalBytesRead += bytesRead
                            
                            if (fileLength > 0) {
                                val fileProgress = totalBytesRead.toFloat() / fileLength.toFloat()
                                val totalProgress = (i + fileProgress) / modelFiles.size.toFloat()
                                emit(DownloadState.Downloading(totalProgress))
                            }
                        }
                    }
                }

                if (!tempFile.renameTo(targetFile)) {
                    emit(DownloadState.Error(Exception("Error al renombrar archivo temporal: $fileName")))
                    return@flow
                }
            }
            emit(DownloadState.Success)
        } catch (e: Exception) {
            emit(DownloadState.Error(e))
        }
    }.flowOn(Dispatchers.IO)
}

