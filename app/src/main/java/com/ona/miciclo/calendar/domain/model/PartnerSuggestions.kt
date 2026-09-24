package com.ona.miciclo.calendar.domain.model

import java.time.LocalDate

/**
 * Tipos de sugerencia del modo pareja + lógica pura (testeable en JVM, sin Android).
 *
 * - START_PERIOD: el partner sugiere inicio de periodo → al aprobar se crea un CycleRecord.
 * - OVULATION_DAY: el partner sugiere día de ovulación → al aprobar se registra el
 *   síntoma "ovulacion" en el DailyLog de esa fecha (visible en calendario y sincronizado).
 */
object PartnerSuggestions {

    const val START_PERIOD = "START_PERIOD"
    const val OVULATION_DAY = "OVULATION_DAY"

    /** Notas de apoyo (B2): el TIPO es el mensaje (sin columna de texto en la nube). */
    const val SUPPORT_ANIMO = "SUPPORT_ANIMO"
    const val SUPPORT_DESCANSA = "SUPPORT_DESCANSA"
    const val SUPPORT_ORGULLO = "SUPPORT_ORGULLO"
    const val SUPPORT_ABRAZO = "SUPPORT_ABRAZO"

    val NOTE_TYPES = listOf(SUPPORT_ANIMO, SUPPORT_DESCANSA, SUPPORT_ORGULLO, SUPPORT_ABRAZO)

    /** Síntoma que se registra al aprobar una sugerencia de ovulación. */
    const val OVULATION_SYMPTOM = "ovulacion"

    fun isKnown(type: String): Boolean =
        type == START_PERIOD || type == OVULATION_DAY || isSupportNote(type)

    fun isSupportNote(type: String): Boolean = type.startsWith("SUPPORT_")

    /** Mensaje de la nota de apoyo, o null si no es una nota. */
    fun noteMessage(type: String): String? = when (type) {
        SUPPORT_ANIMO -> "¡Ánimo! Estoy contigo ❤"
        SUPPORT_DESCANSA -> "Descansa, te lo mereces 🌙"
        SUPPORT_ORGULLO -> "Estoy orgulloso de ti ✨"
        SUPPORT_ABRAZO -> "Te mando un abrazo fuerte 🤗"
        else -> null
    }

    fun dialogTitle(type: String): String = if (isSupportNote(type)) {
        "Mensaje de tu pareja 💌"
    } else {
        "Sugerencia de tu pareja"
    }

    /** Etiqueta corta para UI ("inicio de periodo" / "día de ovulación"). */
    fun label(type: String): String = when (type) {
        OVULATION_DAY -> "día de ovulación"
        else -> "inicio de periodo"
    }

    fun dialogText(type: String, date: String): String {
        noteMessage(type)?.let { msg ->
            return "Tu pareja te envió un mensaje:\n\n\"$msg\""
        }
        return when (type) {
        OVULATION_DAY ->
            "Tu pareja sugiere marcar el día $date como día de ovulación. ¿Deseas registrarlo en tu calendario?"
        else ->
            "Tu pareja sugiere marcar el inicio de tu periodo el día $date. ¿Deseas aplicar este ajuste en tu calendario?"
        }
    }

    fun notificationText(type: String, date: String): String {
        noteMessage(type)?.let { msg -> return "💌 $msg" }
        return when (type) {
        OVULATION_DAY -> "Tu pareja sugiere marcar el $date como día de ovulación"
        else -> "Tu pareja sugiere marcar el inicio del periodo el $date"
        }
    }

    fun approveMessage(type: String): String {
        if (isSupportNote(type)) return "💌 ¡Mensaje recibido!"
        return when (type) {
        OVULATION_DAY -> "Ovulación registrada ✓"
        else -> "Sugerencia aprobada y aplicada"
        }
    }

    fun sentMessage(type: String): String {
        if (isSupportNote(type)) return "Mensaje enviado 💌"
        return when (type) {
        OVULATION_DAY -> "Sugerencia de ovulación enviada ✓"
        else -> "Sugerencia enviada a tu pareja ✓"
        }
    }

    fun statusLine(type: String, date: String, status: String): String {
        val msg = noteMessage(type)
        val estado = when (status) {
            "APPROVED" -> if (msg != null) "visto 💌" else "aprobada ✓"
            "REJECTED" -> "rechazada"
            else -> "pendiente…"
        }
        val what = if (msg != null) "Tu mensaje (\"$msg\")" else "Tu sugerencia (${label(type)} $date)"
        return "$what: $estado"
    }

    /**
     * Fusiona la confirmación de ovulación en un DailyLog existente (o crea uno mínimo).
     * Preserva flujo, temperatura, moco cervical, LH y notas previas.
     */
    fun withOvulationConfirmed(existing: DailyLog?, userId: String, date: LocalDate): DailyLog {
        val base = existing ?: DailyLog(userId = userId, fecha = date)
        val symptoms = (base.sintomasBasicos + OVULATION_SYMPTOM).distinct()
        val confirmNote = "Ovulación confirmada con tu pareja ✓"
        val notes = base.notas?.takeIf { it.isNotBlank() }?.let {
            if (it.contains(confirmNote)) it else "$it · $confirmNote"
        } ?: confirmNote
        return base.copy(sintomasBasicos = symptoms, notas = notes)
    }
}
