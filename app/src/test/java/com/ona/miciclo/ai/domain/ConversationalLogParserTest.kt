package com.ona.miciclo.ai.domain

import com.ona.miciclo.calendar.domain.model.CyclePhase
import com.ona.miciclo.calendar.domain.model.CyclePrediction
import com.ona.miciclo.calendar.domain.model.DailyLog
import com.ona.miciclo.calendar.domain.model.PredictionConfidence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * TDD A5: parser conversacional + proveedor de insights.
 */
class ConversationalLogParserTest {

    @Test
    fun `dolor de cabeza y colicos`() {
        val p = ConversationalLogParser.parse("registra dolor de cabeza y cólicos")
        assertTrue(p.matched)
        assertTrue(p.symptoms.contains("dolor_cabeza"))
        assertTrue(p.symptoms.contains("calambres"))
    }

    @Test
    fun `flujo y temperatura`() {
        val p = ConversationalLogParser.parse("tengo manchado leve y temperatura 36.6")
        assertTrue(p.matched)
        assertEquals(com.ona.miciclo.calendar.domain.model.FlowLevel.LIGHT, p.flow)
        assertEquals(36.6, p.basalTemp!!, 0.001)
    }

    @Test
    fun `temperatura fuera de rango se ignora`() {
        val p = ConversationalLogParser.parse("temperatura 41.2")
        assertEquals(null, p.basalTemp)
        assertFalse(p.matched)
    }

    @Test
    fun `moco clara de huevo`() {
        val p = ConversationalLogParser.parse("moco clara de huevo hoy")
        assertEquals("clara_de_huevo", p.mucus)
    }

    @Test
    fun `inicio de periodo`() {
        val p = ConversationalLogParser.parse("me llegó el periodo hoy")
        assertTrue(p.isPeriodStart)
        assertTrue(p.matched)
    }

    @Test
    fun `texto sin intencion no matchea`() {
        val p = ConversationalLogParser.parse("hola, ¿cómo estás?")
        assertFalse(p.matched)
    }

    @Test
    fun `sin falsos positivos por subcadenas`() {
        assertFalse(ConversationalLogParser.parse("descansan muy bien").matched)
        assertFalse(ConversationalLogParser.parse("sospecho que lloverá").matched)
    }

    @Test
    fun `cansancio real si matchea`() {
        val p = ConversationalLogParser.parse("estoy muy cansada hoy")
        assertTrue(p.symptoms.contains("fatiga"))
    }

    @Test
    fun `sintomas premenstruales nuevos`() {
        assertTrue(ConversationalLogParser.parse("estoy irritable").symptoms.contains("irritabilidad"))
        assertTrue(ConversationalLogParser.parse("lloro por todo").symptoms.contains("llanto_facil"))
        assertTrue(ConversationalLogParser.parse("con ansiedad").symptoms.contains("ansiedad"))
        assertTrue(ConversationalLogParser.parse("estoy estreñida").symptoms.contains("estrenimiento"))
        assertTrue(ConversationalLogParser.parse("tengo diarrea").symptoms.contains("diarrea"))
    }

    @Test
    fun `merge preserva y agrega`() {
        val existing = DailyLog(
            userId = "h",
            fecha = LocalDate.of(2026, 9, 24),
            sintomasBasicos = listOf("calambres"),
            temperaturaBasal = 36.5
        )
        val parsed = ConversationalLogParser.parse("dolor de cabeza y temperatura 36.7")
        val merged = ConversationalLogParser.merge(existing, "h", LocalDate.of(2026, 9, 24), parsed)
        assertEquals(listOf("calambres", "dolor_cabeza"), merged.sintomasBasicos)
        assertEquals(36.7, merged.temperaturaBasal!!, 0.001)
    }

    @Test
    fun `describe resume para la burbuja`() {
        val p = ConversationalLogParser.parse("cólicos y flujo abundante")
        val desc = ConversationalLogParser.describe(p) { if (it == "calambres") "Calambres" else it }
        assertTrue(desc.contains("Calambres"))
        assertTrue(desc.contains("flujo"))
    }
}

class CycleInsightProviderTest {

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

    private fun log(date: LocalDate, symptoms: List<String> = emptyList()) = DailyLog(
        userId = "h", fecha = date, sintomasBasicos = symptoms
    )

    @Test
    fun `ventana fertil activa genera insight`() {
        val list = CycleInsightProvider.insights(prediction(), emptyList(), today)
        assertTrue(list.any { it.contains("fértil") })
    }

    @Test
    fun `periodo en 3 dias genera insight`() {
        val list = CycleInsightProvider.insights(
            prediction(nextPeriod = today.plusDays(3)), emptyList(), today
        )
        assertTrue(list.any { it.contains("periodo") && it.contains("3") })
    }

    @Test
    fun `sintoma repetido en 7 dias genera patron`() {
        val logs = listOf(
            log(today.minusDays(1), listOf("calambres")),
            log(today.minusDays(2), listOf("calambres", "fatiga")),
            log(today.minusDays(5), listOf("calambres"))
        )
        val list = CycleInsightProvider.insights(prediction(), logs, today)
        assertTrue(list.any { it.contains("alambres") && it.contains("3") })
    }

    @Test
    fun `sin registros varios dias sugiere registrar`() {
        val list = CycleInsightProvider.insights(
            prediction(), listOf(log(today.minusDays(6))), today
        )
        assertTrue(list.any { it.contains("sin registrar") })
    }

    @Test
    fun `sin prediccion devuelve lista vacia`() {
        assertTrue(CycleInsightProvider.insights(null, emptyList(), today).isEmpty())
    }
}
