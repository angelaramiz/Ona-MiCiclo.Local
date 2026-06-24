package com.ona.miciclo.core.sync

import com.google.firebase.firestore.FirebaseFirestore
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
import java.time.LocalDate
import java.util.UUID

/**
 * Gestor de sincronización y vinculación de pareja mediante Firebase Firestore.
 */
class FirestoreSyncManager(
    private val db: FirebaseFirestore,
    private val database: OnaDatabase,
    private val cycleRecordDao: CycleRecordDao,
    private val dailyLogDao: DailyLogDao,
    private val userPreferencesDao: UserPreferencesDao
) {
    private val scope = CoroutineScope(Dispatchers.IO)

    // Data classes simplificadas para Firestore que coinciden con Room
    data class FirestoreCycle(
        val id: Long = 0,
        val fechaInicioMenstruacion: String = "",
        val duracionSangrado: Int = 5,
        val duracionCiclo: Int = 28,
        val metodoRegistrado: String = "calendario",
        val objetivoUsuario: String = "conocimiento",
        val cicloConfirmado: Boolean = false
    )

    data class FirestoreDailyLog(
        val id: Long = 0,
        val dateString: String = "",
        val nivelFlujo: String? = null,
        val sintomasBasicos: String? = null,
        val temperaturaBasal: Double? = null,
        val notas: String? = null
    )

    /**
     * Genera un código de invitación único de 6 caracteres para vincular a la pareja.
     */
    suspend fun generateInvitationCode(hostessId: String): String = withContext(Dispatchers.IO) {
        val code = UUID.randomUUID().toString().substring(0, 6).uppercase()
        val invitationData = mapOf(
            "hostessId" to hostessId,
            "createdAt" to System.currentTimeMillis()
        )
        db.collection("invitations").document(code).set(invitationData).await()
        
        // Registrar rol localmente como hostess
        val currentPrefs = userPreferencesDao.getByUserId(hostessId)
        if (currentPrefs != null) {
            userPreferencesDao.insertOrUpdate(currentPrefs.copy(userRole = "hostess"))
        } else {
            userPreferencesDao.insertOrUpdate(UserPreferencesEntity(userId = hostessId, userRole = "hostess"))
        }
        
        code
    }

    /**
     * Valida un código de invitación y vincula la cuenta de la pareja con la anfitriona.
     * Retorna el ID de la anfitriona si tiene éxito, o lanza excepción.
     */
    suspend fun linkPartnerWithCode(partnerId: String, code: String): String = withContext(Dispatchers.IO) {
        val doc = db.collection("invitations").document(code.uppercase()).get().await()
        if (!doc.exists()) {
            throw Exception("Código de invitación inválido o expirado.")
        }
        
        val hostessId = doc.getString("hostessId") ?: throw Exception("Datos de invitación incompletos.")
        
        // Guardar relación en Firestore
        val partnerData = mapOf(
            "role" to "partner",
            "linkedUserId" to hostessId,
            "updatedAt" to System.currentTimeMillis()
        )
        db.collection("users").document(partnerId).set(partnerData).await()
        
        val hostessData = mapOf(
            "role" to "hostess",
            "linkedUserId" to partnerId,
            "updatedAt" to System.currentTimeMillis()
        )
        db.collection("users").document(hostessId).set(hostessData).await()

        // Guardar localmente el rol y vinculación del partner
        val currentPrefs = userPreferencesDao.getByUserId(partnerId)
        if (currentPrefs != null) {
            userPreferencesDao.insertOrUpdate(currentPrefs.copy(userRole = "partner", linkedUserId = hostessId))
        } else {
            userPreferencesDao.insertOrUpdate(UserPreferencesEntity(userId = partnerId, userRole = "partner", linkedUserId = hostessId))
        }

        hostessId
    }

    /**
     * Sincroniza los datos locales de la anfitriona hacia Firestore.
     */
    fun syncHostessDataToCloud(hostessId: String) {
        scope.launch {
            try {
                // 1. Sincronizar Ciclos
                val cycles = cycleRecordDao.getAllByUserSync(hostessId)
                for (cycle in cycles) {
                    val firestoreCycle = FirestoreCycle(
                        id = cycle.id,
                        fechaInicioMenstruacion = cycle.fechaInicioMenstruacion.toString(),
                        duracionSangrado = cycle.duracionSangrado,
                        duracionCiclo = cycle.duracionCiclo,
                        metodoRegistrado = cycle.metodoRegistrado,
                        objetivoUsuario = cycle.objetivoUsuario,
                        cicloConfirmado = cycle.cicloConfirmado
                    )
                    db.collection("users").document(hostessId)
                        .collection("cycles").document(cycle.id.toString()).set(firestoreCycle)
                }

                // 2. Sincronizar Logs Diarios
                val logs = dailyLogDao.getAllByUserSync(hostessId)
                for (log in logs) {
                    val dateStr = log.fecha.toString()
                    val firestoreLog = FirestoreDailyLog(
                        id = log.id,
                        dateString = dateStr,
                        nivelFlujo = log.nivelFlujo,
                        sintomasBasicos = log.sintomasBasicos,
                        temperaturaBasal = log.temperaturaBasal,
                        notas = log.notas
                    )
                    db.collection("users").document(hostessId)
                        .collection("daily_logs").document(dateStr).set(firestoreLog)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Escucha en tiempo real los datos de la anfitriona y los escribe en la base de datos local de la pareja.
     */
    fun startPartnerSyncListener(partnerId: String, hostessId: String) {
        // Escuchar ciclos de la anfitriona
        db.collection("users").document(hostessId).collection("cycles")
            .addSnapshotListener { snapshots, e ->
                if (e != null || snapshots == null) return@addSnapshotListener
                scope.launch {
                    val entities = snapshots.documents.mapNotNull { doc ->
                        val fc = doc.toObject(FirestoreCycle::class.java) ?: return@mapNotNull null
                        CycleRecordEntity(
                            id = fc.id,
                            userId = hostessId, // Guardar bajo el ID de la anfitriona
                            fechaInicioMenstruacion = LocalDate.parse(fc.fechaInicioMenstruacion),
                            duracionSangrado = fc.duracionSangrado,
                            duracionCiclo = fc.duracionCiclo,
                            metodoRegistrado = fc.metodoRegistrado,
                            objetivoUsuario = fc.objetivoUsuario,
                            cicloConfirmado = fc.cicloConfirmado
                        )
                    }
                    cycleRecordDao.clearAndInsertCycles(hostessId, entities)
                }
            }

        // Escuchar logs diarios de la anfitriona
        db.collection("users").document(hostessId).collection("daily_logs")
            .addSnapshotListener { snapshots, e ->
                if (e != null || snapshots == null) return@addSnapshotListener
                scope.launch {
                    val entities = snapshots.documents.mapNotNull { doc ->
                        val fl = doc.toObject(FirestoreDailyLog::class.java) ?: return@mapNotNull null
                        DailyLogEntity(
                            id = fl.id,
                            fecha = LocalDate.parse(fl.dateString),
                            userId = hostessId, // Guardar bajo el ID de la anfitriona
                            nivelFlujo = fl.nivelFlujo,
                            sintomasBasicos = fl.sintomasBasicos,
                            temperaturaBasal = fl.temperaturaBasal,
                            notas = fl.notas
                        )
                    }
                    dailyLogDao.clearAndInsertLogs(hostessId, entities)
                }
            }
    }
}
