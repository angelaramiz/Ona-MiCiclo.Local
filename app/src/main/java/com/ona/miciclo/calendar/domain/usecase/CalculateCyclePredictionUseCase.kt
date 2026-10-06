package com.ona.miciclo.calendar.domain.usecase

import com.ona.miciclo.calendar.domain.model.CyclePhase
import com.ona.miciclo.calendar.domain.model.CyclePrediction
import com.ona.miciclo.calendar.domain.model.CycleRecord
import com.ona.miciclo.calendar.domain.model.PredictionConfidence
import com.ona.miciclo.calendar.domain.repository.CycleRepository
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import javax.inject.Inject

/**
 * LÓGICA DE CÁLCULO DE CICLO — FASE 1
 *
 * ═══════════════════════════════════════════════════════════════
 * REGLAS DETERMINISTAS (sin IA):
 *
 * 1. Si hay < 3 ciclos registrados → usar duración default (28 días)
 * 2. Si hay >= 3 ciclos → promedio de los últimos 3 ciclos completados
 * 3. Predicción de próxima menstruación = última fecha inicio + duración promedio
 * 4. Ventana fértil estimada = días 10-16 del ciclo (regla básica del calendario)
 * 5. MODO CONSERVADOR: Si la desviación estándar > 5 días,
 *    mostrar mensaje de incertidumbre y ampliar ventana fértil
 *
 * LIMITACIONES EXPLÍCITAS DE FASE 1:
 * - Esta NO es una herramienta médica certificada
 * - La predicción se basa únicamente en el método del calendario
 * - No se consideran síntomas, temperatura basal, ni moco cervical
 * - La predicción mejora con más datos acumulados
 * - Fase 2+ mejorará con método sintotérmico + IA local
 * ═══════════════════════════════════════════════════════════════
 */
