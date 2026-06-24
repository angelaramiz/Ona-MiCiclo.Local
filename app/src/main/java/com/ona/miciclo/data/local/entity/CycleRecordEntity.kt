package com.ona.miciclo.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate

/**
 * Entidad principal de registro de ciclo menstrual.
 *
 * Cada registro representa un ciclo completo desde el primer día de sangrado
 * hasta el día antes del siguiente sangrado.
 *
 * Diseñada para recolección progresiva:
 * - Campos obligatorios: userId, fechaInicioMenstruacion
 * - Campos opcionales: duracionSangrado, duracionCiclo (se calculan/confirman con más datos)
 * - Campos de contexto: metodoRegistrado, objetivoUsuario
 */
@Entity(
    tableName = "cycle_records",
    indices = [
        Index(value = ["user_id"]),
        Index(value = ["user_id", "fecha_inicio_menstruacion"], unique = true)
    ]
)
data class CycleRecordEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "user_id")
    val userId: String,

    /** Primer día de sangrado — marca el inicio del ciclo */
    @ColumnInfo(name = "fecha_inicio_menstruacion")
    val fechaInicioMenstruacion: LocalDate,

    /** Días de sangrado activo (1-10 típicamente) */
    @ColumnInfo(name = "duracion_sangrado")
    val duracionSangrado: Int = 5,

    /** Duración total del ciclo en días (21-45 rango normal, default 28) */
    @ColumnInfo(name = "duracion_ciclo")
    val duracionCiclo: Int = 28,

    /** Método de seguimiento: "calendario", "sintotermico", "app_prediccion" */
    @ColumnInfo(name = "metodo_registrado")
    val metodoRegistrado: String = "calendario",

    /** Objetivo: "conocimiento", "fertilidad", "anticoncepcion" */
    @ColumnInfo(name = "objetivo_usuario")
    val objetivoUsuario: String = "conocimiento",

    /** Indica si este ciclo fue confirmado con la llegada de la siguiente menstruación */
    @ColumnInfo(name = "ciclo_confirmado")
    val cicloConfirmado: Boolean = false,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
)
