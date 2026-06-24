package com.ona.miciclo.auth.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.ona.miciclo.auth.domain.model.AuthProvider
import com.ona.miciclo.auth.domain.model.AuthUser
import com.ona.miciclo.auth.domain.repository.AuthRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementación de AuthRepository usando Firebase Auth.
 *
 * IMPORTANTE:
 * - Solo se usa Firebase para AUTENTICACIÓN de identidad.
 * - NO se transmiten datos de salud, ciclo, síntomas, ni cualquier dato biométrico.
 * - El UID de Firebase se usa SOLO como identificador local en Room.
 * - No se habilita Firebase Analytics ni ningún otro servicio de Firebase.
 */
@Singleton
class AuthRepositoryImpl @Inject constructor() : AuthRepository {

    private val firebaseAuth: FirebaseAuth = FirebaseAuth.getInstance()

    private val _currentUser = MutableStateFlow<AuthUser?>(
        firebaseAuth.currentUser?.toDomainUser()
    )
    override val currentUser: StateFlow<AuthUser?> = _currentUser.asStateFlow()

    override val isAuthenticated: Boolean
        get() = firebaseAuth.currentUser != null

    init {
        // Escuchar cambios de estado de autenticación
        firebaseAuth.addAuthStateListener { auth ->
            _currentUser.value = auth.currentUser?.toDomainUser()
        }
    }

    override suspend fun signInWithEmail(email: String, password: String): Result<AuthUser> {
        return try {
            val result = firebaseAuth.signInWithEmailAndPassword(email, password).await()
            val user = result.user?.toDomainUser()
                ?: return Result.failure(Exception("No se pudo obtener el usuario después del inicio de sesión"))
            _currentUser.value = user
            Result.success(user)
        } catch (e: Exception) {
            Result.failure(mapFirebaseException(e))
        }
    }

    override suspend fun signInWithGoogle(idToken: String): Result<AuthUser> {
        return try {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            val result = firebaseAuth.signInWithCredential(credential).await()
            val user = result.user?.toDomainUser(AuthProvider.GOOGLE)
                ?: return Result.failure(Exception("No se pudo obtener el usuario de Google"))
            _currentUser.value = user
            Result.success(user)
        } catch (e: Exception) {
            Result.failure(mapFirebaseException(e))
        }
    }

    override suspend fun signUpWithEmail(email: String, password: String): Result<AuthUser> {
        return try {
            val result = firebaseAuth.createUserWithEmailAndPassword(email, password).await()
            val user = result.user?.toDomainUser()
                ?: return Result.failure(Exception("No se pudo crear el usuario"))
            _currentUser.value = user
            Result.success(user)
        } catch (e: Exception) {
            Result.failure(mapFirebaseException(e))
        }
    }

    override suspend fun resetPassword(email: String): Result<Unit> {
        return try {
            firebaseAuth.sendPasswordResetEmail(email).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(mapFirebaseException(e))
        }
    }

    override suspend fun signOut() {
        firebaseAuth.signOut()
        _currentUser.value = null
    }

    // ── Private helpers ──

    /**
     * Mapea excepciones de Firebase a mensajes amigables en español.
     */
    private fun mapFirebaseException(e: Exception): Exception {
        val message = when {
            e.message?.contains("INVALID_LOGIN_CREDENTIALS") == true ->
                "Email o contraseña incorrectos"
            e.message?.contains("EMAIL_EXISTS") == true ||
            e.message?.contains("email-already-in-use") == true ->
                "Ya existe una cuenta con este email"
            e.message?.contains("WEAK_PASSWORD") == true ->
                "La contraseña debe tener al menos 6 caracteres"
            e.message?.contains("INVALID_EMAIL") == true ->
                "El formato del email no es válido"
            e.message?.contains("USER_NOT_FOUND") == true ->
                "No existe una cuenta con este email"
            e.message?.contains("TOO_MANY_ATTEMPTS") == true ->
                "Demasiados intentos. Espera unos minutos e intenta de nuevo"
            e.message?.contains("NETWORK") == true ->
                "Error de conexión. Verifica tu internet"
            else -> e.message ?: "Error de autenticación desconocido"
        }
        return Exception(message)
    }

    private fun com.google.firebase.auth.FirebaseUser.toDomainUser(
        provider: AuthProvider = AuthProvider.EMAIL
    ): AuthUser {
        return AuthUser(
            uid = uid,
            email = email,
            displayName = displayName,
            isEmailVerified = isEmailVerified,
            provider = provider
        )
    }
}
