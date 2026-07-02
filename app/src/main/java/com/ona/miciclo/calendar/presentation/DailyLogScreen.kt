package com.ona.miciclo.calendar.presentation

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ona.miciclo.calendar.domain.model.FlowLevel
import com.ona.miciclo.core.ui.components.OnaButton
import com.ona.miciclo.core.ui.components.OnaTopBar
import com.ona.miciclo.core.ui.theme.*
import com.ona.miciclo.ocr.presentation.OcrCaptureActivity
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Pantalla de registro diario de síntomas y flujo con soporte para variables sintotérmicas y OCR de tiras de ovulación.
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

    val context = LocalContext.current
    val sharedPrefs = remember { context.getSharedPreferences("ona_screen_prefs", Context.MODE_PRIVATE) }

    // Configuración de visibilidad de variables sintotérmicas
    var showTemp by rememberSaveable { mutableStateOf(sharedPrefs.getBoolean("show_temp", true)) }
    var showMucus by rememberSaveable { mutableStateOf(sharedPrefs.getBoolean("show_mucus", true)) }
    var showPosition by rememberSaveable { mutableStateOf(sharedPrefs.getBoolean("show_position", true)) }
    var showLh by rememberSaveable { mutableStateOf(sharedPrefs.getBoolean("show_lh", true)) }

    LaunchedEffect(showTemp) { sharedPrefs.edit().putBoolean("show_temp", showTemp).apply() }
    LaunchedEffect(showMucus) { sharedPrefs.edit().putBoolean("show_mucus", showMucus).apply() }
    LaunchedEffect(showPosition) { sharedPrefs.edit().putBoolean("show_position", showPosition).apply() }
    LaunchedEffect(showLh) { sharedPrefs.edit().putBoolean("show_lh", showLh).apply() }

    var selectedFlow by rememberSaveable { mutableStateOf(FlowLevel.NONE.value) }
    val selectedSymptoms = remember { mutableStateListOf<String>() }
    var basalTemp by rememberSaveable { mutableStateOf("") }
    var selectedMucus by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedPosition by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedLh by rememberSaveable { mutableStateOf<String?>(null) }
    var notes by rememberSaveable { mutableStateOf("") }
    var isPeriodStart by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(uiState.isSelectedDatePeriodStart) {
        isPeriodStart = uiState.isSelectedDatePeriodStart
    }

    // Estados para diálogos educativos (Tooltips)
    var activeTooltipTitle by remember { mutableStateOf<String?>(null) }
    var activeTooltipText by remember { mutableStateOf<String?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }

    // Launcher para escaneo OCR de tiras
    val ocrLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
        onResult = { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val ocrResult = result.data?.getStringExtra("resultado_ocr")
                if (ocrResult != null) {
                    selectedLh = ocrResult
                }
            }
        }
    )

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
            selectedMucus = log.mocoCervical
            selectedPosition = log.posicionCervical
            selectedLh = log.resultadoTiraLh
            notes = log.notas ?: ""

            // Forzar visibilidad si hay registros previos guardados
            if (log.temperaturaBasal != null) showTemp = true
            if (log.mocoCervical != null) showMucus = true
            if (log.posicionCervical != null) showPosition = true
            if (log.resultadoTiraLh != null) showLh = true
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

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.WaterDrop,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Inicio de periodo menstrual",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
                Switch(
                    checked = isPeriodStart,
                    onCheckedChange = { isPeriodStart = it }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Configuración de Campos Sintotérmicos ──
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Parámetros Sintotérmicos (Fase 2)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Temperatura Basal", style = MaterialTheme.typography.bodyMedium)
                        Switch(checked = showTemp, onCheckedChange = { showTemp = it })
                    }
                    Divider(modifier = Modifier.padding(vertical = 8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Moco Cervical", style = MaterialTheme.typography.bodyMedium)
                        Switch(checked = showMucus, onCheckedChange = { showMucus = it })
                    }
                    Divider(modifier = Modifier.padding(vertical = 8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Posición Cervical", style = MaterialTheme.typography.bodyMedium)
                        Switch(checked = showPosition, onCheckedChange = { showPosition = it })
                    }
                    Divider(modifier = Modifier.padding(vertical = 8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Tiras de Ovulación (LH)", style = MaterialTheme.typography.bodyMedium)
                        Switch(checked = showLh, onCheckedChange = { showLh = it })
                    }
                }
            }

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

            // ── SECCIÓN DE VARIABLES SINTOTÉRMICAS ACTIVAS ──

            if (showTemp) {
                Spacer(modifier = Modifier.height(24.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Temperatura Basal",
                        style = MaterialTheme.typography.titleSmall
                    )
                    IconButton(onClick = {
                        activeTooltipTitle = "Temperatura Basal"
                        activeTooltipText = "Mide tu temperatura inmediatamente al despertar, antes de levantarte de la cama. Hazlo siempre a la misma hora para obtener datos de alta calidad. La calidad disminuye si duermes menos de 4 horas o consumes alcohol la noche anterior."
                    }) {
                        Icon(Icons.Default.Info, contentDescription = "Ayuda", tint = MaterialTheme.colorScheme.primary)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = basalTemp,
                    onValueChange = { value ->
                        if (value.isEmpty() || value.matches(Regex("^\\d{0,2}\\.?\\d{0,2}$"))) {
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
                            Text("Calidad: Rango fisiológico esperado es 35.0°C - 38.5°C")
                        } else {
                            Text("Calidad: Asegúrate de medir con termómetro de dos decimales.")
                        }
                    }
                )
            }

            if (showMucus) {
                Spacer(modifier = Modifier.height(24.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Moco Cervical",
                        style = MaterialTheme.typography.titleSmall
                    )
                    IconButton(onClick = {
                        activeTooltipTitle = "Moco Cervical"
                        activeTooltipText = "El flujo cambia a lo largo del ciclo. El moco tipo 'Clara de Huevo' (transparente, elástico, resbaladizo) indica máxima fertilidad. El moco cremoso/pegajoso es de fertilidad baja a media, y seco es no fértil."
                    }) {
                        Icon(Icons.Default.Info, contentDescription = "Ayuda", tint = MaterialTheme.colorScheme.primary)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))

                val mucusOptions = listOf(
                    "seco" to "Seco",
                    "pegajoso" to "Pegajoso",
                    "cremoso" to "Cremoso",
                    "clara_de_huevo" to "Clara de huevo (Fértil)"
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    mucusOptions.forEach { (value, displayName) ->
                        val isSelected = selectedMucus == value
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedMucus = if (isSelected) null else value },
                            label = { Text(displayName) }
                        )
                    }
                }
            }

            if (showPosition) {
                Spacer(modifier = Modifier.height(24.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Posición Cervical",
                        style = MaterialTheme.typography.titleSmall
                    )
                    IconButton(onClick = {
                        activeTooltipTitle = "Posición Cervical"
                        activeTooltipText = "Evalúa la posición, firmeza y apertura del cuello uterino con las manos limpias. Alta/Blanda/Abierta indica ovulación inminente (fértil). Baja/Firme/Cerrada indica fase no fértil."
                    }) {
                        Icon(Icons.Default.Info, contentDescription = "Ayuda", tint = MaterialTheme.colorScheme.primary)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))

                val positionOptions = listOf(
                    "baja" to "Baja (Firme/Cerrada)",
                    "media" to "Media",
                    "alta" to "Alta (Blanda/Abierta)"
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    positionOptions.forEach { (value, displayName) ->
                        val isSelected = selectedPosition == value
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedPosition = if (isSelected) null else value },
                            label = { Text(displayName) }
                        )
                    }
                }
            }

            if (showLh) {
                Spacer(modifier = Modifier.height(24.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Tiras de Ovulación (LH)",
                        style = MaterialTheme.typography.titleSmall
                    )
                    IconButton(onClick = {
                        activeTooltipTitle = "Escaneo OCR Local de LH"
                        activeTooltipText = "Utiliza la cámara trasera para escanear y clasificar tu tira física de ovulación. El procesamiento es 100% en dispositivo para respetar tu privacidad."
                    }) {
                        Icon(Icons.Default.Info, contentDescription = "Ayuda", tint = MaterialTheme.colorScheme.primary)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val intent = Intent(context, OcrCaptureActivity::class.java)
                            ocrLauncher.launch(intent)
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.CameraAlt, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Escanear Tira")
                    }

                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = when (selectedLh) {
                                "positivo" -> Color(0xFFE91E63).copy(alpha = 0.15f)
                                "debil" -> Color(0xFFFF9800).copy(alpha = 0.15f)
                                "negativo" -> Color(0xFF9E9E9E).copy(alpha = 0.15f)
                                else -> MaterialTheme.colorScheme.surfaceVariant
                            }
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = selectedLh?.uppercase() ?: "SIN ESCANEAR",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = when (selectedLh) {
                                    "positivo" -> Color(0xFFE91E63)
                                    "debil" -> Color(0xFFFF9800)
                                    "negativo" -> Color(0xFF757575)
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }
                }
            }

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

            OnaButton(
                text = "Guardar Registro",
                onClick = {
                    viewModel.saveDailyLog(
                        date = date,
                        flowLevel = FlowLevel.fromString(selectedFlow),
                        symptoms = selectedSymptoms.toList(),
                        basalTemp = if (showTemp) basalTemp.toDoubleOrNull() else null,
                        mocoCervical = if (showMucus) selectedMucus else null,
                        posicionCervical = if (showPosition) selectedPosition else null,
                        resultadoTiraLh = if (showLh) selectedLh else null,
                        notes = notes.ifBlank { null }
                    )
                    if (isPeriodStart && !uiState.isSelectedDatePeriodStart) {
                        viewModel.startNewPeriod(date)
                    } else if (!isPeriodStart && uiState.isSelectedDatePeriodStart) {
                        viewModel.deletePeriodStart(date)
                    }
                },
                isLoading = uiState.isSaving
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // Modal de Tooltip Educativo
    if (activeTooltipTitle != null && activeTooltipText != null) {
        AlertDialog(
            onDismissRequest = {
                activeTooltipTitle = null
                activeTooltipText = null
            },
            title = {
                Text(
                    text = activeTooltipTitle!!,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            },
            text = { Text(activeTooltipText!!) },
            confirmButton = {
                TextButton(onClick = {
                    activeTooltipTitle = null
                    activeTooltipText = null
                }) {
                    Text("Entendido")
                }
            }
        )
    }
}
