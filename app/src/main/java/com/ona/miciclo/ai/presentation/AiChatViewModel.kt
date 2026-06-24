package com.ona.miciclo.ai.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ona.miciclo.ai.domain.IInferenceEngine
import com.ona.miciclo.ai.domain.PromptBuilder
import com.ona.miciclo.auth.domain.repository.AuthRepository
import com.ona.miciclo.calendar.domain.repository.CycleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

data class AiChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val isGenerating: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class AiChatViewModel @Inject constructor(
    private val inferenceEngine: IInferenceEngine,
    private val cycleRepository: CycleRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AiChatUiState())
    val uiState: StateFlow<AiChatUiState> = _uiState.asStateFlow()

    private val userId: String
        get() = authRepository.currentUser.value?.uid ?: ""

    init {
        // Mensaje de bienvenida inicial de la IA
        _uiState.update {
            it.copy(
                messages = listOf(
                    ChatMessage(
                        text = "¡Hola! Soy tu asistente local de salud Ona. Puedes consultarme sobre tus ciclos, síntomas sintotérmicos o predicciones. Todo nuestro procesamiento se realiza de forma local en tu dispositivo.",
                        isUser = false
                    )
                )
            )
        }
    }

    fun sendMessage(text: String) {
        if (text.isBlank() || _uiState.value.isGenerating) return

        val userMessage = ChatMessage(text = text, isUser = true)
        _uiState.update {
            it.copy(
                messages = it.messages + userMessage,
                isGenerating = true
            )
        }

        viewModelScope.launch {
            try {
                // Obtener datos históricos del ciclo actual
                val ciclos = cycleRepository.getLastCycleRecords(userId, 3)
                // Obtener registros diarios recientes para contexto sintotérmico
                val logs = cycleRepository.getAllDailyLogsSync(userId)

                // Generar contexto biológico del usuario
                val promptContext = if (ciclos.isNotEmpty() || logs.isNotEmpty()) {
                    "Contexto de la usuaria:\n" +
                            "- Ciclos recientes: ${ciclos.size} registrados.\n" +
                            "- Registros de moco/temperatura en el mes: ${logs.size}.\n" +
                            "- Última temperatura: ${logs.lastOrNull()?.temperaturaBasal?.let { "$it°C" } ?: "no registrada"}.\n" +
                            "- Último moco cervical: ${logs.lastOrNull()?.mocoCervical ?: "no registrado"}.\n"
                } else {
                    "La usuaria no tiene registros previos."
                }

                // Construcción de la consulta estructurada con el template oficial de chat de Gemma sin espacios extra
                val systemPrompt = "<start_of_turn>user\n" +
                        "Instrucciones: Eres Ona, la asistente de salud e IA local de esta app de ciclo menstrual. Responde en español de forma breve, empática y clara. No tienes conexión a internet, por lo que toda la información es 100% privada y local.\n" +
                        "$promptContext\n\n" +
                        "$text<end_of_turn>\n" +
                        "<start_of_turn>model\n"

                // Crear un mensaje vacío para el asistente
                val assistantMessageId = UUID.randomUUID().toString()
                val initialAssistantMessage = ChatMessage(id = assistantMessageId, text = "", isUser = false)
                
                _uiState.update {
                    it.copy(messages = it.messages + initialAssistantMessage)
                }

                var fullResponseText = ""
                inferenceEngine.generateResponse(systemPrompt).collect { token ->
                    fullResponseText += token
                    _uiState.update { state ->
                        state.copy(
                            messages = state.messages.map { msg ->
                                if (msg.id == assistantMessageId) {
                                    msg.copy(text = fullResponseText)
                                } else {
                                    msg
                                }
                            }
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Error al procesar: ${e.message}") }
            } finally {
                _uiState.update { it.copy(isGenerating = false) }
            }
        }
    }

    private suspend fun delay(time: Long) {
        kotlinx.coroutines.delay(time)
    }
}
