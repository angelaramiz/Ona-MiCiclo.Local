package com.ona.miciclo.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate

/**
 * Registro diario de síntomas, flujo y observaciones.
 *
 * Cada día puede tener un solo registro por usuario.
 * Los campos son mayormente opcionales para no presionar a la usuaria
 * a completar todo cada día — la recolección progresiva es clave.
 *
 * Preparado para Fase 2:
 * - temperaturaBasal: campo opcional que se activará con el método sintotérmico
 * - sintomasBasicos: JSON array extensible sin cambiar esquema de BD
 */
@Entity(
    tableName = "daily_logs",
    indices = [
        Index(value = ["user_id"]),
        Index(value = ["user_id", "fecha"], unique = true)
    ]
)
data class DailyLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "user_id")
    val userId: String,

    /** Fecha del registro */
    @ColumnInfo(name = "fecha")
    val fecha: LocalDate,

    /**
     * Nivel de flujo menstrual.
     * Valores: "ninguno", "ligero", "moderado", "abundante"
     * Null si no se registró ese día.
     */
    @ColumnInfo(name = "nivel_flujo")
    val nivelFlujo: String? = null,

    /**
     * Síntomas básicos como JSON array.
     * Ejemplo: ["dolor_cabeza", "calambres", "fatiga", "sensibilidad_pechos"]
     *
     * Se usa JSON string en lugar de tabla relacional para:
     * 1. Evitar complejidad de esquema en Fase 1
     * 2. Facilitar la extensión de síntomas sin migraciones
     * 3. La búsqueda por síntomas individuales no es necesaria en Fase 1
     */
    @ColumnInfo(name = "sintomas_basicos")
    val sintomasBasicos: String? = null,

    /**
     * Temperatura basal en grados Celsius.
     * CAMPO PREPARADO PARA FASE 2 — Método Sintotérmico.
     * En Fase 1 se muestra como campo opcional.
     * Rango válido: 35.0 - 38.5 °C
     */
    @ColumnInfo(name = "temperatura_basal")
    val temperaturaBasal: Double? = null,

    /** Notas de texto libre de la usuaria */
    @ColumnInfo(name = "notas")
    val notas: String? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)