class CalculateCyclePredictionUseCase @Inject constructor(
    private val cycleRepository: CycleRepository
) {
    companion object {
        /** Duración estándar cuando no hay datos suficientes */
        const val DEFAULT_CYCLE_LENGTH = 28

        /**
         * REGLA DE VENTANA FÉRTIL (método del calendario, dinámica por duración):
         * la ovulación se estima ~14 días ANTES del siguiente periodo
         * (la fase lútea es casi constante; lo que varía es la folicular).
         * Ventana: 5 días antes de ovular + cola peligrosa de 36 h después
         * ([SymptothermalAdjustment.FERTILE_TAIL_DAYS_AFTER_OVULATION]).
         *
         * Para un ciclo de 28 días:
         * - Ovulación estimada: día 14
         * - Ventana fértil: días 9-16
         *
         * NOTA: Esta es una aproximación. El método del calendario tiene una tasa
         * de fallo del ~12-25% como anticonceptivo. Esto DEBE comunicarse claramente.
         */
    }

    /**
     * Calcula la predicción del ciclo para un usuario.
     *
     * @param userId ID del usuario autenticado
     * @param defaultCycleLength Duración por defecto configurada por la usuaria
     * @return CyclePrediction con toda la información calculada
     */
    suspend operator fun invoke(
        userId: String,
        defaultCycleLength: Int = DEFAULT_CYCLE_LENGTH
    ): CyclePrediction? {
        // Obtener y filtrar registros válidos (solo aquellos con fecha válida)
        val allRecords = cycleRepository.getLastCycleRecords(userId, 100) // Obtener más para filtrar
        val validRecords = allRecords.filter {
            it.fechaInicioMenstruacion != null && it.fechaInicioMenstruacion.isAfter(LocalDate.of(1900, 1, 1))
        }

        if (validRecords.isEmpty()) {
            return null // Sin registros válidos
        }

        val latestRecord = validRecords.first()
        val today = LocalDate.now()

        // ── Paso 1: Determinar duración del ciclo ──
        // Se pasa todo el historial válido: el estimador usa hasta los últimos
        // 6 ciclos y se reajusta con cada registro nuevo.
        val (cycleLength, confidence) = calculateCycleLength(validRecords, defaultCycleLength)

        // ── Paso 2: Calcular día actual del ciclo ──
        val dayOfCycle = ChronoUnit.DAYS.between(latestRecord.fechaInicioMenstruacion, today).toInt() + 1

        // ── Paso 3: Predecir próxima menstruación ──
        val nextPeriodStart = latestRecord.fechaInicioMenstruacion.plusDays(cycleLength.toLong())

        // ── Paso 4: Calcular ventana fértil (ajustada al ciclo real) ──
        // La ovulación se estima ~14 días ANTES del siguiente periodo
        val estimatedOvulation = nextPeriodStart.minusDays(14)
        val fertileStart = estimatedOvulation.minusDays(5) // 5 días antes de ovulación
        // Cola peligrosa de 36 h tras ovular (días completos a granularidad de día)
        val fertileEnd = estimatedOvulation.plusDays(
            com.ona.miciclo.calendar.domain.model.SymptothermalAdjustment
                .FERTILE_TAIL_DAYS_AFTER_OVULATION.toLong()
        )

        // ── Paso 5: Determinar fase actual ──
        val currentPhase = determinePhase(
            dayOfCycle = dayOfCycle,
            bleedingDuration = latestRecord.duracionSangrado,
            cycleLength = cycleLength,
            today = today,
            fertileStart = fertileStart,
            fertileEnd = fertileEnd
        )

        // ── Paso 6: Generar mensaje contextual ──
        val message = generateMessage(currentPhase, confidence, dayOfCycle, cycleLength)

        val calendarPrediction = CyclePrediction(
            proximaMenstruacion = nextPeriodStart,
            duracionPromedio = cycleLength,
            inicioVentanaFertil = fertileStart,
            finVentanaFertil = fertileEnd,
            diaOvulacion = estimatedOvulation,
            faseActual = currentPhase,
            diaDelCiclo = dayOfCycle,
            confianza = confidence,
            mensaje = message
        )

        // ── Paso 7 (A1): Ajuste sintotérmico con los registros del ciclo actual ──
        // Si la usuaria registró evidencia de ovulación (síntoma, tira LH, cambio
        // térmico), las fechas fértiles se recalculan desde la ovulación detectada.
        // Sin evidencia, se devuelve la predicción de calendario tal cual.
        return try {
            val cycleLogs = cycleRepository.getAllDailyLogsSync(userId)
                .filter { it.fecha >= latestRecord.fechaInicioMenstruacion }
            val adjusted = com.ona.miciclo.calendar.domain.model.SymptothermalAdjustment.adjust(
                calendarOvulation = estimatedOvulation,
                cycleStart = latestRecord.fechaInicioMenstruacion,
                cycleLength = cycleLength,
                logs = cycleLogs
            )
            if (adjusted.evidence.isEmpty()) {
                calendarPrediction
            } else {
                // La fase puede cambiar si la ventana fértil se movió: recalcular
                val newPhase = determinePhase(
                    dayOfCycle = dayOfCycle,
                    bleedingDuration = latestRecord.duracionSangrado,
                    cycleLength = cycleLength,
                    today = today,
                    fertileStart = adjusted.fertileStart,
                    fertileEnd = adjusted.fertileEnd
                )
                calendarPrediction.copy(
                    inicioVentanaFertil = adjusted.fertileStart,
                    finVentanaFertil = adjusted.fertileEnd,
                    diaOvulacion = adjusted.ovulationDate,
                    faseActual = newPhase,
                    mensaje = generateMessage(newPhase, confidence, dayOfCycle, cycleLength) +
                        "\n🔬 " + adjusted.evidence.joinToString(" · ")
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            calendarPrediction // Ante cualquier error, no romper la predicción base
        }
    }

    /**
     * Duración del ciclo recalibrada con cada registro (promedio móvil de hasta
     * los últimos 6 ciclos válidos vía [CycleLengthEstimator]).
     */
    private fun calculateCycleLength(
        records: List<CycleRecord>,
        defaultLength: Int
    ): Pair<Int, PredictionConfidence> {
        val starts = records.mapNotNull { it.fechaInicioMenstruacion }
        val estimate = com.ona.miciclo.calendar.domain.model.CycleLengthEstimator.estimate(
            starts,
            defaultLength
        )
        return estimate.length to estimate.confidence
    }

    /**
     * Determina la fase actual del ciclo menstrual.
     */
    private fun determinePhase(
        dayOfCycle: Int,
        bleedingDuration: Int,
        cycleLength: Int,
        today: LocalDate,
        fertileStart: LocalDate,
        fertileEnd: LocalDate
    ): CyclePhase {
        return when {
            dayOfCycle < 1 || dayOfCycle > cycleLength + 7 -> CyclePhase.UNKNOWN
            dayOfCycle <= bleedingDuration -> CyclePhase.MENSTRUATION
            today in fertileStart..fertileEnd -> CyclePhase.FERTILE
            dayOfCycle <= (cycleLength / 2) -> CyclePhase.FOLLICULAR
            else -> CyclePhase.LUTEAL
        }
    }

    /**
     * Genera un mensaje contextual basado en la fase y confianza.
     */
    private fun generateMessage(
        phase: CyclePhase,
        confidence: PredictionConfidence,
        dayOfCycle: Int,
        cycleLength: Int
    ): String {
        val phaseMessage = when (phase) {
            CyclePhase.MENSTRUATION -> "Estás en tu período. Cuídate 💕"
            CyclePhase.FOLLICULAR -> "Fase folicular — tu cuerpo se está preparando"
            CyclePhase.FERTILE -> "⚠️ Ventana fértil estimada — mayor probabilidad de embarazo"
            CyclePhase.LUTEAL -> "Fase lútea — tu cuerpo se prepara para el siguiente ciclo"
            CyclePhase.UNKNOWN -> "Necesitamos más datos para predecir tu ciclo"
        }

        val confidenceNote = when (confidence) {
            PredictionConfidence.LOW ->
                "\n📊 Predicción basada en valores estándar. Registra al menos 3 ciclos para mejorar la precisión."
            PredictionConfidence.MEDIUM ->
                "\n📊 Tus ciclos muestran variabilidad. Las fechas son aproximadas."
            PredictionConfidence.HIGH -> ""
        }

        return "$phaseMessage$confidenceNote"
    }
}
