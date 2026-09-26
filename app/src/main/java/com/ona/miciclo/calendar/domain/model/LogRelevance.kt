package com.ona.miciclo.calendar.domain.model

import java.time.LocalDate

/**
 * Relevancia por fase para el Registro Diario: qué campos destacar primero
 * según el momento del ciclo de la fecha seleccionada. Puro, testeable en JVM.
 */
object LogRelevance {

    /** Síntomas típicamente premenstruales (fase lútea). */
    val PMS_SYMPTOMS = listOf(
        "calambres", "dolor_cabeza", "fatiga", "sensibilidad_pechos",
        "hinchazón", "cambios_humor", "nauseas", "antojos",
        "irritabilidad", "llanto_facil", "ansiedad", "estrenimiento", "diarrea"
    )

    enum class Moment { PERIOD, FERTILE, LUTEAL, FOLLICULAR, UNKNOWN }

    fun momentFor(
        date: LocalDate,
        prediction: CyclePrediction?,
        cycleStart: LocalDate?,
        bleedingDays: Int = 5
    ): Moment {
        if (prediction == null || cycleStart == null) return Moment.UNKNOWN
        val dayOfCycle = java.time.temporal.ChronoUnit.DAYS.between(cycleStart, date).toInt() + 1
        if (dayOfCycle < 1) return Moment.UNKNOWN
        if (dayOfCycle <= bleedingDays) return Moment.PERIOD
        if (!date.isBefore(prediction.inicioVentanaFertil) &&
            !date.isAfter(prediction.finVentanaFertil)
        ) return Moment.FERTILE
        if (date.isAfter(prediction.finVentanaFertil) &&
            !date.isAfter(prediction.proximaMenstruacion)
        ) return Moment.LUTEAL
        return Moment.FOLLICULAR
    }

    fun bannerFor(moment: Moment): String? = when (moment) {
        Moment.PERIOD -> "🩸 Registra tu flujo de hoy."
        Moment.FERTILE -> "🌸 Ventana fértil: el moco, la temperatura y la tira LH son lo más útil hoy."
        Moment.LUTEAL -> "🌙 Fase lútea: atenta a los síntomas premenstruales."
        else -> null
    }

    /**
     * Ordena los síntomas: en fértil va "ovulacion" primero; en lútea, los
     * premenstruales primero. El resto conserva su orden.
     */
    fun orderedSymptoms(all: List<String>, moment: Moment): List<String> {
        val priority: List<String> = when (moment) {
            Moment.FERTILE -> listOf("ovulacion")
            Moment.LUTEAL -> PMS_SYMPTOMS
            else -> return all
        }
        val head = priority.filter { it in all }
        return head + all.filter { it !in head }
    }
}
