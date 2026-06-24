package com.ona.miciclo.onboarding.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ona.miciclo.auth.domain.repository.AuthRepository
import com.ona.miciclo.data.local.dao.UserPreferencesDao
import com.ona.miciclo.data.local.entity.UserPreferencesEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val userPreferencesDao: UserPreferencesDao,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    private val userId: String
        get() = authRepository.currentUser.value?.uid ?: ""

    init {
        checkOnboardingStatus()
    }

    private fun checkOnboardingStatus() {
        viewModelScope.launch {
            val prefs = userPreferencesDao.getByUserId(userId)
            _uiState.update {
                it.copy(isOnboardingCompleted = prefs?.onboardingCompletado == true)
            }
        }
    }

    fun completeSetup(
        cycleLength: Int,
        bleedingDuration: Int,
        lastPeriodDate: LocalDate?,
        objective: String
    ) {
        viewModelScope.launch {
            try {
                val prefs = UserPreferencesEntity(
                    userId = userId,
                    duracionCicloDefault = cycleLength,
                    duracionSangradoDefault = bleedingDuration,
                    objetivoUsuario = objective,
                    onboardingCompletado = true,
                    ultimaFechaMenstruacion = lastPeriodDate
                )
                userPreferencesDao.insertOrUpdate(prefs)
                _uiState.update { it.copy(isOnboardingCompleted = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }
}

data class OnboardingUiState(
    val isOnboardingCompleted: Boolean = false,
    val error: String? = null
)
