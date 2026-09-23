package com.ona.miciclo.core.debug

import android.util.Log
import com.ona.miciclo.BuildConfig
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Telemetría de depuración para la sesión `app-update-crash`.
 *
 * Fire-and-forget: nunca lanza excepciones ni bloquea el hilo llamante.
 * Se desactiva por completo poniendo BuildConfig.TELEMETRY_ENABLED = false
 * (requerido antes del release final de producción).
 */
object DebugTelemetry {

    private const val TAG = "OnaTelemetry"
    private const val SESSION_ID = "app-update-crash"
    private const val RUN_ID = "pre-fix"

    fun emit(hypothesisId: String, location: String, msg: String, data: JSONObject = JSONObject()) {
        if (!BuildConfig.TELEMETRY_ENABLED) return
        Log.i(TAG, "[$hypothesisId] $location — $msg $data")
        send(
            JSONObject()
                .put("sessionId", SESSION_ID)
                .put("runId", RUN_ID)
                .put("hypothesisId", hypothesisId)
                .put("location", location)
                .put("msg", msg)
                .put("data", data)
                .put("ts", System.currentTimeMillis())
        )
    }

    fun emitException(tag: String, throwable: Throwable) {
        if (!BuildConfig.TELEMETRY_ENABLED) return
        Log.e(TAG, "[$tag] ${throwable.javaClass.name}: ${throwable.message}", throwable)
        val stack = throwable.stackTrace.take(50).joinToString("\n") { "  at $it" }
        send(
            JSONObject()
                .put("sessionId", SESSION_ID)
                .put("runId", RUN_ID)
                .put("hypothesisId", "CRASH")
                .put("location", tag)
                .put("msg", "FATAL: ${throwable.javaClass.name}: ${throwable.message}")
                .put("data", JSONObject().put("stacktrace", stack))
                .put("ts", System.currentTimeMillis())
        )
    }

    private fun send(payload: JSONObject) {
        if (!BuildConfig.TELEMETRY_ENABLED) return
        kotlin.concurrent.thread(start = true) {
            try {
                val conn = (URL(BuildConfig.TELEMETRY_URL).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 1000
                    readTimeout = 1000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                }
                conn.outputStream.bufferedWriter().use { it.write(payload.toString()) }
                conn.inputStream.close()
                conn.disconnect()
            } catch (ignored: Exception) {
                // Fire-and-forget: si el servidor no está, no romper la app
            }
        }
    }
}
