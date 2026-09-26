package com.ona.miciclo.core.sync

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

/**
 * IDs de nube por clave natural + filtro de filas legacy.
 *
 * Contexto: antes cada dispositivo subía con su propio id local
 * (`uid_5`), así que el espejo del partner re-subía las mismas filas
 * lógicas con PK distinta → duplicados que ningún download colapsaba.
 * Ahora la PK es la clave natural (`uid_cycle_fecha` / `uid_log_fecha`):
 * re-subir la misma fila lógica hace upsert idempotente.
 */
class SyncNaturalKeyTest {

    @Test
    fun `cycleId usa clave natural`() {
        assertEquals(
            "hostess1_cycle_2026-08-29",
            SupabaseSyncManager.cloudCycleId("hostess1", LocalDate.of(2026, 8, 29))
        )
    }

    @Test
    fun `logId usa clave natural`() {
        assertEquals(
            "hostess1_log_2026-09-26",
            SupabaseSyncManager.cloudLogId("hostess1", LocalDate.of(2026, 9, 26))
        )
    }

    @Test
    fun `mismo registro logico mismo id aunque cambie el id local`() {
        // Partner re-sube el espejo con SUS ids locales: la PK de nube no cambia.
        assertEquals(
            SupabaseSyncManager.cloudCycleId("h", LocalDate.of(2026, 8, 29)),
            SupabaseSyncManager.cloudCycleId("h", LocalDate.of(2026, 8, 29))
        )
    }

    @Test
    fun `isNaturalCloudId acepta formato nuevo`() {
        assertTrue(SupabaseSyncManager.isNaturalCloudId("h_cycle_2026-08-29"))
        assertTrue(SupabaseSyncManager.isNaturalCloudId("h_log_2026-09-26"))
    }

    @Test
    fun `isNaturalCloudId rechaza formato legacy`() {
        // uid_5 (ciclos viejos) y uid_fecha (logs viejos) se ignoran al descargar:
        // su contenido ya vive en las filas con id natural tras el primer upload.
        assertFalse(SupabaseSyncManager.isNaturalCloudId("h_5"))
        assertFalse(SupabaseSyncManager.isNaturalCloudId("h_2026-09-24"))
        assertFalse(SupabaseSyncManager.isNaturalCloudId("h"))
    }

    @Test
    fun `selectRowsForDownload ignora filas legacy`() {
        val rows = listOf(
            SupabaseSyncManager.EncryptedPayloadRow("h_5", "h", "viejo"),
            SupabaseSyncManager.EncryptedPayloadRow("h_2026-09-24", "h", "viejo"),
            SupabaseSyncManager.EncryptedPayloadRow("h_cycle_2026-08-29", "h", "nuevo"),
            SupabaseSyncManager.EncryptedPayloadRow("h_log_2026-09-26", "h", "nuevo")
        )
        val selected = SupabaseSyncManager.selectRowsForDownload(rows)
        assertEquals(2, selected.size)
        assertTrue(selected.all { it.encrypted_payload == "nuevo" })
    }
}
