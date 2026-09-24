package com.ona.miciclo.calendar.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * TDD A1: la predicción de calendario debe ajustarse con evidencia sintotérmica
 * (síntoma ovulación, tira LH, cambio térmico, moco fértil).
 */
class SymptothermalAdjustmentTest {

    private val cycleStart = LocalDate.of(2026, 9, 28) // ciclo de 28 días
    private val calendarOvulation = LocalDate.of(2026, 10, 11) // día 14

    private fun log(
        date: LocalDate,
        symptoms: List<String> = emptyList(),
        temp: Double? = null,
        mucus: String? = null,
        lh: String? = null
    ) = DailyLog(
        userId = "h",
        fecha = date,
        sintomasBasicos = symptoms,
        temperaturaBasal = temp,
        mocoCervical = mucus,
        resultadoTiraLh = lh
    )

    private fun adjust(logs: List<DailyLog>) = SymptothermalAdjustment.adjust(
        calendarOvulation = calendarOvulation,
        cycleStart = cycleStart,
        cycleLength = 28,
        logs = logs,
        today = LocalDate.of(2026, 10, 20)
    )

    @Test
    fun `sin registros devuelve valores de calendario y sin evidencia`() {
        val r = adjust(emptyList())
        assertEquals(calendarOvulation, r.ovulationDate)
        assertEquals(calendarOvulation.minusDays(5), r.fertileStart)
        assertEquals(calendarOvulation.plusDays(1), r.fertileEnd)
        assertTrue(r.evidence.isEmpty())
    }

    @Test
    fun `sintoma ovulacion confirmado fija la ovulacion`() {
        val r = adjust(listOf(log(LocalDate.of(2026, 10, 12), symptoms = listOf("calambres", "ovulacion"))))
        assertEquals(LocalDate.of(2026, 10, 12), r.ovulationDate)
        assertEquals(LocalDate.of(2026, 10, 7), r.fertileStart)
        assertEquals(LocalDate.of(2026, 10, 13), r.fertileEnd)
        assertTrue(r.evidence.any { it.contains("12 de octubre") })
    }

    @Test
    fun `LH positivo mueve ovulacion al dia siguiente`() {
        val r = adjust(listOf(log(LocalDate.of(2026, 10, 10), lh = "positivo")))
        assertEquals(LocalDate.of(2026, 10, 11), r.ovulationDate)
        assertTrue(r.evidence.any { it.contains("LH") && it.contains("10 de octubre") })
    }

    @Test
    fun `LH fuera de rango se ignora`() {
        // Día 3 del ciclo: fisiológicamente implausible → no mueve nada
        val r = adjust(listOf(log(LocalDate.of(2026, 9, 30), lh = "positivo")))
        assertEquals(calendarOvulation, r.ovulationDate)
        assertTrue(r.evidence.isEmpty())
    }

    @Test
    fun `prioridad sintoma confirmado gana a LH`() {
        val r = adjust(
            listOf(
                log(LocalDate.of(2026, 10, 9), lh = "positivo"),
                log(LocalDate.of(2026, 10, 12), symptoms = listOf("ovulacion"))
            )
        )
        assertEquals(LocalDate.of(2026, 10, 12), r.ovulationDate)
    }

    @Test
    fun `cambio termico detecta ovulacion en ultimo dia bajo`() {
        val temps = listOf(36.4, 36.5, 36.4, 36.5, 36.8, 36.9, 36.8)
        val logs = temps.mapIndexed { i, t -> log(cycleStart.plusDays(7L + i), temp = t) }
        val r = adjust(logs)
        // Último día bajo = 4.º registro (día 11 del ciclo)
        assertEquals(cycleStart.plusDays(10), r.ovulationDate)
        assertTrue(r.evidence.any { it.contains("térmico") || it.contains("Temperatura") })
    }

    @Test
    fun `moco fertil solo aporta evidencia sin mover fechas`() {
        val r = adjust(listOf(log(LocalDate.of(2026, 10, 8), mucus = "clara_de_huevo")))
        assertEquals(calendarOvulation, r.ovulationDate)
        assertTrue(r.evidence.any { it.contains("fértil") })
    }

    @Test
    fun `logs irrelevantes no cambian nada`() {
        val r = adjust(
            listOf(
                log(LocalDate.of(2026, 10, 5), symptoms = listOf("calambres"), temp = 36.5),
                log(LocalDate.of(2026, 10, 6), lh = "negativo", mucus = "seco")
            )
        )
        assertEquals(calendarOvulation, r.ovulationDate)
        assertTrue(r.evidence.isEmpty())
    }
}
