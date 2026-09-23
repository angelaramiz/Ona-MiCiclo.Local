package com.ona.miciclo.data.local

import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter
import java.time.LocalDate

/**
 * Gson TypeAdapter compartido para `LocalDate`.
 *
 * Serializa como ISO string ("2026-09-15"). Lo usan tanto el sync de pareja
 * (`SupabaseSyncManager`, que sube/descarga entidades a Supabase) como el
 * export/import de respaldos (`ExportImportRepositoryImpl`).
 *
 * SIN este adapter, Gson intenta serializar `LocalDate` por reflexión (falla en
 * JVM 17 y produce objetos {"year":...} en Android), rompiendo el sync.
 */
class LocalDateAdapter : TypeAdapter<LocalDate>() {
    override fun write(out: JsonWriter, value: LocalDate?) {
        if (value == null) out.nullValue()
        else out.value(value.toString())
    }

    override fun read(`in`: JsonReader): LocalDate? {
        return if (`in`.peek() == JsonToken.NULL) {
            `in`.nextNull()
            null
        } else {
            LocalDate.parse(`in`.nextString())
        }
    }
}
