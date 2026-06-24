package com.ona.miciclo.data.mapper

import com.ona.miciclo.calendar.domain.model.DailyLog
import com.ona.miciclo.calendar.domain.model.FlowLevel
import com.ona.miciclo.data.local.entity.DailyLogEntity
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Mappers entre entidad Room y modelo de dominio para registros diarios.
 */
object DailyLogMapper {

    private val gson = Gson()

    fun entityToDomain(entity: DailyLogEntity): DailyLog {
        return DailyLog(
            id = entity.id,
            userId = entity.userId,
            fecha = entity.fecha,
            nivelFlujo = entity.nivelFlujo?.let { FlowLevel.fromString(it) } ?: FlowLevel.NONE,
            sintomasBasicos = parseSymptoms(entity.sintomasBasicos),
            temperaturaBasal = entity.temperaturaBasal,
            mocoCervical = entity.mocoCervical,
            posicionCervical = entity.posicionCervical,
            resultadoTiraLh = entity.resultadoTiraLh,
            notas = entity.notas,
            createdAt = entity.createdAt
        )
    }

    fun domainToEntity(domain: DailyLog): DailyLogEntity {
        return DailyLogEntity(
            id = domain.id,
            userId = domain.userId,
            fecha = domain.fecha,
            nivelFlujo = domain.nivelFlujo.value,
            sintomasBasicos = if (domain.sintomasBasicos.isEmpty()) null else gson.toJson(domain.sintomasBasicos),
            temperaturaBasal = domain.temperaturaBasal,
            mocoCervical = domain.mocoCervical,
            posicionCervical = domain.posicionCervical,
            resultadoTiraLh = domain.resultadoTiraLh,
            notas = domain.notas,
            createdAt = domain.createdAt
        )
    }

    private fun parseSymptoms(json: String?): List<String> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val type = object : TypeToken<List<String>>() {}.type
            gson.fromJson(json, type)
        } catch (e: Exception) {
            emptyList()
        }
    }
}
