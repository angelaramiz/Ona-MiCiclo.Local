package com.ona.miciclo.calendar.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * TDD B3: modo íntimo dual (concebir / disfrutar seguro).
 */
class CoupleGuidanceTest {

    private val today = LocalDate.of(2026, 9, 24)

    private fun prediction(
        nextPeriod: LocalDate = LocalDate.of(2026, 10, 10),
        fertileStart: LocalDate = LocalDate.of(2026, 10, 4),
        fertileEnd: LocalDate = LocalDate.of(2026, 10, 10),
        phase: CyclePhase = CyclePhase.LUTEAL
    ) = CyclePrediction(
        proximaMenstruacion = nextPeriod,
        duracionPromedio = 28,
        inicioVentanaFertil = fertileStart,
        finVentanaFertil = fertileEnd,
        diaOvulacion = LocalDate.of(2026, 10, 9),
        faseActual = phase,
        diaDelCiclo = 27,
        confianza = PredictionConfidence.LOW,
        mensaje = "msg"
    )

    @Test
    fun `sin prediccion u objetivo no hay guia`() {
        assertNull(CoupleGuidance.forCouple(null, today, CoupleGuidance.GOAL_AVOID, false))
        assertNull(CoupleGuidance.forCouple(prediction(), today, null, false))
        assertNull(CoupleGuidance.forCouple(prediction(), today, "conocimiento", false))
    }

    @Test
    fun `dia fertil buscando embarazo`() {
        val fertileDay = LocalDate.of(2026, 10, 6)
        val r = CoupleGuidance.forCouple(prediction(), fertileDay, CoupleGuidance.GOAL_CONCEIVE, false)!!
        assertEquals(CoupleGuidance.Level.FERTILE, r.level)
        assertTrue(r.title.contains("fértil"))
        assertTrue(r.text.contains("embarazo"))
        assertFalse(r.showDisclaimer)
    }

    @Test
    fun `dia fertil evitando con disclaimer`() {
        val fertileDay = LocalDate.of(2026, 10, 6)
        val r = CoupleGuidance.forCouple(prediction(), fertileDay, CoupleGuidance.GOAL_AVOID, false)!!
        assertEquals(CoupleGuidance.Level.FERTILE, r.level)
        assertTrue(r.text.contains("protección"))
        assertTrue(r.showDisclaimer)
    }

    @Test
    fun `dia seguro evitando sin remordimientos`() {
        val r = CoupleGuidance.forCouple(prediction(), today, CoupleGuidance.GOAL_AVOID, false)!!
        assertEquals(CoupleGuidance.Level.SAFE, r.level)
        assertTrue(r.text.contains("sin remordimientos"))
        assertTrue(r.showDisclaimer)
    }

    @Test
    fun `fuera de fertil buscando es neutral`() {
        val r = CoupleGuidance.forCouple(prediction(), today, CoupleGuidance.GOAL_CONCEIVE, false)!!
        assertEquals(CoupleGuidance.Level.NEUTRAL, r.level)
        assertFalse(r.showDisclaimer)
    }

    @Test
    fun `periodo cercano domina en ambos objetivos`() {
        val almost = today.plusDays(2)
        val c = CoupleGuidance.forCouple(
            prediction(nextPeriod = almost), today, CoupleGuidance.GOAL_CONCEIVE, false
        )!!
        val a = CoupleGuidance.forCouple(
            prediction(nextPeriod = almost), today, CoupleGuidance.GOAL_AVOID, false
        )!!
        assertEquals(CoupleGuidance.Level.PERIOD, c.level)
        assertEquals(CoupleGuidance.Level.PERIOD, a.level)
    }

    @Test
    fun `modo pareja habla de tu pareja`() {
        val fertileDay = LocalDate.of(2026, 10, 6)
        val r = CoupleGuidance.forCouple(prediction(), fertileDay, CoupleGuidance.GOAL_CONCEIVE, true)!!
        assertTrue(r.title.contains("tu pareja") || r.text.contains("tu pareja"))
        val s = CoupleGuidance.forCouple(prediction(), today, CoupleGuidance.GOAL_AVOID, true)!!
        assertTrue(s.title.contains("tu pareja") || s.text.contains("tu pareja"))
    }
}
