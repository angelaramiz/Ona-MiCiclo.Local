package com.ona.miciclo.core.widget

import android.content.Context
import android.content.SharedPreferences
import com.ona.miciclo.ai.domain.ConversationalLogParser
import com.ona.miciclo.calendar.domain.model.CyclePhase
import com.ona.miciclo.calendar.domain.model.CyclePrediction
import com.ona.miciclo.calendar.domain.model.DailyLog
import com.ona.miciclo.calendar.domain.model.FlowLevel
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Snapshot del widget de pantalla principal (A3).
 *
 * [CycleSnapshotBuilder] es puro (testeable en JVM) y resume la predicción en
 * 3 líneas. [WidgetSnapshotStore] persiste el snapshot en SharedPreferences para
 * que el [OnaWidgetProvider] lo lea sin necesidad de abrir la DB cifrada.
 */
data class CycleSnapshot(
    val title: String,
    val subtitle: String,
    val phase: String,
    /** Probabilidad estimada de embarazo hoy ("Alta", "Baja"...). */
    val pregnancy: String = "—",
    /** Resumen del registro de hoy (síntomas, flujo, temp, nota). */
    val notes: String = "Sin registros hoy"
)

object CycleSnapshotBuilder {

    fun build(
        prediction: CyclePrediction?,
        today: LocalDate,
        isPartner: Boolean,
        todayLog: DailyLog? = null
    ): CycleSnapshot {
        if (prediction == null) {
            return CycleSnapshot(
                title = "Ona",
                subtitle = "Abre la app",
                phase = "Sin datos todavía"
            )
        }
        val daysToPeriod = ChronoUnit.DAYS.between(today, prediction.proximaMenstruacion).toInt()
        val periodText = when {
            daysToPeriod <= 0 -> "Periodo: hoy"
            daysToPeriod == 1 -> "Periodo: mañana"
            else -> "Periodo: ${prediction.proximaMenstruacion.dayOfMonth} de " +
                "${shortMonth(prediction.proximaMenstruacion.monthValue)}"
        }
        val phaseText = when (prediction.faseActual) {
            CyclePhase.MENSTRUATION -> "Menstruación"
            CyclePhase.FOLLICULAR -> "Fase folicular"
            CyclePhase.FERTILE -> "Ventana fértil"
            CyclePhase.LUTEAL -> "Fase lútea"
            CyclePhase.UNKNOWN -> "Tu ciclo"
        }
        val title = if (isPartner) {
            "Pareja · Día ${prediction.diaDelCiclo} de ${prediction.duracionPromedio}"
        } else {
            "Día ${prediction.diaDelCiclo} de ${prediction.duracionPromedio}"
        }
        val subtitle = if (isPartner) {
            "$periodText (pareja)"
        } else {
            periodText
        }
        return CycleSnapshot(
            title = title,
            subtitle = subtitle,
            phase = phaseText,
            pregnancy = pregnancyChance(prediction, today),
            notes = notesLine(todayLog)
        )
    }

    /**
     * Probabilidad estimada de embarazo hoy según la fase (método calendario).
     * El día exacto de ovulación se marca como pico máximo.
     */
    fun pregnancyChance(prediction: CyclePrediction, today: LocalDate): String {
        if (today == prediction.diaOvulacion) return "Muy alta"
        return when (prediction.faseActual) {
            CyclePhase.MENSTRUATION -> "Muy baja"
            CyclePhase.FOLLICULAR -> "Baja"
            CyclePhase.FERTILE -> "Alta"
            CyclePhase.LUTEAL -> "Baja"
            CyclePhase.UNKNOWN -> "—"
        }
    }

    /** Una línea con lo registrado hoy: síntomas, flujo, temperatura y nota. */
    fun notesLine(log: DailyLog?): String {
        if (log == null) return "Sin registros hoy"
        val parts = mutableListOf<String>()
        if (log.sintomasBasicos.isNotEmpty()) {
            parts += log.sintomasBasicos.joinToString(", ", transform = {
                ConversationalLogParser.prettySymptom(it)
            })
        }
        if (log.nivelFlujo != FlowLevel.NONE) parts += "Flujo ${log.nivelFlujo.displayName.lowercase()}"
        log.temperaturaBasal?.let { parts += "$it°" }
        log.notas?.takeIf { it.isNotBlank() }?.let { parts += it.trim() }
        if (parts.isEmpty()) return "Sin registros hoy"
        val line = parts.joinToString(" · ")
        return if (line.length <= 80) line else line.take(77) + "..."
    }

    private fun shortMonth(m: Int): String = when (m) {
        1 -> "ene"; 2 -> "feb"; 3 -> "mar"; 4 -> "abr"; 5 -> "may"; 6 -> "jun"
        7 -> "jul"; 8 -> "ago"; 9 -> "sep"; 10 -> "oct"; 11 -> "nov"; else -> "dic"
    }
}

/** Persistencia del snapshot para el provider (sin tocar Room/SQLCipher). */
class WidgetSnapshotStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun save(snapshot: CycleSnapshot) {
        prefs.edit()
            .putString(KEY_TITLE, snapshot.title)
            .putString(KEY_SUBTITLE, snapshot.subtitle)
            .putString(KEY_PHASE, snapshot.phase)
            .putString(KEY_PREGNANCY, snapshot.pregnancy)
            .putString(KEY_NOTES, snapshot.notes)
            .putLong(KEY_UPDATED, System.currentTimeMillis())
            .apply()
    }

    fun load(): CycleSnapshot = CycleSnapshot(
        title = prefs.getString(KEY_TITLE, "Ona") ?: "Ona",
        subtitle = prefs.getString(KEY_SUBTITLE, "Abre la app") ?: "Abre la app",
        phase = prefs.getString(KEY_PHASE, "Sin datos todavía") ?: "Sin datos todavía",
        pregnancy = prefs.getString(KEY_PREGNANCY, "—") ?: "—",
        notes = prefs.getString(KEY_NOTES, "Sin registros hoy") ?: "Sin registros hoy"
    )

    companion object {
        const val FILE = "ona_widget"
        const val KEY_TITLE = "w_title"
        const val KEY_SUBTITLE = "w_subtitle"
        const val KEY_PHASE = "w_phase"
        const val KEY_PREGNANCY = "w_pregnancy"
        const val KEY_NOTES = "w_notes"
        const val KEY_UPDATED = "w_updated"
    }
}
