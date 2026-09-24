package com.ona.miciclo.core.notification

import android.content.Context

/**
 * Preferencias de recordatorios (A2) en SharedPreferences — sin migración Room.
 *
 * Tres interruptores (periodo / fértil / registro, activados por defecto) y el
 * conjunto de claves ya notificadas para no repetir avisos.
 */
class ReminderPrefs(context: Context) {

    private val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    var periodEnabled: Boolean
        get() = prefs.getBoolean(KEY_PERIOD, true)
        set(v) = prefs.edit().putBoolean(KEY_PERIOD, v).apply()

    var fertileEnabled: Boolean
        get() = prefs.getBoolean(KEY_FERTILE, true)
        set(v) = prefs.edit().putBoolean(KEY_FERTILE, v).apply()

    var logEnabled: Boolean
        get() = prefs.getBoolean(KEY_LOG, true)
        set(v) = prefs.edit().putBoolean(KEY_LOG, v).apply()

    /**
     * Avisos al partner (B1): interruptor DEL partner en SU teléfono
     * (apagado por defecto). Sin columna en la nube, el permiso de la
     * hostess vive en [coupleAlertsAllowed] como registro de consentimiento.
     */
    var partnerAlertsEnabled: Boolean
        get() = prefs.getBoolean(KEY_PARTNER_ALERTS, false)
        set(v) = prefs.edit().putBoolean(KEY_PARTNER_ALERTS, v).apply()

    /** Consentimiento de la hostess para que su pareja reciba avisos (opt-in). */
    var coupleAlertsAllowed: Boolean
        get() = prefs.getBoolean(KEY_COUPLE_ALLOWED, false)
        set(v) = prefs.edit().putBoolean(KEY_COUPLE_ALLOWED, v).apply()

    fun notifiedKeys(): Set<String> =
        prefs.getStringSet(KEY_NOTIFIED, emptySet()) ?: emptySet()

    fun markNotified(keys: Collection<String>) {
        if (keys.isEmpty()) return
        prefs.edit()
            .putStringSet(KEY_NOTIFIED, notifiedKeys() + keys)
            .apply()
    }

    companion object {
        const val FILE = "ona_reminders"
        const val KEY_PERIOD = "rem_period"
        const val KEY_FERTILE = "rem_fertile"
        const val KEY_LOG = "rem_log"
        const val KEY_PARTNER_ALERTS = "rem_partner_alerts"
        const val KEY_COUPLE_ALLOWED = "rem_couple_allowed"
        const val KEY_NOTIFIED = "rem_notified"
    }
}
