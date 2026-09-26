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
import com.ona.miciclo.calendar.domain.model.CyclePrediction
import java.time.LocalDate

/**
 * Widget de pantalla principal (A3): muestra el snapshot del ciclo
 * (título + próximo periodo + fase). Tocar abre la app.
 *
 * No lee Room/SQLCipher: consume el snapshot que escriben el
 * CalendarViewModel (al abrir la app) y el ReminderWorker (a diario).
 */
class OnaWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val snapshot = WidgetSnapshotStore(context).load()
        appWidgetIds.forEach { id ->
            appWidgetManager.updateAppWidget(id, views(context, snapshot))
        }
    }

    companion object {
        private fun views(context: Context, snapshot: CycleSnapshot): RemoteViews {
            val intent = Intent(context, MainActivity::class.java)
            val pending = PendingIntent.getActivity(
                context, 0, intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            return RemoteViews(context.packageName, R.layout.ona_widget).apply {
                setTextViewText(R.id.widget_title, snapshot.title)
                setTextViewText(R.id.widget_subtitle, snapshot.subtitle)
                setTextViewText(R.id.widget_phase, snapshot.phase)
                setTextViewText(R.id.widget_pregnancy, "Embarazo: ${snapshot.pregnancy}")
                setTextViewText(R.id.widget_notes, snapshot.notes)
                setOnClickPendingIntent(R.id.widget_title, pending)
            }
        }

        /** Escribe el snapshot y pide actualización del widget. */
        fun refresh(
            context: Context,
            prediction: CyclePrediction?,
            isPartner: Boolean,
            todayLog: com.ona.miciclo.calendar.domain.model.DailyLog? = null
        ) {
            val snapshot = CycleSnapshotBuilder.build(prediction, LocalDate.now(), isPartner, todayLog)
            WidgetSnapshotStore(context).save(snapshot)
            requestUpdate(context)
        }

        fun requestUpdate(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                ComponentName(context, OnaWidgetProvider::class.java)
            )
            if (ids.isNotEmpty()) {
                val snapshot = WidgetSnapshotStore(context).load()
                ids.forEach { manager.updateAppWidget(it, views(context, snapshot)) }
            }
        }
    }
}
