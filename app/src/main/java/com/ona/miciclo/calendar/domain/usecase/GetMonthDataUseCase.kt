package com.ona.miciclo.calendar.domain.usecase

import com.ona.miciclo.calendar.domain.model.DailyLog
import com.ona.miciclo.calendar.domain.repository.CycleRepository
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import javax.inject.Inject

/**
 * Obtiene los datos de un mes completo para el calendario.
 */
class GetMonthDataUseCase @Inject constructor(
    private val cycleRepository: CycleRepository
) {
    operator fun invoke(
        userId: String,
        year: Int,
        month: Int
    ): Flow<List<DailyLog>> {
        val startDate = LocalDate.of(year, month, 1)
        val endDate = startDate.plusMonths(1).minusDays(1)
        return cycleRepository.getDailyLogsByDateRange(userId, startDate, endDate)
    }
}
