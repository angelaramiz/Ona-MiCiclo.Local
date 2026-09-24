package com.ona.miciclo.calendar.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ona.miciclo.auth.domain.repository.AuthRepository
import com.ona.miciclo.calendar.domain.model.CyclePrediction
import com.ona.miciclo.calendar.domain.model.CycleRecord
import com.ona.miciclo.calendar.domain.model.DailyLog
import com.ona.miciclo.calendar.domain.model.FlowLevel
import com.ona.miciclo.calendar.domain.model.PartnerSuggestions
import com.ona.miciclo.calendar.domain.usecase.CalculateCyclePredictionUseCase
import com.ona.miciclo.calendar.domain.usecase.GetMonthDataUseCase
import com.ona.miciclo.calendar.domain.usecase.SaveDailyLogUseCase
import com.ona.miciclo.calendar.domain.repository.CycleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

import com.ona.miciclo.data.local.dao.UserPreferencesDao
import com.ona.miciclo.core.sync.SupabaseSyncManager
import com.ona.miciclo.core.notification.NotificationHelper
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext

@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val getMonthDataUseCase: GetMonthDataUseCase,
    private val saveDailyLogUseCase: SaveDailyLogUseCase,
    private val calculateCyclePredictionUseCase: CalculateCyclePredictionUseCase,
    private val cycleRepository: CycleRepository,
    private val authRepository: AuthRepository,
    private val userPreferencesDao: UserPreferencesDao,
    private val syncManager: SupabaseSyncManager,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(CalendarUiState())
    val uiState: StateFlow<CalendarUiState> = _uiState.asStateFlow()

    private var activeUserId: String = ""
    private var lastSuggestionId: String? = null
    private val notificationHelper = NotificationHelper(context)

    private val userId: String
        get() = activeUserId.ifEmpty { authRepository.currentUser.value?.uid ?: "" }

    init {
        viewModelScope.launch {
            val myUid = authRepository.currentUser.value?.uid ?: ""
            val prefs = userPreferencesDao.getByUserId(myUid)
            val isPartner = prefs?.userRole == "partner"
            activeUserId = if (isPartner && !prefs?.linkedUserId.isNullOrEmpty()) {
                prefs?.linkedUserId!!
            } else {
                myUid
            }
            // #region debug-point D:calendar-role-resolution
            com.ona.miciclo.core.debug.DebugTelemetry.emit(
                hypothesisId = "D",
                location = "CalendarViewModel:init",
                msg = "[DEBUG] Resolucion de rol y usuario activo en calendario",
                data = org.json.JSONObject()
                    .put("myUid", myUid)
                    .put("isPartner", isPartner)
                    .put("linkedUserId", prefs?.linkedUserId ?: "null")
                    .put("activeUserId", activeUserId)
            )
            // #endregion
            _uiState.update { it.copy(isReadOnly = isPartner) }
            loadCurrentMonth()
            loadPrediction()
            loadPendingSuggestions()
            
            // Cargar sugerencias periódicamente cada 30 segundos si es la usuaria principal
            if (!isPartner) {
                while (true) {
                    kotlinx.coroutines.delay(30000)
                    loadPendingSuggestions()
                }
            }
        }
        // Auto-refresh: sincroniza y recarga el calendario cada minuto (silencioso).
        viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(60_000)
                refresh(showFeedback = false)
            }
        }
    }

    /**
     * Refresco manual (pull-to-refresh) o automático del calendario.
     * - Partner: fuerza la descarga de los datos de la hostess.
     * - Hostess/solo: fuerza la subida de sus datos locales.
     * Después recarga el mes, la predicción y las sugerencias pendientes.
     */
    fun refresh(showFeedback: Boolean = true) {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            try {
                val myUid = authRepository.currentUser.value?.uid ?: ""
                val prefs = userPreferencesDao.getByUserId(myUid)
                if (prefs?.userRole == "partner" && !prefs.linkedUserId.isNullOrEmpty()) {
                    syncManager.refreshPartnerData(prefs.linkedUserId!!)
                } else if (myUid.isNotEmpty()) {
                    syncManager.syncHostessDataToCloud(myUid)
                }
                loadCurrentMonth()
                loadPrediction()
                loadPendingSuggestions()
                loadMySuggestionStatus()
                _uiState.update {
                    if (showFeedback) it.copy(
                        isRefreshing = false,
                        lastSyncTimeMillis = System.currentTimeMillis(),
                        message = "Datos actualizados ✓"
                    )
                    else it.copy(
                        isRefreshing = false,
                        lastSyncTimeMillis = System.currentTimeMillis()
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    if (showFeedback) it.copy(isRefreshing = false, error = "Error al actualizar: ${e.message}")
                    else it.copy(isRefreshing = false)
                }
            }
        }
    }

    fun loadCurrentMonth() {
        val now = YearMonth.now()
        loadMonth(now.year, now.monthValue)
    }

    fun loadMonth(year: Int, month: Int) {
        _uiState.update { it.copy(currentYearMonth = YearMonth.of(year, month)) }

        viewModelScope.launch {
            getMonthDataUseCase(userId, year, month).collect { logs ->
                _uiState.update { it.copy(monthLogs = logs) }
            }
        }
    }

    fun navigateToNextMonth() {
        val next = _uiState.value.currentYearMonth.plusMonths(1)
        loadMonth(next.year, next.monthValue)
    }

    fun navigateToPreviousMonth() {
        val prev = _uiState.value.currentYearMonth.minusMonths(1)
        loadMonth(prev.year, prev.monthValue)
    }

    fun selectDate(date: LocalDate) {
        _uiState.update { it.copy(selectedDate = date) }
        loadDailyLogForDate(date)
        viewModelScope.launch {
            val cycleRecord = cycleRepository.getCycleRecordByDate(userId, date)
            _uiState.update { it.copy(isSelectedDatePeriodStart = cycleRecord != null) }
        }
    }

    fun loadPrediction() {
        viewModelScope.launch {
            try {
                val prediction = calculateCyclePredictionUseCase(userId)
                _uiState.update { it.copy(prediction = prediction) }
                // Widget (A3): persistir snapshot para la pantalla principal.
                try {
                    com.ona.miciclo.core.widget.OnaWidgetProvider.refresh(
                        context, prediction, _uiState.value.isReadOnly
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(predictionError = e.message) }
            } finally {
                // Determinar si hay datos de ciclo guardados (sirve para mostrar el botón de inicio)
                val count = cycleRepository.getCycleRecordCount(userId)
                _uiState.update { it.copy(hasAnyCycleData = count > 0) }
            }
        }
    }

    fun saveDailyLog(
        date: LocalDate,
        flowLevel: FlowLevel,
        symptoms: List<String>,
        basalTemp: Double?,
        mocoCervical: String? = null,
        posicionCervical: String? = null,
        resultadoTiraLh: String? = null,
        notes: String?
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }

            val log = DailyLog(
                userId = userId,
                fecha = date,
                nivelFlujo = flowLevel,
                sintomasBasicos = symptoms,
                temperaturaBasal = basalTemp,
                mocoCervical = mocoCervical,
                posicionCervical = posicionCervical,
                resultadoTiraLh = resultadoTiraLh,
                notas = notes
            )

            saveDailyLogUseCase(log)
                .onSuccess {
                    _uiState.update { it.copy(isSaving = false, saveSuccess = true) }
                    // Recargar predicción después de guardar
                    loadPrediction()
                    val myUid = authRepository.currentUser.value?.uid ?: ""
                    if (!_uiState.value.isReadOnly && myUid.isNotEmpty()) {
                        syncManager.syncHostessDataToCloud(myUid)
                    }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isSaving = false, error = error.message) }
                }
        }
    }

    /**
     * Registra el inicio de un nuevo periodo menstrual.
     * Esto crea un nuevo CycleRecord y opcionalmente confirma el ciclo anterior.
     */
    fun startNewPeriod(date: LocalDate, bleedingDuration: Int = 5) {
        viewModelScope.launch {
            try {
                // Confirmar ciclo anterior si existe
                val previousRecord = cycleRepository.getLatestCycleRecord(userId)
                if (previousRecord != null && !previousRecord.cicloConfirmado) {
                    val actualDuration = java.time.temporal.ChronoUnit.DAYS.between(
                        previousRecord.fechaInicioMenstruacion, date
                    ).toInt()
                    cycleRepository.updateCycleRecord(
                        previousRecord.copy(
                            duracionCiclo = actualDuration,
                            cicloConfirmado = true,
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                }

                // Crear nuevo registro de ciclo
                val newRecord = CycleRecord(
                    userId = userId,
                    fechaInicioMenstruacion = date,
                    duracionSangrado = bleedingDuration
                )
                cycleRepository.saveCycleRecord(newRecord)

                // Recargar datos
                loadPrediction()
                val ym = _uiState.value.currentYearMonth
                loadMonth(ym.year, ym.monthValue)
                val myUid = authRepository.currentUser.value?.uid ?: ""
                if (!_uiState.value.isReadOnly && myUid.isNotEmpty()) {
                    syncManager.syncHostessDataToCloud(myUid)
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    fun suggestPeriodStart(date: LocalDate) =
        sendSuggestion(date, PartnerSuggestions.START_PERIOD)

    fun suggestOvulationDay(date: LocalDate) =
        sendSuggestion(date, PartnerSuggestions.OVULATION_DAY)

    private fun sendSuggestion(date: LocalDate, type: String) {
        viewModelScope.launch {
            try {
                val myUid = authRepository.currentUser.value?.uid ?: ""
                syncManager.sendPartnerSuggestion(userId, myUid, date, type)
                loadMySuggestionStatus()
                _uiState.update { it.copy(message = PartnerSuggestions.sentMessage(type)) }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Error al sugerir: ${e.localizedMessage}") }
            }
        }
    }

    /**
     * Carga el estado de la última sugerencia enviada por el partner
     * (para mostrarle si sigue pendiente, fue aprobada o rechazada).
     */
    fun loadMySuggestionStatus() {
        val myUid = authRepository.currentUser.value?.uid ?: ""
        viewModelScope.launch {
            try {
                val prefs = userPreferencesDao.getByUserId(myUid)
                if (prefs?.userRole != "partner") {
                    _uiState.update { it.copy(mySuggestionStatus = null) }
                    return@launch
                }
                val latest = syncManager.getLatestPartnerSuggestion(myUid)
                _uiState.update {
                    it.copy(
                        mySuggestionStatus = latest?.let { s ->
                            PartnerSuggestions.statusLine(s.suggestion_type, s.suggested_date, s.status)
                        }
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun loadPendingSuggestions() {
        val myUid = authRepository.currentUser.value?.uid ?: ""
        viewModelScope.launch {
            val prefs = userPreferencesDao.getByUserId(myUid)
            if (prefs?.userRole == "partner") return@launch
            try {
                val suggestions = syncManager.getPendingSuggestions(myUid)
                if (suggestions.isNotEmpty()) {
                    val newSuggestion = suggestions.first()
                    // Verificar si es una sugerencia nueva para mostrar notificación
                    if (lastSuggestionId != newSuggestion.id) {
                        lastSuggestionId = newSuggestion.id
                        notificationHelper.showPartnerSuggestionNotification(newSuggestion.suggested_date, newSuggestion.suggestion_type)
                    }
                    _uiState.update { it.copy(pendingSuggestion = newSuggestion) }
                } else {
                    _uiState.update { it.copy(pendingSuggestion = null) }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun approveSuggestion(suggestion: SupabaseSyncManager.PartnerSuggestionRow) {
        viewModelScope.launch {
            try {
                val suggestedDate = LocalDate.parse(suggestion.suggested_date)
                when (suggestion.suggestion_type) {
                    PartnerSuggestions.OVULATION_DAY -> confirmOvulationDay(suggestedDate)
                    PartnerSuggestions.START_PERIOD -> startNewPeriod(suggestedDate)
                    // Tipo futuro/desconocido: solo marcar, sin efectos (seguridad)
                }
                syncManager.updateSuggestionStatus(suggestion.id!!, "APPROVED")
                _uiState.update { it.copy(pendingSuggestion = null, message = PartnerSuggestions.approveMessage(suggestion.suggestion_type)) }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Error al aprobar sugerencia: ${e.localizedMessage}") }
            }
        }
    }

    /**
     * Registra un día de ovulación confirmado por la pareja: fusiona el síntoma
     * "ovulacion" en el DailyLog de esa fecha (creándolo si no existe) y sincroniza.
     * Es suspend (sin launch interno) para que approveSuggestion lo complete
     * antes de marcar la sugerencia como APPROVED.
     */
    private suspend fun confirmOvulationDay(date: LocalDate) {
        val existing = cycleRepository.getDailyLogByDate(userId, date)
        val merged = PartnerSuggestions.withOvulationConfirmed(existing, userId, date)
        saveDailyLogUseCase(merged)
            .onSuccess {
                loadPrediction()
                val ym = _uiState.value.currentYearMonth
                loadMonth(ym.year, ym.monthValue)
                val myUid = authRepository.currentUser.value?.uid ?: ""
                if (myUid.isNotEmpty()) {
                    syncManager.syncHostessDataToCloud(myUid)
                }
            }
            .onFailure { error ->
                throw error
            }
    }

    fun rejectSuggestion(suggestion: SupabaseSyncManager.PartnerSuggestionRow) {
        viewModelScope.launch {
            try {
                syncManager.updateSuggestionStatus(suggestion.id!!, "REJECTED")
                _uiState.update { it.copy(pendingSuggestion = null, message = "Sugerencia rechazada") }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Error al rechazar sugerencia: ${e.localizedMessage}") }
            }
        }
    }

    fun deletePeriodStart(date: LocalDate) {
        viewModelScope.launch {
            try {
                val record = cycleRepository.getCycleRecordByDate(userId, date)
                if (record != null) {
                    cycleRepository.deleteCycleRecord(record.id)
                    loadPrediction()
                    val ym = _uiState.value.currentYearMonth
                    loadMonth(ym.year, ym.monthValue)
                    selectDate(date)
                    val myUid = authRepository.currentUser.value?.uid ?: ""
                    if (!_uiState.value.isReadOnly && myUid.isNotEmpty()) {
                        syncManager.syncHostessDataToCloud(myUid)
                    }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun clearSaveSuccess() {
        _uiState.update { it.copy(saveSuccess = false) }
    }

    private fun loadDailyLogForDate(date: LocalDate) {
        viewModelScope.launch {
            val log = cycleRepository.getDailyLogByDate(userId, date)
            _uiState.update { it.copy(selectedDayLog = log) }
        }
    }
}

data class CalendarUiState(
    val currentYearMonth: YearMonth = YearMonth.now(),
    val selectedDate: LocalDate? = null,
    val monthLogs: List<DailyLog> = emptyList(),
    val selectedDayLog: DailyLog? = null,
    val prediction: CyclePrediction? = null,
    val predictionError: String? = null,
    val isSaving: Boolean = false,
    val saveSuccess: Boolean = false,
    val error: String? = null,
    val isReadOnly: Boolean = false,
    val isRefreshing: Boolean = false,
    /** Última sincronización exitosa (nube). Null = aún no sincronizado. */
    val lastSyncTimeMillis: Long? = null,
    val isSelectedDatePeriodStart: Boolean = false,
    val pendingSuggestion: SupabaseSyncManager.PartnerSuggestionRow? = null,
    /** Estado de la última sugerencia enviada por el partner (solo modo pareja). */
    val mySuggestionStatus: String? = null,
    val message: String? = null,
    /**
     * true  → hay al menos un CycleRecord guardado → predicción disponible o en camino.
     * false → base de datos vacía (borrado de datos o primera instalación).
     * Controla la visibilidad del botón de inicialización de calendario.
     */
    val hasAnyCycleData: Boolean = true
)
