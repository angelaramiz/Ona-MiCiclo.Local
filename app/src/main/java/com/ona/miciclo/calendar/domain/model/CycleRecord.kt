package com.ona.miciclo.calendar.domain.model

import java.time.LocalDate

/**
 * Modelo de dominio para un registro de ciclo menstrual.
 * Libre de dependencias de Room — puro Kotlin.
 */
data class CycleRecord(
    val id: Long = 0,
    val userId: String,
    val fechaInicioMenstruacion: LocalDate,
    val duracionSangrado: Int = 5,
    val duracionCiclo: Int = 28,
    val metodoRegistrado: String = "calendario",
    val objetivoUsuario: String = "conocimiento",
    val cicloConfirmado: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    /** Fecha estimada de fin de sangrado */
    val fechaFinSangrado: LocalDate
        get() = fechaInicioMenstruacion.plusDays(duracionSangrado.toLong() - 1)

    /** Fecha estimada de inicio del siguiente ciclo */
    val fechaProximoInicio: LocalDate
        get() = fechaInicioMenstruacion.plusDays(duracionCiclo.toLong())
}
