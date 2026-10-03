package com.ona.miciclo.core.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.ona.miciclo.MainActivity
import com.ona.miciclo.R

/**
 * Widget post-it de pareja: muestra la última notita en la pantalla de
 * inicio. Tocar abre la app (los RemoteViews no permiten escribir texto).
 *
 * Se actualiza cada vez que el Dashboard carga/envía notitas (ver
 * `DashboardViewModel.pushNoteToWidget`) y al agregarse el widget.
 * Sin push instantáneo tipo FCM: la latencia típica es el tick de sync.
 */
class NotaWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val data = NotaWidgetStore(context).load()
        appWidgetIds.forEach { id ->
            appWidgetManager.updateAppWidget(id, views(context, data))
        }
    }

    companion object {
        private fun views(context: Context, data: NotaWidgetData): RemoteViews {
            val intent = Intent(context, MainActivity::class.java)
            val pending = PendingIntent.getActivity(
                context, 1, intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            return RemoteViews(context.packageName, R.layout.nota_widget).apply {
                val header = if (data.senderLabel.isEmpty()) {
                    "🌸 Notitas"
                } else {
                    "🌸 ${data.senderLabel}"
                }
                setTextViewText(R.id.nota_widget_sender, header)
                setTextViewText(R.id.nota_widget_text, data.text)
                setTextViewText(R.id.nota_widget_time, data.timeAgo)
                setOnClickPendingIntent(R.id.nota_widget_root, pending)
            }
        }

        fun requestUpdate(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                ComponentName(context, NotaWidgetProvider::class.java)
            )
            if (ids.isNotEmpty()) {
                val data = NotaWidgetStore(context).load()
                ids.forEach { manager.updateAppWidget(it, views(context, data)) }
            }
        }
    }
}
