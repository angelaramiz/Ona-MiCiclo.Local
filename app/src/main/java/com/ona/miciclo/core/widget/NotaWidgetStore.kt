package com.ona.miciclo.core.widget

import android.content.Context
import android.content.SharedPreferences

/**
 * Última notita de pareja para el widget post-it (sin tocar Room/SQLCipher).
 *
 * PRIVACIDAD: se guarda el texto YA descifrado en prefs privadas de la app
 * (mismo nivel que el resto de snapshots del widget). Solo la última nota,
 * nunca el historial.
 */
data class NotaWidgetData(
    val senderLabel: String,
    val text: String,
    val timeAgo: String,
    val createdAt: Long
)

class NotaWidgetStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /**
     * Guarda la más reciente. La etiqueta ("Tú"/"Tu pareja") y el "hace X" son
     * relativos al visor y al momento: se reescriben siempre, pero solo se
     * pide re-render al widget si algo visible cambió (evita parpadeos).
     */
    fun saveLatest(data: NotaWidgetData): Boolean {
        val changed = data.senderLabel != prefs.getString(KEY_SENDER, "") ||
            data.text != prefs.getString(KEY_TEXT, null) ||
            data.timeAgo != prefs.getString(KEY_TIME, "")
        prefs.edit()
            .putString(KEY_SENDER, data.senderLabel)
            .putString(KEY_TEXT, data.text)
            .putString(KEY_TIME, data.timeAgo)
            .putLong(KEY_CREATED, data.createdAt)
            .apply()
        return changed
    }

    fun load(): NotaWidgetData = NotaWidgetData(
        senderLabel = prefs.getString(KEY_SENDER, "") ?: "",
        text = prefs.getString(KEY_TEXT, "Aún no hay notitas 💛") ?: "Aún no hay notitas 💛",
        timeAgo = prefs.getString(KEY_TIME, "") ?: "",
        createdAt = prefs.getLong(KEY_CREATED, 0L)
    )

    companion object {
        const val FILE = "ona_nota_widget"
        const val KEY_SENDER = "n_sender"
        const val KEY_TEXT = "n_text"
        const val KEY_TIME = "n_time"
        const val KEY_CREATED = "n_created"
    }
}
