package com.ona.miciclo.settings.data

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.ona.miciclo.calendar.domain.repository.CycleRepository
import com.ona.miciclo.core.security.CryptoUtils
import com.ona.miciclo.data.local.dao.UserPreferencesDao
import com.ona.miciclo.settings.domain.repository.ExportImportRepository
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementación de export/import con encriptación AES-GCM.
 *
 * Formato de exportación:
 * JSON encriptado con contraseña maestra del usuario.
 * Incluye todos los registros de ciclo, logs diarios y preferencias.
 */
@Singleton
class ExportImportRepositoryImpl @Inject constructor(
    private val cycleRepository: CycleRepository,
    private val userPreferencesDao: UserPreferencesDao
) : ExportImportRepository {

    private val gson: Gson = GsonBuilder()
        .registerTypeAdapter(LocalDate::class.java, LocalDateAdapter())
        .setPrettyPrinting()
        .create()

    override suspend fun exportData(userId: String, masterPassword: String): Result<ByteArray> {
        return try {
            val cycleRecords = cycleRepository.getAllCycleRecordsSync(userId)
            val dailyLogs = cycleRepository.getAllDailyLogsSync(userId)
            val preferences = userPreferencesDao.getByUserId(userId)

            val exportData = ExportDataModel(
                version = 1,
                exportDate = LocalDate.now().toString(),
                cycleRecords = cycleRecords,
                dailyLogs = dailyLogs,
                preferences = preferences
            )

            val json = gson.toJson(exportData)

            // PUNTO CRÍTICO: Encriptar con contraseña maestra del usuario
            val encrypted = CryptoUtils.encryptJson(json, masterPassword)
            Result.success(encrypted)
        } catch (e: Exception) {
            Result.failure(Exception("Error al exportar datos: ${e.message}"))
        }
    }

    override suspend fun importData(
        encryptedData: ByteArray,
        masterPassword: String,
        userId: String
    ): Result<Unit> {
        return try {
            // Desencriptar con contraseña maestra
            val json = CryptoUtils.decryptJson(encryptedData, masterPassword)
            val importData = gson.fromJson(json, ExportDataModel::class.java)

            // Validar versión de datos
            if (importData.version > 1) {
                return Result.failure(Exception("Formato de datos más reciente que esta versión de la app"))
            }

            // Importar registros de ciclo
            importData.cycleRecords?.forEach { record ->
                cycleRepository.saveCycleRecord(record.copy(userId = userId))
            }

            // Importar logs diarios
            importData.dailyLogs?.forEach { log ->
                cycleRepository.saveDailyLog(log.copy(userId = userId))
            }

            // Importar preferencias
            importData.preferences?.let { prefs ->
                userPreferencesDao.insertOrUpdate(prefs.copy(userId = userId))
            }

            Result.success(Unit)
        } catch (e: javax.crypto.AEADBadTagException) {
            Result.failure(Exception("Contraseña incorrecta o datos corrompidos"))
        } catch (e: Exception) {
            Result.failure(Exception("Error al importar datos: ${e.message}"))
        }
    }

    override suspend fun deleteAllData(userId: String): Result<Unit> {
        return try {
            cycleRepository.deleteAllData(userId)
            userPreferencesDao.deleteByUserId(userId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception("Error al eliminar datos: ${e.message}"))
        }
    }
}

/**
 * Modelo de datos para exportación JSON.
 */
private data class ExportDataModel(
    val version: Int = 1,
    val exportDate: String,
    val cycleRecords: List<com.ona.miciclo.calendar.domain.model.CycleRecord>? = null,
    val dailyLogs: List<com.ona.miciclo.calendar.domain.model.DailyLog>? = null,
    val preferences: com.ona.miciclo.data.local.entity.UserPreferencesEntity? = null
)

/**
 * Gson adapter for LocalDate serialization.
 */
private class LocalDateAdapter : com.google.gson.TypeAdapter<LocalDate>() {
    override fun write(out: com.google.gson.stream.JsonWriter, value: LocalDate?) {
        if (value == null) out.nullValue()
        else out.value(value.toString())
    }

    override fun read(`in`: com.google.gson.stream.JsonReader): LocalDate? {
        return if (`in`.peek() == com.google.gson.stream.JsonToken.NULL) {
            `in`.nextNull()
            null
        } else {
            LocalDate.parse(`in`.nextString())
        }
    }
}
