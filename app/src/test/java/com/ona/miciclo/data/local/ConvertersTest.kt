package com.ona.miciclo.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/**
 * Caracterización TDD de los converters de Room.
 * Cubre la tolerancia a fechas corruptas (regresión de CAL-001) que evitó
 * un crash de arranque causado por filas con `0000-00-00`.
 */
class ConvertersTest {

    private val converters = Converters()

    @Test
    fun `localDate round trip through converter`() {
        val date = LocalDate.of(2026, 9, 10)
        val stored = converters.fromLocalDate(date)
        assertEquals("2026-09-10", stored)
        assertEquals(date, converters.toLocalDate(stored))
    }

    @Test
    fun `null date round trips as null`() {
        assertNull(converters.fromLocalDate(null))
        assertNull(converters.toLocalDate(null))
    }

    @Test
    fun `corrupt date 0000-00-00 maps to null`() {
        // El bug histórico: fechas serializadas como "0000-00-00" rompían LocalDate.parse.
        assertNull(converters.toLocalDate("0000-00-00"))
    }

    @Test
    fun `blank string maps to null`() {
        assertNull(converters.toLocalDate(""))
        assertNull(converters.toLocalDate("   "))
    }

    @Test
    fun `malformed date maps to null instead of throwing`() {
        assertNull(converters.toLocalDate("2026-13-45"))
        assertNull(converters.toLocalDate("not-a-date"))
    }
}