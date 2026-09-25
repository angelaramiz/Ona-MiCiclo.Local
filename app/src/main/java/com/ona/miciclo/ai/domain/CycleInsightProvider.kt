package com.ona.miciclo.ai.domain

import com.ona.miciclo.calendar.domain.model.CyclePhase
import com.ona.miciclo.calendar.domain.model.CyclePrediction
import com.ona.miciclo.calendar.domain.model.DailyLog
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Insights con reglas (A5): observaciones accionables a partir de la predicción
 * y los registros recientes. Puro (sin Android), testeable en JVM.
 *
 * Se muestran en el Dashboard ("Insights de Ona") y enriquecen el contexto del chat.
 */
object CycleInsightProvider {

    fun insights(
        prediction: CyclePrediction?,
        recentLogs: List<DailyLog>,
        today: LocalDate = LocalDate.now()
    ): List<String> {
        if (prediction == null) return emptyList()
        val out = mutableListOf<String>()

        val daysToPeriod = ChronoUnit.DAYS.between(today, prediction.proximaMenstruacion).toInt()
        if (daysToPeriod in 0..3) {
            val whenText = when (daysToPeriod) {
                0 -> "hoy"
                1 -> "mañana"
                else -> "en $daysToPeriod días"
            }
            out += "🩸 Tu periodo podría llegar $whenText. Ten a la mano lo necesario."
        }

        val inFertile = !today.isBefore(prediction.inicioVentanaFertil) &&
            !today.isAfter(prediction.finVentanaFertil)
        if (inFertile && prediction.faseActual == CyclePhase.FERTILE) {
            out += "🌸 Estás en tu ventana fértil (hasta el ${prediction.finVentanaFertil.dayOfMonth} de " +
                "${monthName(prediction.finVentanaFertil.monthValue)})."
        } else {
            val daysToFertile = ChronoUnit.DAYS.between(today, prediction.inicioVentanaFertil).toInt()
            if (daysToFertile in 1..2) {
                val whenText = if (daysToFertile == 1) "mañana" else "en $daysToFertile días"
                out += "🌸 Tu ventana fértil empieza $whenText."
            }
        }

        val weekLogs = recentLogs.filter { it.fecha >= today.minusDays(7) && it.fecha <= today }
        val topSymptom = weekLogs.flatMap { it.sintomasBasicos }
            .filter { it != "ovulacion" }
            .groupingBy { it }.eachCount()
            .entries.sortedByDescending { it.value }
            .firstOrNull()
        if (topSymptom != null && topSymptom.value >= 2) {
            out += "📊 Esta semana registraste ${prettySymptom(topSymptom.key)} " +
                "${topSymptom.value} veces. Observa si se repite cada ciclo."
        }

        val confirmedOv = recentLogs.firstOrNull {
            "ovulacion" in it.sintomasBasicos && it.fecha >= today.minusDays(10)
        }
        if (confirmedOv != null) {
            out += "🔬 Ovulación detectada el ${confirmedOv.fecha.dayOfMonth} de " +
                "${monthName(confirmedOv.fecha.monthValue)}. Tu ventana se ajustó con tus datos."
        }

        val lastLog = recentLogs.maxOfOrNull { it.fecha }
        if (lastLog != null && lastLog < today.minusDays(2)) {
            val days = ChronoUnit.DAYS.between(lastLog, today).toInt()
            out += "💭 Llevas $days días sin registrar. Un minuto al día mejora tus predicciones."
        }

        return out.take(4)
    }

    private fun monthName(m: Int): String = when (m) {
        1 -> "enero"; 2 -> "febrero"; 3 -> "marzo"; 4 -> "abril"
        5 -> "mayo"; 6 -> "junio"; 7 -> "julio"; 8 -> "agosto"
        9 -> "septiembre"; 10 -> "octubre"; 11 -> "noviembre"; else -> "diciembre"
    }

    private fun prettySymptom(key: String): String = when (key) {
        "dolor_cabeza" -> "dolor de cabeza"
        "sensibilidad_pechos" -> "sensibilidad en pechos"
        "cambios_humor" -> "cambios de humor"
        "dolor_espalda" -> "dolor de espalda"
        "dolor_articulaciones" -> "dolor de articulaciones"
        else -> key.replace('_', ' ')
    }
}
