package com.ona.miciclo.ocr.presentation

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.ona.miciclo.core.ui.theme.OnaMiCicloTheme
import com.ona.miciclo.ocr.OvulationOcrAnalyzer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class OcrCaptureActivity : ComponentActivity() {

    private lateinit var cameraExecutor: ExecutorService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cameraExecutor = Executors.newSingleThreadExecutor()

        setContent {
            OnaMiCicloTheme {
                OcrCaptureScreen(
                    onResultConfirmed = { result ->
                        val intent = Intent().apply {
                            putExtra("resultado_ocr", result)
                        }
                        setResult(Activity.RESULT_OK, intent)
                        finish()
                    },
                    onClose = {
                        setResult(Activity.RESULT_CANCELED)
                        finish()
                    },
                    cameraExecutor = cameraExecutor
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}

@Composable
fun OcrCaptureScreen(
    onResultConfirmed: (String) -> Unit,
    onClose: () -> Unit,
    cameraExecutor: ExecutorService
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            hasCameraPermission = granted
        }
    )

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            launcher.launch(Manifest.permission.CAMERA)
        }
    }

    if (!hasCameraPermission) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Permiso de Cámara Requerido",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Ona necesita acceso a tu cámara para escanear y calibrar la tira de ovulación de forma 100% local en tu dispositivo.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(onClick = { launcher.launch(Manifest.permission.CAMERA) }) {
                    Text("Conceder Permiso")
                }
            }
        }
        return
    }

    // Preferencias de calibración
    val sharedPrefs = remember { context.getSharedPreferences("ona_ocr_calib", Context.MODE_PRIVATE) }
    var calibrationCount by remember { mutableStateOf(sharedPrefs.getInt("calib_count", 0)) }
    val isCalibrating = calibrationCount < 3

    var detectedResult by remember { mutableStateOf("Analizando...") }
    var showCalibrationDialog by remember { mutableStateOf(false) }
    var resultToConfirm by remember { mutableStateOf("") }

    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }

    Box(modifier = Modifier.fillMaxSize()) {
        // Camera Preview
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx)
                val executor = ContextCompat.getMainExecutor(ctx)
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }

                    val imageAnalyzer = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                        .also {
                            it.setAnalyzer(
                                cameraExecutor,
                                OvulationOcrAnalyzer { result ->
                                    detectedResult = result
                                }
                            )
                        }

                    val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                    try {
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            cameraSelector,
                            preview,
                            imageAnalyzer
                        )
                    } catch (e: Exception) {
                        Toast.makeText(ctx, "Error al iniciar la cámara", Toast.LENGTH_SHORT).show()
                    }
                }, executor)
                previewView
            },
            modifier = Modifier.fillMaxSize()
        )

        // Overlay de alineación de tira reactiva
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.4f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Header instruccional
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = if (isCalibrating) "Calibración Inicial (${calibrationCount + 1}/3)" else "Escaneo de Tira de Ovulación",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Alinea la tira de ovulación en el marco central. Asegura una buena iluminación local.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                // Marco guía central
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .height(100.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .border(3.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                        .background(Color.Transparent),
                    contentAlignment = Alignment.Center
                ) {
                    Divider(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                        thickness = 1.dp,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Control inferior y resultado
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Lectura local: ${detectedResult.uppercase()}",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (detectedResult == "positivo") Color(0xFFE91E63) else MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            OutlinedButton(onClick = onClose) {
                                Text("Cancelar")
                            }

                            Button(
                                onClick = {
                                    if (isCalibrating) {
                                        resultToConfirm = detectedResult
                                        showCalibrationDialog = true
                                    } else {
                                        onResultConfirmed(detectedResult)
                                    }
                                }
                            ) {
                                Text(if (isCalibrating) "Confirmar y Calibrar" else "Aceptar Resultado")
                            }
                        }
                    }
                }
            }
        }
    }

    // Diálogo de calibración
    if (showCalibrationDialog) {
        AlertDialog(
            onDismissRequest = { showCalibrationDialog = false },
            title = { Text("Confirmar Lectura (Paso ${calibrationCount + 1}/3)") },
            text = {
                Column {
                    Text("El analizador local detectó: ${resultToConfirm.uppercase()}.")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("¿Es correcto? Si no, por favor selecciona el estado real para calibrar los umbrales de la cámara de tu dispositivo.")
                }
            },
            confirmButton = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Button(
                            onClick = {
                                val nextCount = calibrationCount + 1
                                sharedPrefs.edit().putInt("calib_count", nextCount).apply()
                                showCalibrationDialog = false
                                onResultConfirmed("positivo")
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE91E63))
                        ) {
                            Text("Positivo (+)")
                        }

                        Button(
                            onClick = {
                                val nextCount = calibrationCount + 1
                                sharedPrefs.edit().putInt("calib_count", nextCount).apply()
                                showCalibrationDialog = false
                                onResultConfirmed("debil")
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800))
                        ) {
                            Text("Débil")
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Button(
                            onClick = {
                                val nextCount = calibrationCount + 1
                                sharedPrefs.edit().putInt("calib_count", nextCount).apply()
                                showCalibrationDialog = false
                                onResultConfirmed("negativo")
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9E9E9E))
                        ) {
                            Text("Negativo (-)")
                        }

                        TextButton(onClick = { showCalibrationDialog = false }) {
                            Text("Cancelar")
                        }
                    }
                }
            }
        )
    }
}
