package com.ona.miciclo.dashboard.presentation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ona.miciclo.calendar.presentation.components.CycleSummaryCard
import com.ona.miciclo.core.ui.components.OnaButton
import com.ona.miciclo.core.ui.components.OnaTopBar
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()
    val dateFormatter = DateTimeFormatter.ofPattern("d 'de' MMMM yyyy", Locale("es"))
    val headerDateFormatter = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", Locale("es"))
    val todayLabel = LocalDate.now().format(headerDateFormatter)
        .replaceFirstChar { it.uppercase() }

    Scaffold(
        topBar = { OnaTopBar(title = "Dashboard") }
    ) { innerPadding ->
        Column(
            modifier = modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(16.dp)
        ) {
            // Cabecera: saludo + fecha + modo (visible de un vistazo).
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                shape = MaterialTheme.shapes.large
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = if (uiState.isPartner) "👁️ Modo pareja" else "🌸 Mi ciclo",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (uiState.isPartner) {
                            "Viendo el calendario de tu pareja · $todayLabel"
                        } else {
                            todayLabel
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            uiState.latestPeriodStart?.let { last ->
                Text(
                    text = "Último inicio de periodo: ${last.format(dateFormatter)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            if (uiState.prediction != null) {
                CycleSummaryCard(prediction = uiState.prediction!!)
            } else {
                Text(
                    text = if (uiState.hasAnyCycleData) {
                        "Calculando predicción del ciclo..."
                    } else {
                        "Aún no hay datos suficientes para mostrar estadísticas."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Insights de Ona (A5): observaciones con reglas sobre tus datos.
            if (uiState.insights.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    ),
                    shape = MaterialTheme.shapes.large
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "✨ Insights de Ona",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        uiState.insights.forEach { insight ->
                            Text(
                                text = "• $insight",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                    }
                }
            }

            uiState.error?.let { err ->
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = err,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            OnaButton(
                text = if (uiState.isLoading) "Actualizando..." else "Actualizar",
                onClick = { viewModel.refresh() },
                isLoading = uiState.isLoading
            )
        }
    }
}
