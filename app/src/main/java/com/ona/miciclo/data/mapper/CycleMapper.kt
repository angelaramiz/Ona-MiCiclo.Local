package com.ona.miciclo.data.mapper

import com.ona.miciclo.calendar.domain.model.CycleRecord
import com.ona.miciclo.data.local.entity.CycleRecordEntity

/**
 * Mappers entre entidad Room y modelo de dominio para ciclos.
 * Mantiene la capa de dominio libre de dependencias de Room.
 */
object CycleMapper {

    fun entityToDomain(entity: CycleRecordEntity): CycleRecord {
        return CycleRecord(
            id = entity.id,
            userId = entity.userId,
            fechaInicioMenstruacion = entity.fechaInicioMenstruacion,
            duracionSangrado = entity.duracionSangrado,
            duracionCiclo = entity.duracionCiclo,
            metodoRegistrado = entity.metodoRegistrado,
            objetivoUsuario = entity.objetivoUsuario,
            cicloConfirmado = entity.cicloConfirmado,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt
        )
    }

    fun domainToEntity(domain: CycleRecord): CycleRecordEntity {
        return CycleRecordEntity(
            id = domain.id,
            userId = domain.userId,
            fechaInicioMenstruacion = domain.fechaInicioMenstruacion,
            duracionSangrado = domain.duracionSangrado,
            duracionCiclo = domain.duracionCiclo,
            metodoRegistrado = domain.metodoRegistrado,
            objetivoUsuario = domain.objetivoUsuario,
            cicloConfirmado = domain.cicloConfirmado,
            createdAt = domain.createdAt,
            updatedAt = domain.updatedAt
        )
    }
}
