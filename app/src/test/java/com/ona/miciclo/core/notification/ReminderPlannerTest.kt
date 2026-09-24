package com.ona.miciclo.core.notification

import com.ona.miciclo.calendar.domain.model.CyclePhase
import com.ona.miciclo.calendar.domain.model.CyclePrediction
import com.ona.miciclo.calendar.domain.model.PredictionConfidence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * TDD A2: el planificador decide qué recordatorios vencen hoy.
 */
class ReminderPlannerTest {

    private val today = LocalDate.of(2026, 9, 24)

    private fun prediction(
        nextPeriod: LocalDate = LocalDate.of(2026, 10, 10),
        fertileStart: LocalDate = LocalDate.of(2026, 9, 19),
        fertileEnd: LocalDate = LocalDate.of(2026, 9, 25)
    ) = CyclePrediction(
        proximaMenstruacion = nextPeriod,
        duracionPromedio = 28,
        inicioVentanaFertil = fertileStart,
        finVentanaFertil = fertileEnd,
        diaOvulacion = LocalDate.of(2026, 9, 24),
        faseActual = CyclePhase.FERTILE,
        diaDelCiclo = 13,
        confianza = PredictionConfidence.LOW,
        mensaje = "msg"
    )

    private fun inputs(
        prediction: CyclePrediction? = prediction(),
        period: Boolean = true,
        fertile: Boolean = true,
        log: Boolean = true,
        lastLog: LocalDate? = today,
        notified: Set<String> = emptySet()
    ) = ReminderPlanner.Inputs(
        prediction = prediction,
        today = today,
        periodEnabled = period,
        fertileEnabled = fertile,
        logEnabled = log,
        lastLogDate = lastLog,
        alreadyNotified = notified
    )

    @Test
    fun `periodo en 2 dias avisa`() {
        // La ventana default (19-25 sep) también termina mañana → 2 avisos
        val r = ReminderPlanner.due(inputs(prediction = prediction(nextPeriod = today.plusDays(2))))
        assertEquals(2, r.size)
        val period = r.first { it.type == ReminderPlanner.TYPE_PERIOD }
        assertTrue(period.text.contains("2 días"))
        assertEquals("PERIOD_2026-09-26", period.key)
    }

    @Test
    fun `periodo manana avisa y periodo lejos no`() {
        val soon = ReminderPlanner.due(inputs(prediction = prediction(nextPeriod = today.plusDays(1))))
        assertTrue(soon.any { it.type == ReminderPlanner.TYPE_PERIOD && it.text.contains("mañana") })
        val far = ReminderPlanner.due(inputs(prediction = prediction(nextPeriod = today.plusDays(16))))
        assertTrue(far.none { it.type == ReminderPlanner.TYPE_PERIOD })
    }

    @Test
    fun `ventana fertil que empieza manana avisa`() {
        val r = ReminderPlanner.due(
            inputs(prediction = prediction(fertileStart = today.plusDays(1), fertileEnd = today.plusDays(7)))
        )
        assertTrue(r.any { it.type == ReminderPlanner.TYPE_FERTILE_START })
    }

    @Test
    fun `ventana fertil que termina manana avisa`() {
        // Estado E2E real: ventana 19-25 sep, hoy 24 → termina mañana
        val r = ReminderPlanner.due(inputs())
        assertTrue(r.any { it.type == ReminderPlanner.TYPE_FERTILE_END })
        assertTrue(r.none { it.type == ReminderPlanner.TYPE_FERTILE_START })
    }

    @Test
    fun `sin registro en 2 dias sugiere registrar`() {
        val r = ReminderPlanner.due(inputs(lastLog = today.minusDays(3)))
        assertTrue(r.any { it.type == ReminderPlanner.TYPE_LOG })
        assertEquals("LOG_2026-09-24", r.first { it.type == ReminderPlanner.TYPE_LOG }.key)
    }

    @Test
    fun `registro de hoy no sugiere nada`() {
        val r = ReminderPlanner.due(inputs(lastLog = today))
        assertTrue(r.none { it.type == ReminderPlanner.TYPE_LOG })
    }

    @Test
    fun `tipos desactivados no avisan aunque venzan`() {
        val r = ReminderPlanner.due(
            inputs(
                prediction = prediction(nextPeriod = today.plusDays(1)),
                period = false, fertile = false, log = false,
                lastLog = today.minusDays(9)
            )
        )
        assertTrue(r.isEmpty())
    }

    @Test
    fun `avisos ya notificados no se repiten`() {
        val first = ReminderPlanner.due(inputs())
        assertTrue(first.isNotEmpty())
        val keys = first.map { it.key }.toSet()
        val second = ReminderPlanner.due(inputs(notified = keys))
        assertTrue(second.isEmpty())
    }

    @Test
    fun `sin prediccion solo puede sugerir registro`() {
        val r = ReminderPlanner.due(inputs(prediction = null, lastLog = today.minusDays(5)))
        assertEquals(1, r.size)
        assertEquals(ReminderPlanner.TYPE_LOG, r[0].type)
    }
}
