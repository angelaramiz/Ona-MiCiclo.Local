package com.ona.miciclo.settings.domain.repository

/**
 * Interfaz del repositorio de exportación/importación.
 */
interface ExportImportRepository {
    /**
     * Exporta todos los datos del usuario como JSON encriptado.
     * @param userId ID del usuario
     * @param masterPassword Contraseña maestra para encriptación
     * @return ByteArray encriptado listo para guardar en archivo
     */
    suspend fun exportData(userId: String, masterPassword: String): Result<ByteArray>

    /**
     * Importa datos desde un archivo JSON encriptado.
     * @param encryptedData Datos encriptados leídos del archivo
     * @param masterPassword Contraseña maestra para desencriptación
     * @param userId ID del usuario destino
     */
    suspend fun importData(encryptedData: ByteArray, masterPassword: String, userId: String): Result<Unit>

    /**
     * Elimina TODOS los datos del usuario de forma irreversible.
     */
    suspend fun deleteAllData(userId: String): Result<Unit>
}
