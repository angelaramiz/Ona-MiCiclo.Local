package com.ona.miciclo.settings.domain.usecase

import com.ona.miciclo.settings.domain.repository.ExportImportRepository
import javax.inject.Inject

class ImportDataUseCase @Inject constructor(
    private val exportImportRepository: ExportImportRepository
) {
    suspend operator fun invoke(
        encryptedData: ByteArray,
        masterPassword: String,
        userId: String
    ): Result<Unit> {
        if (masterPassword.isBlank()) {
            return Result.failure(Exception("La contraseña no puede estar vacía"))
        }
        return exportImportRepository.importData(encryptedData, masterPassword, userId)
    }
}
