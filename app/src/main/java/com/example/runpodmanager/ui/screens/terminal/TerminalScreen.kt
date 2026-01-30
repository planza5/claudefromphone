package com.example.runpodmanager.ui.screens.terminal

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.termux.view.TerminalView
import kotlinx.coroutines.launch
import java.util.Locale

private data class ExtraKeyData(val label: String, val bytes: ByteArray)

private val extraKeys = listOf(
    ExtraKeyData("↵", byteArrayOf(0x0D)),
    ExtraKeyData("TAB", byteArrayOf(0x09)),
    ExtraKeyData("↑", byteArrayOf(0x1B, 0x5B, 0x41)),
    ExtraKeyData("↓", byteArrayOf(0x1B, 0x5B, 0x42)),
    ExtraKeyData("←", byteArrayOf(0x1B, 0x5B, 0x44)),
    ExtraKeyData("→", byteArrayOf(0x1B, 0x5B, 0x43)),
    ExtraKeyData("C-c", byteArrayOf(0x03)),
    ExtraKeyData("~", "~".toByteArray()),
    ExtraKeyData("ESC", byteArrayOf(0x1B))
)

@Composable
fun ExtraKeysBar(
    onKey: (ByteArray) -> Unit,
    onRequestFocus: () -> Unit,
    onMicClick: () -> Unit,
    isListening: Boolean = false
) {
    val keyColor = Color(0xFF2D2D2D)
    val textColor = Color(0xFF4EC9B0)
    val micActiveColor = Color(0xFFE53935)

    val sendKey: (ByteArray) -> Unit = { bytes ->
        onKey(bytes)
        onRequestFocus()
    }

    Surface(color = Color(0xFF1A1A1A), modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 4.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Botón de micrófono
            Surface(
                onClick = onMicClick,
                color = if (isListening) micActiveColor else keyColor,
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier.padding(2.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = "Voz",
                    tint = if (isListening) Color.White else textColor,
                    modifier = Modifier
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .size(20.dp)
                )
            }

            extraKeys.forEach { key ->
                Surface(
                    onClick = { sendKey(key.bytes) },
                    color = keyColor,
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.padding(2.dp)
                ) {
                    Text(key.label, color = textColor, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen(
    viewModel: TerminalViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Estado para el reconocimiento de voz
    var isListening by remember { mutableStateOf(false) }

    // SpeechRecognizer sin diálogo
    val speechRecognizer = remember {
        SpeechRecognizer.createSpeechRecognizer(context)
    }

    // Configurar el listener del reconocimiento
    DisposableEffect(Unit) {
        val listener = object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                Log.d("VoiceInput", "Listo para escuchar")
            }

            override fun onBeginningOfSpeech() {
                Log.d("VoiceInput", "Comenzó a hablar")
            }

            override fun onRmsChanged(rmsdB: Float) {}

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                Log.d("VoiceInput", "Terminó de hablar")
                isListening = false
            }

            override fun onError(error: Int) {
                Log.e("VoiceInput", "Error de reconocimiento: $error")
                isListening = false
                val errorMsg = when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH -> "No se reconoció ninguna palabra"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Tiempo de espera agotado"
                    else -> "Error de reconocimiento"
                }
                scope.launch {
                    snackbarHostState.showSnackbar(errorMsg)
                }
            }

            override fun onResults(results: Bundle?) {
                isListening = false
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val spokenText = matches?.firstOrNull()
                if (!spokenText.isNullOrEmpty()) {
                    Log.d("VoiceInput", "Texto reconocido: $spokenText")
                    viewModel.sendVoiceCommand(spokenText)
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {}

            override fun onEvent(eventType: Int, params: Bundle?) {}
        }
        speechRecognizer.setRecognitionListener(listener)

        onDispose {
            speechRecognizer.destroy()
        }
    }

    // Launcher para el permiso de audio
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            scope.launch {
                snackbarHostState.showSnackbar("Se necesita permiso de micrófono para usar voz")
            }
        }
    }

    // Función para iniciar el reconocimiento de voz (sin diálogo)
    fun startVoiceRecognition() {
        // Verificar si el reconocimiento de voz está disponible
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            scope.launch {
                snackbarHostState.showSnackbar("Reconocimiento de voz no disponible")
            }
            return
        }

        // Verificar permiso
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasPermission) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }

        // Iniciar reconocimiento silencioso (sin diálogo)
        isListening = true
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        }
        try {
            speechRecognizer.startListening(intent)
        } catch (e: Exception) {
            isListening = false
            scope.launch {
                snackbarHostState.showSnackbar("Error al iniciar reconocimiento")
            }
            Log.e("VoiceInput", "Error starting speech recognizer", e)
        }
    }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    // APK Download Dialog
    if (uiState.showApkDialog) {
        Log.d("TerminalScreen", "=== DIALOG VISIBLE === isDownloaded=${uiState.downloadedApkFile != null}, isWaiting=${uiState.isWaitingUninstall}")
        ApkDownloadDialog(
            apkPath = uiState.apkRemotePath,
            isDownloading = uiState.isDownloadingApk,
            downloadProgress = uiState.downloadProgress,
            isDownloaded = uiState.downloadedApkFile != null,
            isWaitingUninstall = uiState.isWaitingUninstall,
            onDownload = { viewModel.downloadApk() },
            onInstall = { viewModel.installApk() },
            onUninstallAndInstall = {
                Log.d("TerminalScreen", "Botón Desinstalar e Instalar pulsado!")
                viewModel.uninstallAndInstallApk()
            },
            onDismiss = { viewModel.dismissApkDialog() }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Terminal SSH")
                        Text(
                            text = "${uiState.host}:${uiState.port}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    if (uiState.isConnected) {
                        IconButton(onClick = viewModel::disconnect) {
                            Icon(
                                Icons.Default.LinkOff,
                                contentDescription = "Desconectar",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    } else if (!uiState.isConnecting) {
                        IconButton(onClick = viewModel::connect) {
                            Icon(
                                Icons.Default.Link,
                                contentDescription = "Conectar",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF1E1E1E),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Color(0xFF0D0D0D)
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .imePadding()
                .background(Color(0xFF0D0D0D))
        ) {
            when {
                uiState.isConnecting -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(color = Color(0xFF4EC9B0))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Conectando a ${uiState.host}:${uiState.port}...",
                            color = Color(0xFF4EC9B0)
                        )
                    }
                }

                !uiState.isConnected -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "Desconectado",
                            color = Color.Gray,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = viewModel::connect,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF4EC9B0)
                            )
                        ) {
                            Icon(Icons.Default.Link, contentDescription = null)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Conectar")
                        }
                    }
                }

                uiState.terminalReady && viewModel.session != null -> {
                    val controller = viewModel.controller
                    val backgroundColor = Color(0xFF0D0D0D).toArgb()
                    var terminalView by remember { mutableStateOf<TerminalView?>(null) }

                    DisposableEffect(lifecycleOwner) {
                        val observer = LifecycleEventObserver { _, event ->
                            val view = controller.getView()
                            when (event) {
                                Lifecycle.Event.ON_RESUME -> {
                                    view?.onScreenUpdated()
                                    view?.setTerminalCursorBlinkerState(true, true)
                                }
                                Lifecycle.Event.ON_PAUSE -> {
                                    view?.setTerminalCursorBlinkerState(false, true)
                                }
                                else -> {}
                            }
                        }
                        lifecycleOwner.lifecycle.addObserver(observer)
                        onDispose {
                            lifecycleOwner.lifecycle.removeObserver(observer)
                            controller.detachView()
                        }
                    }

                    LaunchedEffect(terminalView) {
                        terminalView?.let {
                            kotlinx.coroutines.delay(500)
                            controller.showKeyboard()
                        }
                    }

                    Column(modifier = Modifier.fillMaxSize()) {
                        AndroidView(
                            factory = { ctx ->
                                TerminalView(ctx, null).apply {
                                    setBackgroundColor(backgroundColor)
                                    controller.attachView(this)
                                    controller.setTextSize(56)
                                    isFocusable = true
                                    isFocusableInTouchMode = true
                                    isClickable = true
                                    setOnClickListener { controller.showKeyboard() }
                                    terminalView = this
                                }
                            },
                            update = { view -> view.onScreenUpdated() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        )
                        ExtraKeysBar(
                            onKey = { sequence -> viewModel.sendEscapeSequence(sequence) },
                            onRequestFocus = { controller.showKeyboard() },
                            onMicClick = { startVoiceRecognition() },
                            isListening = isListening
                        )
                        ProjectsBar(
                            projects = uiState.projects,
                            isLoading = uiState.isLoadingProjects,
                            selectedProject = uiState.selectedProject,
                            isBuilding = uiState.isBuilding,
                            onProjectClick = { project -> viewModel.selectProject(project) },
                            onBackClick = { viewModel.goBackFromProject() },
                            onBuildClick = { viewModel.buildAndInstall() }
                        )
                    }
                }

                else -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(color = Color(0xFF4EC9B0))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Inicializando terminal...",
                            color = Color(0xFF4EC9B0)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ProjectsBar(
    projects: List<String>,
    isLoading: Boolean,
    selectedProject: String?,
    isBuilding: Boolean,
    onProjectClick: (String) -> Unit,
    onBackClick: () -> Unit,
    onBuildClick: () -> Unit
) {
    if (projects.isEmpty() && !isLoading && selectedProject == null) return

    val keyColor = Color(0xFF2D2D2D)
    val textColor = Color(0xFF9CDCFE)
    val accentColor = Color(0xFF4EC9B0)

    Surface(color = Color(0xFF1A1A1A), modifier = Modifier.fillMaxWidth()) {
        if (selectedProject != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    onClick = onBackClick,
                    color = keyColor,
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                            tint = textColor,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.size(4.dp))
                        Text("Back", color = textColor)
                    }
                }
                Surface(
                    onClick = { if (!isBuilding) onBuildClick() },
                    color = if (isBuilding) keyColor else accentColor,
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.weight(2f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isBuilding) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = accentColor,
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.size(8.dp))
                            Text("Building...", color = textColor)
                        } else {
                            Text("Build & Install", color = Color.Black)
                        }
                    }
                }
            }
        } else {
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(Modifier.size(16.dp), accentColor, strokeWidth = 2.dp)
                    Text("Buscando proyectos...", color = Color.Gray, modifier = Modifier.padding(start = 8.dp))
                } else {
                    projects.forEach { project ->
                        val projectName = project.substringAfterLast("/")
                        Surface(
                            onClick = { onProjectClick(project) },
                            color = keyColor,
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                projectName,
                                color = textColor,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ApkDownloadDialog(
    apkPath: String?,
    isDownloading: Boolean,
    downloadProgress: Float,
    isDownloaded: Boolean,
    isWaitingUninstall: Boolean = false,
    onDownload: () -> Unit,
    onInstall: () -> Unit,
    onUninstallAndInstall: () -> Unit,
    onDismiss: () -> Unit
) {
    val accentColor = Color(0xFF4EC9B0)
    val warningColor = Color(0xFFFF9800)

    AlertDialog(
        onDismissRequest = { if (!isDownloading && !isWaitingUninstall) onDismiss() },
        containerColor = Color(0xFF1E1E1E),
        titleContentColor = Color.White,
        textContentColor = Color.LightGray,
        title = {
            Text(
                when {
                    isWaitingUninstall -> "Esperando desinstalación..."
                    isDownloaded -> "APK Descargado"
                    else -> "APK Generado"
                },
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column {
                if (isWaitingUninstall) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = warningColor,
                            strokeWidth = 2.dp
                        )
                        Text(
                            "Confirma la desinstalación en el diálogo del sistema.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "La instalación comenzará automáticamente después.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                } else if (!isDownloaded) {
                    Text(
                        "Se ha generado un APK:",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        apkPath?.substringAfterLast("/") ?: "app.apk",
                        style = MaterialTheme.typography.bodySmall,
                        color = accentColor
                    )
                } else if (isDownloading) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Descargando...")
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { downloadProgress },
                        modifier = Modifier.fillMaxWidth(),
                        color = accentColor
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "${(downloadProgress * 100).toInt()}%",
                        style = MaterialTheme.typography.bodySmall
                    )
                } else if (isDownloaded) {
                    Text(
                        "El APK se ha descargado correctamente.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Si la instalacion falla por conflicto, usa 'Desinstalar e Instalar'.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }
            }
        },
        confirmButton = {
            if (isWaitingUninstall) {
                // No mostrar botones de acción mientras espera
            } else if (isDownloaded) {
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onInstall,
                        colors = ButtonDefaults.buttonColors(containerColor = accentColor)
                    ) {
                        Icon(Icons.Default.InstallMobile, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Instalar", color = Color.Black)
                    }
                    Button(
                        onClick = onUninstallAndInstall,
                        colors = ButtonDefaults.buttonColors(containerColor = warningColor)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Desinstalar e Instalar", color = Color.Black)
                    }
                }
            } else if (!isDownloading) {
                Button(
                    onClick = onDownload,
                    colors = ButtonDefaults.buttonColors(containerColor = accentColor)
                ) {
                    Icon(Icons.Default.Download, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Descargar", color = Color.Black)
                }
            }
        },
        dismissButton = {
            if (!isDownloading) {
                TextButton(onClick = onDismiss) {
                    Text("Cancelar", color = Color.Gray)
                }
            }
        }
    )
}
