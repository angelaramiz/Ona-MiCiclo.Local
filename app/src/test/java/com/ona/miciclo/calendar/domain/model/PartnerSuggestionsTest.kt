package com.ona.miciclo.calendar.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PartnerSuggestionsTest {

    @Test
    fun `tipos conocidos`() {
        assertTrue(PartnerSuggestions.isKnown(PartnerSuggestions.START_PERIOD))
        assertTrue(PartnerSuggestions.isKnown(PartnerSuggestions.OVULATION_DAY))
        assertFalse(PartnerSuggestions.isKnown("FUTURE_TYPE"))
    }

    @Test
    fun `etiquetas por tipo`() {
        assertEquals("inicio de periodo", PartnerSuggestions.label(PartnerSuggestions.START_PERIOD))
        assertEquals("día de ovulación", PartnerSuggestions.label(PartnerSuggestions.OVULATION_DAY))
    }

    @Test
    fun `tipos desconocidos caen en texto de periodo (compatibilidad)`() {
        assertEquals("inicio de periodo", PartnerSuggestions.label("FUTURE_TYPE"))
        assertTrue(PartnerSuggestions.dialogText("FUTURE_TYPE", "2026-09-24").contains("periodo"))
    }

    @Test
    fun `textos de ovulacion mencionan ovulacion`() {
        val date = "2026-10-12"
        assertTrue(PartnerSuggestions.dialogText(PartnerSuggestions.OVULATION_DAY, date).contains(date))
        assertTrue(PartnerSuggestions.dialogText(PartnerSuggestions.OVULATION_DAY, date).contains("ovulación"))
        assertTrue(PartnerSuggestions.notificationText(PartnerSuggestions.OVULATION_DAY, date).contains("ovulación"))
        assertEquals("Ovulación registrada ✓", PartnerSuggestions.approveMessage(PartnerSuggestions.OVULATION_DAY))
        assertEquals("Sugerencia de ovulación enviada ✓", PartnerSuggestions.sentMessage(PartnerSuggestions.OVULATION_DAY))
    }

    @Test
    fun `linea de estado refleja PENDING APPROVED REJECTED`() {
        assertTrue(PartnerSuggestions.statusLine("START_PERIOD", "2026-09-24", "PENDING").contains("pendiente"))
        assertTrue(PartnerSuggestions.statusLine("OVULATION_DAY", "2026-10-12", "APPROVED").contains("aprobada"))
        assertTrue(PartnerSuggestions.statusLine("START_PERIOD", "2026-09-24", "REJECTED").contains("rechazada"))
    }

    @Test
    fun `merge crea log minimo cuando no existe`() {
        val date = LocalDate.of(2026, 10, 12)
        val merged = PartnerSuggestions.withOvulationConfirmed(null, "hostess1", date)
        assertEquals("hostess1", merged.userId)
        assertEquals(date, merged.fecha)
        assertEquals(listOf("ovulacion"), merged.sintomasBasicos)
        assertEquals(FlowLevel.NONE, merged.nivelFlujo)
        assertTrue(merged.notas!!.contains("Ovulación confirmada"))
    }

    @Test
    fun `merge preserva datos existentes y no duplica`() {
        val date = LocalDate.of(2026, 10, 12)
        val existing = DailyLog(
            id = 7,
            userId = "hostess1",
            fecha = date,
            nivelFlujo = FlowLevel.LIGHT,
            sintomasBasicos = listOf("calambres", "ovulacion"),
            temperaturaBasal = 36.6,
            mocoCervical = "clara_de_huevo",
            notas = "nota previa"
        )
        val merged = PartnerSuggestions.withOvulationConfirmed(existing, "hostess1", date)
        assertEquals(7, merged.id)
        assertEquals(FlowLevel.LIGHT, merged.nivelFlujo)
        assertEquals(listOf("calambres", "ovulacion"), merged.sintomasBasicos)
        assertEquals(36.6, merged.temperaturaBasal!!, 0.001)
        assertEquals("clara_de_huevo", merged.mocoCervical)
        assertTrue(merged.notas!!.contains("nota previa"))
        assertTrue(merged.notas!!.contains("Ovulación confirmada"))
    }

    @Test
    fun `merge no repite la nota de confirmacion`() {
        val date = LocalDate.of(2026, 10, 12)
        val once = PartnerSuggestions.withOvulationConfirmed(null, "h", date)
        val twice = PartnerSuggestions.withOvulationConfirmed(once, "h", date)
        assertEquals(once.notas, twice.notas)
        assertEquals(listOf("ovulacion"), twice.sintomasBasicos)
    }

    // ── B2: notas de apoyo (tipos-mensaje) ──

    @Test
    fun `notas de apoyo detectadas y mapeadas`() {
        val notes = listOf(
            PartnerSuggestions.SUPPORT_ANIMO,
            PartnerSuggestions.SUPPORT_DESCANSA,
            PartnerSuggestions.SUPPORT_ORGULLO,
            PartnerSuggestions.SUPPORT_ABRAZO
        )
        notes.forEach {
            assertTrue(PartnerSuggestions.isSupportNote(it))
            assertTrue(PartnerSuggestions.isKnown(it))
            assertTrue(PartnerSuggestions.noteMessage(it)!!.isNotBlank())
        }
        assertFalse(PartnerSuggestions.isSupportNote(PartnerSuggestions.START_PERIOD))
        assertFalse(PartnerSuggestions.isSupportNote("FUTURE_TYPE"))
        assertEquals(null, PartnerSuggestions.noteMessage(PartnerSuggestions.START_PERIOD))
    }

    @Test
    fun `textos de notas hablan de mensaje no de periodo`() {
        val type = PartnerSuggestions.SUPPORT_ANIMO
        val msg = PartnerSuggestions.noteMessage(type)!!
        assertTrue(PartnerSuggestions.dialogText(type, "2026-09-24").contains(msg))
        assertTrue(PartnerSuggestions.notificationText(type, "2026-09-24").contains(msg))
        assertEquals("Mensaje enviado 💌", PartnerSuggestions.sentMessage(type))
        assertEquals("💌 ¡Mensaje recibido!", PartnerSuggestions.approveMessage(type))
        assertEquals("Mensaje de tu pareja 💌", PartnerSuggestions.dialogTitle(type))
    }

    @Test
    fun `estado de nota aprobada dice visto`() {
        val line = PartnerSuggestions.statusLine(
            PartnerSuggestions.SUPPORT_ABRAZO, "2026-09-24", "APPROVED"
        )
        assertTrue(line.contains("visto"))
        val pending = PartnerSuggestions.statusLine(
            PartnerSuggestions.SUPPORT_ABRAZO, "2026-09-24", "PENDING"
        )
        assertTrue(pending.contains("pendiente"))
    }
}
