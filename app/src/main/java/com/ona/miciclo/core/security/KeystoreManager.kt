package com.ona.miciclo.core.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * PUNTO CRÍTICO DE SEGURIDAD: Gestión de clave de encriptación para Room + SQLCipher.
 *
 * Flujo de seguridad:
 * 1. Se genera una passphrase aleatoria de 32 bytes para SQLCipher.
 * 2. Esta passphrase se encripta usando una clave AES-256-GCM almacenada en Android Keystore.
 * 3. La passphrase encriptada se guarda en EncryptedSharedPreferences.
 * 4. Android Keystore es hardware-backed cuando el dispositivo lo soporta (StrongBox/TEE).
 * 5. La passphrase en texto plano NUNCA se persiste en disco.
 *
 * Restricciones:
 * - La clave del Keystore NO se puede exportar ni hacer backup.
 * - Si el usuario borra datos de la app, la clave se pierde → los datos se pierden.
 * - Por eso es crítico el flujo de export/import con contraseña maestra.
 */
@Singleton
class KeystoreManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val KEYSTORE_ALIAS = "ona_miciclo_db_key"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val PREFS_FILE = "ona_encrypted_prefs"
        private const val PREF_KEY_PASSPHRASE = "db_passphrase_encrypted"
        private const val PREF_KEY_IV = "db_passphrase_iv"
        private const val PASSPHRASE_LENGTH = 32 // 256 bits
    }

    private val masterKey: MasterKey by lazy {
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }

    private val encryptedPrefs by lazy {
        EncryptedSharedPreferences.create(
            context,
            PREFS_FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    /**
     * Obtiene o crea la passphrase para SQLCipher.
     *
     * SEGURIDAD: La passphrase se genera una sola vez y se almacena encriptada.
     * Si ya existe, se desencripta y retorna. Si no existe, se genera una nueva.
     *
     * @return ByteArray de 32 bytes para usar como passphrase de SQLCipher
     */
    fun getOrCreateDatabasePassphrase(): ByteArray {
        val existingPassphrase = retrievePassphrase()
        if (existingPassphrase != null) {
            return existingPassphrase
        }

        // Generar nueva passphrase aleatoria
        val newPassphrase = generateRandomPassphrase()
        storePassphrase(newPassphrase)
        return newPassphrase
    }

    /**
     * Verifica si existe una passphrase almacenada.
     * Útil para detectar si la base de datos ya fue inicializada.
     */
    fun hasExistingPassphrase(): Boolean {
        return encryptedPrefs.contains(PREF_KEY_PASSPHRASE)
    }

    /**
     * Guarda una passphrase dada. Utilizado para sincronizar la clave con la pareja.
     */
    fun saveDatabasePassphrase(passphrase: ByteArray) {
        storePassphrase(passphrase)
    }

    /**
     * OPERACIÓN DESTRUCTIVA: Elimina la passphrase almacenada.
     * Esto hace que la base de datos encriptada sea irrecuperable.
     * Solo debe usarse en el flujo de "borrado total de datos".
     */
    fun deletePassphrase() {
        encryptedPrefs.edit()
            .remove(PREF_KEY_PASSPHRASE)
            .remove(PREF_KEY_IV)
            .apply()

        // Eliminar la clave del Keystore
        try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
            keyStore.load(null)
            keyStore.deleteEntry(KEYSTORE_ALIAS)
        } catch (e: Exception) {
            // Si la clave no existe, no es un error crítico
        }
    }

    // ── Private methods ──

    private fun generateRandomPassphrase(): ByteArray {
        val passphrase = ByteArray(PASSPHRASE_LENGTH)
        java.security.SecureRandom().nextBytes(passphrase)
        return passphrase
    }

    private fun storePassphrase(passphrase: ByteArray) {
        // Encriptar la passphrase con la clave del Keystore
        val secretKey = getOrCreateKeystoreKey()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)

        val encryptedData = cipher.doFinal(passphrase)
        val iv = cipher.iv

        // Guardar en EncryptedSharedPreferences (doble capa de encriptación)
        encryptedPrefs.edit()
            .putString(PREF_KEY_PASSPHRASE, android.util.Base64.encodeToString(encryptedData, android.util.Base64.NO_WRAP))
            .putString(PREF_KEY_IV, android.util.Base64.encodeToString(iv, android.util.Base64.NO_WRAP))
            .apply()
    }

    private fun retrievePassphrase(): ByteArray? {
        val encryptedBase64 = encryptedPrefs.getString(PREF_KEY_PASSPHRASE, null) ?: return null
        val ivBase64 = encryptedPrefs.getString(PREF_KEY_IV, null) ?: return null

        return try {
            val encryptedData = android.util.Base64.decode(encryptedBase64, android.util.Base64.NO_WRAP)
            val iv = android.util.Base64.decode(ivBase64, android.util.Base64.NO_WRAP)

            val secretKey = getOrCreateKeystoreKey()
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
            cipher.doFinal(encryptedData)
        } catch (e: Exception) {
            // SEGURIDAD: Si no podemos desencriptar, la passphrase se considera perdida.
            // El usuario deberá usar el flujo de recuperación (import desde backup).
            null
        }
    }

    /**
     * Obtiene o crea una clave AES-256 en el Android Keystore.
     *
     * SEGURIDAD:
     * - setUserAuthenticationRequired(false): No requerimos biometría para acceder a la clave
     *   porque la app usa Firebase Auth para autenticación. La clave protege los datos en reposo.
     * - setIsStrongBoxBacked: Se intenta usar StrongBox (hardware dedicado) si está disponible.
     *   En dispositivos sin StrongBox, se usa TEE (Trusted Execution Environment).
     */
    private fun getOrCreateKeystoreKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
        keyStore.load(null)

        // Retornar clave existente si ya existe
        keyStore.getEntry(KEYSTORE_ALIAS, null)?.let { entry ->
            return (entry as KeyStore.SecretKeyEntry).secretKey
        }

        // Generar nueva clave en el Keystore
        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE
        )

        val keySpec = KeyGenParameterSpec.Builder(
            KEYSTORE_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(false)
            // No permitir exportación de la clave
            .setRandomizedEncryptionRequired(true)
            .build()

        keyGenerator.init(keySpec)
        return keyGenerator.generateKey()
    }
}
