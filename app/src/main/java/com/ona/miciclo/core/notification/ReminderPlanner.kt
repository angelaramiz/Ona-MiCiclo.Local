package com.ona.miciclo.core.notification

import com.ona.miciclo.calendar.domain.model.CyclePrediction
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Planificador puro de recordatorios (A2, testeable en JVM).
 *
 * Decide qué avisos vencen HOY a partir de la predicción del ciclo, sin Android.
 * El `Worker` se encarga de leer prefs/DB, mostrar y marcar como notificados.
 */
object ReminderPlanner {

    const val TYPE_PERIOD = "PERIOD"
    const val TYPE_FERTILE_START = "FERTILE_START"
    const val TYPE_FERTILE_END = "FERTILE_END"
    const val TYPE_LOG = "LOG"

    data class Reminder(
        val type: String,
        /** Clave de deduplicación (tipo + fecha objetivo), ej. "PERIOD_2026-10-10". */
        val key: String,
        val title: String,
        val text: String
    )

    data class Inputs(
        val prediction: CyclePrediction?,
        val today: LocalDate,
        val periodEnabled: Boolean,
        val fertileEnabled: Boolean,
        val logEnabled: Boolean,
        /** Fecha del último DailyLog (null si nunca registró). */
        val lastLogDate: LocalDate?,
        /** Claves ya notificadas (para no repetir el mismo aviso). */
        val alreadyNotified: Set<String> = emptySet()
    )

    fun due(i: Inputs): List<Reminder> {
        val out = mutableListOf<Reminder>()
        val p = i.prediction

        if (p != null) {
            val daysToPeriod = ChronoUnit.DAYS.between(i.today, p.proximaMenstruacion).toInt()
            if (i.periodEnabled && daysToPeriod in 1..2) {
                val whenText = if (daysToPeriod == 1) "mañana" else "en 2 días"
                out += Reminder(
                    type = TYPE_PERIOD,
                    key = "${TYPE_PERIOD}_${p.proximaMenstruacion}",
                    title = "Tu periodo se acerca 🩸",
                    text = "Tu periodo llega $whenText (${p.proximaMenstruacion.dayOfMonth} de " +
                        "${monthName(p.proximaMenstruacion)}). Prepárate 💕"
                )
            }
            if (i.fertileEnabled) {
                val daysToFertile = ChronoUnit.DAYS.between(i.today, p.inicioVentanaFertil).toInt()
                if (daysToFertile == 1) {
                    out += Reminder(
                        type = TYPE_FERTILE_START,
                        key = "${TYPE_FERTILE_START}_${p.inicioVentanaFertil}",
                        title = "Ventana fértil mañana 🌸",
                        text = "Tu ventana fértil empieza mañana. ¡Tómala en cuenta!"
                    )
                }
                val daysToFertileEnd = ChronoUnit.DAYS.between(i.today, p.finVentanaFertil).toInt()
                if (daysToFertileEnd == 1) {
                    out += Reminder(
                        type = TYPE_FERTILE_END,
                        key = "${TYPE_FERTILE_END}_${p.finVentanaFertil}",
                        title = "Ventana fértil termina mañana 🌙",
                        text = "Tu ventana fértil termina mañana (${p.finVentanaFertil.dayOfMonth} de " +
                            "${monthName(p.finVentanaFertil)})."
                    )
                }
            }
        }

        if (i.logEnabled) {
            val stale = i.lastLogDate == null || i.lastLogDate.isBefore(i.today.minusDays(1))
            if (stale) {
                out += Reminder(
                    type = TYPE_LOG,
                    key = "${TYPE_LOG}_${i.today}",
                    title = "¿Cómo te sientes hoy? 💭",
                    text = "Registra tu día en Ona: síntomas, flujo o temperatura en 1 minuto."
                )
            }
        }

        return out.filter { it.key !in i.alreadyNotified }
    }

    private fun monthName(date: LocalDate): String = when (date.monthValue) {
        1 -> "enero"; 2 -> "febrero"; 3 -> "marzo"; 4 -> "abril"
        5 -> "mayo"; 6 -> "junio"; 7 -> "julio"; 8 -> "agosto"
        9 -> "septiembre"; 10 -> "octubre"; 11 -> "noviembre"; else -> "diciembre"
    }
}
