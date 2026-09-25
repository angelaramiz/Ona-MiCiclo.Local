package com.ona.miciclo.calendar.domain.model

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Modo íntimo en pareja (B3): guía dual según el objetivo compartido.
 *
 * - CONCEIVE ("buscamos embarazo"): destaca los días fértiles como oportunidad.
 * - AVOID ("solo disfrutar"): destaca los días seguros y advierte en fértiles,
 *   SIEMPRE con disclaimer (el calendario no es anticonceptivo confiable).
 *
 * Objeto puro (sin Android), testeable en JVM.
 */
object CoupleGuidance {

    const val GOAL_CONCEIVE = "CONCEIVE"
    const val GOAL_AVOID = "AVOID"

    /** Mapea el objetivo de la hostess (onboarding) al modo íntimo. */
    fun fromHostessObjective(objetivo: String?): String? = when (objetivo) {
        "fertilidad" -> GOAL_CONCEIVE
        "anticoncepcion" -> GOAL_AVOID
        else -> null
    }

    const val DISCLAIMER =
        "⚠️ El método del calendario no es un anticonceptivo confiable. Usen protección siempre."

    enum class Level { FERTILE, SAFE, PERIOD, NEUTRAL }

    data class Result(
        val level: Level,
        val title: String,
        val text: String,
        val showDisclaimer: Boolean
    )

    fun forCouple(
        prediction: CyclePrediction?,
        today: LocalDate,
        goal: String?,
        partnerMode: Boolean
    ): Result? {
        if (prediction == null) return null
        if (goal != GOAL_CONCEIVE && goal != GOAL_AVOID) return null
        val her = if (partnerMode) " de tu pareja" else ""

        val daysToPeriod = ChronoUnit.DAYS.between(today, prediction.proximaMenstruacion).toInt()
        if (daysToPeriod in 0..3) {
            val whenText = when (daysToPeriod) {
                0 -> "hoy"
                1 -> "mañana"
                else -> "en $daysToPeriod días"
            }
            return Result(
                level = Level.PERIOD,
                title = "Periodo muy cerca 🩸",
                text = if (partnerMode) {
                    "El periodo de tu pareja llega $whenText. Apóyala 💕"
                } else {
                    "Tu periodo llega $whenText. Cuídate 💕"
                },
                showDisclaimer = goal == GOAL_AVOID
            )
        }

        val fertile = !today.isBefore(prediction.inicioVentanaFertil) &&
            !today.isAfter(prediction.finVentanaFertil)
        if (fertile) {
            return if (goal == GOAL_CONCEIVE) {
                Result(
                    level = Level.FERTILE,
                    title = "Día fértil$her ❤",
                    text = if (partnerMode) {
                        "Momento ideal para buscar el embarazo juntos."
                    } else {
                        "Momento ideal para buscar el embarazo."
                    },
                    showDisclaimer = false
                )
            } else {
                Result(
                    level = Level.FERTILE,
                    title = "Día fértil$her ⚠️",
                    text = if (partnerMode) {
                        "Si quieren evitar el embarazo, usen protección hoy."
                    } else {
                        "Si quieres evitar el embarazo, usa protección hoy."
                    },
                    showDisclaimer = true
                )
            }
        }

        return if (goal == GOAL_AVOID) {
            Result(
                level = Level.SAFE,
                title = "Día seguro$her ✓",
                text = if (partnerMode) {
                    "Fuera de la ventana fértil. Disfruten sin remordimientos 💞"
                } else {
                    "Fuera de la ventana fértil. Disfruta sin remordimientos 💞"
                },
                showDisclaimer = true
            )
        } else {
            val past = today.isAfter(prediction.finVentanaFertil)
            Result(
                level = Level.NEUTRAL,
                title = "Fuera de ventana fértil",
                text = if (past) {
                    if (partnerMode) {
                        "La ventana fértil de tu pareja ya pasó este ciclo."
                    } else {
                        "Tu ventana fértil ya pasó este ciclo."
                    }
                } else {
                    val days = ChronoUnit.DAYS.between(today, prediction.inicioVentanaFertil).toInt()
                    if (partnerMode) {
                        "La ventana fértil de tu pareja empieza en $days días."
                    } else {
                        "Tu ventana fértil empieza en $days días."
                    }
                },
                showDisclaimer = false
            )
        }
    }
}
