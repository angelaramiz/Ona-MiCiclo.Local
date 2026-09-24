package com.ona.miciclo.dashboard.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ona.miciclo.auth.domain.repository.AuthRepository
import com.ona.miciclo.calendar.domain.model.CyclePrediction
import com.ona.miciclo.calendar.domain.usecase.CalculateCyclePredictionUseCase
import com.ona.miciclo.calendar.domain.repository.CycleRepository
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
    @dagger.hilt.android.qualifiers.ApplicationContext private val appContext: android.content.Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    private var activeUserId: String = ""

    init {
        refresh()
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
        }
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
    val error: String? = null
)
