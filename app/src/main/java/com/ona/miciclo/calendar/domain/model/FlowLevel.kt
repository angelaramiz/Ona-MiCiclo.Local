package com.ona.miciclo.calendar.domain.model

/**
 * Niveles de flujo menstrual.
 * Cada valor tiene una representación string para persistencia en Room.
 */
enum class FlowLevel(val value: String, val displayName: String) {
    NONE("ninguno", "Sin flujo"),
    LIGHT("ligero", "Ligero"),
    MODERATE("moderado", "Moderado"),
    HEAVY("abundante", "Abundante");

    companion object {
        fun fromString(value: String): FlowLevel {
            return entries.find { it.value == value } ?: NONE
        }
    }
}
