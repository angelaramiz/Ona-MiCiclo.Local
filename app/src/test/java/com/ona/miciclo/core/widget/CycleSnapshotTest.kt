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
        diaOvulacion = LocalDate.of(2026, 10, 9),
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

    // ── Widget ampliado: embarazo + notas ──

    private fun log(
        date: LocalDate,
        symptoms: List<String> = emptyList(),
        temp: Double? = null,
        flow: com.ona.miciclo.calendar.domain.model.FlowLevel =
            com.ona.miciclo.calendar.domain.model.FlowLevel.NONE,
        notes: String? = null
    ) = com.ona.miciclo.calendar.domain.model.DailyLog(
        userId = "h",
        fecha = date,
        nivelFlujo = flow,
        sintomasBasicos = symptoms,
        temperaturaBasal = temp,
        notas = notes
    )

    @Test
    fun `probabilidad segun fase`() {
        val cases = mapOf(
            CyclePhase.MENSTRUATION to "Muy baja",
            CyclePhase.FOLLICULAR to "Baja",
            CyclePhase.FERTILE to "Alta",
            CyclePhase.LUTEAL to "Baja",
            CyclePhase.UNKNOWN to "—"
        )
        cases.forEach { (phase, expected) ->
            val s = CycleSnapshotBuilder.build(prediction(phase = phase), today, false)
            assertEquals(expected, s.pregnancy)
        }
    }

    @Test
    fun `dia de ovulacion es muy alta`() {
        val ovDay = LocalDate.of(2026, 9, 24)
        val p = prediction().copy(diaOvulacion = ovDay, faseActual = CyclePhase.FERTILE)
        val s = CycleSnapshotBuilder.build(p, ovDay, false)
        assertEquals("Muy alta", s.pregnancy)
    }

    @Test
    fun `notas del dia con sintomas y temperatura`() {
        val l = log(
            today,
            symptoms = listOf("calambres", "dolor_cabeza"),
            temp = 36.6,
            flow = com.ona.miciclo.calendar.domain.model.FlowLevel.LIGHT,
            notes = "me siento mejor en la tarde"
        )
        val s = CycleSnapshotBuilder.build(prediction(), today, false, l)
        assertTrue(s.notes.contains("Calambres"))
        assertTrue(s.notes.contains("36.6"))
        assertTrue(s.notes.contains("Ligero") || s.notes.contains("ligero"))
    }

    @Test
    fun `sin log muestra sin registros`() {
        val s = CycleSnapshotBuilder.build(prediction(), today, false, null)
        assertTrue(s.notes.contains("Sin registros"))
    }

    @Test
    fun `notas largas se truncan`() {
        val l = log(today, notes = "x".repeat(200))
        val s = CycleSnapshotBuilder.build(prediction(), today, false, l)
        assertTrue(s.notes.length <= 85)
    }
}
