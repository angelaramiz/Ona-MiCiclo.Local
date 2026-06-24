package com.ona.miciclo.core.security

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Utilidades de encriptación para export/import de datos.
 *
 * Algoritmo: AES-256-GCM con clave derivada de PBKDF2.
 * La contraseña maestra la proporciona el usuario al exportar/importar.
 *
 * Formato del archivo encriptado:
 * [SALT (16 bytes)] [IV (12 bytes)] [CIPHERTEXT (variable)] [AUTH TAG (16 bytes, incluido en GCM)]
 *
 * SEGURIDAD:
 * - PBKDF2 con 210,000 iteraciones (OWASP 2024 recommendation)
 * - Salt aleatorio por cada exportación
 * - IV aleatorio por cada operación de encriptación
 * - GCM proporciona autenticación integrada (detecta tampering)
 */
object CryptoUtils {

    private const val ALGORITHM = "AES/GCM/NoPadding"
    private const val KEY_ALGORITHM = "AES"
    private const val PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val KEY_LENGTH = 256 // bits
    private const val IV_LENGTH = 12 // bytes (recomendado para GCM)
    private const val SALT_LENGTH = 16 // bytes
    private const val GCM_TAG_LENGTH = 128 // bits
    private const val PBKDF2_ITERATIONS = 210_000 // OWASP 2024 recommendation

    /**
     * Encripta un string JSON con una contraseña maestra del usuario.
     *
     * @param data Datos en texto plano (JSON serializado)
     * @param masterPassword Contraseña proporcionada por el usuario
     * @return ByteArray con formato [SALT][IV][CIPHERTEXT+TAG]
     * @throws Exception si la encriptación falla
     */
    fun encryptJson(data: String, masterPassword: String): ByteArray {
        require(masterPassword.length >= 8) {
            "La contraseña maestra debe tener al menos 8 caracteres"
        }

        val salt = generateRandomBytes(SALT_LENGTH)
        val iv = generateRandomBytes(IV_LENGTH)
        val key = deriveKey(masterPassword, salt)

        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH, iv))

        val encryptedData = cipher.doFinal(data.toByteArray(Charsets.UTF_8))

        // Concatenar: salt + iv + datos encriptados (incluye tag de autenticación GCM)
        return salt + iv + encryptedData
    }

    /**
     * Desencripta datos previamente encriptados con [encryptJson].
     *
     * @param encrypted ByteArray con formato [SALT][IV][CIPHERTEXT+TAG]
     * @param masterPassword Contraseña proporcionada por el usuario
     * @return String JSON desencriptado
     * @throws javax.crypto.AEADBadTagException si la contraseña es incorrecta o datos corrompidos
     */
    fun decryptJson(encrypted: ByteArray, masterPassword: String): String {
        require(encrypted.size > SALT_LENGTH + IV_LENGTH) {
            "Datos encriptados inválidos: tamaño insuficiente"
        }

        val salt = encrypted.copyOfRange(0, SALT_LENGTH)
        val iv = encrypted.copyOfRange(SALT_LENGTH, SALT_LENGTH + IV_LENGTH)
        val ciphertext = encrypted.copyOfRange(SALT_LENGTH + IV_LENGTH, encrypted.size)

        val key = deriveKey(masterPassword, salt)

        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH, iv))

        val decryptedData = cipher.doFinal(ciphertext)
        return String(decryptedData, Charsets.UTF_8)
    }

    // ── Private helpers ──

    /**
     * Deriva una clave AES-256 de la contraseña del usuario usando PBKDF2.
     *
     * PUNTO CRÍTICO: Las iteraciones altas (210K) hacen que ataques de fuerza bruta
     * sean computacionalmente costosos. Esto es intencional aunque cause un pequeño
     * delay (~500ms) al exportar/importar.
     */
    private fun deriveKey(password: String, salt: ByteArray): SecretKeySpec {
        val factory = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM)
        val spec = PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_LENGTH)
        val secretKey = factory.generateSecret(spec)
        return SecretKeySpec(secretKey.encoded, KEY_ALGORITHM)
    }

    private fun generateRandomBytes(length: Int): ByteArray {
        val bytes = ByteArray(length)
        SecureRandom().nextBytes(bytes)
        return bytes
    }
}
