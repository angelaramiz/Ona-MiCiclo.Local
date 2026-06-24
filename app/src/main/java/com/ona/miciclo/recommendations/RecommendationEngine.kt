package com.ona.miciclo.recommendations

import com.ona.miciclo.calendar.domain.model.CycleRecord
import com.ona.miciclo.calendar.domain.model.DailyLog
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Motor de Recomendaciones Clínicas y Sintotérmicas de Ona.
 */
@Singleton
class RecommendationEngine @Inject constructor() {

    data class RecommendationResult(
        val proximaMenstruacion: LocalDate,
        val ventanaFertilInicio: LocalDate,
        val ventanaFertilFin: LocalDate,
        val mensajeEducativo: String,
        val descargoLegal: String = "Aviso: Ona es una herramienta educativa de autoconocimiento. No debe usarse como método anticonceptivo o diagnóstico médico profesional."
    )

    /**
     * Calcula la predicción combinando reglas clínicas deterministas (Sintotérmicas)
     * y adaptándolas al objetivo actual de la usuaria.
     */
    fun calculateRecommendations(
        objetivo: String,
        ciclos: List<CycleRecord>,
        logsDiarios: List<DailyLog>
    ): RecommendationResult {
        // Fallback a 28 días
        val duracionCicloPromedio = if (ciclos.isNotEmpty()) {
            ciclos.mapNotNull { it.duracionCiclo }.average().toInt()
        } else {
            28
        }

        val ultimaMenstruacion = logsDiarios
            .filter { it.nivelFlujo != com.ona.miciclo.calendar.domain.model.FlowLevel.NONE }
            .map { it.fecha }
            .maxOrNull() ?: LocalDate.now().minusDays(14)

        var proximaMenstruacion = ultimaMenstruacion.plusDays(duracionCicloPromedio.toLong())
        var ovulacionEstimada = proximaMenstruacion.minusDays(14)

        // ── Ajuste Sintotérmico basado en observaciones clínicas ──
        // 1. Detección de moco tipo "Clara de Huevo" (más fértil)
        val mocoFertilLog = logsDiarios.find { it.mocoCervical == "clara_de_huevo" }
        if (mocoFertilLog != null) {
            ovulacionEstimada = mocoFertilLog.fecha
        }

        // 2. Detección de tira reactiva LH Positiva
        val lhPositivoLog = logsDiarios.find { it.resultadoTiraLh == "positivo" }
        if (lhPositivoLog != null) {
            ovulacionEstimada = lhPositivoLog.fecha.plusDays(1) // Ovulación ocurre 24-36h después del pico de LH
        }

        // 3. Ventana Fértil
        var ventanaFertilInicio = ovulacionEstimada.minusDays(5)
        var ventanaFertilFin = ovulacionEstimada.plusDays(1)

        // ── Ajustar según el Objetivo ──
        val mensajeEducativo = when (objetivo.lowercase()) {
            "evitar" -> {
                // Aumentar margen de seguridad
                ventanaFertilInicio = ventanaFertilInicio.minusDays(2)
                ventanaFertilFin = ventanaFertilFin.plusDays(1)
                "Objetivo: Evitar embarazo. Tus días fértiles estimados se han extendido preventivamente. Evita las relaciones sin protección durante esta ventana."
            }
            "concebir" -> {
                "Objetivo: Concebir. Se ha detectado la ventana ideal de concepción basada en tus observaciones sintotérmicas de flujo y hormona LH."
            }
            else -> {
                "Historial de registros normal. Mantener observaciones diarias para seguir puliendo la precisión."
            }
        }

        return RecommendationResult(
            proximaMenstruacion = proximaMenstruacion,
            ventanaFertilInicio = ventanaFertilInicio,
            ventanaFertilFin = ventanaFertilFin,
            mensajeEducativo = mensajeEducativo
        )
    }
}
