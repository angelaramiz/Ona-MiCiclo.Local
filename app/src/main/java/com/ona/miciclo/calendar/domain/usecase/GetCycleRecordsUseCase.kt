package com.ona.miciclo.calendar.domain.usecase

import com.ona.miciclo.calendar.domain.model.CycleRecord
import com.ona.miciclo.calendar.domain.repository.CycleRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetCycleRecordsUseCase @Inject constructor(
    private val cycleRepository: CycleRepository
) {
    operator fun invoke(userId: String): Flow<List<CycleRecord>> {
        return cycleRepository.getAllCycleRecords(userId)
    }
}
