package com.ona.miciclo.calendar.domain.model

import org.junit.Assert.*
import org.junit.Test

class CoupleNoteTest {

    @Test
    fun `valida vacio y limite`() {
        assertFalse(CoupleNotes.isValid(""))
        assertFalse(CoupleNotes.isValid("   "))
        assertTrue(CoupleNotes.isValid("Hola 💕"))
        assertTrue(CoupleNotes.isValid("a".repeat(140)))
        assertFalse(CoupleNotes.isValid("a".repeat(141)))
    }

    @Test
    fun `etiqueta de remitente`() {
        assertEquals("Tú", CoupleNotes.senderLabel(true))
        assertEquals("Tu pareja", CoupleNotes.senderLabel(false))
    }

    @Test
    fun `timeAgo en espanol`() {
        val now = 1_700_000_000_000L
        assertEquals("ahora mismo", CoupleNotes.timeAgoEs(now, now - 10_000))
        assertEquals("hace 5 min", CoupleNotes.timeAgoEs(now, now - 5 * 60_000))
        assertEquals("hace 2 h", CoupleNotes.timeAgoEs(now, now - 2 * 3_600_000))
        assertEquals("ayer", CoupleNotes.timeAgoEs(now, now - 25 * 3_600_000))
    }
}
