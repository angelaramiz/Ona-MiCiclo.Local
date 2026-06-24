package com.ona.miciclo.ai.domain

import com.ona.miciclo.calendar.domain.model.CycleRecord
import com.ona.miciclo.calendar.domain.model.DailyLog

/**
 * Generador de prompts estructurados para el LLM local.
 */
object PromptBuilder {

    /**
     * Construye el prompt para predecir la ventana de fertilidad y el siguiente periodo.
     * Cero datos personales identificativos (PII) son incluidos.
     */
    fun buildPredictionPrompt(
        objetivo: String,
        ciclos: List<CycleRecord>,
        logsDiarios: List<DailyLog>
    ): String {
        val ciclosContext = ciclos.joinToString(separator = "\n") { cycle ->
            "- Ciclo duró ${cycle.duracionCiclo ?: 28} días, sangrado por ${cycle.duracionSangrado} días."
        }

        val logsContext = logsDiarios.joinToString(separator = "\n") { log ->
            val sintomas = log.sintomasBasicos.joinToString()
            val temp = log.temperaturaBasal?.let { "$it°C" } ?: "no registrada"
            val moco = log.mocoCervical ?: "no registrado"
            "- Fecha: ${log.fecha}, Flujo: ${log.nivelFlujo.value}, Síntomas: [$sintomas], Temperatura: $temp, Moco Cervical: $moco"
        }

        return """
            [System]
            Eres un asistente de salud reproductiva local (Zero-Knowledge) para la app "Ona".
            Tu tarea es analizar el historial de ciclos y registros sintotérmicos diarios para estimar la próxima ventana fértil y el inicio del periodo.
            Responde ÚNICAMENTE en formato JSON plano con el esquema detallado abajo. No agregues explicaciones fuera del JSON.

            [Objetivo del Usuario]
            $objetivo (concebir / evitar embarazo / seguimiento general)

            [Historial de Ciclos Recientes]
            $ciclosContext

            [Registros Diarios del Ciclo Actual]
            $logsContext

            [Formato JSON Requerido]
            {
              "proxima_menstruacion_fecha": "YYYY-MM-DD",
              "ventana_fertil_inicio": "YYYY-MM-DD",
              "ventana_fertil_fin": "YYYY-MM-DD",
              "mensaje_educativo": "Tu recomendación adaptada al objetivo aquí...",
              "nivel_incertidumbre": "bajo/medio/alto"
            }
        """.trimIndent()
    }
}
