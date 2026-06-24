package com.ona.miciclo.auth.domain.usecase

import com.ona.miciclo.auth.domain.model.AuthUser
import com.ona.miciclo.auth.domain.repository.AuthRepository
import javax.inject.Inject

class SignInWithGoogleUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(idToken: String): Result<AuthUser> {
        if (idToken.isBlank()) return Result.failure(Exception("Token de Google inválido"))
        return authRepository.signInWithGoogle(idToken)
    }
}
