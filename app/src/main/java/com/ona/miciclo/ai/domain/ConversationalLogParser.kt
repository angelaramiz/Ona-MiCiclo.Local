package com.ona.miciclo.ai.domain

import com.ona.miciclo.calendar.domain.model.DailyLog
import com.ona.miciclo.calendar.domain.model.FlowLevel
import java.time.LocalDate

/**
 * Registro conversacional (A5): interpreta texto libre del chat y lo convierte
 * en datos de DailyLog. Puro (sin Android), testeable en JVM.
 *
 * Ejemplos: "registra dolor de cabeza", "tengo cólicos y manchado leve",
 * "temperatura 36.6", "moco clara de huevo", "me llegó el periodo".
 */
object ConversationalLogParser {

    data class ParsedLog(
        val symptoms: List<String> = emptyList(),
        val flow: FlowLevel? = null,
        val basalTemp: Double? = null,
        val mucus: String? = null,
        val isPeriodStart: Boolean = false
    ) {
        val matched: Boolean
            get() = symptoms.isNotEmpty() || flow != null || basalTemp != null ||
                mucus != null || isPeriodStart
    }

    private val symptomKeywords: List<Pair<Regex, String>> = listOf(
        Regex("dolor de cabeza|duele la cabeza|cabeza") to "dolor_cabeza",
        Regex("c[oó]lic|calambre") to "calambres",
        Regex("cansan|fatiga|agotad") to "fatiga",
        Regex("n[aá]usea|ganas de vomitar") to "nauseas",
        Regex("hincha") to "hinchazón",
        Regex("humor|irritable|triste|ansios") to "cambios_humor",
        Regex("pecho|sensible") to "sensibilidad_pechos",
        Regex("acn|grano|espinilla") to "acne",
        Regex("antojo") to "antojos",
        Regex("insomnio|no dorm|desvel") to "insomnio",
        Regex("espalda") to "dolor_espalda",
        Regex("articulaci|rodilla|coyuntura") to "dolor_articulaciones",
        Regex("ovul") to "ovulacion"
    )

    private val tempRegex = Regex("(\\d{2}[.,]\\d{1,2})")
    private val mucusKeywords: List<Pair<Regex, String>> = listOf(
        Regex("clara de huevo") to "clara_de_huevo",
        Regex("cremoso") to "cremoso",
        Regex("pegajoso") to "pegajoso",
        Regex("\\bmoco seco\\b|\\bseco\\b") to "seco"
    )

    fun parse(text: String): ParsedLog {
        val lower = text.lowercase()

        val symptoms = symptomKeywords
            .filter { (re, _) -> re.containsMatchIn(lower) }
            .map { (_, symptom) -> symptom }
            .distinct()

        val flow = when {
            Regex("abundante|fuerte|sangrado intenso").containsMatchIn(lower) -> FlowLevel.HEAVY
            Regex("moderado|normal").containsMatchIn(lower) -> FlowLevel.MODERATE
            Regex("leve|ligero|manchado|manch").containsMatchIn(lower) -> FlowLevel.LIGHT
            Regex("sin flujo").containsMatchIn(lower) -> FlowLevel.NONE
            else -> null
        }

        val temp = tempRegex.find(lower)?.groupValues?.get(1)
            ?.replace(',', '.')?.toDoubleOrNull()
            ?.takeIf { it in 35.0..38.5 }

        val mucus = mucusKeywords.firstOrNull { (re, _) -> re.containsMatchIn(lower) }?.second

        val periodStart = Regex("lleg[óo] el periodo|empez[óo] el periodo|inici[óo] mi periodo|estoy menstruando|me baj[óo]")
            .containsMatchIn(lower)

        return ParsedLog(
            symptoms = symptoms,
            flow = flow,
            basalTemp = temp,
            mucus = mucus,
            isPeriodStart = periodStart
        )
    }

    /** Resumen legible para la burbuja de confirmación del chat. */
    fun describe(parsed: ParsedLog, symptomLabel: (String) -> String = ::prettySymptom): String {
        val parts = mutableListOf<String>()
        if (parsed.isPeriodStart) parts += "inicio de periodo"
        parsed.flow?.let { parts += "flujo ${it.displayName.lowercase()}" }
        if (parsed.symptoms.isNotEmpty()) parts += parsed.symptoms.joinToString(", ", transform = symptomLabel)
        parsed.basalTemp?.let { parts += "temperatura $it °C" }
        parsed.mucus?.let { parts += "moco $it" }
        return parts.joinToString(" · ")
    }

    /** Etiquetas en español para los síntomas (igual que Registro Diario). */
    fun prettySymptom(key: String): String = when (key) {
        "calambres" -> "Calambres"
        "dolor_cabeza" -> "Dolor de cabeza"
        "fatiga" -> "Fatiga"
        "sensibilidad_pechos" -> "Sensibilidad en pechos"
        "acne" -> "Acné"
        "hinchazón" -> "Hinchazón"
        "cambios_humor" -> "Cambios de humor"
        "dolor_espalda" -> "Dolor de espalda"
        "nauseas" -> "Náuseas"
        "insomnio" -> "Insomnio"
        "antojos" -> "Antojos"
        "dolor_articulaciones" -> "Dolor de articulaciones"
        "ovulacion" -> "Ovulación"
        else -> key.replace('_', ' ')
    }

    /** Fusiona lo parseado en un DailyLog existente (o crea uno nuevo). */
    fun merge(existing: DailyLog?, userId: String, date: LocalDate, parsed: ParsedLog): DailyLog {
        val base = existing ?: DailyLog(userId = userId, fecha = date)
        return base.copy(
            nivelFlujo = parsed.flow ?: base.nivelFlujo,
            sintomasBasicos = (base.sintomasBasicos + parsed.symptoms).distinct(),
            temperaturaBasal = parsed.basalTemp ?: base.temperaturaBasal,
            mocoCervical = parsed.mucus ?: base.mocoCervical
        )
    }
}
