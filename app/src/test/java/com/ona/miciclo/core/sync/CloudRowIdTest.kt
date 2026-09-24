package com.ona.miciclo.core.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Los IDs de fila en la nube deben ser únicos por (usuario, registro local):
 * dos installs frescos generan los mismos IDs locales (1, 2, ...) y el POST
 * colisionaba en la PK (409 silencioso) sin subir nada nuevo.
 */
class CloudRowIdTest {

    @Test
    fun `ids namespaced por usuario`() {
        assertEquals(
            "hostess1_1",
            SupabaseSyncManager.cloudRowId("hostess1", "1")
        )
        assertEquals(
            "hostess1_2026-09-24",
            SupabaseSyncManager.cloudRowId("hostess1", "2026-09-24")
        )
    }

    @Test
    fun `distintos usuarios no colisionan`() {
        assertNotEquals(
            SupabaseSyncManager.cloudRowId("hostess1", "1"),
            SupabaseSyncManager.cloudRowId("hostess2", "1")
        )
    }
}
