package com.ona.miciclo.calendar.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ona.miciclo.auth.domain.repository.AuthRepository
import com.ona.miciclo.calendar.domain.model.CyclePrediction
import com.ona.miciclo.calendar.domain.model.CycleRecord
import com.ona.miciclo.calendar.domain.model.DailyLog
import com.ona.miciclo.calendar.domain.model.FlowLevel
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

@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val getMonthDataUseCase: GetMonthDataUseCase,
    private val saveDailyLogUseCase: SaveDailyLogUseCase,
    private val calculateCyclePredictionUseCase: CalculateCyclePredictionUseCase,
    private val cycleRepository: CycleRepository,
    private val authRepository: AuthRepository,
    private val userPreferencesDao: UserPreferencesDao,
    private val syncManager: SupabaseSyncManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(CalendarUiState())
    val uiState: StateFlow<CalendarUiState> = _uiState.asStateFlow()

    private var activeUserId: String = ""

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
            _uiState.update { it.copy(isReadOnly = isPartner) }
            loadCurrentMonth()
            loadPrediction()
            loadPendingSuggestions()
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
            } catch (e: Exception) {
                _uiState.update { it.copy(predictionError = e.message) }
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
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    fun suggestPeriodStart(date: LocalDate) {
        viewModelScope.launch {
            try {
                val myUid = authRepository.currentUser.value?.uid ?: ""
                syncManager.sendPartnerSuggestion(userId, myUid, date)
                _uiState.update { it.copy(message = "Sugerencia enviada a tu pareja ✓") }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Error al sugerir: ${e.localizedMessage}") }
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
                    _uiState.update { it.copy(pendingSuggestion = suggestions.first()) }
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
                startNewPeriod(suggestedDate)
                syncManager.updateSuggestionStatus(suggestion.id!!, "APPROVED")
                _uiState.update { it.copy(pendingSuggestion = null, message = "Sugerencia aprobada y aplicada") }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Error al aprobar sugerencia: ${e.localizedMessage}") }
            }
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
    val isSelectedDatePeriodStart: Boolean = false,
    val pendingSuggestion: SupabaseSyncManager.PartnerSuggestionRow? = null,
    val message: String? = null
)
