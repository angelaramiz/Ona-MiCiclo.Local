package com.ona.miciclo.core.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.ona.miciclo.MainActivity
import com.ona.miciclo.R

class NotificationHelper(private val context: Context) {

    companion object {
        private const val CHANNEL_ID = "partner_suggestions"
        private const val CHANNEL_NAME = "Sugerencias de Pareja"
        private const val CHANNEL_DESCRIPTION = "Notificaciones de sugerencias de inicio de periodo"
        private const val NOTIFICATION_ID = 1001

        private const val REMINDER_CHANNEL_ID = "ona_reminders"
        private const val REMINDER_CHANNEL_NAME = "Recordatorios de Ciclo"
        private const val REMINDER_CHANNEL_DESCRIPTION = "Avisos de periodo, ventana fértil y registro diario"
    }

    init {
        createNotificationChannel()
        createReminderChannel()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = CHANNEL_DESCRIPTION
        }
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    private fun createReminderChannel() {
        val channel = NotificationChannel(
            REMINDER_CHANNEL_ID,
            REMINDER_CHANNEL_NAME,
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = REMINDER_CHANNEL_DESCRIPTION
        }
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    /**
     * Muestra un recordatorio de ciclo (A2). Cada tipo usa su propio id
     * (derivado de la clave) para no pisarse entre sí.
     */
    fun showReminder(notificationId: Int, title: String, text: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val builder = NotificationCompat.Builder(context, REMINDER_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        with(NotificationManagerCompat.from(context)) {
            try {
                notify(notificationId, builder.build())
            } catch (e: SecurityException) {
                // Sin permiso POST_NOTIFICATIONS: no romper el worker.
                e.printStackTrace()
            }
        }
    }

    fun showPartnerSuggestionNotification(suggestedDate: String, suggestionType: String = "START_PERIOD") {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        
        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("Sugerencia de tu pareja")
            .setContentText(com.ona.miciclo.calendar.domain.model.PartnerSuggestions.notificationText(suggestionType, suggestedDate))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        with(NotificationManagerCompat.from(context)) {
            try {
                notify(NOTIFICATION_ID, builder.build())
            } catch (e: SecurityException) {
                // Sin permiso POST_NOTIFICATIONS: no romper al llamador.
                e.printStackTrace()
            }
        }
    }
}
