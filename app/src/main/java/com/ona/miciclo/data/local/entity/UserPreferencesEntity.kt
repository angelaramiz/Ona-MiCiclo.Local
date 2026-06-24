package com.ona.miciclo.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate

/**
 * Preferencias del usuario almacenadas localmente.
 * Un registro por userId.
 */
@Entity(tableName = "user_preferences")
data class UserPreferencesEntity(
    @PrimaryKey
    @ColumnInfo(name = "user_id")
    val userId: String,

    /** Duración del ciclo por defecto cuando no hay datos suficientes */
    @ColumnInfo(name = "duracion_ciclo_default")
    val duracionCicloDefault: Int = 28,

    /** Duración del sangrado por defecto */
    @ColumnInfo(name = "duracion_sangrado_default")
    val duracionSangradoDefault: Int = 5,

    /** Objetivo principal: "conocimiento", "fertilidad", "anticoncepcion" */
    @ColumnInfo(name = "objetivo_usuario")
    val objetivoUsuario: String = "conocimiento",

    /** Si la usuaria completó el flujo de onboarding */
    @ColumnInfo(name = "onboarding_completado")
    val onboardingCompletado: Boolean = false,

    /** Última fecha de menstruación conocida (configurada en onboarding o auto-detectada) */
    @ColumnInfo(name = "ultima_fecha_menstruacion")
    val ultimaFechaMenstruacion: LocalDate? = null,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
)
