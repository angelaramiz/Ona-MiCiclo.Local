package com.ona.miciclo.auth.domain.usecase

import com.ona.miciclo.auth.domain.repository.AuthRepository
import javax.inject.Inject

class ResetPasswordUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(email: String): Result<Unit> {
        if (email.isBlank()) return Result.failure(Exception("El email no puede estar vacío"))
        if (!email.contains("@")) return Result.failure(Exception("El formato del email no es válido"))
        return authRepository.resetPassword(email.trim())
    }
}
