package com.ona.miciclo.core.widget

import com.ona.miciclo.calendar.domain.model.CyclePhase
import com.ona.miciclo.calendar.domain.model.CyclePrediction
import com.ona.miciclo.calendar.domain.model.PredictionConfidence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * TDD A3: el snapshot del widget resume el estado del ciclo en 3 líneas.
 */
class CycleSnapshotTest {

    private val today = LocalDate.of(2026, 9, 24)

    private fun prediction(
        nextPeriod: LocalDate = LocalDate.of(2026, 10, 10),
        fertileStart: LocalDate = LocalDate.of(2026, 9, 19),
        fertileEnd: LocalDate = LocalDate.of(2026, 9, 25),
        dayOfCycle: Int = 13,
        avgLen: Int = 28,
        phase: CyclePhase = CyclePhase.FERTILE
    ) = CyclePrediction(
        proximaMenstruacion = nextPeriod,
        duracionPromedio = avgLen,
        inicioVentanaFertil = fertileStart,
        finVentanaFertil = fertileEnd,
        diaOvulacion = LocalDate.of(2026, 9, 24),
        faseActual = phase,
        diaDelCiclo = dayOfCycle,
        confianza = PredictionConfidence.LOW,
        mensaje = "msg"
    )

    @Test
    fun `hostess en ventana fertil resume dia periodo y fase`() {
        val s = CycleSnapshotBuilder.build(prediction(), today, isPartner = false)
        assertEquals("Día 13 de 28", s.title)
        assertTrue(s.subtitle.contains("10 de oct"))
        assertEquals("Ventana fértil", s.phase)
    }

    @Test
    fun `partner ve etiqueta de pareja`() {
        val s = CycleSnapshotBuilder.build(prediction(), today, isPartner = true)
        assertTrue(s.title.contains("pareja") || s.subtitle.contains("pareja"))
    }

    @Test
    fun `sin prediccion muestra placeholder`() {
        val s = CycleSnapshotBuilder.build(null, today, isPartner = false)
        assertEquals("Ona", s.title)
        assertTrue(s.subtitle.contains("Sin datos") || s.phase.contains("Sin datos"))
    }

    @Test
    fun `periodo manana se destaca en el subtitulo`() {
        val s = CycleSnapshotBuilder.build(
            prediction(nextPeriod = today.plusDays(1)),
            today,
            isPartner = false
        )
        assertTrue(s.subtitle.contains("mañana"))
    }
}
