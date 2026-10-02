package com.ona.miciclo.dashboard.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ona.miciclo.auth.domain.repository.AuthRepository
import com.ona.miciclo.calendar.domain.model.CoupleNote
import com.ona.miciclo.calendar.domain.model.CoupleNotes
import com.ona.miciclo.calendar.domain.model.CyclePrediction
import com.ona.miciclo.calendar.domain.usecase.CalculateCyclePredictionUseCase
import com.ona.miciclo.calendar.domain.repository.CycleRepository
import com.ona.miciclo.core.sync.SupabaseSyncManager
import com.ona.miciclo.core.widget.NotaWidgetData
import com.ona.miciclo.core.widget.NotaWidgetProvider
import com.ona.miciclo.core.widget.NotaWidgetStore
import com.ona.miciclo.data.local.dao.UserPreferencesDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val userPreferencesDao: UserPreferencesDao,
    private val cycleRepository: CycleRepository,
    private val calculateCyclePredictionUseCase: CalculateCyclePredictionUseCase,
    private val syncManager: SupabaseSyncManager,
    @dagger.hilt.android.qualifiers.ApplicationContext private val appContext: android.content.Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    private var activeUserId: String = ""

    init {
        refresh()
        // Notitas en vivo: recarga cada 30s (mismo ritmo que las sugerencias).
        viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(30_000)
                loadNotes()
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            val myUid = authRepository.currentUser.value?.uid.orEmpty()
            val prefs = userPreferencesDao.getByUserId(myUid)
            val isPartner = prefs?.userRole == "partner"
            val linkedUserId = prefs?.linkedUserId

            activeUserId = if (isPartner && !linkedUserId.isNullOrEmpty()) linkedUserId else myUid

            _uiState.update {
                it.copy(
                    myUserId = myUid,
                    isPartner = isPartner,
                    linkedUserId = linkedUserId,
                    isLoading = true,
                    error = null
                )
            }

            val count = runCatching { cycleRepository.getCycleRecordCount(activeUserId) }.getOrNull() ?: 0
            val latestPeriodStart = runCatching {
                cycleRepository.getLatestCycleRecord(activeUserId)?.fechaInicioMenstruacion
            }.getOrNull()

            val predictionResult = runCatching { calculateCyclePredictionUseCase(activeUserId) }
            val prediction = predictionResult.getOrNull()
            // Insights con reglas (A5): predicción + registros de los últimos 30 días.
            val insights = runCatching {
                val logs = cycleRepository.getAllDailyLogsSync(activeUserId)
                    .filter { it.fecha >= LocalDate.now().minusDays(30) }
                com.ona.miciclo.ai.domain.CycleInsightProvider.insights(prediction, logs)
            }.getOrDefault(emptyList())
            // Modo íntimo en pareja (B3): solo con vínculo y objetivo elegido.
            val coupleGuidance = runCatching {
                val linked = !linkedUserId.isNullOrEmpty()
                val goal = if (isPartner) {
                    com.ona.miciclo.core.notification.ReminderPrefs(appContext)
                        .coupleGoal.ifEmpty { null }
                } else {
                    com.ona.miciclo.calendar.domain.model.CoupleGuidance.fromHostessObjective(
                        prefs?.objetivoUsuario
                    )
                }
                if (linked) {
                    com.ona.miciclo.calendar.domain.model.CoupleGuidance.forCouple(
                        prediction, LocalDate.now(), goal, isPartner
                    )
                } else {
                    null
                }
            }.getOrNull()
            _uiState.update {
                it.copy(
                    isLoading = false,
                    hasAnyCycleData = count > 0,
                    latestPeriodStart = latestPeriodStart,
                    prediction = prediction,
                    insights = insights,
                    coupleGuidance = coupleGuidance,
                    error = predictionResult.exceptionOrNull()?.localizedMessage
                )
            }
            loadNotes()
        }
    }

    /** Ids del vínculo para las notitas (null si no hay pareja vinculada). */
    private fun noteIds(): Triple<String, String, String>? {
        val s = _uiState.value
        val linked = s.linkedUserId
        if (s.myUserId.isEmpty() || linked.isNullOrEmpty()) return null
        val hostessId = if (s.isPartner) linked else s.myUserId
        val partnerId = if (s.isPartner) s.myUserId else linked
        return Triple(hostessId, partnerId, s.myUserId)
    }

    /** Carga las últimas notitas (silencioso: lo llama el poll de 30s). */
    fun loadNotes() {
        val ids = noteIds() ?: run {
            _uiState.update { it.copy(notes = emptyList(), notesError = null) }
            return
        }
        viewModelScope.launch {
            val notes = runCatching {
                syncManager.getCoupleNotes(ids.first, ids.third)
            }
            notes.onSuccess { list ->
                _uiState.update { it.copy(notes = list, notesError = null) }
                pushNoteToWidget(list)
            }.onFailure { e ->
                _uiState.update { it.copy(notesError = notesHint(e)) }
            }
        }
    }

    /** Envía una notita y recarga (el widget se actualiza en ambas). */
    fun sendNote(rawText: String) {
        val text = rawText.trim()
        if (!CoupleNotes.isValid(text)) return
        val ids = noteIds() ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(noteSending = true, notesError = null) }
            runCatching {
                syncManager.sendCoupleNote(ids.first, ids.second, ids.third, text)
            }.onSuccess {
                _uiState.update { it.copy(noteSending = false, noteDraft = "") }
                loadNotes()
            }.onFailure { e ->
                _uiState.update { it.copy(noteSending = false, notesError = notesHint(e)) }
            }
        }
    }

    /** Borra una notita propia. */
    fun deleteNote(note: CoupleNote) {
        if (!note.isMine || note.id.isEmpty()) return
        viewModelScope.launch {
            runCatching { syncManager.deleteCoupleNote(note.id) }
                .onSuccess { loadNotes() }
                .onFailure { e ->
                    _uiState.update { it.copy(notesError = notesHint(e)) }
                }
        }
    }

    fun onNoteDraftChange(text: String) {
        if (text.length <= CoupleNotes.MAX_LEN + 20) {
            _uiState.update { it.copy(noteDraft = text) }
        }
    }

    /** Si la tabla no existe, pista accionable en vez de error críptico. */
    private fun notesHint(e: Throwable): String {
        val msg = e.message.orEmpty()
        return if ("couple_notes" in msg || "404" in msg || "relation" in msg) {
            "Las notitas aún no están activadas: falta crear la tabla couple_notes en Supabase."
        } else {
            "No se pudieron cargar las notitas: $msg"
        }
    }

    /** Empuja la más reciente al widget post-it (solo si cambió). */
    private fun pushNoteToWidget(notes: List<CoupleNote>) {
        val newest = notes.firstOrNull() ?: return
        val now = System.currentTimeMillis()
        val saved = NotaWidgetStore(appContext).saveLatest(
            NotaWidgetData(
                senderLabel = CoupleNotes.senderLabel(newest.isMine),
                text = newest.text,
                timeAgo = CoupleNotes.timeAgoEs(now, newest.createdAtMillis),
                createdAt = newest.createdAtMillis
            )
        )
        if (saved) NotaWidgetProvider.requestUpdate(appContext)
    }
}

data class DashboardUiState(
    val myUserId: String = "",
    val isPartner: Boolean = false,
    val linkedUserId: String? = null,
    val isLoading: Boolean = false,
    val hasAnyCycleData: Boolean = true,
    val latestPeriodStart: LocalDate? = null,
    val prediction: CyclePrediction? = null,
    val insights: List<String> = emptyList(),
    val coupleGuidance: com.ona.miciclo.calendar.domain.model.CoupleGuidance.Result? = null,
    val error: String? = null,
    val notes: List<CoupleNote> = emptyList(),
    val notesError: String? = null,
    val noteDraft: String = "",
    val noteSending: Boolean = false
)
