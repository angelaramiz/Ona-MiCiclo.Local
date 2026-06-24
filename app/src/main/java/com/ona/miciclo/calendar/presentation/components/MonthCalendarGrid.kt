package com.ona.miciclo.calendar.presentation.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ona.miciclo.calendar.domain.model.CyclePhase
import com.ona.miciclo.calendar.domain.model.CyclePrediction
import com.ona.miciclo.calendar.domain.model.DailyLog
import com.ona.miciclo.calendar.domain.model.FlowLevel
import com.ona.miciclo.core.ui.theme.*
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

/**
 * Grid de calendario mensual 7×6 con colores por fase del ciclo.
 */
@Composable
fun MonthCalendarGrid(
    yearMonth: YearMonth,
    dailyLogs: List<DailyLog>,
    prediction: CyclePrediction?,
    selectedDate: LocalDate?,
    onDateSelected: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    val firstDayOfMonth = yearMonth.atDay(1)
    val lastDayOfMonth = yearMonth.atEndOfMonth()
    val firstDayOfWeek = firstDayOfMonth.dayOfWeek

    // Calcular offset: cuántas celdas vacías antes del primer día
    val startOffset = (firstDayOfWeek.value - DayOfWeek.MONDAY.value + 7) % 7
    val totalDays = yearMonth.lengthOfMonth()

    // Mapa de logs por fecha para búsqueda rápida
    val logsMap = dailyLogs.associateBy { it.fecha }

    Column(modifier = modifier) {
        // Header con días de la semana
        Row(modifier = Modifier.fillMaxWidth()) {
            val daysOfWeek = listOf("Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom")
            daysOfWeek.forEach { day ->
                Text(
                    text = day,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Grid de días
        var dayCounter = 1
        val totalCells = startOffset + totalDays
        val rows = (totalCells + 6) / 7

        for (row in 0 until rows) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                for (col in 0 until 7) {
                    val cellIndex = row * 7 + col

                    if (cellIndex < startOffset || dayCounter > totalDays) {
                        // Celda vacía
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                        )
                    } else {
                        val date = yearMonth.atDay(dayCounter)
                        val log = logsMap[date]
                        val isSelected = date == selectedDate
                        val isToday = date == LocalDate.now()

                        DayCell(
                            date = date,
                            dailyLog = log,
                            prediction = prediction,
                            isSelected = isSelected,
                            isToday = isToday,
                            onClick = { onDateSelected(date) },
                            modifier = Modifier.weight(1f)
                        )
                        dayCounter++
                    }
                }
            }
        }
    }
}

/**
 * Celda individual de un día en el calendario.
 */
@Composable
fun DayCell(
    date: LocalDate,
    dailyLog: DailyLog?,
    prediction: CyclePrediction?,
    isSelected: Boolean,
    isToday: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val phaseColor = getPhaseColor(date, prediction)
    val backgroundColor by animateColorAsState(
        targetValue = when {
            isSelected -> MaterialTheme.colorScheme.primary
            phaseColor != null -> phaseColor
            else -> Color.Transparent
        },
        animationSpec = tween(200),
        label = "day_bg_color"
    )
    val textColor = when {
        isSelected -> MaterialTheme.colorScheme.onPrimary
        date.monthValue != date.monthValue -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
        else -> MaterialTheme.colorScheme.onSurface
    }

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .padding(2.dp)
            .clip(CircleShape)
            .background(backgroundColor)
            .then(
                if (isToday && !isSelected) {
                    Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                } else Modifier
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = date.dayOfMonth.toString(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                color = textColor
            )
            // Indicador de registro completado
            if (dailyLog != null) {
                Box(
                    modifier = Modifier
                        .size(4.dp)
                        .clip(CircleShape)
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.primary
                        )
                )
            }
        }
    }
}

/**
 * Determina el color de fondo de un día basándose en la predicción del ciclo.
 */
private fun getPhaseColor(date: LocalDate, prediction: CyclePrediction?): Color? {
    if (prediction == null) return null

    val lastPeriodStart = prediction.proximaMenstruacion.minusDays(prediction.duracionPromedio.toLong())
    val dayOfCycle = java.time.temporal.ChronoUnit.DAYS.between(lastPeriodStart, date).toInt() + 1

    return when {
        dayOfCycle < 1 || dayOfCycle > prediction.duracionPromedio -> null
        date in prediction.inicioVentanaFertil..prediction.finVentanaFertil -> PhaseFertileLight
        dayOfCycle <= 5 -> PhaseMenstruationLight // Sangrado estimado ~5 días
        dayOfCycle <= prediction.duracionPromedio / 2 -> PhaseFollicularLight
        else -> PhaseLutealLight
    }
}

/**
 * Indicador visual de la fase actual.
 */
@Composable
fun PhaseIndicator(
    phase: CyclePhase,
    modifier: Modifier = Modifier
) {
    val color = when (phase) {
        CyclePhase.MENSTRUATION -> PhaseMenstruation
        CyclePhase.FOLLICULAR -> PhaseFollicular
        CyclePhase.FERTILE -> PhaseFertile
        CyclePhase.LUTEAL -> PhaseLuteal
        CyclePhase.UNKNOWN -> Color.Gray
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = phase.displayName,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
