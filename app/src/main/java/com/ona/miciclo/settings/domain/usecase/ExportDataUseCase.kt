package com.ona.miciclo.settings.domain.usecase

import com.ona.miciclo.settings.domain.repository.ExportImportRepository
import javax.inject.Inject

class ExportDataUseCase @Inject constructor(
    private val exportImportRepository: ExportImportRepository
) {
    suspend operator fun invoke(userId: String, masterPassword: String): Result<ByteArray> {
        if (masterPassword.length < 8) {
            return Result.failure(Exception("La contraseña debe tener al menos 8 caracteres"))
        }
        return exportImportRepository.exportData(userId, masterPassword)
    }
}
