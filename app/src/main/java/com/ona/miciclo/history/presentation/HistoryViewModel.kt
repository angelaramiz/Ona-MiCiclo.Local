package com.ona.miciclo.history.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ona.miciclo.auth.domain.repository.AuthRepository
import com.ona.miciclo.calendar.domain.model.CycleRecord
import com.ona.miciclo.history.domain.usecase.GetCycleHistoryUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val getCycleHistoryUseCase: GetCycleHistoryUseCase,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    private val userId: String
        get() = authRepository.currentUser.value?.uid ?: ""

    init {
        loadHistory()
    }

    private fun loadHistory() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            getCycleHistoryUseCase(userId)
                .catch { e ->
                    // Error de BD (SQLCipher, migración fallida, etc.) — no crashear
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = "No se pudo leer el historial: ${e.localizedMessage}"
                        )
                    }
                }
                .collect { records ->
                    val avg = if (records.size >= 2) {
                        val average = records.map { it.duracionCiclo }.average()
                        if (average.isNaN() || average.isInfinite()) null else average.toInt()
                    } else null

                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            records = records,
                            averageCycleLength = avg,
                            totalCycles = records.size,
                            error = null
                        )
                    }
                }
        }
    }
}

data class HistoryUiState(
    val records: List<CycleRecord> = emptyList(),
    val averageCycleLength: Int? = null,
    val totalCycles: Int = 0,
    val isLoading: Boolean = true,
    val error: String? = null
)

