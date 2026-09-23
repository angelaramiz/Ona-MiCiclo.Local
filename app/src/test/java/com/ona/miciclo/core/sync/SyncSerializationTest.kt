package com.ona.miciclo.core.sync

import com.ona.miciclo.data.local.entity.CycleRecordEntity
import com.ona.miciclo.data.local.entity.DailyLogEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * TDD: el sync de pareja serializa entidades Room con Gson para subirlas a Supabase
 * y las deserializa al descargarlas. La factory de producción `createSyncGson()`
 * (la que usa `SupabaseSyncManager`) debe preservar las fechas como ISO string.
 * En RED este test usaba `Gson()` plano y fallaba con JsonIOException
 * (LocalDate inaccesible por reflexión).
 */
class SyncSerializationTest {

    @Test
    fun `cycle entity LocalDate survives sync round trip`() {
        val gson = createSyncGson()
        val original = CycleRecordEntity(
            userId = "hostess-uid",
            fechaInicioMenstruacion = LocalDate.of(2026, 9, 15),
            duracionSangrado = 5,
            duracionCiclo = 28
        )

        val json = gson.toJson(original)
        // La fecha viaja como ISO string, no como objeto de reflexión.
        assertTrue(json.contains("\"2026-09-15\""))

        val restored = gson.fromJson(json, CycleRecordEntity::class.java)
        assertEquals(original.fechaInicioMenstruacion, restored.fechaInicioMenstruacion)
        assertEquals(original.userId, restored.userId)
    }

    @Test
    fun `daily log entity LocalDate survives sync round trip`() {
        val gson = createSyncGson()
        val original = DailyLogEntity(
            userId = "hostess-uid",
            fecha = LocalDate.of(2026, 9, 15),
            nivelFlujo = "moderado"
        )

        val json = gson.toJson(original)
        assertTrue(json.contains("\"2026-09-15\""))

        val restored = gson.fromJson(json, DailyLogEntity::class.java)
        assertEquals(original.fecha, restored.fecha)
        assertEquals(original.userId, restored.userId)
    }

    @Test
    fun `nullable cycle date survives sync round trip`() {
        val gson = createSyncGson()
        val original = CycleRecordEntity(
            userId = "hostess-uid",
            fechaInicioMenstruacion = null
        )

        val json = gson.toJson(original)
        val restored = gson.fromJson(json, CycleRecordEntity::class.java)

        assertEquals(null, restored.fechaInicioMenstruacion)
    }
}