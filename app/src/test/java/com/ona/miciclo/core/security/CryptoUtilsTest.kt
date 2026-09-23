package com.ona.miciclo.core.security

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import javax.crypto.AEADBadTagException

/**
 * Caracterización TDD del invariante criptográfico que hace funcionar el sync de pareja:
 * la hostess cifra su DB passphrase y sus payloads con [CryptoUtils], y la pareja debe
 * poder descifrarlos con la MISMA clave (compartida vía el código de invitación).
 *
 * Si cualquiera de estos tests falla, el flujo "la pareja ve los datos" está roto.
 */
class CryptoUtilsTest {

    private val gson = Gson()

    @Test
    fun `roundTrip encrypt then decrypt returns original json`() {
        val original = """{"id":1,"user_id":"hostess-uid","fecha_inicio_menstruacion":"2026-09-10","duracion_sangrado":5}"""
        val encrypted = CryptoUtils.encryptJson(original, "OnaQa2026")

        val decrypted = CryptoUtils.decryptJson(encrypted, "OnaQa2026")

        assertEquals(original, decrypted)
    }

    @Test
    fun `roundTrip with 6-char invitation style code works`() {
        // El código de invitación es un substring UUID de 6 chars (generaInvitationCode).
        val code = "A1B2C3"
        val original = "secret-passphrase-base64"
        val encrypted = CryptoUtils.encryptJson(original, code)
        assertEquals(original, CryptoUtils.decryptJson(encrypted, code))
    }

    @Test
    fun `sync data path cycle entity round trips hostess to partner`() {
        // Escenario real: la hostess sube el JSON de un CycleRecordEntity cifrado con su
        // DB passphrase; la pareja (que recibió la passphrase por la invitación) lo descifra.
        val hostessPassphraseKey = "Zm9vYmFyYmF6cXV4cXV4MTIzNDU2Nzg5MGFiY2RlZg=="
        val entityJson = gson.toJson(
            mapOf(
                "id" to 7L,
                "user_id" to "hostess-uid",
                "fecha_inicio_menstruacion" to "2026-09-10",
                "duracion_sangrado" to 5,
                "duracion_ciclo" to 28,
                "metodo_registrado" to "sintotermico",
                "objetivo_usuario" to "conocimiento",
                "ciclo_confirmado" to true,
                "created_at" to 1_700_000_000_000L,
                "updated_at" to 1_700_000_000_000L
            )
        )

        val encryptedPayload = CryptoUtils.encryptJson(entityJson, hostessPassphraseKey)

        // La pareja lo descifra con la misma clave (la que obtuvo de la invitación).
        val plainJson = CryptoUtils.decryptJson(encryptedPayload, hostessPassphraseKey)

        assertEquals(entityJson, plainJson)
        // Y reconstruye el ciclo para insertarlo en su Room local.
        val reconstructed = gson.fromJson(plainJson, java.util.LinkedHashMap::class.java)
        assertEquals("hostess-uid", reconstructed["user_id"])
        assertEquals("2026-09-10", reconstructed["fecha_inicio_menstruacion"])
    }

    @Test
    fun `decrypt with wrong key fails authentication`() {
        val encrypted = CryptoUtils.encryptJson("datos-secretos", "correcta-key")

        val ex = assertThrows(AEADBadTagException::class.java) {
            CryptoUtils.decryptJson(encrypted, "clave-incorrecta")
        }
        assertTrue(ex.message != null)
    }

    @Test
    fun `decrypt with payload too short is rejected`() {
        // Formato mínimo [SALT 16][IV 12][CT+tag 16+] → 28 bytes mínimo.
        val short = ByteArray(20)
        assertThrows(IllegalArgumentException::class.java) {
            CryptoUtils.decryptJson(short, "clave-larga-suficiente")
        }
    }

    @Test
    fun `master password shorter than 6 chars is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            CryptoUtils.encryptJson("data", "12345")
        }
    }

    @Test
    fun `same plaintext produces different ciphertext each time`() {
        // Salt + IV aleatorios por operación → mismo dato, distinto cifrado.
        val data = "mismo-contenido"
        val a = CryptoUtils.encryptJson(data, "clave-estable")
        val b = CryptoUtils.encryptJson(data, "clave-estable")
        assertNotEquals(a.toList(), b.toList())
        assertEquals(data, CryptoUtils.decryptJson(a, "clave-estable"))
        assertEquals(data, CryptoUtils.decryptJson(b, "clave-estable"))
    }

    @Test
    fun `ciphertext layout is salt plus iv plus payload`() {
        val encrypted = CryptoUtils.encryptJson("hola", "clave12345")
        // El salt (16) y el IV (12) van al inicio, en claro; el resto es cifrado.
        assertTrue(encrypted.size > 16 + 12)
        val salt = encrypted.copyOfRange(0, 16)
        val iv = encrypted.copyOfRange(16, 28)
        assertNotEquals(salt.toList(), ByteArray(16).toList()) // salt no vacío/cero
        assertNotEquals(iv.toList(), ByteArray(12).toList())   // iv no vacío/cero
    }
}