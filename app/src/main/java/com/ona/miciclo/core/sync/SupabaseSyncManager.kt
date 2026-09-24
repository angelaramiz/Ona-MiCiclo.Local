package com.ona.miciclo.core.sync

import android.util.Base64
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.ona.miciclo.core.security.CryptoUtils
import com.ona.miciclo.core.security.KeystoreManager
import com.ona.miciclo.data.local.LocalDateAdapter
import com.ona.miciclo.data.local.OnaDatabase
import com.ona.miciclo.data.local.dao.CycleRecordDao
import com.ona.miciclo.data.local.dao.DailyLogDao
import com.ona.miciclo.data.local.dao.UserPreferencesDao
import com.ona.miciclo.data.local.entity.CycleRecordEntity
import com.ona.miciclo.data.local.entity.DailyLogEntity
import com.ona.miciclo.data.local.entity.UserPreferencesEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.util.UUID

/**
 * Gson del sync de pareja: registra LocalDateAdapter para que las fechas de
 * CycleRecordEntity/DailyLogEntity viajen como ISO string ("2026-09-15").
 * Sin el adapter, Gson serializa LocalDate por reflexión y el partner no puede
 * reconstruir los datos (ver SyncSerializationTest).
 */
internal fun createSyncGson(): Gson =
    GsonBuilder()
        .registerTypeAdapter(LocalDate::class.java, LocalDateAdapter())
        .create()

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
    private val gson = createSyncGson()
    private var partnerSyncJob: Job? = null
    private var hostessSyncJob: Job? = null

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

    data class PartnerSuggestionRow(
        val id: String? = null,
        val hostess_id: String,
        val partner_id: String,
        val suggestion_type: String,
        val suggested_date: String,
        val status: String,
        val created_at: Long = System.currentTimeMillis()
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

        // 5. Backfill de datos existentes: para perfiles que ya tenían datos locales
        // antes de activar el modo pareja, subirlos YA para que el partner los reciba
        // al vincularse (sin depender del loop de auto-sync de 15s ni de que la app
        // esté abierta después).
        runCatching { syncHostessDataToCloudOnce(hostessId) }
            .onFailure { it.printStackTrace() }

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

        keystoreManager.saveSyncPassphrase(dbPassphrase)

        // 2. Registrar relación en Supabase
        val partnerRow = UserRow(id = partnerId, role = "partner", linked_user_id = invitation.hostess_id)
        performRequest("POST", "users", gson.toJson(partnerRow))

        val hostessRow = UserRow(id = invitation.hostess_id, role = "hostess", linked_user_id = partnerId)
        performRequest("POST", "users", gson.toJson(hostessRow))

        // 3. Registrar localmente
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
            runCatching { syncHostessDataToCloudOnce(hostessId) }
                .onFailure { it.printStackTrace() }
        }
    }

    private suspend fun syncHostessDataToCloudOnce(hostessId: String) {
        val dbPassphrase = keystoreManager.getOrCreateDatabasePassphrase()
        val encryptionKey = Base64.encodeToString(dbPassphrase, Base64.NO_WRAP)

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

        val logs = dailyLogDao.getAllByUserSync(hostessId)
        for (log in logs) {
            val plainJson = gson.toJson(log)
            val encryptedBytes = CryptoUtils.encryptJson(plainJson, encryptionKey)
            val base64Payload = Base64.encodeToString(encryptedBytes, Base64.NO_WRAP)

            val row = EncryptedPayloadRow(
                id = log.fecha.toString(),
                user_id = hostessId,
                encrypted_payload = base64Payload
            )
            performRequest("POST", "daily_logs", gson.toJson(row))
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
                        // MERGE (upsert por PK) en vez de clearAndInsert: el dispositivo
                        // puede tener datos locales más ricos que el snapshot de la nube
                        // (p. ej. perfiles con datos previos al modo pareja). clearAndInsert
                        // los reemplazaría por el snapshot parcial y el siguiente upload
                        // subiría el set reducido (pérdida permanente).
                        cycleRecordDao.insertAll(cycleEntities)
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
                        dailyLogDao.insertAll(logEntities)
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
        // Exclusión mutua: solo un modo de sync a la vez. Al cambiar de cuenta
        // (hostess<->partner) el job anterior quedaba vivo y ambos loops (upload +
        // delete/insert cada 15s) corrían concurrentes sobre la misma DB cifrada.
        partnerSyncJob?.cancel()
        hostessSyncJob?.cancel()
        hostessSyncJob = null
        partnerSyncJob = scope.launch {
            while (true) {
                try {
                    downloadHostessDataOnce(hostessId)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                delay(15000)
            }
        }
    }

    /**
     * Descarga los ciclos y logs de la hostess, los desencripta con la passphrase
     * de sync y reemplaza la vista local (espejo de solo lectura). Lo usa tanto el
     * listener de 15s como `SyncWorker` (WorkManager, app cerrada).
     */
    private suspend fun downloadHostessDataOnce(hostessId: String) {
        val syncPassphrase = keystoreManager.getSyncPassphrase()
            ?: keystoreManager.getOrCreateDatabasePassphrase()
        val decryptionKey = Base64.encodeToString(syncPassphrase, Base64.NO_WRAP)

        val cyclesResponse = performRequest("GET", "cycles", queryParams = "user_id=eq.$hostessId")
        val cyclesRows = gson.fromJson(cyclesResponse, Array<EncryptedPayloadRow>::class.java)
        val cycleEntities = cyclesRows.mapNotNull { row ->
            try {
                val encryptedBytes = Base64.decode(row.encrypted_payload, Base64.NO_WRAP)
                val plainJson = CryptoUtils.decryptJson(encryptedBytes, decryptionKey)
                gson.fromJson(plainJson, CycleRecordEntity::class.java).copy(userId = hostessId)
            } catch (e: Exception) {
                null
            }
        }
        // Hay datos en la nube pero NINGUNO se puede descifrar: la passphrase del
        // código de invitación es obsoleta (la anfitriona regeneró su clave). En vez
        // de fallar en silencio, reportarlo para que sepa que debe regenerar el código.
        if (cyclesRows.isNotEmpty() && cycleEntities.isEmpty()) {
            throw Exception(
                "No se pudieron descifrar los datos de la anfitriona. " +
                    "Pídele que genere un nuevo código de invitación y vincúlate de nuevo."
            )
        }
        if (cycleEntities.isNotEmpty()) {
            // Remapear ids a 0 (autoGenerate) para que NO colisionen con los
            // registros propios del partner (que también empiezan en id=1). Sin
            // esto, insertAll(REPLACE) sobrescribiría los datos del partner.
            cycleRecordDao.clearAndInsertCycles(hostessId, cycleEntities.map { it.copy(id = 0) })
        }

        val logsResponse = performRequest("GET", "daily_logs", queryParams = "user_id=eq.$hostessId")
        val logsRows = gson.fromJson(logsResponse, Array<EncryptedPayloadRow>::class.java)
        val logEntities = logsRows.mapNotNull { row ->
            try {
                val encryptedBytes = Base64.decode(row.encrypted_payload, Base64.NO_WRAP)
                val plainJson = CryptoUtils.decryptJson(encryptedBytes, decryptionKey)
                gson.fromJson(plainJson, DailyLogEntity::class.java).copy(userId = hostessId)
            } catch (e: Exception) {
                null
            }
        }
        if (logEntities.isNotEmpty()) {
            dailyLogDao.clearAndInsertLogs(hostessId, logEntities.map { it.copy(id = 0) })
        }
    }

/**
 * Refresco inmediato del partner: descarga los datos de la hostess ahora.
 * Lanza excepción si hay datos en la nube pero ninguno se puede descifrar
 * (passphrase obsoleta) para que la UI lo muestre.
 */
suspend fun refreshPartnerData(hostessId: String) {
    downloadHostessDataOnce(hostessId)
}

/**
 * Un ciclo de sync completo para un usuario, usado por `SyncWorker` (WorkManager)
 * para mantener los datos sincronizados aunque la app esté cerrada.
 * - Partner: descarga los datos de la hostess (vista solo lectura).
 * - Hostess/solo: sube todos sus datos locales a la nube.
 */
suspend fun runSyncOnce(userId: String) {
        val prefs = userPreferencesDao.getByUserId(userId) ?: return
        val linkedId = prefs.linkedUserId
        if (prefs.userRole == "partner" && !linkedId.isNullOrEmpty()) {
            downloadHostessDataOnce(linkedId)
        } else {
            syncHostessDataToCloudOnce(userId)
        }
    }

    fun startHostessAutoSync(hostessId: String) {
        // Exclusión mutua: ver comentario en startPartnerSyncListener.
        hostessSyncJob?.cancel()
        partnerSyncJob?.cancel()
        partnerSyncJob = null
        hostessSyncJob = scope.launch {
            while (true) {
                try {
                    syncHostessDataToCloudOnce(hostessId)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                delay(15000)
            }
        }
    }

    fun stopAllSync() {
        partnerSyncJob?.cancel()
        partnerSyncJob = null
        hostessSyncJob?.cancel()
        hostessSyncJob = null
    }

    /**
     * Envía una sugerencia de inicio de periodo a la anfitriona.
     */
    suspend fun sendPartnerSuggestion(
        hostessId: String,
        partnerId: String,
        suggestedDate: LocalDate
    ): String = withContext(Dispatchers.IO) {
        val row = PartnerSuggestionRow(
            hostess_id = hostessId,
            partner_id = partnerId,
            suggestion_type = "START_PERIOD",
            suggested_date = suggestedDate.toString(),
            status = "PENDING"
        )
        performRequest("POST", "partner_suggestions", gson.toJson(row))
    }

    /**
     * Obtiene las sugerencias pendientes para la anfitriona.
     */
    suspend fun getPendingSuggestions(hostessId: String): List<PartnerSuggestionRow> = withContext(Dispatchers.IO) {
        val response = performRequest(
            method = "GET",
            table = "partner_suggestions",
            queryParams = "hostess_id=eq.$hostessId&status=eq.PENDING"
        )
        gson.fromJson(response, Array<PartnerSuggestionRow>::class.java).toList()
    }

    /**
     * Actualiza el estado de una sugerencia (ej. APPROVED o REJECTED).
     */
    suspend fun updateSuggestionStatus(suggestionId: String, status: String) = withContext(Dispatchers.IO) {
        val body = "{\"status\": \"$status\"}"
        performRequest(
            method = "PATCH",
            table = "partner_suggestions",
            body = body,
            queryParams = "id=eq.$suggestionId"
        )
    }
}
