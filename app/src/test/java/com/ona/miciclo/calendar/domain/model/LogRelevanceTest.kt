package com.ona.miciclo.calendar.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * TDD: relevancia por fase + síntomas premenstruales.
 */
class LogRelevanceTest {

    private val start = LocalDate.of(2026, 8, 29) // ciclo 28 días
    private val prediction = CyclePrediction(
        proximaMenstruacion = LocalDate.of(2026, 9, 26),
        duracionPromedio = 28,
        inicioVentanaFertil = LocalDate.of(2026, 9, 7),
        finVentanaFertil = LocalDate.of(2026, 9, 13),
        diaOvulacion = LocalDate.of(2026, 9, 12),
        faseActual = CyclePhase.LUTEAL,
        diaDelCiclo = 27,
        confianza = PredictionConfidence.LOW,
        mensaje = "msg"
    )

    @Test
    fun `momentos del ciclo`() {
        assertEquals(
            LogRelevance.Moment.PERIOD,
            LogRelevance.momentFor(LocalDate.of(2026, 8, 30), prediction, start)
        )
        assertEquals(
            LogRelevance.Moment.FERTILE,
            LogRelevance.momentFor(LocalDate.of(2026, 9, 10), prediction, start)
        )
        assertEquals(
            LogRelevance.Moment.LUTEAL,
            LogRelevance.momentFor(LocalDate.of(2026, 9, 24), prediction, start)
        )
        assertEquals(
            LogRelevance.Moment.FOLLICULAR,
            LogRelevance.momentFor(LocalDate.of(2026, 9, 5), prediction, start)
        )
    }

    @Test
    fun `sin datos es desconocido`() {
        assertEquals(LogRelevance.Moment.UNKNOWN, LogRelevance.momentFor(start, null, start))
        assertEquals(LogRelevance.Moment.UNKNOWN, LogRelevance.momentFor(start, prediction, null))
        assertEquals(
            LogRelevance.Moment.UNKNOWN,
            LogRelevance.momentFor(start.minusDays(3), prediction, start)
        )
    }

    @Test
    fun `banners por momento`() {
        assertTrue(LogRelevance.bannerFor(LogRelevance.Moment.FERTILE)!!.contains("tira LH"))
        assertTrue(LogRelevance.bannerFor(LogRelevance.Moment.LUTEAL)!!.contains("premenstruales"))
        assertTrue(LogRelevance.bannerFor(LogRelevance.Moment.PERIOD)!!.contains("flujo"))
        assertNull(LogRelevance.bannerFor(LogRelevance.Moment.FOLLICULAR))
        assertNull(LogRelevance.bannerFor(LogRelevance.Moment.UNKNOWN))
    }

    @Test
    fun `lutea prioriza premenstruales y fertil ovulacion`() {
        val all = listOf("acne", "ovulacion", "calambres", "insomnio", "irritabilidad")
        val luteal = LogRelevance.orderedSymptoms(all, LogRelevance.Moment.LUTEAL)
        assertEquals(listOf("calambres", "irritabilidad", "acne", "ovulacion", "insomnio"), luteal)
        val fertile = LogRelevance.orderedSymptoms(all, LogRelevance.Moment.FERTILE)
        assertEquals("ovulacion", fertile.first())
        val period = LogRelevance.orderedSymptoms(all, LogRelevance.Moment.PERIOD)
        assertEquals(all, period)
    }

    @Test
    fun `pms incluye los nuevos sintomas`() {
        listOf("irritabilidad", "llanto_facil", "ansiedad", "estrenimiento", "diarrea").forEach {
            assertTrue(LogRelevance.PMS_SYMPTOMS.contains(it))
        }
    }
}
