package com.ona.miciclo.auth.domain.repository

import com.ona.miciclo.auth.domain.model.AuthUser
import kotlinx.coroutines.flow.StateFlow

/**
 * Interfaz del repositorio de autenticación.
 *
 * Desacoplada de Firebase — permite testing con mocks
 * y futura migración a otro proveedor sin cambiar la capa domain.
 *
 * NOTA: Solo se usa Firebase para autenticación de identidad.
 * NINGÚN dato de salud se transmite a Firebase ni a ningún servidor.
 */
interface AuthRepository {

    /** Estado reactivo del usuario actual. Null si no está autenticado. */
    val currentUser: StateFlow<AuthUser?>

    /** Indica si hay un usuario autenticado actualmente */
    val isAuthenticated: Boolean

    /**
     * Inicia sesión con email y contraseña.
     * @return Result con AuthUser en caso de éxito, o excepción en caso de error
     */
    suspend fun signInWithEmail(email: String, password: String): Result<AuthUser>

    /**
     * Inicia sesión con Google via Credential Manager.
     * @param idToken Token obtenido del Credential Manager
     * @return Result con AuthUser en caso de éxito
     */
    suspend fun signInWithGoogle(idToken: String): Result<AuthUser>

    /**
     * Registra un nuevo usuario con email y contraseña.
     * @return Result con AuthUser en caso de éxito
     */
    suspend fun signUpWithEmail(email: String, password: String): Result<AuthUser>

    /**
     * Envía email de recuperación de contraseña.
     * @return Result<Unit> en caso de éxito
     */
    suspend fun resetPassword(email: String): Result<Unit>

    /**
     * Cierra la sesión actual.
     */
    suspend fun signOut()
}
