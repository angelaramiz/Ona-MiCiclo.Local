package com.ona.miciclo.calendar.domain.model

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Ajuste sintotérmico de la predicción de calendario (A1).
 *
 * Objeto puro (sin Android): combina la ovulación estimada por calendario con la
 * evidencia registrada en los DailyLogs del ciclo actual.
 *
 * Prioridad de señales (de mayor a menor):
 * 1. Síntoma "ovulacion" confirmado (manual o por la pareja) → ovulación = esa fecha.
 * 2. Tira LH "positivo" → ovulación = día siguiente (el pico LH precede 12-36 h).
 * 3. Cambio térmico (3 temps ≥ +0.2 °C sobre el máximo de las 6 previas) →
 *    ovulación = último día bajo.
 * El moco "clara_de_huevo" solo aporta evidencia, no mueve fechas.
 *
 * Las señales fuera del rango plausible (día < 8 o fase lútea < 10 días) se ignoran.
 */
object SymptothermalAdjustment {

    private const val MIN_OVULATION_DAY = 8
    private const val MIN_LUTEAL_DAYS = 10
    private const val TEMP_SHIFT_DELTA = 0.2

    private val dateFmt = DateTimeFormatter.ofPattern("d 'de' MMMM", Locale("es"))

    data class Result(
        val ovulationDate: LocalDate,
        val fertileStart: LocalDate,
        val fertileEnd: LocalDate,
        val evidence: List<String>
    )

    fun adjust(
        calendarOvulation: LocalDate,
        cycleStart: LocalDate,
        cycleLength: Int,
        logs: List<DailyLog>,
        today: LocalDate = LocalDate.now()
    ): Result {
        val unchanged = Result(
            ovulationDate = calendarOvulation,
            fertileStart = calendarOvulation.minusDays(5),
            fertileEnd = calendarOvulation.plusDays(1),
            evidence = emptyList()
        )
        val minDate = cycleStart.plusDays((MIN_OVULATION_DAY - 1).toLong())
        val maxDate = cycleStart.plusDays((cycleLength - MIN_LUTEAL_DAYS).toLong())
        val cycleLogs = logs
            .filter { it.fecha >= cycleStart && it.fecha <= today }
            .sortedBy { it.fecha }
        if (cycleLogs.isEmpty()) return unchanged

        // 1. Ovulación confirmada por síntoma
        cycleLogs.firstOrNull {
            PartnerSuggestions.OVULATION_SYMPTOM in it.sintomasBasicos &&
                it.fecha in minDate..maxDate
        }?.let {
            return buildResult(it.fecha, listOf("Ovulación confirmada el ${it.fecha.format(dateFmt)}"))
        }

        // 2. Pico LH → ovulación al día siguiente
        cycleLogs.firstOrNull {
            it.resultadoTiraLh?.lowercase() == "positivo" &&
                it.fecha.plusDays(1) in minDate..maxDate
        }?.let {
            val ov = it.fecha.plusDays(1)
            return buildResult(ov, listOf("Tira LH positiva el ${it.fecha.format(dateFmt)}"))
        }

        // 3. Cambio térmico → ovulación = último día bajo
        detectThermalShift(cycleLogs, minDate, maxDate)?.let { (ovDate, shiftDate) ->
            return buildResult(
                ovDate,
                listOf("Cambio térmico el ${shiftDate.format(dateFmt)} (subida ≥ $TEMP_SHIFT_DELTA °C)")
            )
        }

        // 4. Moco fértil: solo evidencia
        val mucusEvidence = cycleLogs
            .filter { it.mocoCervical == "clara_de_huevo" }
            .map { "Moco fértil el ${it.fecha.format(dateFmt)}" }
        if (mucusEvidence.isNotEmpty()) {
            return unchanged.copy(evidence = mucusEvidence)
        }

        return unchanged
    }

    private fun buildResult(ovulation: LocalDate, evidence: List<String>): Result = Result(
        ovulationDate = ovulation,
        fertileStart = ovulation.minusDays(5),
        fertileEnd = ovulation.plusDays(1),
        evidence = evidence
    )

    /**
     * Detecta cambio térmico sostenido: 3 registros consecutivos con temperatura
     * ≥ (máximo de hasta 6 previas) + 0.2 °C. Devuelve (ovulación, fecha del cambio).
     */
    private fun detectThermalShift(
        logs: List<DailyLog>,
        minDate: LocalDate,
        maxDate: LocalDate
    ): Pair<LocalDate, LocalDate>? {
        val withTemp = logs.filter { it.temperaturaBasal != null }
        for (s in withTemp.indices) {
            if (s + 2 >= withTemp.size) break
            val prev = withTemp.take(s).takeLast(6).mapNotNull { it.temperaturaBasal }
            if (prev.isEmpty()) continue
            val coverline = prev.max()
            val trio = withTemp.subList(s, s + 3)
            if (trio.all { (it.temperaturaBasal ?: 0.0) >= coverline + TEMP_SHIFT_DELTA }) {
                val ovDate = if (s > 0) withTemp[s - 1].fecha else trio.first().fecha
                if (ovDate in minDate..maxDate) {
                    return ovDate to trio.first().fecha
                }
            }
        }
        return null
    }
}
