package com.ona.miciclo.auth.domain.model

/**
 * Modelo de dominio para el usuario autenticado.
 * Abstrae la implementación de Firebase Auth.
 */
data class AuthUser(
    val uid: String,
    val email: String?,
    val displayName: String?,
    val isEmailVerified: Boolean = false,
    val provider: AuthProvider = AuthProvider.EMAIL
)

/**
 * Proveedores de autenticación soportados.
 */
enum class AuthProvider {
    EMAIL,
    GOOGLE
}
