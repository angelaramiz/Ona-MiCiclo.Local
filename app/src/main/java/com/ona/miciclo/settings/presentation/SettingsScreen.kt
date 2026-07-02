package com.ona.miciclo.settings.presentation

import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ona.miciclo.core.ui.components.OnaButton
import com.ona.miciclo.core.ui.components.OnaOutlinedButton
import com.ona.miciclo.core.ui.components.OnaTopBar

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onSignedOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val userPrefs by viewModel.userPreferencesState.collectAsState()
    var partnerCodeInput by rememberSaveable { mutableStateOf("") }
    val context = androidx.compose.ui.platform.LocalContext.current
    val packageInfo = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0)
        }
    }
    val currentVersionName = packageInfo.versionName ?: "1.0.0"
    val currentVersionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        packageInfo.longVersionCode.toInt()
    } else {
        @Suppress("DEPRECATION")
        packageInfo.versionCode
    }

    val snackbarHostState = remember { SnackbarHostState() }
    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }
    var showExportDialog by rememberSaveable { mutableStateOf(false) }
    var showDownloadSpecsDialog by rememberSaveable { mutableStateOf(false) }
    var exportPassword by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(uiState.isSignedOut) {
        if (uiState.isSignedOut) {
            onSignedOut()
        }
    }

    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    LaunchedEffect(uiState.updateError) {
        uiState.updateError?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearUpdateError()
        }
    }

    Scaffold(
        topBar = { OnaTopBar(title = "Configuración") },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Cuenta
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Cuenta", style = MaterialTheme.typography.titleSmall)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = viewModel.userEmail,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            // Vinculación de Pareja
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Compartir con mi Pareja", style = MaterialTheme.typography.titleSmall)
                    Spacer(modifier = Modifier.height(8.dp))

                    val role = userPrefs?.userRole ?: "hostess"
                    if (role == "partner") {
                        Text(
                            text = "Rol: Pareja vinculada (Solo Lectura) 👁️\nEstás viendo el calendario de la anfitriona.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        // Rol Hostess (por defecto)
                        if (!userPrefs?.linkedUserId.isNullOrEmpty()) {
                            Text(
                                text = "¡Tu cuenta está vinculada con tu pareja! ❤️",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Text(
                                text = "Permite que tu pareja vea tu calendario en modo lectura.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            if (uiState.invitationCode != null) {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer
                                    )
                                ) {
                                    Column(modifier = Modifier.padding(8.dp)) {
                                        Text(
                                            text = "Código de Invitación: ${uiState.invitationCode}",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                        Text(
                                            text = "Comparte este código con tu pareja para vincular las cuentas.",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                OnaOutlinedButton(
                                    text = "Entendido",
                                    onClick = { viewModel.clearInvitationCode() }
                                )
                            } else {
                                OnaButton(
                                    text = if (uiState.isGeneratingCode) "Generando..." else "Generar código de invitación",
                                    onClick = { viewModel.generateInvitationCode() },
                                    enabled = !uiState.isGeneratingCode
                                )
                            }
                        }

                        // Opción para vincularse como pareja (si aún no está vinculado)
                        if (userPrefs?.linkedUserId.isNullOrEmpty()) {
                            Spacer(modifier = Modifier.height(16.dp))
                            HorizontalDivider()
                            Spacer(modifier = Modifier.height(16.dp))

                            Text("¿Eres la pareja? Ingresa el código:", style = MaterialTheme.typography.titleSmall)
                            Spacer(modifier = Modifier.height(8.dp))

                            OutlinedTextField(
                                value = partnerCodeInput,
                                onValueChange = { partnerCodeInput = it.take(6).uppercase() },
                                label = { Text("Código de 6 letras") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OnaButton(
                                text = if (uiState.isLinking) "Vinculando..." else "Vincular como Pareja",
                                onClick = {
                                    viewModel.linkPartner(partnerCodeInput)
                                    partnerCodeInput = ""
                                },
                                enabled = partnerCodeInput.length == 6 && !uiState.isLinking
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Datos
            Text("Datos", style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(8.dp))

            OnaOutlinedButton(
                text = "📥 Exportar datos (encriptados)",
                onClick = { showExportDialog = true }
            )

            Spacer(modifier = Modifier.height(8.dp))

            OnaOutlinedButton(
                text = "📤 Importar datos",
                onClick = {
                    // TODO: Abrir file picker con SAF
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Actualizaciones
            Text("Aplicación", style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(8.dp))

            OnaOutlinedButton(
                text = if (uiState.isCheckingForUpdates) "🔄 Buscando..." else "🔄 Comprobar actualización",
                onClick = { viewModel.checkForUpdates() },
                enabled = !uiState.isCheckingForUpdates && !uiState.isDownloadingUpdate
            )

            if (uiState.updateInfo != null) {
                Text(
                    text = "⚠️ Hay una actualización disponible: v${uiState.updateInfo?.versionName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 4.dp)
                )
            } else if (uiState.message != null && uiState.message?.contains("actualizada") == true) {
                Text(
                    text = "✅ Tu aplicación está en la versión más reciente",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Inteligencia Artificial Local (LLM)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Inteligencia Artificial Local (LLM)", style = MaterialTheme.typography.titleSmall)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Descarga el modelo multimodal Qwen2-VL 2B (1.2 GB) para habilitar interpretaciones avanzadas 100% locales, privadas y sin necesidad de internet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
 
                    if (uiState.isAiModelDownloaded) {
                        Text(
                            text = "✅ Modelo Qwen2-VL 2B descargado e inicializado en disco (1.2 GB)",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OnaOutlinedButton(
                            text = "🗑️ Eliminar modelo de IA",
                            onClick = { viewModel.deleteAiModel() }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OnaOutlinedButton(
                            text = "🔄 Reinicializar modelo de IA",
                            onClick = { viewModel.reloadAndInitializeAiModel() }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OnaOutlinedButton(
                            text = "🔧 Reparar archivos del modelo",
                            onClick = { viewModel.repairAiModel() }
                        )
                    } else if (uiState.isDownloadingAi) {
                        Text(
                            text = "Descargando modelo neuronal... ${(uiState.aiDownloadProgress * 100).toInt()}%",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        androidx.compose.material3.LinearProgressIndicator(
                            progress = { uiState.aiDownloadProgress },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OnaOutlinedButton(
                            text = "Cancelar Descarga",
                            onClick = { viewModel.cancelAiDownload() }
                        )
                    } else {
                        OnaButton(
                            text = "📥 Descargar modelo Qwen2-VL 2B (1.2 GB)",
                            onClick = { showDownloadSpecsDialog = true }
                        )
                        uiState.aiDownloadError?.let { error ->
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "⚠️ Error: $error",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(24.dp))

            // Zona de peligro
            Text(
                "Zona de peligro",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(8.dp))

            OnaOutlinedButton(
                text = "🗑️ Eliminar todos mis datos",
                onClick = { showDeleteDialog = true }
            )

            Spacer(modifier = Modifier.height(16.dp))

            OnaOutlinedButton(
                text = "Cerrar sesión",
                onClick = { viewModel.signOut() }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Info de privacidad
            Text(
                text = "🔒 Tus datos se almacenan encriptados solo en este dispositivo. " +
                        "Ona nunca envía datos de salud a ningún servidor.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Ona v$currentVersionName (Build $currentVersionCode) — Fase 1\nHerramienta educativa, no dispositivo médico.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // Diálogo de actualización disponible o progreso de descarga
    uiState.updateInfo?.let { updateInfo ->
        AlertDialog(
            onDismissRequest = {
                if (!uiState.isDownloadingUpdate) {
                    viewModel.dismissUpdateDialog()
                }
            },
            title = { Text("Actualización disponible") },
            text = {
                Column {
                    Text("Se ha encontrado una nueva versión: v${updateInfo.versionName}")
                    Spacer(modifier = Modifier.height(12.dp))
                    if (uiState.isDownloadingUpdate) {
                        Text("Descargando actualización... ${(uiState.downloadProgress * 100).toInt()}%")
                        Spacer(modifier = Modifier.height(8.dp))
                        androidx.compose.material3.LinearProgressIndicator(
                            progress = { uiState.downloadProgress },
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Text("¿Deseas descargar la actualización e instalarla automáticamente?")
                    }
                }
            },
            confirmButton = {
                if (!uiState.isDownloadingUpdate) {
                    TextButton(onClick = { viewModel.downloadAndInstallUpdate() }) {
                        Text("Descargar e Instalar")
                    }
                }
            },
            dismissButton = {
                if (!uiState.isDownloadingUpdate) {
                    TextButton(onClick = { viewModel.dismissUpdateDialog() }) {
                        Text("Cancelar")
                    }
                }
            }
        )
    }

    // Diálogo de confirmación de borrado
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("¿Eliminar todos los datos?") },
            text = {
                Text(
                    "Esta acción es IRREVERSIBLE. Se eliminarán todos tus registros de ciclo, " +
                            "síntomas y preferencias de este dispositivo. " +
                            "Te recomendamos exportar tus datos antes."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteAllData()
                        showDeleteDialog = false
                    }
                ) {
                    Text("Eliminar todo", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    // Diálogo de exportación
    if (showExportDialog) {
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = { Text("Exportar datos") },
            text = {
                Column {
                    Text("Ingresa una contraseña maestra para encriptar el archivo de respaldo.")
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = exportPassword,
                        onValueChange = { exportPassword = it },
                        label = { Text("Contraseña (mín. 8 caracteres)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.exportData(exportPassword)
                        showExportDialog = false
                        exportPassword = ""
                    },
                    enabled = exportPassword.length >= 8
                ) {
                    Text("Exportar")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showExportDialog = false
                    exportPassword = ""
                }) {
                    Text("Cancelar")
                }
            }
        )
    }
    // Diálogo de especificaciones de descarga
    if (showDownloadSpecsDialog) {
        AlertDialog(
            onDismissRequest = { showDownloadSpecsDialog = false },
            title = { Text("Especificaciones Requeridas") },
            text = {
                Column {
                    Text(
                        text = "Para procesar Qwen2-VL localmente (offline) y habilitar visión e interpretación sin internet, tu dispositivo móvil debe cumplir con los siguientes requisitos mínimos y recomendados:",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Requisitos Mínimos:\n" +
                                "• RAM disponible: 4 GB\n" +
                                "• Almacenamiento libre: 1.5 GB\n" +
                                "• Procesador de 64 bits (ARM64-v8a)\n" +
                                "• Sistema Operativo: Android 8.0+",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Requisitos Recomendados:\n" +
                                "• RAM disponible: 6 GB o superior (mayor velocidad)\n" +
                                "• GPU compatible con Vulkan / OpenCL\n" +
                                "• Sistema Operativo: Android 10.0+",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "¿Deseas iniciar la descarga ahora?",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.downloadAiModel()
                        showDownloadSpecsDialog = false
                    }
                ) {
                    Text("Iniciar Descarga")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDownloadSpecsDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}
