package com.ona.miciclo.calendar.domain.model

import java.time.LocalDate

/**
 * Resultado de la predicción del ciclo.
 *
 * Contiene las fechas calculadas y un indicador de confianza
 * para que la UI muestre mensajes apropiados de incertidumbre.
 */
data class CyclePrediction(
    /** Fecha predicha para el inicio de la próxima menstruación */
    val proximaMenstruacion: LocalDate,

    /** Duración promedio calculada del ciclo en días */
    val duracionPromedio: Int,

    /** Inicio estimado de la ventana fértil */
    val inicioVentanaFertil: LocalDate,

    /** Fin estimado de la ventana fértil */
    val finVentanaFertil: LocalDate,

    /** Día estimado de ovulación */
    val diaOvulacion: LocalDate,

    /** Fase actual del ciclo */
    val faseActual: CyclePhase,

    /** Día actual dentro del ciclo (1-based) */
    val diaDelCiclo: Int,

    /**
     * Nivel de confianza de la predicción.
     * LOW: < 3 ciclos registrados — usando default de 28 días
     * MEDIUM: 3+ ciclos pero varianza > 5 días
     * HIGH: 3+ ciclos con varianza ≤ 5 días
     */
    val confianza: PredictionConfidence,

    /** Mensaje descriptivo para mostrar a la usuaria */
    val mensaje: String
)

/**
 * Fases del ciclo menstrual.
 */
enum class CyclePhase(val displayName: String) {
    MENSTRUATION("Menstruación"),
    FOLLICULAR("Fase Folicular"),
    FERTILE("Ventana Fértil"),
    LUTEAL("Fase Lútea"),
    UNKNOWN("Sin datos suficientes")
}

/**
 * Nivel de confianza en las predicciones.
 */
enum class PredictionConfidence(val displayName: String) {
    LOW("Baja — necesitamos más datos"),
    MEDIUM("Media — hay variabilidad en tus ciclos"),
    HIGH("Alta — tus ciclos son regulares")
}
