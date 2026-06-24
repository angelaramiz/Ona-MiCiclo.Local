package com.ona.miciclo.onboarding.presentation

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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ona.miciclo.core.ui.components.OnaButton
import com.ona.miciclo.core.ui.components.OnaTopBar
import java.time.LocalDate

@Composable
fun CycleSetupScreen(
    viewModel: OnboardingViewModel,
    onSetupComplete: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    var cycleLength by rememberSaveable { mutableFloatStateOf(28f) }
    var bleedingDuration by rememberSaveable { mutableFloatStateOf(5f) }
    var selectedObjective by rememberSaveable { mutableStateOf("conocimiento") }

    LaunchedEffect(uiState.isOnboardingCompleted) {
        if (uiState.isOnboardingCompleted) {
            onSetupComplete()
        }
    }

    val objectives = listOf(
        "conocimiento" to "Conocer mi cuerpo",
        "fertilidad" to "Planificar embarazo",
        "anticoncepcion" to "Evitar embarazo"
    )

    Scaffold(
        topBar = {
            OnaTopBar(title = "Configurar tu Ciclo")
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Cuéntanos sobre tu ciclo",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Estos datos iniciales nos ayudan a empezar. Los ajustaremos con tus registros reales.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Duración del ciclo
            Text(
                text = "Duración promedio de tu ciclo",
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                text = "${cycleLength.toInt()} días",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Slider(
                value = cycleLength,
                onValueChange = { cycleLength = it },
                valueRange = 21f..45f,
                steps = 23,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Duración del sangrado
            Text(
                text = "Duración promedio del sangrado",
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                text = "${bleedingDuration.toInt()} días",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Slider(
                value = bleedingDuration,
                onValueChange = { bleedingDuration = it },
                valueRange = 1f..10f,
                steps = 8,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Objetivo
            Text(
                text = "¿Cuál es tu objetivo principal?",
                style = MaterialTheme.typography.titleSmall
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                objectives.forEach { (value, label) ->
                    FilterChip(
                        selected = selectedObjective == value,
                        onClick = { selectedObjective = value },
                        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(48.dp))

            OnaButton(
                text = "Comenzar Seguimiento",
                onClick = {
                    viewModel.completeSetup(
                        cycleLength = cycleLength.toInt(),
                        bleedingDuration = bleedingDuration.toInt(),
                        lastPeriodDate = null, // Se marcará en el calendario
                        objective = selectedObjective
                    )
                }
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
