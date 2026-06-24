package com.ona.miciclo.calendar.domain.usecase

import com.ona.miciclo.calendar.domain.model.CyclePhase
import com.ona.miciclo.calendar.domain.model.CyclePrediction
import com.ona.miciclo.calendar.domain.model.CycleRecord
import com.ona.miciclo.calendar.domain.model.PredictionConfidence
import com.ona.miciclo.calendar.domain.repository.CycleRepository
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import kotlin.math.roundToInt
import kotlin.math.sqrt

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

        /** Mínimo de ciclos necesarios para usar promedios reales */
        const val MIN_CYCLES_FOR_AVERAGE = 3

        /** Umbral de desviación estándar para activar modo conservador */
        const val CONSERVATIVE_STD_DEV_THRESHOLD = 5.0

        /**
         * REGLA BÁSICA DE VENTANA FÉRTIL (método del calendario):
         * La ovulación típicamente ocurre ~14 días antes del siguiente periodo.
         * La ventana fértil se estima 5 días antes y 1 día después de la ovulación.
         * 
         * Para un ciclo de 28 días:
         * - Ovulación estimada: día 14
         * - Ventana fértil: días 10-16
         *
         * NOTA: Esta es una aproximación. El método del calendario tiene una tasa
         * de fallo del ~12-25% como anticonceptivo. Esto DEBE comunicarse claramente.
         */
        const val FERTILE_WINDOW_START_OFFSET = 10 // Día del ciclo donde comienza la ventana
        const val FERTILE_WINDOW_END_OFFSET = 16   // Día del ciclo donde termina la ventana
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
        val latestRecord = cycleRepository.getLatestCycleRecord(userId)
            ?: return null // Sin registros, no podemos predecir nada

        val lastRecords = cycleRepository.getLastCycleRecords(userId, MIN_CYCLES_FOR_AVERAGE)
        val today = LocalDate.now()

        // ── Paso 1: Determinar duración del ciclo ──
        val (cycleLength, confidence) = calculateCycleLength(lastRecords, defaultCycleLength)

        // ── Paso 2: Calcular día actual del ciclo ──
        val dayOfCycle = ChronoUnit.DAYS.between(latestRecord.fechaInicioMenstruacion, today).toInt() + 1

        // ── Paso 3: Predecir próxima menstruación ──
        val nextPeriodStart = latestRecord.fechaInicioMenstruacion.plusDays(cycleLength.toLong())

        // ── Paso 4: Calcular ventana fértil (ajustada al ciclo real) ──
        // La ovulación se estima ~14 días ANTES del siguiente periodo
        val estimatedOvulation = nextPeriodStart.minusDays(14)
        val fertileStart = estimatedOvulation.minusDays(5) // 5 días antes de ovulación
        val fertileEnd = estimatedOvulation.plusDays(1)     // 1 día después de ovulación

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

        return CyclePrediction(
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
    }

    /**
     * Calcula la duración del ciclo basándose en registros históricos.
     *
     * LÓGICA:
     * - < 3 registros: usa default (28 días o configurado por la usuaria)
     * - ≥ 3 registros: promedio de los últimos 3 ciclos
     * - Si la varianza es alta (σ > 5 días): marca confianza MEDIUM
     */
    private fun calculateCycleLength(
        records: List<CycleRecord>,
        defaultLength: Int
    ): Pair<Int, PredictionConfidence> {
        if (records.size < MIN_CYCLES_FOR_AVERAGE) {
            // No hay suficientes datos — usar default con confianza baja
            return Pair(defaultLength, PredictionConfidence.LOW)
        }

        // Calcular duraciones reales entre ciclos consecutivos
        val durations = records.zipWithNext { newer, older ->
            // Los registros vienen ordenados DESC, así que newer.fecha > older.fecha
            ChronoUnit.DAYS.between(older.fechaInicioMenstruacion, newer.fechaInicioMenstruacion).toInt()
        }.filter { it in 21..45 } // Filtrar duraciones fisiológicamente plausibles

        if (durations.isEmpty()) {
            return Pair(defaultLength, PredictionConfidence.LOW)
        }

        val average = durations.average().roundToInt()

        // Calcular desviación estándar para evaluar regularidad
        val stdDev = calculateStdDev(durations)

        val confidence = when {
            stdDev > CONSERVATIVE_STD_DEV_THRESHOLD -> PredictionConfidence.MEDIUM
            else -> PredictionConfidence.HIGH
        }

        return Pair(average, confidence)
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

    /**
     * Calcula la desviación estándar de una lista de valores.
     */
    private fun calculateStdDev(values: List<Int>): Double {
        if (values.size < 2) return 0.0
        val mean = values.average()
        val variance = values.map { (it - mean) * (it - mean) }.average()
        return sqrt(variance)
    }
}
