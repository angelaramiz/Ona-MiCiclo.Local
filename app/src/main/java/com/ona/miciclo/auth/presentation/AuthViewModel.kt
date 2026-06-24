package com.ona.miciclo.auth.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ona.miciclo.auth.domain.model.AuthUser
import com.ona.miciclo.auth.domain.repository.AuthRepository
import com.ona.miciclo.auth.domain.usecase.ResetPasswordUseCase
import com.ona.miciclo.auth.domain.usecase.SignInWithEmailUseCase
import com.ona.miciclo.auth.domain.usecase.SignInWithGoogleUseCase
import com.ona.miciclo.auth.domain.usecase.SignUpWithEmailUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel para las pantallas de autenticación.
 * Usa StateFlow exclusivamente — no LiveData.
 */
@HiltViewModel
class AuthViewModel @Inject constructor(
    private val signInWithEmailUseCase: SignInWithEmailUseCase,
    private val signInWithGoogleUseCase: SignInWithGoogleUseCase,
    private val signUpWithEmailUseCase: SignUpWithEmailUseCase,
    private val resetPasswordUseCase: ResetPasswordUseCase,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    /** Estado reactivo del usuario actual */
    val currentUser: StateFlow<AuthUser?> = authRepository.currentUser

    val isAuthenticated: Boolean
        get() = authRepository.isAuthenticated

    fun signInWithEmail(email: String, password: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            signInWithEmailUseCase(email, password)
                .onSuccess { user ->
                    _uiState.update { it.copy(isLoading = false, isSuccess = true, error = null) }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isLoading = false, error = error.message) }
                }
        }
    }

    fun signInWithGoogle(idToken: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            signInWithGoogleUseCase(idToken)
                .onSuccess { user ->
                    _uiState.update { it.copy(isLoading = false, isSuccess = true, error = null) }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isLoading = false, error = error.message) }
                }
        }
    }

    fun signUpWithEmail(email: String, password: String, confirmPassword: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            signUpWithEmailUseCase(email, password, confirmPassword)
                .onSuccess { user ->
                    _uiState.update { it.copy(isLoading = false, isSuccess = true, error = null) }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isLoading = false, error = error.message) }
                }
        }
    }

    fun resetPassword(email: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            resetPasswordUseCase(email)
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            resetEmailSent = true,
                            error = null
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isLoading = false, error = error.message) }
                }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun resetState() {
        _uiState.value = AuthUiState()
    }
}

/**
 * Estado de la UI de autenticación.
 */
data class AuthUiState(
    val isLoading: Boolean = false,
    val isSuccess: Boolean = false,
    val error: String? = null,
    val resetEmailSent: Boolean = false
)
