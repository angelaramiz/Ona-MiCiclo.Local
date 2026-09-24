package com.ona.miciclo.settings.domain.report

import com.ona.miciclo.calendar.domain.model.CycleRecord
import com.ona.miciclo.calendar.domain.model.DailyLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * TDD A4: el informe médico resume ciclos y síntomas para compartir con el doctor.
 */
class MedicalReportBuilderTest {

    private fun period(start: LocalDate, len: Int = 28, bleed: Int = 5, confirmed: Boolean = true) =
        CycleRecord(
            userId = "h",
            fechaInicioMenstruacion = start,
            duracionSangrado = bleed,
            duracionCiclo = len,
            cicloConfirmado = confirmed
        )

    private fun log(date: LocalDate, symptoms: List<String> = emptyList()) = DailyLog(
        userId = "h",
        fecha = date,
        sintomasBasicos = symptoms
    )

    @Test
    fun `resume promedios y ultimo periodo`() {
        val r = MedicalReportBuilder.build(
            periods = listOf(
                period(LocalDate.of(2026, 7, 16), len = 28),
                period(LocalDate.of(2026, 8, 13), len = 28),
                period(LocalDate.of(2026, 9, 12), len = 30)
            ),
            logs = emptyList(),
            nextPeriod = LocalDate.of(2026, 10, 10),
            today = LocalDate.of(2026, 9, 24)
        )
        assertEquals(3, r.totalCycles)
        assertEquals(29, r.avgCycleLength)
        assertEquals(5, r.avgBleeding)
        assertEquals(LocalDate.of(2026, 9, 12), r.lastPeriodStart)
        assertEquals(LocalDate.of(2026, 10, 10), r.nextPeriodEstimate)
    }

    @Test
    fun `tabla con los ultimos periodos ordenados`() {
        val r = MedicalReportBuilder.build(
            periods = listOf(
                period(LocalDate.of(2026, 9, 12)),
                period(LocalDate.of(2026, 8, 13)),
                period(LocalDate.of(2026, 7, 16))
            ),
            logs = emptyList(),
            nextPeriod = null,
            today = LocalDate.of(2026, 9, 24)
        )
        assertEquals(3, r.recentPeriods.size)
        assertEquals(LocalDate.of(2026, 9, 12), r.recentPeriods[0].start)
    }

    @Test
    fun `sintomas frecuentes ordenados por conteo`() {
        val r = MedicalReportBuilder.build(
            periods = listOf(period(LocalDate.of(2026, 9, 12))),
            logs = listOf(
                log(LocalDate.of(2026, 9, 20), listOf("calambres", "fatiga")),
                log(LocalDate.of(2026, 9, 21), listOf("calambres")),
                log(LocalDate.of(2026, 9, 22), listOf("ovulacion"))
            ),
            nextPeriod = null,
            today = LocalDate.of(2026, 9, 24)
        )
        assertEquals("calambres", r.topSymptoms[0].key)
        assertEquals(2, r.topSymptoms[0].count)
        assertEquals(3, r.totalLogs)
    }

    @Test
    fun `sin periodos el informe queda vacio pero valido`() {
        val r = MedicalReportBuilder.build(
            periods = emptyList(),
            logs = emptyList(),
            nextPeriod = null,
            today = LocalDate.of(2026, 9, 24)
        )
        assertEquals(0, r.totalCycles)
        assertTrue(r.recentPeriods.isEmpty())
        assertTrue(r.topSymptoms.isEmpty())
    }

    @Test
    fun `etiquetas de sintomas usan el mapeo dado`() {
        val r = MedicalReportBuilder.build(
            periods = listOf(period(LocalDate.of(2026, 9, 12))),
            logs = listOf(log(LocalDate.of(2026, 9, 20), listOf("dolor_cabeza"))),
            nextPeriod = null,
            today = LocalDate.of(2026, 9, 24),
            symptomLabel = { if (it == "dolor_cabeza") "Dolor de cabeza" else it }
        )
        assertEquals("Dolor de cabeza", r.topSymptoms[0].label)
    }
}
