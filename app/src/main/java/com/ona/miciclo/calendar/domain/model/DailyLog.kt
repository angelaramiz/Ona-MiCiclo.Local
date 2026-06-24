package com.ona.miciclo.calendar.domain.model

import java.time.LocalDate

/**
 * Modelo de dominio para un registro diario.
 */
data class DailyLog(
    val id: Long = 0,
    val userId: String,
    val fecha: LocalDate,
    val nivelFlujo: FlowLevel = FlowLevel.NONE,
    val sintomasBasicos: List<String> = emptyList(),
    val temperaturaBasal: Double? = null,
    val mocoCervical: String? = null,
    val posicionCervical: String? = null,
    val resultadoTiraLh: String? = null,
    val notas: String? = null,
    val createdAt: Long = System.currentTimeMillis()
) {
    /** Valida que la temperatura basal esté en rango fisiológico */
    val isTemperaturaValida: Boolean
        get() = temperaturaBasal == null || (temperaturaBasal in 35.0..38.5)
}
