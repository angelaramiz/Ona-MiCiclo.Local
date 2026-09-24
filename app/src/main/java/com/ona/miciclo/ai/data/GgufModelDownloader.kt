package com.ona.miciclo.ai.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Descargador del modelo GGUF de texto (Qwen3-4B-Q4_K_M, ~2.5 GB, un solo archivo).
 * Motor de inferencia: llama.cpp (ver LlamaCppInferenceEngine).
 */
@Singleton
class GgufModelDownloader @Inject constructor(
    @ApplicationContext private val context: Context
) {
    val modelDir: File
        get() = File(context.filesDir, GgufModelSpec.MODEL_DIR_NAME)

    val modelFile: File
        get() = File(modelDir, GgufModelSpec.FILE_NAME)

    fun isModelDownloaded(): Boolean {
        val f = modelFile
        return f.exists() && f.length() >= GgufModelSpec.MIN_SIZE_BYTES
    }

    fun deleteModel(): Boolean {
        return if (modelDir.exists()) modelDir.deleteRecursively() else false
    }

    fun downloadModel(
        url: String = GgufModelSpec.downloadUrl(),
        minSizeBytes: Long = GgufModelSpec.MIN_SIZE_BYTES,
        requiredSpaceBytes: Long = GgufModelSpec.REQUIRED_SPACE_BYTES,
        usableSpace: Long = context.filesDir.usableSpace
    ): Flow<DownloadState> = flow {
        emit(DownloadState.Downloading(0f))

        if (usableSpace < requiredSpaceBytes) {
            emit(DownloadState.Error(Exception("Espacio insuficiente. Se requieren al menos 3.5 GB libres.")))
            return@flow
        }

        if (!modelDir.exists()) {
            modelDir.mkdirs()
        }

        // Si ya está completo, omitir descarga.
        if (isModelDownloaded()) {
            emit(DownloadState.Downloading(1f))
            emit(DownloadState.Success)
            return@flow
        }

        val targetFile = modelFile
        if (targetFile.exists()) {
            targetFile.delete()
        }
        val tempFile = File(modelDir, "${GgufModelSpec.FILE_NAME}.tmp")
        if (tempFile.exists()) {
            tempFile.delete()
        }

        try {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.connectTimeout = 60000
            connection.readTimeout = 600000 // 10 minutos para descarga pesada
            // HuggingFace redirige a CDN; seguir redirects está activado por defecto.
            connection.instanceFollowRedirects = true
            // FIX "unexpected end of stream": sin Connection: close, HttpURLConnection
            // reutiliza la conexión keep-alive tras el redirect al CDN de HF y el
            // stream se corta a mitad de archivo. Forzar close evita el reuso corrupto.
            connection.setRequestProperty("Connection", "close")
            connection.setRequestProperty("User-Agent", "Ona-MiCiclo/1.0")
            connection.setRequestProperty("Accept", "*/*")
            connection.connect()

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                emit(DownloadState.Error(Exception("Error al descargar ${GgufModelSpec.FILE_NAME}: HTTP ${connection.responseCode}")))
                return@flow
            }

            val fileLength = connection.contentLengthLong
            var totalBytesRead = 0L
            var cancelled = false

            connection.inputStream.use { input ->
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        if (!kotlinx.coroutines.currentCoroutineContext().isActive) {
                            cancelled = true
                            break
                        }
                        output.write(buffer, 0, bytesRead)
                        totalBytesRead += bytesRead
                        if (fileLength > 0) {
                            emit(DownloadState.Downloading(totalBytesRead.toFloat() / fileLength.toFloat()))
                        }
                    }
                }
            }

            if (cancelled) {
                // Los streams ya se cerraron (use): en Windows no se puede borrar
                // un archivo abierto, así que la limpieza va DESPUÉS del cierre.
                tempFile.delete()
                emit(DownloadState.Idle)
                return@flow
            }

            if (tempFile.length() < minSizeBytes) {
                tempFile.delete()
                emit(DownloadState.Error(Exception("Descarga incompleta de ${GgufModelSpec.FILE_NAME}.")))
                return@flow
            }

            if (!tempFile.renameTo(targetFile)) {
                emit(DownloadState.Error(Exception("Error al renombrar archivo temporal del modelo.")))
                return@flow
            }
            emit(DownloadState.Success)
        } catch (e: Exception) {
            emit(DownloadState.Error(e))
        }
    }.flowOn(Dispatchers.IO)
}
