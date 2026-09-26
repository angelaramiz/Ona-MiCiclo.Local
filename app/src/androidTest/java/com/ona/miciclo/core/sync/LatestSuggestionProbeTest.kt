package com.ona.miciclo.core.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.auth.FirebaseAuth
import com.ona.miciclo.core.security.KeystoreManager
import com.ona.miciclo.data.local.OnaDatabase
import kotlinx.coroutines.runBlocking
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Diagnóstico B2: la lectura de la última sugerencia del partner debe
 * devolver la fila (la escritura funciona; la UI no la muestra).
 */
@RunWith(AndroidJUnit4::class)
class LatestSuggestionProbeTest {

    @Test
    fun probeGetLatestPartnerSuggestion() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        assertTrue("Sin sesión en el emulador", !uid.isNullOrEmpty())

        val passphrase = KeystoreManager(context).getOrCreateDatabasePassphrase()
        val db = Room.databaseBuilder(context, OnaDatabase::class.java, "ona_miciclo.db")
            .openHelperFactory(SupportOpenHelperFactory(passphrase))
            .setJournalMode(androidx.room.RoomDatabase.JournalMode.TRUNCATE)
            .build()
        try {
            val syncManager = SupabaseSyncManager(
                KeystoreManager(context),
                db,
                db.cycleRecordDao(),
                db.dailyLogDao(),
                db.userPreferencesDao()
            )
            val latest = runBlocking { syncManager.getLatestPartnerSuggestion(uid!!) }
            assertNotNull("getLatestPartnerSuggestion devolvió null (query o parse fallan en app)", latest)
        } finally {
            db.close()
        }
    }
}
