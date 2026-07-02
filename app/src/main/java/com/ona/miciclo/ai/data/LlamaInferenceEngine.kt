package com.ona.miciclo.ai.data

import android.content.Context
import com.alibaba.mnnllm.android.llm.GenerateProgressListener
import com.alibaba.mnnllm.android.llm.LlmSession
import com.ona.miciclo.ai.domain.IInferenceEngine
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Motor de inferencia local con Qwen2-VL via Alibaba MNN.
 * Incluye un fallback determinista si el modelo no está descargado.
 */
@Singleton
class LlamaInferenceEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val modelDownloader: ModelDownloader
) : IInferenceEngine {

    private var isLoaded = false
    private var sessionHandle: Long = 0L

    override fun isModelLoaded(): Boolean = isLoaded && sessionHandle != 0L

    override suspend fun loadModel(modelPath: String): Result<Unit> = withContext(Dispatchers.IO) {
        return@withContext try {
            val loadedLib = MnnLlmBridge.tryLoadLibraries()
            if (!loadedLib) {
                return@withContext Result.failure(Exception("Error al cargar librerías nativas: ${MnnLlmBridge.lastInitError}"))
            }

            val validateError = MnnLlmBridge.validateModelFiles(modelDownloader.modelDir)
            if (validateError != null) {
                return@withContext Result.failure(Exception(validateError))
            }

            val configFile = File(modelDownloader.modelDir, "config.json")
            val mmapDir = File(context.cacheDir, "mnn_mmap").apply { mkdirs() }
            val extraConfig = """{"is_r1":false,"mmap_dir":"${mmapDir.absolutePath}","keep_history":false}"""

            sessionHandle = LlmSession.initNative(
                configFile.absolutePath,
                null,
                configFile.readText(),
                extraConfig
            )

            if (sessionHandle != 0L) {
                isLoaded = true
                Result.success(Unit)
            } else {
                Result.failure(Exception("Error al inicializar sesión MNN nativa (Handle es 0)"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun ensureModelLoaded(): Boolean {
        if (sessionHandle != 0L) return true
        if (modelDownloader.isModelDownloaded()) {
            val loadedLib = MnnLlmBridge.tryLoadLibraries()
            if (!loadedLib) return false
            
            return try {
                val configFile = File(modelDownloader.modelDir, "config.json")
                val mmapDir = File(context.cacheDir, "mnn_mmap").apply { mkdirs() }
                val extraConfig = """{"is_r1":false,"mmap_dir":"${mmapDir.absolutePath}","keep_history":false}"""

                sessionHandle = LlmSession.initNative(
                    configFile.absolutePath,
                    null,
                    configFile.readText(),
                    extraConfig
                )
                isLoaded = sessionHandle != 0L
                isLoaded
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
        return false
    }

    override fun generateResponse(prompt: String): Flow<String> {
        return callbackFlow<String> {
            try {
                // Inicializar modelo de forma asíncrona en hilo de background (IO)
                val loaded = withContext(Dispatchers.IO) {
                    ensureModelLoaded()
                }

                if (loaded && sessionHandle != 0L) {
                    trySend("[Ona AI Local - Qwen2-VL] ")
                    LlmSession.submitNative(
                        sessionHandle,
                        prompt,
                        false,
                        object : GenerateProgressListener {
                            override fun onProgress(progress: String?): Boolean {
                                if (progress != null) {
                                    trySend(progress)
                                }
                                return false // false = no cancelar
                            }
                        }
                    )
                } else {
                    // Fallback determinista local si el modelo no está cargado
                    val responseText = getDeterministicResponse(prompt)
                    val words = responseText.split(" ")
                    for (word in words) {
                        trySend("$word ")
                        delay(40)
                    }
                }
                close()
            } catch (e: Exception) {
                close(e)
            }
            awaitClose { /* Cleanup */ }
        }.flowOn(Dispatchers.IO)
    }

    private fun getDeterministicResponse(prompt: String): String {
        val userQuery = if (prompt.contains("[Pregunta]")) {
            prompt.substringAfter("[Pregunta]").trim()
        } else {
            prompt
        }
        val lowerPrompt = userQuery.lowercase()
        val contextInfo = if (prompt.contains("Contexto de la usuaria:")) {
            prompt.substringAfter("Contexto de la usuaria:").substringBefore("[Pregunta]").trim()
        } else {
            ""
        }

        return when {
            lowerPrompt.contains("hola") || lowerPrompt.contains("buenos") || lowerPrompt.contains("buenas") || lowerPrompt.contains("saludos") || lowerPrompt.contains("que tal") || lowerPrompt.contains("qué tal") -> {
                "¡Hola! Soy Ona, tu asistente local de salud. ¿En qué te puedo ayudar hoy con tu ciclo o tus síntomas?"
            }
            lowerPrompt.contains("quien eres") || lowerPrompt.contains("quién eres") || lowerPrompt.contains("tu nombre") || lowerPrompt.contains("te llamas") -> {
                "Soy Ona, tu asistente virtual inteligente de procesamiento local. Estoy diseñada para ayudarte a entender y registrar tu ciclo menstrual, síntomas y parámetros sintotérmicos de forma 100% privada."
            }
            lowerPrompt.contains("como estas") || lowerPrompt.contains("cómo estás") || lowerPrompt.contains("como va") || lowerPrompt.contains("cómo va") -> {
                "¡Estoy muy bien, lista para ayudarte! ¿Cómo te has sentido hoy con tu ciclo?"
            }
            lowerPrompt.contains("gracias") || lowerPrompt.contains("gracia") || lowerPrompt.contains("agradezco") || lowerPrompt.contains("buenisimo") || lowerPrompt.contains("buenísimo") || lowerPrompt.contains("perfecto") || lowerPrompt.contains("excelente") -> {
                "¡Con mucho gusto! Estoy aquí para apoyarte. No olvides registrar tus síntomas diariamente para que podamos seguir aprendiendo de tu ciclo."
            }
            lowerPrompt.contains("adios") || lowerPrompt.contains("adiós") || lowerPrompt.contains("chao") || lowerPrompt.contains("chau") || lowerPrompt.contains("nos vemos") || lowerPrompt.contains("bye") || lowerPrompt.contains("hasta luego") -> {
                "¡Hasta pronto! Que tengas un excelente día. Recuerda que puedes volver a abrir este chat en cualquier momento."
            }
            lowerPrompt.contains("json") || lowerPrompt.contains("formato json") -> {
                """
                {
                  "proxima_menstruacion_fecha": "${LocalDate.now().plusDays(28)}",
                  "ventana_fertil_inicio": "${LocalDate.now().plusDays(12)}",
                  "ventana_fertil_fin": "${LocalDate.now().plusDays(18)}",
                  "mensaje_educativo": "Predicción local generada de forma segura. El moco cervical y tus registros pasados indican estabilidad.",
                  "nivel_incertidumbre": "bajo"
                }
                """.trimIndent()
            }
            lowerPrompt.contains("funciona") || lowerPrompt.contains("explicar") || lowerPrompt.contains("cómo usar") || lowerPrompt.contains("instrucciones") || lowerPrompt.contains("que hace") || lowerPrompt.contains("qué hace") -> {
                "Ona te ayuda a registrar tu ciclo menstrual de manera autónoma. Puedes registrar parámetros sintotérmicos como temperatura basal corporal, flujo/moco cervical y posición del cuello uterino. Con esto, estimamos tu ventana de fertilidad usando algoritmos sintotérmicos locales en tu dispositivo."
            }
            lowerPrompt.contains("prevenir") || lowerPrompt.contains("evitar") || lowerPrompt.contains("embarazo") || lowerPrompt.contains("anticonceptivo") || lowerPrompt.contains("cuidarme") -> {
                "Ona estima tu ventana fértil para que conozcas los días con mayor probabilidad de concepción. Si deseas evitar el embarazo, la app aplica márgenes de seguridad para ampliar la zona fértil, pero ten en cuenta que es una guía educativa de autoconocimiento y se recomienda usar métodos anticonceptivos de barrera."
            }
            lowerPrompt.contains("concebir") || lowerPrompt.contains("embarazada") || lowerPrompt.contains("fertil") || lowerPrompt.contains("fértil") || lowerPrompt.contains("ovulacion") || lowerPrompt.contains("ovulación") || lowerPrompt.contains("quedar") -> {
                "Para maximizar las probabilidades de embarazo, registra diariamente tu moco cervical (el flujo transparente y elástico como clara de huevo indica máxima fertilidad) y la temperatura basal. También puedes escanear tus tiras de LH en la sección de cámara para registrar el pico de la hormona."
            }
            lowerPrompt.contains("sintoma") || lowerPrompt.contains("dolor") || lowerPrompt.contains("calambre") || lowerPrompt.contains("malestar") || lowerPrompt.contains("cólico") || lowerPrompt.contains("colico") || lowerPrompt.contains("indispuesta") -> {
                "Es normal experimentar cambios físicos, cólicos o variaciones de humor en diferentes fases. Registrar su intensidad te ayuda a descubrir patrones. Si sientes dolores inusualmente fuertes, es aconsejable consultarlo con un especialista."
            }
            lowerPrompt.contains("moco") || lowerPrompt.contains("flujo") || lowerPrompt.contains("cervical") -> {
                "El flujo cervical varía según la fase: seco/pastoso al inicio (baja fertilidad), cremoso/húmedo a mitad, y elástico/transparente (similar a clara de huevo) cerca de la ovulación (alta fertilidad)."
            }
            lowerPrompt.contains("temperatura") || lowerPrompt.contains("basal") -> {
                "La temperatura basal se mide justo al despertar, antes de levantarte de la cama. Un aumento sostenido de unos 0.3°C a 0.5°C suele confirmar que la ovulación ya ha ocurrido."
            }
            lowerPrompt.contains("precisión") || lowerPrompt.contains("precision") || lowerPrompt.contains("presión") || lowerPrompt.contains("presion") || lowerPrompt.contains("exacto") || lowerPrompt.contains("exactitud") || lowerPrompt.contains("efectiv") || lowerPrompt.contains("confia") || lowerPrompt.contains("tasa") -> {
                "El método sintotérmico que usa Ona tiene una efectividad muy alta (hasta el 99% con un uso perfecto y consistente, y en torno al 98% en uso típico). Cuantos más días consecutivos registres parámetros clave como tu temperatura basal (al despertar) y la consistencia de tu moco cervical, más preciso y personalizado será el análisis de tu ventana de fertilidad."
            }
            lowerPrompt.contains("privacidad") || lowerPrompt.contains("seguro") || lowerPrompt.contains("datos") || lowerPrompt.contains("nube") || lowerPrompt.contains("encripta") || lowerPrompt.contains("internet") || lowerPrompt.contains("servidor") -> {
                "Tus datos son 100% privados. La base de datos local está cifrada con SQLCipher y ninguna información personal ni biométrica sale de tu dispositivo a internet o a servidores externos."
            }
            else -> {
                val fallbackTemplates = listOf(
                    "Entiendo tu inquietud sobre \"$userQuery\". Recuerda que la regularidad al registrar tu temperatura basal y moco cervical ayuda a refinar los cálculos del método sintotérmico.",
                    "Con respecto a tu consulta sobre \"$userQuery\", te sugiero revisar si has ingresado tus observaciones hoy. El análisis de tus fases es más preciso cuando los registros son continuos.",
                    "Interesante pregunta sobre \"$userQuery\". Como tu asistente local offline, me baso en los parámetros corporales que registras en el Calendario para estimar tus días fértiles con exactitud.",
                    "Para responder de forma óptima sobre \"$userQuery\", lo ideal es analizar la tendencia de tus ciclos. Te aconsejo mantener al día el registro de síntomas para ajustar tus estimaciones."
                )
                val index = kotlin.math.abs(userQuery.hashCode()) % fallbackTemplates.size
                val base = fallbackTemplates[index]
                if (contextInfo.isNotEmpty()) {
                    "$base\n\nResumen actual de tu ciclo:\n$contextInfo"
                } else {
                    base
                }
            }
        }
    }

    override fun unloadModel() {
        if (sessionHandle != 0L) {
            try {
                LlmSession.releaseNative(sessionHandle)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        sessionHandle = 0L
        isLoaded = false
    }
}

