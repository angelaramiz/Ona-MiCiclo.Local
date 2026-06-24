package com.ona.miciclo.data.local

import androidx.room.TypeConverter
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Type converters para Room.
 * Convierte tipos que Room no soporta nativamente (LocalDate) a tipos almacenables.
 */
class Converters {
    private val formatter = DateTimeFormatter.ISO_LOCAL_DATE

    @TypeConverter
    fun fromLocalDate(date: LocalDate?): String? {
        return date?.format(formatter)
    }

    @TypeConverter
    fun toLocalDate(dateString: String?): LocalDate? {
        return dateString?.let { LocalDate.parse(it, formatter) }
    }
}
