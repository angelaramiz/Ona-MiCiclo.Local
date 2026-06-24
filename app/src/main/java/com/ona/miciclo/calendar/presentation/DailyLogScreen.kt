package com.ona.miciclo.calendar.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ona.miciclo.calendar.domain.model.FlowLevel
import com.ona.miciclo.core.ui.components.OnaButton
import com.ona.miciclo.core.ui.components.OnaTopBar
import com.ona.miciclo.core.ui.theme.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Pantalla de registro diario de síntomas y flujo.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DailyLogScreen(
    viewModel: CalendarViewModel,
    dateString: String,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val date = LocalDate.parse(dateString)
    val dateFormatter = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", Locale("es"))

    var selectedFlow by rememberSaveable { mutableStateOf(FlowLevel.NONE.value) }
    val selectedSymptoms = remember { mutableStateListOf<String>() }
    var basalTemp by rememberSaveable { mutableStateOf("") }
    var notes by rememberSaveable { mutableStateOf("") }
    val snackbarHostState = remember { SnackbarHostState() }

    // Cargar datos existentes para esta fecha
    LaunchedEffect(date) {
        viewModel.selectDate(date)
    }

    // Pre-rellenar con datos existentes
    LaunchedEffect(uiState.selectedDayLog) {
        uiState.selectedDayLog?.let { log ->
            selectedFlow = log.nivelFlujo.value
            selectedSymptoms.clear()
            selectedSymptoms.addAll(log.sintomasBasicos)
            basalTemp = log.temperaturaBasal?.toString() ?: ""
            notes = log.notas ?: ""
        }
    }

    // Manejar éxito al guardar
    LaunchedEffect(uiState.saveSuccess) {
        if (uiState.saveSuccess) {
            snackbarHostState.showSnackbar("Registro guardado ✓")
            viewModel.clearSaveSuccess()
            onNavigateBack()
        }
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.clearError()
        }
    }

    val availableSymptoms = listOf(
        "calambres", "dolor_cabeza", "fatiga", "sensibilidad_pechos",
        "acne", "hinchazón", "cambios_humor", "dolor_espalda",
        "nauseas", "insomnio", "antojos", "dolor_articulaciones"
    )

    val symptomDisplayNames = mapOf(
        "calambres" to "Calambres",
        "dolor_cabeza" to "Dolor de cabeza",
        "fatiga" to "Fatiga",
        "sensibilidad_pechos" to "Sensibilidad en pechos",
        "acne" to "Acné",
        "hinchazón" to "Hinchazón",
        "cambios_humor" to "Cambios de humor",
        "dolor_espalda" to "Dolor de espalda",
        "nauseas" to "Náuseas",
        "insomnio" to "Insomnio",
        "antojos" to "Antojos",
        "dolor_articulaciones" to "Dolor de articulaciones"
    )

    Scaffold(
        topBar = {
            OnaTopBar(
                title = "Registro Diario",
                onNavigateBack = onNavigateBack
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Fecha
            Text(
                text = date.format(dateFormatter).replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(24.dp))

            // ── Nivel de flujo ──
            Text(
                text = "Nivel de flujo",
                style = MaterialTheme.typography.titleSmall
            )
            Spacer(modifier = Modifier.height(8.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FlowLevel.entries.forEach { level ->
                    val isSelected = selectedFlow == level.value
                    val chipColor = when (level) {
                        FlowLevel.NONE -> FlowNone
                        FlowLevel.LIGHT -> FlowLight
                        FlowLevel.MODERATE -> FlowModerate
                        FlowLevel.HEAVY -> FlowHeavy
                    }

                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedFlow = level.value },
                        label = { Text(level.displayName) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = chipColor.copy(alpha = 0.3f),
                            selectedLabelColor = MaterialTheme.colorScheme.onSurface
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ── Síntomas ──
            Text(
                text = "Síntomas",
                style = MaterialTheme.typography.titleSmall
            )
            Spacer(modifier = Modifier.height(8.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                availableSymptoms.forEach { symptom ->
                    val isSelected = symptom in selectedSymptoms
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            if (isSelected) selectedSymptoms.remove(symptom)
                            else selectedSymptoms.add(symptom)
                        },
                        label = { Text(symptomDisplayNames[symptom] ?: symptom) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ── Temperatura basal (opcional) ──
            Text(
                text = "Temperatura Basal (opcional)",
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                text = "Preparado para el método sintotérmico — Fase 2",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = basalTemp,
                onValueChange = { value ->
                    // Solo permitir números y punto decimal
                    if (value.isEmpty() || value.matches(Regex("^\\d{0,2}\\.?\\d{0,1}$"))) {
                        basalTemp = value
                    }
                },
                label = { Text("°C (35.0 - 38.5)") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                isError = basalTemp.isNotEmpty() && basalTemp.toDoubleOrNull()?.let { it !in 35.0..38.5 } == true,
                supportingText = {
                    if (basalTemp.isNotEmpty() && basalTemp.toDoubleOrNull()?.let { it !in 35.0..38.5 } == true) {
                        Text("Rango válido: 35.0°C - 38.5°C")
                    }
                }
            )

            Spacer(modifier = Modifier.height(24.dp))

            // ── Notas ──
            Text(
                text = "Notas",
                style = MaterialTheme.typography.titleSmall
            )
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Escribe aquí tus observaciones...") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                shape = MaterialTheme.shapes.medium,
                maxLines = 5
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Botón guardar
            OnaButton(
                text = "Guardar Registro",
                onClick = {
                    viewModel.saveDailyLog(
                        date = date,
                        flowLevel = FlowLevel.fromString(selectedFlow),
                        symptoms = selectedSymptoms.toList(),
                        basalTemp = basalTemp.toDoubleOrNull(),
                        notes = notes.ifBlank { null }
                    )
                },
                isLoading = uiState.isSaving
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
