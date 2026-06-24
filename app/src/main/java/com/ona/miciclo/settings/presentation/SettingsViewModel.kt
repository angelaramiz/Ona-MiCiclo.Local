package com.ona.miciclo.settings.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ona.miciclo.auth.domain.repository.AuthRepository
import com.ona.miciclo.auth.domain.usecase.SignOutUseCase
import com.ona.miciclo.settings.domain.repository.ExportImportRepository
import com.ona.miciclo.settings.domain.usecase.ExportDataUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val exportDataUseCase: ExportDataUseCase,
    private val exportImportRepository: ExportImportRepository,
    private val signOutUseCase: SignOutUseCase,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private val userId: String
        get() = authRepository.currentUser.value?.uid ?: ""

    val userEmail: String
        get() = authRepository.currentUser.value?.email ?: ""

    fun exportData(masterPassword: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isExporting = true) }
            exportDataUseCase(userId, masterPassword)
                .onSuccess { data ->
                    _uiState.update {
                        it.copy(isExporting = false, exportedData = data, message = "Datos exportados correctamente")
                    }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isExporting = false, error = error.message) }
                }
        }
    }

    fun deleteAllData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isDeleting = true) }
            exportImportRepository.deleteAllData(userId)
                .onSuccess {
                    _uiState.update {
                        it.copy(isDeleting = false, message = "Todos los datos han sido eliminados")
                    }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isDeleting = false, error = error.message) }
                }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            signOutUseCase()
            _uiState.update { it.copy(isSignedOut = true) }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }
}

data class SettingsUiState(
    val isExporting: Boolean = false,
    val isDeleting: Boolean = false,
    val isSignedOut: Boolean = false,
    val exportedData: ByteArray? = null,
    val message: String? = null,
    val error: String? = null
)
