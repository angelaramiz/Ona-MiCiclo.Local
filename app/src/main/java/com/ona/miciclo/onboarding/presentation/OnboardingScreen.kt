package com.ona.miciclo.onboarding.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ona.miciclo.core.ui.components.OnaButton
import com.ona.miciclo.core.ui.components.OnaOutlinedButton
import kotlinx.coroutines.launch

@Composable
fun OnboardingScreen(
    onNavigateToCycleSetup: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pages = listOf(
        OnboardingPage(
            emoji = "🌸",
            title = "Bienvenida a Ona",
            description = "Tu compañera para entender y seguir tu ciclo menstrual de forma privada y segura."
        ),
        OnboardingPage(
            emoji = "🔒",
            title = "Tu Privacidad es Primero",
            description = "Todos tus datos se almacenan SOLO en tu dispositivo, encriptados. Nunca se envían a ningún servidor. Tú tienes el control total."
        ),
        OnboardingPage(
            emoji = "📊",
            title = "Seguimiento Inteligente",
            description = "Registra tu ciclo, síntomas y observaciones. Con el tiempo, Ona aprenderá tus patrones para darte predicciones más precisas."
        ),
        OnboardingPage(
            emoji = "⚠️",
            title = "Aviso Importante",
            description = "Ona es una herramienta educativa para el autoconocimiento. NO es un dispositivo médico ni un método anticonceptivo certificado. Consulta siempre a tu profesional de salud."
        )
    )

    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Progress indicator
        LinearProgressIndicator(
            progress = { (pagerState.currentPage + 1f) / pages.size },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )

        Spacer(modifier = Modifier.height(32.dp))

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f)
        ) { page ->
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = pages[page].emoji,
                    style = MaterialTheme.typography.displayLarge
                )
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = pages[page].title,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = pages[page].description,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }

        // Botones
        if (pagerState.currentPage < pages.size - 1) {
            OnaButton(
                text = "Siguiente",
                onClick = {
                    scope.launch {
                        pagerState.animateScrollToPage(pagerState.currentPage + 1)
                    }
                }
            )
            Spacer(modifier = Modifier.height(12.dp))
            OnaOutlinedButton(
                text = "Omitir",
                onClick = onNavigateToCycleSetup
            )
        } else {
            OnaButton(
                text = "Comenzar Configuración",
                onClick = onNavigateToCycleSetup
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

private data class OnboardingPage(
    val emoji: String,
    val title: String,
    val description: String
)
