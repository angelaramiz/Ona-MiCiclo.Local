package com.ona.miciclo.core.sync

import android.util.Base64
import com.google.gson.Gson
import com.ona.miciclo.core.security.CryptoUtils
import com.ona.miciclo.core.security.KeystoreManager
import com.ona.miciclo.data.local.OnaDatabase
import com.ona.miciclo.data.local.dao.CycleRecordDao
import com.ona.miciclo.data.local.dao.DailyLogDao
import com.ona.miciclo.data.local.dao.UserPreferencesDao
import com.ona.miciclo.data.local.entity.CycleRecordEntity
import com.ona.miciclo.data.local.entity.DailyLogEntity
import com.ona.miciclo.data.local.entity.UserPreferencesEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.util.UUID

/**
 * Gestor de sincronización Zero-Knowledge utilizando Supabase REST API.
 * Toda la información sensible es cifrada con AES-256-GCM localmente en el dispositivo
 * antes de subirse a la nube.
 */
class SupabaseSyncManager(
    private val keystoreManager: KeystoreManager,
    private val database: OnaDatabase,
    private val cycleRecordDao: CycleRecordDao,
    private val dailyLogDao: DailyLogDao,
    private val userPreferencesDao: UserPreferencesDao
) {
    private val scope = CoroutineScope(Dispatchers.IO)
    private val gson = Gson()

    // Credenciales de Supabase
    private val supabaseUrl = "https://cjwozffwcqqiwsmjgjjo.supabase.co"
    private val supabaseKey = "sb_publishable_m6WEXMesgxH48iBX9M3v1w_xehXf1z9"

    // Modelos para la REST API de Supabase
    data class InvitationRow(
        val code: String,
        val hostess_id: String,
        val encrypted_passphrase: String,
        val salt: String,
        val created_at: Long = System.currentTimeMillis()
    )

    data class UserRow(
        val id: String,
        val role: String,
        val linked_user_id: String?,
        val updated_at: Long = System.currentTimeMillis()
    )

    data class EncryptedPayloadRow(
        val id: String,
        val user_id: String,
        val encrypted_payload: String,
        val updated_at: Long = System.currentTimeMillis()
    )

    /**
     * Helper para hacer solicitudes HTTP a Supabase.
     */
    private suspend fun performRequest(
        method: String,
        table: String,
        body: String? = null,
        queryParams: String? = null
    ): String = withContext(Dispatchers.IO) {
        val urlStr = "$supabaseUrl/rest/v1/$table" + (if (queryParams != null) "?$queryParams" else "")
        val url = URL(urlStr)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.connectTimeout = 15000
        conn.readTimeout = 15000
        conn.setRequestProperty("apikey", supabaseKey)
        conn.setRequestProperty("Authorization", "Bearer $supabaseKey")
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("Prefer", "resolution=merge-duplicates")

        if (body != null) {
            conn.doOutput = true
            OutputStreamWriter(conn.outputStream).use { it.write(body) }
        }

        val code = conn.responseCode
        if (code in 200..299) {
            conn.inputStream.bufferedReader().use { it.readText() }
        } else {
            val errorMsg = conn.errorStream?.bufferedReader()?.use { it.readText() }
            throw Exception("HTTP $code: $errorMsg")
        }
    }

    /**
     * Genera un código de invitación y sube la DB Passphrase encriptada a Supabase.
     */
    suspend fun generateInvitationCode(hostessId: String): String = withContext(Dispatchers.IO) {
        val code = UUID.randomUUID().toString().substring(0, 6).uppercase()
        
        // 1. Obtener DB Passphrase
        val dbPassphrase = keystoreManager.getOrCreateDatabasePassphrase()
        val dbPassphraseHex = Base64.encodeToString(dbPassphrase, Base64.NO_WRAP)

        // 2. Cifrar la DB Passphrase usando el código de invitación como contraseña
        // CryptoUtils genera un Salt aleatorio de 16 bytes y un IV de 12 bytes internamente
        val encryptedBytes = CryptoUtils.encryptJson(dbPassphraseHex, code)
        
        // Formato devuelto: [SALT (16 bytes)] [IV (12 bytes)] [CIPHERTEXT]
        val salt = Base64.encodeToString(encryptedBytes.copyOfRange(0, 16), Base64.NO_WRAP)
        val encryptedPayload = Base64.encodeToString(encryptedBytes, Base64.NO_WRAP)

        // 3. Subir invitación a Supabase
        val row = InvitationRow(
            code = code,
            hostess_id = hostessId,
            encrypted_passphrase = encryptedPayload,
            salt = salt
        )
        performRequest("POST", "invitations", gson.toJson(row))

        // 4. Guardar rol hostess localmente
        val currentPrefs = userPreferencesDao.getByUserId(hostessId)
        if (currentPrefs != null) {
            userPreferencesDao.insertOrUpdate(currentPrefs.copy(userRole = "hostess"))
        } else {
            userPreferencesDao.insertOrUpdate(UserPreferencesEntity(userId = hostessId, userRole = "hostess"))
        }

        code
    }

    /**
     * Descarga la clave síncrona cifrada, la descifra y vincula la cuenta del partner.
     */
    suspend fun linkPartnerWithCode(partnerId: String, code: String): String = withContext(Dispatchers.IO) {
        val response = performRequest("GET", "invitations", queryParams = "code=eq.${code.uppercase()}")
        val invitations = gson.fromJson(response, Array<InvitationRow>::class.java)
        if (invitations.isEmpty()) {
            throw Exception("Código de invitación inválido o expirado.")
        }
        val invitation = invitations[0]

        // 1. Desencriptar DB Passphrase usando el código de invitación
        val encryptedBytes = Base64.decode(invitation.encrypted_passphrase, Base64.NO_WRAP)
        val decryptedPassphraseHex = CryptoUtils.decryptJson(encryptedBytes, code.uppercase())
        val dbPassphrase = Base64.decode(decryptedPassphraseHex, Base64.NO_WRAP)

        // 2. Guardar la passphrase de la anfitriona en el Keystore del Partner
        // A partir de ahora, la BD local del Partner estará cifrada con la misma clave.
        keystoreManager.saveDatabasePassphrase(dbPassphrase)

        // 3. Registrar relación en Supabase
        val partnerRow = UserRow(id = partnerId, role = "partner", linked_user_id = invitation.hostess_id)
        performRequest("POST", "users", gson.toJson(partnerRow))

        val hostessRow = UserRow(id = invitation.hostess_id, role = "hostess", linked_user_id = partnerId)
        performRequest("POST", "users", gson.toJson(hostessRow))

        // 4. Registrar localmente
        val currentPrefs = userPreferencesDao.getByUserId(partnerId)
        if (currentPrefs != null) {
            userPreferencesDao.insertOrUpdate(currentPrefs.copy(userRole = "partner", linkedUserId = invitation.hostess_id))
        } else {
            userPreferencesDao.insertOrUpdate(UserPreferencesEntity(userId = partnerId, userRole = "partner", linkedUserId = invitation.hostess_id))
        }

        invitation.hostess_id
    }

    /**
     * Encripta los ciclos y logs diarios y los sube a Supabase.
     */
    fun syncHostessDataToCloud(hostessId: String) {
        scope.launch {
            try {
                val dbPassphrase = keystoreManager.getOrCreateDatabasePassphrase()
                val encryptionKey = Base64.encodeToString(dbPassphrase, Base64.NO_WRAP)

                // 1. Sincronizar Ciclos encriptados
                val cycles = cycleRecordDao.getAllByUserSync(hostessId)
                for (cycle in cycles) {
                    val plainJson = gson.toJson(cycle)
                    val encryptedBytes = CryptoUtils.encryptJson(plainJson, encryptionKey)
                    val base64Payload = Base64.encodeToString(encryptedBytes, Base64.NO_WRAP)

                    val row = EncryptedPayloadRow(
                        id = cycle.id.toString(),
                        user_id = hostessId,
                        encrypted_payload = base64Payload
                    )
                    performRequest("POST", "cycles", gson.toJson(row))
                }

                // 2. Sincronizar Logs Diarios encriptados
                val logs = dailyLogDao.getAllByUserSync(hostessId)
                for (log in logs) {
                    val plainJson = gson.toJson(log)
                    val encryptedBytes = CryptoUtils.encryptJson(plainJson, encryptionKey)
                    val base64Payload = Base64.encodeToString(encryptedBytes, Base64.NO_WRAP)

                    val row = EncryptedPayloadRow(
                        id = log.fecha.toString(), // Usamos la fecha como ID de fila única
                        user_id = hostessId,
                        encrypted_payload = base64Payload
                    )
                    performRequest("POST", "daily_logs", gson.toJson(row))
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Descarga y desencripta los ciclos y logs diarios del propio usuario (hostess) si existen en la nube.
     */
    fun downloadUserDataFromCloud(userId: String) {
        scope.launch {
            try {
                val dbPassphrase = keystoreManager.getOrCreateDatabasePassphrase()
                val decryptionKey = Base64.encodeToString(dbPassphrase, Base64.NO_WRAP)

                // 1. Descargar y desencriptar Ciclos
                val cyclesResponse = performRequest("GET", "cycles", queryParams = "user_id=eq.$userId")
                val cyclesRows = gson.fromJson(cyclesResponse, Array<EncryptedPayloadRow>::class.java)
                if (cyclesRows.isNotEmpty()) {
                    val cycleEntities = cyclesRows.mapNotNull { row ->
                        try {
                            val encryptedBytes = Base64.decode(row.encrypted_payload, Base64.NO_WRAP)
                            val plainJson = CryptoUtils.decryptJson(encryptedBytes, decryptionKey)
                            gson.fromJson(plainJson, CycleRecordEntity::class.java).copy(userId = userId)
                        } catch (e: Exception) {
                            null // Si no se puede desencriptar (ej. diferente clave), ignorar
                        }
                    }
                    if (cycleEntities.isNotEmpty()) {
                        cycleRecordDao.clearAndInsertCycles(userId, cycleEntities)
                    }
                }

                // 2. Descargar y desencriptar Logs Diarios
                val logsResponse = performRequest("GET", "daily_logs", queryParams = "user_id=eq.$userId")
                val logsRows = gson.fromJson(logsResponse, Array<EncryptedPayloadRow>::class.java)
                if (logsRows.isNotEmpty()) {
                    val logEntities = logsRows.mapNotNull { row ->
                        try {
                            val encryptedBytes = Base64.decode(row.encrypted_payload, Base64.NO_WRAP)
                            val plainJson = CryptoUtils.decryptJson(encryptedBytes, decryptionKey)
                            gson.fromJson(plainJson, DailyLogEntity::class.java).copy(userId = userId)
                        } catch (e: Exception) {
                            null
                        }
                    }
                    if (logEntities.isNotEmpty()) {
                        dailyLogDao.clearAndInsertLogs(userId, logEntities)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Descarga periódica y desencriptación para la base de datos de la pareja.
     */
    fun startPartnerSyncListener(partnerId: String, hostessId: String) {
        scope.launch {
            try {
                val dbPassphrase = keystoreManager.getOrCreateDatabasePassphrase()
                val decryptionKey = Base64.encodeToString(dbPassphrase, Base64.NO_WRAP)

                // 1. Descargar y desencriptar Ciclos
                val cyclesResponse = performRequest("GET", "cycles", queryParams = "user_id=eq.$hostessId")
                val cyclesRows = gson.fromJson(cyclesResponse, Array<EncryptedPayloadRow>::class.java)
                val cycleEntities = cyclesRows.map { row ->
                    val encryptedBytes = Base64.decode(row.encrypted_payload, Base64.NO_WRAP)
                    val plainJson = CryptoUtils.decryptJson(encryptedBytes, decryptionKey)
                    gson.fromJson(plainJson, CycleRecordEntity::class.java).copy(userId = hostessId)
                }
                cycleRecordDao.clearAndInsertCycles(hostessId, cycleEntities)

                // 2. Descargar y desencriptar Logs Diarios
                val logsResponse = performRequest("GET", "daily_logs", queryParams = "user_id=eq.$hostessId")
                val logsRows = gson.fromJson(logsResponse, Array<EncryptedPayloadRow>::class.java)
                val logEntities = logsRows.map { row ->
                    val encryptedBytes = Base64.decode(row.encrypted_payload, Base64.NO_WRAP)
                    val plainJson = CryptoUtils.decryptJson(encryptedBytes, decryptionKey)
                    gson.fromJson(plainJson, DailyLogEntity::class.java).copy(userId = hostessId)
                }
                dailyLogDao.clearAndInsertLogs(hostessId, logEntities)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
