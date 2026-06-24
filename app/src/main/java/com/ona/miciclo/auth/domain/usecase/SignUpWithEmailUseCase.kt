package com.ona.miciclo.auth.domain.usecase

import com.ona.miciclo.auth.domain.model.AuthUser
import com.ona.miciclo.auth.domain.repository.AuthRepository
import javax.inject.Inject

class SignUpWithEmailUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(email: String, password: String, confirmPassword: String): Result<AuthUser> {
        if (email.isBlank()) return Result.failure(Exception("El email no puede estar vacío"))
        if (!email.contains("@")) return Result.failure(Exception("El formato del email no es válido"))
        if (password.isBlank()) return Result.failure(Exception("La contraseña no puede estar vacía"))
        if (password.length < 6) return Result.failure(Exception("La contraseña debe tener al menos 6 caracteres"))
        if (password != confirmPassword) return Result.failure(Exception("Las contraseñas no coinciden"))
        return authRepository.signUpWithEmail(email.trim(), password)
    }
}
