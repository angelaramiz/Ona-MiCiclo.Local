package com.ona.miciclo.calendar.domain.usecase

import com.ona.miciclo.calendar.domain.model.DailyLog
import com.ona.miciclo.calendar.domain.repository.CycleRepository
import javax.inject.Inject

class SaveDailyLogUseCase @Inject constructor(
    private val cycleRepository: CycleRepository
) {
    /**
     * Guarda o actualiza un registro diario.
     * Si ya existe un registro para esa fecha, lo actualiza.
     */
    suspend operator fun invoke(log: DailyLog): Result<Long> {
        return try {
            // Validar temperatura si se proporcionó
            if (log.temperaturaBasal != null && log.temperaturaBasal !in 35.0..38.5) {
                return Result.failure(
                    Exception("La temperatura basal debe estar entre 35.0°C y 38.5°C")
                )
            }

            val existingLog = cycleRepository.getDailyLogByDate(log.userId, log.fecha)
            if (existingLog != null) {
                // Actualizar registro existente
                cycleRepository.updateDailyLog(log.copy(id = existingLog.id))
                Result.success(existingLog.id)
            } else {
                // Crear nuevo registro
                val id = cycleRepository.saveDailyLog(log)
                Result.success(id)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
