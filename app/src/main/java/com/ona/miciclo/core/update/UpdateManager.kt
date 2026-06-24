package com.ona.miciclo.core.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Gestor de actualizaciones automáticas para la aplicación.
 * Permite comprobar la versión remota, descargar y desencadenar la instalación local.
 */
class UpdateManager(private val context: Context) {

    private val gson = Gson()
    private val updateUrl = "https://raw.githubusercontent.com/angelaramiz/Ona-MiCiclo.Local/main/release/update.json"

    data class UpdateInfo(
        val versionCode: Int,
        val versionName: String,
        val downloadUrl: String
    )

    sealed interface UpdateResult {
        data class UpdateAvailable(val info: UpdateInfo) : UpdateResult
        object UpToDate : UpdateResult
        data class Error(val error: Throwable) : UpdateResult
    }

    sealed interface DownloadResult {
        object Success : DownloadResult
        data class Error(val error: Throwable) : DownloadResult
    }

    /**
     * Comprueba si hay una nueva versión disponible en el repositorio de GitHub.
     */
    suspend fun checkForUpdates(): UpdateResult = withContext(Dispatchers.IO) {
        try {
            val url = URL(updateUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            connection.requestMethod = "GET"

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val jsonString = connection.inputStream.bufferedReader().use { it.readText() }
                val updateInfo = gson.fromJson(jsonString, UpdateInfo::class.java)

                val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
                } else {
                    @Suppress("DEPRECATION")
                    context.packageManager.getPackageInfo(context.packageName, 0)
                }

                val currentVersionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    packageInfo.longVersionCode.toInt()
                } else {
                    @Suppress("DEPRECATION")
                    packageInfo.versionCode
                }

                if (updateInfo.versionCode > currentVersionCode) {
                    UpdateResult.UpdateAvailable(updateInfo)
                } else {
                    UpdateResult.UpToDate
                }
            } else {
                UpdateResult.Error(Exception("Error de conexión: HTTP ${connection.responseCode}"))
            }
        } catch (e: Exception) {
            UpdateResult.Error(e)
        }
    }

    /**
     * Descarga e instala la última APK disponible.
     * Limpia descargas anteriores para evitar consumo de almacenamiento.
     */
    suspend fun downloadAndInstallUpdate(
        downloadUrl: String,
        onProgress: (Float) -> Unit
    ): DownloadResult = withContext(Dispatchers.IO) {
        try {
            // 1. Limpieza de descargas previas antes de descargar la nueva
            val updatesDir = File(context.cacheDir, "updates")
            if (updatesDir.exists()) {
                updatesDir.deleteRecursively()
            }
            updatesDir.mkdirs()

            val tempApkFile = File(updatesDir, "ona-miciclo-update.apk")

            // 2. Descargar la APK nueva
            val url = URL(downloadUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            connection.connect()

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                return@withContext DownloadResult.Error(Exception("Error al descargar APK: HTTP ${connection.responseCode}"))
            }

            val fileLength = connection.contentLength
            connection.inputStream.use { input ->
                FileOutputStream(tempApkFile).use { output ->
                    val data = ByteArray(4096)
                    var total: Long = 0
                    var count: Int
                    while (input.read(data).also { count = it } != -1) {
                        total += count
                        if (fileLength > 0) {
                            onProgress(total.toFloat() / fileLength.toFloat())
                        }
                        output.write(data, 0, count)
                    }
                }
            }

            // 3. Ejecutar instalación automática mediante Intent
            installApk(tempApkFile)
            DownloadResult.Success
        } catch (e: Exception) {
            DownloadResult.Error(e)
        }
    }

    private fun installApk(file: File) {
        val apkUri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        context.startActivity(intent)
    }
}
