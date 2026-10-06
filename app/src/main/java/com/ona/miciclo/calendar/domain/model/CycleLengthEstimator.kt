package com.ona.miciclo.calendar.domain.model

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Estimador puro (sin Android) de la duración del ciclo.
 *
 * Recalibra con cada ciclo registrado: promedia las duraciones consecutivas
 * más recientes (hasta [MAX_CYCLES_FOR_AVERAGE]) en vez de anclarse a un valor
 * fijo. Las duraciones fisiológicamente implausibles (olvidos de registro,
 * datos corruptos) se descartan antes de promediar.
 */
object CycleLengthEstimator {

    /** Mínimo de duraciones para dejar el default y usar el promedio real. */
    const val MIN_CYCLES_FOR_AVERAGE = 3

    /** Tope de historial: más muestras = promedio más preciso. */
    const val MAX_CYCLES_FOR_AVERAGE = 6

    /** Rango fisiológicamente plausible (días) para una duración entre reglas. */
    const val MIN_PLAUSIBLE_LENGTH = 21
    const val MAX_PLAUSIBLE_LENGTH = 45

    /** Umbral de desviación estándar para marcar confianza media. */
    const val CONSERVATIVE_STD_DEV_THRESHOLD = 5.0

    data class Estimate(val length: Int, val confidence: PredictionConfidence)

    /**
     * @param periodStartsNewestFirst inicios de periodo, del más reciente al más antiguo.
     * @param defaultLength duración configurada o estándar (28) si no hay datos suficientes.
     */
    fun estimate(periodStartsNewestFirst: List<LocalDate>, defaultLength: Int): Estimate {
        val durations = periodStartsNewestFirst
            .zipWithNext { newer, older -> ChronoUnit.DAYS.between(older, newer).toInt() }
            .filter { it in MIN_PLAUSIBLE_LENGTH..MAX_PLAUSIBLE_LENGTH }
            .take(MAX_CYCLES_FOR_AVERAGE)

        if (durations.size < MIN_CYCLES_FOR_AVERAGE) {
            return Estimate(defaultLength, PredictionConfidence.LOW)
        }

        val average = durations.average().roundToInt()
        val mean = durations.average()
        val stdDev = sqrt(durations.map { (it - mean) * (it - mean) }.average())
        val confidence = if (stdDev > CONSERVATIVE_STD_DEV_THRESHOLD) {
            PredictionConfidence.MEDIUM
        } else {
            PredictionConfidence.HIGH
        }
        return Estimate(average, confidence)
    }
}
