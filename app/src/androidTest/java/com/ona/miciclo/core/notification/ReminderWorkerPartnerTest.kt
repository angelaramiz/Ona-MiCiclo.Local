package com.ona.miciclo.core.notification

import android.app.NotificationManager
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import com.google.firebase.auth.FirebaseAuth
import com.ona.miciclo.calendar.data.CycleRepositoryImpl
import com.ona.miciclo.calendar.domain.usecase.CalculateCyclePredictionUseCase
import com.ona.miciclo.core.security.KeystoreManager
import com.ona.miciclo.data.local.OnaDatabase
import kotlinx.coroutines.runBlocking
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * E2E B1 (instrumentado, determinista): ejecuta el [ReminderWorker] real contra
 * la DB cifrada real del dispositivo y verifica el aviso al partner.
 *
 * Precondiciones (cuentas QA en emulador):
 * - Sesión iniciada como partner vinculado con datos de la hostess descargados.
 * - "Recibir avisos del ciclo" activado y permiso de notificaciones concedido.
 */
@RunWith(AndroidJUnit4::class)
class ReminderWorkerPartnerTest {

    private fun realDatabase(context: Context): OnaDatabase {
        val passphrase = KeystoreManager(context).getOrCreateDatabasePassphrase()
        return Room.databaseBuilder(context, OnaDatabase::class.java, "ona_miciclo.db")
            .openHelperFactory(SupportOpenHelperFactory(passphrase))
            .setJournalMode(androidx.room.RoomDatabase.JournalMode.TRUNCATE)
            .build()
    }

    @Test
    fun partnerRecibeAvisoDePeriodoProximo() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        assertTrue("Sin sesión de partner en el emulador", !uid.isNullOrEmpty())

        val prefs = ReminderPrefs(context)
        assertTrue("Activa 'Recibir avisos del ciclo' en Configuración", prefs.partnerAlertsEnabled)

        val db = realDatabase(context)
        try {
            val repository = CycleRepositoryImpl(
                db.cycleRecordDao(),
                db.dailyLogDao(),
                javax.inject.Provider { error("sin sync en test") }
            )
            val worker = TestListenableWorkerBuilder<ReminderWorker>(context)
                .setInputData(workDataOf(ReminderWorker.KEY_USER_ID to uid))
                .setWorkerFactory(
                    object : androidx.work.WorkerFactory() {
                        override fun createWorker(
                            appContext: Context,
                            workerClassName: String,
                            workerParameters: androidx.work.WorkerParameters
                        ) = ReminderWorker(
                            appContext,
                            workerParameters,
                            CalculateCyclePredictionUseCase(repository),
                            repository,
                            db.userPreferencesDao()
                        )
                    }
                )
                .build()

            val result = runBlocking { worker.doWork() }
            assertEquals(ListenableWorker.Result.success(), result)

            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val posted = manager.activeNotifications.filter {
                it.notification.channelId == "ona_reminders"
            }
            assertTrue(
                "Sin aviso al partner en el canal ona_reminders",
                posted.isNotEmpty()
            )
            val titles = posted.map {
                it.notification.extras.getString("android.title") ?: ""
            }
            assertTrue(
                "Ningún aviso habla de 'tu pareja': $titles",
                titles.any { it.contains("tu pareja") }
            )
        } finally {
            db.close()
        }
    }
}
