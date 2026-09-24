package com.ona.miciclo.settings.domain.report

import com.ona.miciclo.calendar.domain.model.CycleRecord
import com.ona.miciclo.calendar.domain.model.DailyLog
import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * Informe médico (A4): estructura pura con el resumen del ciclo para el doctor.
 *
 * Sin notas personales (privacidad): solo estadísticas, tabla de periodos y
 * frecuencia de síntomas. El render a PDF vive en [MedicalReportPdf].
 */
data class PeriodRow(
    val start: LocalDate,
    val cycleLength: Int,
    val bleedingDays: Int,
    val confirmed: Boolean
)

data class SymptomCount(
    val key: String,
    val label: String,
    val count: Int
)

data class MedicalReport(
    val generatedOn: LocalDate,
    val totalCycles: Int,
    val avgCycleLength: Int,
    val avgBleeding: Int,
    val lastPeriodStart: LocalDate?,
    val nextPeriodEstimate: LocalDate?,
    val recentPeriods: List<PeriodRow>,
    val totalLogs: Int,
    val topSymptoms: List<SymptomCount>
)

object MedicalReportBuilder {

    fun build(
        periods: List<CycleRecord>,
        logs: List<DailyLog>,
        nextPeriod: LocalDate?,
        today: LocalDate = LocalDate.now(),
        symptomLabel: (String) -> String = { it }
    ): MedicalReport {
        val ordered = periods.sortedByDescending { it.fechaInicioMenstruacion }
        val confirmedLens = ordered
            .filter { it.cicloConfirmado }
            .mapNotNull {
                val len = java.time.temporal.ChronoUnit.DAYS.between(
                    it.fechaInicioMenstruacion,
                    it.fechaProximoInicio
                ).toInt()
                len.takeIf { l -> l in 21..45 }
            }
        val avgLen = if (confirmedLens.isNotEmpty()) confirmedLens.average().roundToInt()
        else ordered.firstOrNull()?.duracionCiclo ?: 28
        val avgBleed = if (ordered.isNotEmpty()) ordered.map { it.duracionSangrado }.average().roundToInt() else 5

        val symptomCounts = logs.flatMap { it.sintomasBasicos }
            .groupingBy { it }.eachCount()
            .entries.sortedByDescending { it.value }
            .take(8)
            .map { SymptomCount(it.key, symptomLabel(it.key), it.value) }

        return MedicalReport(
            generatedOn = today,
            totalCycles = ordered.size,
            avgCycleLength = avgLen,
            avgBleeding = avgBleed,
            lastPeriodStart = ordered.firstOrNull()?.fechaInicioMenstruacion,
            nextPeriodEstimate = nextPeriod,
            recentPeriods = ordered.take(6).map {
                PeriodRow(it.fechaInicioMenstruacion, it.duracionCiclo, it.duracionSangrado, it.cicloConfirmado)
            },
            totalLogs = logs.size,
            topSymptoms = symptomCounts
        )
    }
}
