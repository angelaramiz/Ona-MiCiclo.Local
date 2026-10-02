package com.ona.miciclo.calendar.domain.model

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Notita post-it entre hostess y partner (modo pareja).
 * El texto viaja cifrado (ver `SupabaseSyncManager.sendCoupleNote`); aquí
 * solo vive ya descifrado en memoria. Límite corto a propósito: son
 * pequeños mensajes, no chat.
 */
data class CoupleNote(
    val id: String,
    val senderId: String,
    val text: String,
    val createdAtMillis: Long,
    val isMine: Boolean
)

object CoupleNotes {

    /** Máximo de caracteres por notita (pequeños mensajes). */
    const val MAX_LEN = 140

    /** Cuántas se muestran en la tarjeta (el resto vive en la nube). */
    const val SHOWN = 5

    fun isValid(text: String): Boolean {
        val t = text.trim()
        return t.isNotEmpty() && t.length <= MAX_LEN
    }

    fun senderLabel(isMine: Boolean): String =
        if (isMine) "Tú" else "Tu pareja"

    /**
     * "hace 5 min", "hace 2 h", "ayer", o fecha corta. Pura (testeable):
     * `nowMillis` se inyecta en vez de leerse el reloj.
     */
    fun timeAgoEs(nowMillis: Long, createdMillis: Long): String {
        val diff = (nowMillis - createdMillis).coerceAtLeast(0)
        val min = diff / 60_000
        if (min < 1) return "ahora mismo"
        if (min < 60) return "hace $min min"
        val h = min / 60
        if (h < 24) return "hace $h h"
        val zone = ZoneId.systemDefault()
        val day = Instant.ofEpochMilli(createdMillis).atZone(zone).toLocalDate()
        val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        if (day == today.minusDays(1)) return "ayer"
        return day.format(java.time.format.DateTimeFormatter.ofPattern("d MMM"))
    }

    /** Solo para previews/tests: evita LocalDate.now en producción. */
    fun sample(): List<CoupleNote> = listOf(
        CoupleNote("1", "ella", "No olvides tomar agua 💧", 0L, false),
        CoupleNote("2", "yo", "¡Gracias! 💕", 0L, true)
    )
}
