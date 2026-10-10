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
import com.ona.miciclo.calendar.domain.model.CoupleNote
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

    companion object {
        /**
         * ID de fila en la nube: namespaced por usuario para que dos installs
         * frescos (mismos IDs locales 1, 2, ...) no colisionen en la PK (409
         * silencioso que impedía subir datos nuevos). La descarga ignora este
         * id (remap a 0), así que es seguro cambiar el formato.
         */
        fun cloudRowId(userId: String, localId: String) = "${userId}_$localId"

        /**
         * IDs por CLAVE NATURAL (bidireccional): la misma fila lógica siempre
         * produce la misma PK de nube, sin importar el id local del dispositivo
         * que la suba. Sin esto, el partner re-subía su espejo con SUS ids
         * locales → PKs distintas para el mismo periodo/log → duplicados que
         * ningún download colapsaba. Con clave natural, re-subir = upsert
         * idempotente (la nube usa merge-duplicates).
         */
        fun cloudCycleId(userId: String, start: java.time.LocalDate?) =
            if (start == null) cloudRowId(userId, "null") else "${userId}_cycle_${start}"

        fun cloudLogId(userId: String, fecha: java.time.LocalDate) =
            "${userId}_log_${fecha}"

        /**
         * Solo se descargan filas con id natural. Las legacy (`uid_N`,
         * `uid_fecha`) se ignoran: tras el primer upload con id natural su
         * contenido ya vive en las filas nuevas (misma fila lógica).
         */
        fun isNaturalCloudId(id: String) =
            id.contains("_cycle_") || id.contains("_log_")

        fun selectRowsForDownload(rows: List<EncryptedPayloadRow>) =
            rows.filter { isNaturalCloudId(it.id) }
    }

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

        // 1b. Fijarla como clave COMPARTIDA del vínculo: el partner la recibe
        // al vincularse y ambos cifran las notitas con ella (ver
        // noteEncryptionKey). Sin esto, cada lado usaría su clave local y el
        // otro no podría leer en teléfonos distintos.
        keystoreManager.saveSyncPassphrase(dbPassphrase)

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
            // Los ciclos sin fecha no tienen clave natural: se omiten (el repo
            // los purga como corruptos; sin esto reaparecerían como duplicados).
            val start = cycle.fechaInicioMenstruacion ?: continue
            val plainJson = gson.toJson(cycle)
            val encryptedBytes = CryptoUtils.encryptJson(plainJson, encryptionKey)
            val base64Payload = Base64.encodeToString(encryptedBytes, Base64.NO_WRAP)

            val row = EncryptedPayloadRow(
                id = cloudCycleId(hostessId, start),
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
                id = cloudLogId(hostessId, log.fecha),
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
     * Claves de descifrado: la de la DB (la que SIEMPRE usa el upload) más la
     * de sync (código de invitación) si difiere. Extraído para reutilizar en
     * notitas sin duplicar la lógica.
     */
    private fun syncKeys(): List<String> {
        val dbPassphrase = keystoreManager.getOrCreateDatabasePassphrase()
        val syncPassphrase = keystoreManager.getSyncPassphrase()
        return listOfNotNull(
            Base64.encodeToString(dbPassphrase, Base64.NO_WRAP),
            syncPassphrase
                ?.takeIf { !it.contentEquals(dbPassphrase) }
                ?.let { Base64.encodeToString(it, Base64.NO_WRAP) }
        )
    }

    /** Descifra probando cada clave; null si ninguna sirve. */
    private fun decryptWithAnyKey(payloadB64: String, keys: List<String>): String? {
        val encryptedBytes = try {
            Base64.decode(payloadB64, Base64.NO_WRAP)
        } catch (e: Exception) {
            return null
        }
        for (key in keys) {
            try {
                return CryptoUtils.decryptJson(encryptedBytes, key)
            } catch (e: Exception) {
                // Probar con la siguiente clave.
            }
        }
        return null
    }

    /**
     * Descarga y desencripta las filas del usuario en la nube (solo formato
     * con id natural; las legacy se ignoran). Núcleo compartido del espejo
     * del partner y del merge de la hostess.
     *
     * Claves: se intenta con la passphrase de la DB (la que SIEMPRE usa el
     * upload) y además con la de sync (la del código de invitación). La de
     * sync puede ser obsoleta o pertenecer a otra cuenta en este mismo
     * dispositivo (los QA comparten emulador); usarla en exclusiva dejaba a
     * la hostess sin poder descifrar ni sus propias filas.
     *
     * Lanza excepción solo si hay filas con formato natural pero NINGUNA se
     * puede descifrar con ninguna clave (passphrase obsoleta) para que la UI
     * lo muestre. Las legacy filtradas no cuentan: su contenido ya vive en
     * las filas nuevas tras el primer upload.
     */
    private suspend fun fetchDecryptCloudRows(
        userId: String
    ): Pair<List<CycleRecordEntity>, List<DailyLogEntity>> {
        val keys = syncKeys()

        val cyclesResponse = performRequest("GET", "cycles", queryParams = "user_id=eq.$userId")
        val cyclesRows = gson.fromJson(cyclesResponse, Array<EncryptedPayloadRow>::class.java)
        val naturalCycles = selectRowsForDownload(cyclesRows.toList())
        val cycleEntities = naturalCycles.mapNotNull { row ->
            try {
                val plainJson = decryptWithAnyKey(row.encrypted_payload, keys) ?: return@mapNotNull null
                gson.fromJson(plainJson, CycleRecordEntity::class.java).copy(userId = userId)
            } catch (e: Exception) {
                null
            }
        }
        // Hay datos en la nube pero NINGUNO se puede descifrar: la passphrase del
        // código de invitación es obsoleta (la anfitriona regeneró su clave). En vez
        // de fallar en silencio, reportarlo para que sepa que debe regenerar el código.
        if (naturalCycles.isNotEmpty() && cycleEntities.isEmpty()) {
            throw Exception(
                "No se pudieron descifrar los datos de la anfitriona. " +
                    "Pídele que genere un nuevo código de invitación y vincúlate de nuevo."
            )
        }

        val logsResponse = performRequest("GET", "daily_logs", queryParams = "user_id=eq.$userId")
        val logsRows = gson.fromJson(logsResponse, Array<EncryptedPayloadRow>::class.java)
        val logEntities = selectRowsForDownload(logsRows.toList()).mapNotNull { row ->
            try {
                val plainJson = decryptWithAnyKey(row.encrypted_payload, keys) ?: return@mapNotNull null
                gson.fromJson(plainJson, DailyLogEntity::class.java).copy(userId = userId)
            } catch (e: Exception) {
                null
            }
        }
        return cycleEntities to logEntities
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
                    // Bidireccional: también SUBE lo que el partner aprobó/registró
                    // (antes solo descargaba; si la subida al escribir fallaba en
                    // silencio, el cambio quedaba varado para siempre).
                    syncNow(partnerId)
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
        val (cycleEntities, logEntities) = fetchDecryptCloudRows(hostessId)
        if (cycleEntities.isNotEmpty()) {
            // Remapear ids a 0 (autoGenerate) para que NO colisionen con los
            // registros propios del partner (que también empiezan en id=1). Sin
            // esto, insertAll(REPLACE) sobrescribiría los datos del partner.
            cycleRecordDao.clearAndInsertCycles(hostessId, cycleEntities.map { it.copy(id = 0) })
        }
        if (logEntities.isNotEmpty()) {
            dailyLogDao.clearAndInsertLogs(hostessId, logEntities.map { it.copy(id = 0) })
        }
    }

/**
 * Lee el rol y vínculo de la nube (tabla `users`) para restaurarlos en un
 * dispositivo nuevo. El rol/vínculo solo se guardaba localmente, así que al
 * cambiar de móvil la app trataba a un partner como hostess.
 * Retorna (role, linkedUserId) o null si no hay fila / no hay red.
 */
suspend fun fetchCloudRole(userId: String): Pair<String, String?>? = withContext(Dispatchers.IO) {
    try {
        val response = performRequest("GET", "users", queryParams = "id=eq.$userId")
        val rows = gson.fromJson(response, Array<UserRow>::class.java)
        val row = rows.firstOrNull() ?: return@withContext null
        row.role to row.linked_user_id
    } catch (e: Exception) {
        null
    }
}

/**
 * Fusiona en la DB de la hostess lo que el partner aprobó/registró.
 * El partner escribe con el userId de la hostess (su vista es un espejo con
 * ese namespace) y lo sube a la nube; aquí se integra sin duplicar: id=0 +
 * INSERT OR REPLACE colapsa por el índice UNIQUE (user_id, fecha). A
 * diferencia del espejo del partner NO se borra nada local (la hostess puede
 * tener filas más nuevas aún no subidas; el orden upload-primero garantiza
 * que la nube ya las contiene antes de fusionar).
 */
private suspend fun mergeHostessDataOnce(hostessId: String) {
    val (cycleEntities, logEntities) = fetchDecryptCloudRows(hostessId)
    if (cycleEntities.isNotEmpty()) {
        cycleRecordDao.insertAll(
            cycleEntities
                .filter { it.fechaInicioMenstruacion != null }
                .map { it.copy(id = 0, userId = hostessId) }
        )
    }
    if (logEntities.isNotEmpty()) {
        dailyLogDao.insertAll(logEntities.map { it.copy(id = 0, userId = hostessId) })
    }
}

/**
 * Sync bidireccional completo y ESPERADO (suspend): primero SUBE lo local y
 * después DESCARGA/fusiona según rol. Lo usan el gesto pull-to-refresh, el
 * auto-refresh, los loops de 15s y el worker en segundo plano.
 * - Partner con vínculo: sube sus aprobaciones + descarga el espejo.
 * - Hostess con vínculo: sube lo suyo + fusiona lo aprobado por el partner.
 * - Solo / sin vínculo: solo sube (comportamiento histórico).
 * Si la subida falla (sin red), se aborta antes de descargar para no pisar
 * nada local con datos viejos; el error se propaga para mostrarlo en UI.
 */
suspend fun syncNow(userId: String) {
    if (userId.isEmpty()) return
    syncHostessDataToCloudOnce(userId)
    val prefs = userPreferencesDao.getByUserId(userId)
    val linkedId = prefs?.linkedUserId
    if (prefs?.userRole == "partner" && !linkedId.isNullOrEmpty()) {
        downloadHostessDataOnce(linkedId)
    } else if (!linkedId.isNullOrEmpty()) {
        mergeHostessDataOnce(userId)
    }
}

/**
 * Un ciclo de sync completo para un usuario, usado por `SyncWorker` (WorkManager)
 * para mantener los datos sincronizados aunque la app esté cerrada.
 * Bidireccional en ambos roles (ver `syncNow`).
 */
suspend fun runSyncOnce(userId: String) {
    syncNow(userId)
}

    fun startHostessAutoSync(hostessId: String) {
        // Exclusión mutua: ver comentario en startPartnerSyncListener.
        hostessSyncJob?.cancel()
        partnerSyncJob?.cancel()
        partnerSyncJob = null
        hostessSyncJob = scope.launch {
            while (true) {
                try {
                    // Bidireccional: además de subir, fusiona lo que el partner
                    // aprobó (periodos/síntomas) para verlo sin gesto manual.
                    syncNow(hostessId)
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
     * Envía una sugerencia del partner a la anfitriona.
     * Tipos: START_PERIOD (inicio de periodo), OVULATION_DAY (día de ovulación).
     */
    suspend fun sendPartnerSuggestion(
        hostessId: String,
        partnerId: String,
        suggestedDate: LocalDate,
        suggestionType: String = "START_PERIOD"
    ): String = withContext(Dispatchers.IO) {
        val row = PartnerSuggestionRow(
            hostess_id = hostessId,
            partner_id = partnerId,
            suggestion_type = suggestionType,
            suggested_date = suggestedDate.toString(),
            status = "PENDING"
        )
        performRequest("POST", "partner_suggestions", gson.toJson(row))
    }

    /**
     * Última sugerencia enviada por el partner (para mostrarle su estado:
     * pendiente, aprobada o rechazada).
     */
    suspend fun getLatestPartnerSuggestion(partnerId: String): PartnerSuggestionRow? = withContext(Dispatchers.IO) {
        val response = performRequest(
            method = "GET",
            table = "partner_suggestions",
            queryParams = "partner_id=eq.$partnerId&order=created_at.desc&limit=1"
        )
        gson.fromJson(response, Array<PartnerSuggestionRow>::class.java).firstOrNull()
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

    // ── Notitas post-it en pareja ──

    data class CoupleNoteRow(
        val id: String? = null,
        val hostess_id: String,
        val partner_id: String,
        val sender_id: String,
        val encrypted_text: String,
        val created_at: Long = System.currentTimeMillis()
    )

    /**
     * Clave de cifrado para notitas: la COMPARTIDA del vínculo (sync), NO la
     * local del dispositivo. Cada teléfono genera su propia clave local de DB;
     * si el partner cifrara con la suya, la hostess jamás podría leer (ella
     * solo prueba [local, sync] y la local del partner no está entre ellas).
     * En un mismo dispositivo el bug es invisible porque la clave local es la
     * misma para ambas cuentas; en dos teléfonos reales rompe partner→hostess.
     */
    private fun noteEncryptionKey(): ByteArray {
        return keystoreManager.getSyncPassphrase()
            ?: keystoreManager.getOrCreateDatabasePassphrase()
    }

    /**
     * Envía una notita a la pareja. El texto se cifra en el dispositivo con
     * la misma clave del resto del sync (zero-knowledge: la nube solo ve
     * bytes). Requiere la tabla `couple_notes` (ver SQL de instalación).
     */
    suspend fun sendCoupleNote(
        hostessId: String,
        partnerId: String,
        senderId: String,
        plainText: String
    ) = withContext(Dispatchers.IO) {
        val text = plainText.trim()
        require(text.isNotEmpty()) { "La notita está vacía." }
        require(text.length <= com.ona.miciclo.calendar.domain.model.CoupleNotes.MAX_LEN) {
            "La notita es muy larga (máx. ${com.ona.miciclo.calendar.domain.model.CoupleNotes.MAX_LEN})."
        }
        val dbPassphrase = noteEncryptionKey()
        val encryptionKey = Base64.encodeToString(dbPassphrase, Base64.NO_WRAP)
        val encryptedBytes = CryptoUtils.encryptJson(text, encryptionKey)
        val row = CoupleNoteRow(
            hostess_id = hostessId,
            partner_id = partnerId,
            sender_id = senderId,
            encrypted_text = Base64.encodeToString(encryptedBytes, Base64.NO_WRAP)
        )
        performRequest("POST", "couple_notes", gson.toJson(row))
    }

    /**
     * Últimas notitas del vínculo, ya descifradas (las ilegibles se omiten).
     * Orden: más recientes primero.
     */
    suspend fun getCoupleNotes(
        hostessId: String,
        myUid: String,
        limit: Int = 20
    ): List<CoupleNote> = withContext(Dispatchers.IO) {
        val response = performRequest(
            method = "GET",
            table = "couple_notes",
            queryParams = "hostess_id=eq.$hostessId&order=created_at.desc&limit=$limit"
        )
        val keys = syncKeys()
        gson.fromJson(response, Array<CoupleNoteRow>::class.java).mapNotNull { row ->
            try {
                val text = decryptWithAnyKey(row.encrypted_text, keys) ?: return@mapNotNull null
                CoupleNote(
                    id = row.id.orEmpty(),
                    senderId = row.sender_id,
                    text = text,
                    createdAtMillis = row.created_at,
                    isMine = row.sender_id == myUid
                )
            } catch (e: Exception) {
                null
            }
        }
    }

    /** Borra una notita propia por id. */
    suspend fun deleteCoupleNote(noteId: String) = withContext(Dispatchers.IO) {
        performRequest(
            method = "DELETE",
            table = "couple_notes",
            queryParams = "id=eq.$noteId"
        )
    }
}
