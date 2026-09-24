package com.ona.miciclo.settings.presentation

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.ona.miciclo.core.ui.components.LoadingOverlay
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
    var showDeleteAiDialog by rememberSaveable { mutableStateOf(false) }
    var showExportDialog by rememberSaveable { mutableStateOf(false) }
    var showDownloadSpecsDialog by rememberSaveable { mutableStateOf(false) }
    var exportPassword by rememberSaveable { mutableStateOf("") }
    // Permiso de notificaciones: se pide UNA vez, con contexto, al vincularse como pareja.
    var showNotifRationale by rememberSaveable { mutableStateOf(false) }
    val uxFlags = remember {
        context.getSharedPreferences("ona_ux_flags", Context.MODE_PRIVATE)
    }
    fun notifRationaleAsked(): Boolean = uxFlags.getBoolean("notif_rationale_asked", false)
    fun markNotifRationaleAsked() {
        uxFlags.edit().putBoolean("notif_rationale_asked", true).apply()
    }
    val hasNotifPermission: Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    } else {
        true
    }
    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { markNotifRationaleAsked() }

    // Disparar rationale: hostess genera código O partner se acaba de vincular.
    // (No naggea: solo una vez por instalación y solo si aún no tiene el permiso.)
    LaunchedEffect(uiState.invitationCode, userPrefs?.linkedUserId) {
        val justLinked = uiState.invitationCode != null || !userPrefs?.linkedUserId.isNullOrEmpty()
        if (justLinked && !hasNotifPermission && !notifRationaleAsked()) {
            showNotifRationale = true
        }
    }

    if (showNotifRationale) {
        AlertDialog(
            onDismissRequest = { showNotifRationale = false; markNotifRationaleAsked() },
            title = { Text("Activar notificaciones") },
            text = {
                Text(
                    "Activa las notificaciones para recibir avisos de sincronización " +
                            "y sugerencias de tu pareja. Puedes cambiarlo luego en los " +
                            "Ajustes del sistema."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showNotifRationale = false
                        markNotifRationaleAsked()
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                ) {
                    Text("Activar")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showNotifRationale = false; markNotifRationaleAsked() }
                ) {
                    Text("Ahora no")
                }
            }
        )
    }

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

    Box(modifier = modifier.fillMaxSize()) {
        Scaffold(
            topBar = { OnaTopBar(title = "Configuración") },
            snackbarHost = { SnackbarHost(snackbarHostState) }
        ) { padding ->
            Column(
                modifier = Modifier
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
                        text = "Descarga el modelo Qwen3-4B (2.5 GB, motor llama.cpp) para habilitar el asistente de IA 100% local, privado y sin necesidad de internet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
 
                    if (uiState.isAiModelDownloaded) {
                        Text(
                            text = "✅ Modelo Qwen3-4B descargado en disco (2.5 GB)",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OnaOutlinedButton(
                            text = "🗑️ Eliminar modelo de IA",
                            onClick = { showDeleteAiDialog = true }
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
                        // El progreso y el botón "Cancelar" los muestra LoadingOverlay.
                        Text(
                            text = "Descargando modelo Qwen3-4B...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        OnaButton(
                            text = "📥 Descargar modelo Qwen3-4B (2.5 GB)",
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

        // ── Banner superior de actualización (auto-descarga, sin cancelar) ──
        uiState.updateInfo?.let { updateInfo ->
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.primaryContainer,
                tonalElevation = 4.dp,
                shadowElevation = 4.dp
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("⬇️", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (uiState.isDownloadingUpdate) {
                                "Descargando actualización v${updateInfo.versionName}... ${(uiState.downloadProgress * 100).toInt()}%"
                            } else {
                                "Nueva versión v${updateInfo.versionName} disponible — descargando..."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    if (uiState.isDownloadingUpdate) {
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { uiState.downloadProgress },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }

        // ── Ventana de transición para procesos en curso (descargas, sync) ──
        LoadingOverlay(
            visible = uiState.isDownloadingAi ||
                uiState.isGeneratingCode ||
                uiState.isLinking ||
                uiState.isCheckingForUpdates ||
                uiState.isExporting,
            message = when {
                uiState.isDownloadingAi ->
                    "Descargando modelo de IA... ${(uiState.aiDownloadProgress * 100).toInt()}%"
                uiState.isLinking -> "Vinculando con tu pareja..."
                uiState.isGeneratingCode -> "Generando código de invitación..."
                uiState.isCheckingForUpdates -> "Buscando actualizaciones..."
                uiState.isExporting -> "Exportando datos encriptados..."
                else -> null
            },
            progress = if (uiState.isDownloadingAi) uiState.aiDownloadProgress else null,
            onCancel = if (uiState.isDownloadingAi) { { viewModel.cancelAiDownload() } } else null
        )
    }

    // Auto-descarga de actualización: en cuanto se detecta, empieza sin confirmación.
    LaunchedEffect(uiState.updateInfo?.versionCode) {
        if (uiState.updateInfo != null && !uiState.isDownloadingUpdate) {
            viewModel.downloadAndInstallUpdate()
        }
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

    // Diálogo de confirmación para eliminar el modelo de IA (2.5 GB)
    if (showDeleteAiDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAiDialog = false },
            title = { Text("¿Eliminar modelo de IA?") },
            text = {
                Text(
                    "Se eliminarán los 2.5 GB del modelo Qwen3-4B de este dispositivo. " +
                            "El asistente de IA dejará de funcionar hasta que lo descargues de nuevo."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteAiModel()
                        showDeleteAiDialog = false
                    }
                ) {
                    Text("Eliminar modelo", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAiDialog = false }) {
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
                        text = "Para procesar Qwen3-4B localmente (offline) sin internet, tu dispositivo móvil debe cumplir con los siguientes requisitos mínimos y recomendados:",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Requisitos Mínimos:\n" +
                                "• RAM disponible: 6 GB\n" +
                                "• Almacenamiento libre: 3.5 GB\n" +
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
