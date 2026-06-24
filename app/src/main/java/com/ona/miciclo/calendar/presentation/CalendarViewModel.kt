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

@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val getMonthDataUseCase: GetMonthDataUseCase,
    private val saveDailyLogUseCase: SaveDailyLogUseCase,
    private val calculateCyclePredictionUseCase: CalculateCyclePredictionUseCase,
    private val cycleRepository: CycleRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CalendarUiState())
    val uiState: StateFlow<CalendarUiState> = _uiState.asStateFlow()

    private val userId: String
        get() = authRepository.currentUser.value?.uid ?: ""

    init {
        loadCurrentMonth()
        loadPrediction()
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
    val error: String? = null
)
