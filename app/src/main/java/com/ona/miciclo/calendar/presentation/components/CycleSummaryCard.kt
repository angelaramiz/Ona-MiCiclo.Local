package com.ona.miciclo.calendar.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ona.miciclo.calendar.domain.model.CyclePrediction
import com.ona.miciclo.calendar.domain.model.PredictionConfidence
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Card de resumen del ciclo actual con predicción.
 */
@Composable
fun CycleSummaryCard(
    prediction: CyclePrediction,
    modifier: Modifier = Modifier
) {
    val dateFormatter = DateTimeFormatter.ofPattern("d 'de' MMMM", Locale("es"))

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = MaterialTheme.shapes.large
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Fase actual
            PhaseIndicator(phase = prediction.faseActual)

            Spacer(modifier = Modifier.height(8.dp))

            // Día del ciclo
            Text(
                text = "Día ${prediction.diaDelCiclo} de ${prediction.duracionPromedio}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Próxima menstruación
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Próximo periodo",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = prediction.proximaMenstruacion.format(dateFormatter),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // Ventana fértil
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Ventana fértil",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${prediction.inicioVentanaFertil.format(dateFormatter)} - ${prediction.finVentanaFertil.dayOfMonth}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }

            // Mensaje y confianza
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = prediction.mensaje,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (prediction.confianza != PredictionConfidence.HIGH) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "📊 Confianza: ${prediction.confianza.displayName}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
