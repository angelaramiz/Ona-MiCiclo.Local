package com.ona.miciclo.calendar.presentation

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ona.miciclo.calendar.presentation.components.CycleSummaryCard
import com.ona.miciclo.calendar.presentation.components.MonthCalendarGrid
import com.ona.miciclo.core.ui.components.OnaTopBar
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun CalendarScreen(
    viewModel: CalendarViewModel,
    onNavigateToDailyLog: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            OnaTopBar(title = "Mi Ciclo")
        },
        floatingActionButton = {
            if (!uiState.isReadOnly) {
                ExtendedFloatingActionButton(
                    onClick = {
                        val date = uiState.selectedDate ?: java.time.LocalDate.now()
                        onNavigateToDailyLog(date.toString())
                    },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("Registro") },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    ) { padding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // Navegación de mes
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { viewModel.navigateToPreviousMonth() }) {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = "Mes anterior"
                    )
                }

                AnimatedContent(
                    targetState = uiState.currentYearMonth,
                    label = "month_title"
                ) { yearMonth ->
                    Text(
                        text = "${yearMonth.month.getDisplayName(TextStyle.FULL, Locale("es")).replaceFirstChar { it.uppercase() }} ${yearMonth.year}",
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center
                    )
                }

                IconButton(onClick = { viewModel.navigateToNextMonth() }) {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Mes siguiente"
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Calendario
            MonthCalendarGrid(
                yearMonth = uiState.currentYearMonth,
                dailyLogs = uiState.monthLogs,
                prediction = uiState.prediction,
                selectedDate = uiState.selectedDate,
                onDateSelected = { viewModel.selectDate(it) }
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Botón de inicio de periodo
            if (uiState.selectedDate != null && !uiState.isReadOnly) {
                androidx.compose.material3.OutlinedButton(
                    onClick = { viewModel.startNewPeriod(uiState.selectedDate!!) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large
                ) {
                    Icon(
                        Icons.Default.WaterDrop,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = "  Marcar inicio de periodo",
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Resumen del ciclo
            uiState.prediction?.let { prediction ->
                CycleSummaryCard(prediction = prediction)
            } ?: run {
                // Sin predicción — primer uso
                Text(
                    text = "¡Bienvenida! Marca el primer día de tu último periodo para comenzar el seguimiento.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp)
                )
            }

            // Leyenda de fases
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Fases del ciclo",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                com.ona.miciclo.calendar.domain.model.CyclePhase.entries
                    .filter { it != com.ona.miciclo.calendar.domain.model.CyclePhase.UNKNOWN }
                    .forEach { phase ->
                        com.ona.miciclo.calendar.presentation.components.PhaseIndicator(phase = phase)
                    }
            }

            Spacer(modifier = Modifier.height(100.dp)) // Space for FAB
        }
    }
}
