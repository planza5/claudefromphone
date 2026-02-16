package com.example.runpodmanager.ui.screens.terminal

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.platform.LocalLifecycleOwner
import com.example.runpodmanager.ui.components.BuildProgressOverlay
import com.example.runpodmanager.ui.components.BuildResultOverlay
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
    var showExitConfirmDialog by remember { mutableStateOf(false) }

    BackHandler {
        showExitConfirmDialog = true
    }

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
            // Aumentar tiempos de espera para no cortar rápido
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 5000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L)
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
                    IconButton(onClick = { showExitConfirmDialog = true }) {
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
                            isAppInstalled = uiState.isAppInstalled,
                            isApkAvailable = uiState.isApkAvailable,
                            isBuilding = uiState.isBuilding,
                            isDownloadingApk = uiState.isDownloadingApk,
                            downloadProgress = uiState.downloadProgress,
                            onProjectClick = { project -> viewModel.selectProject(project) },
                            onBackClick = { viewModel.goBackFromProject() },
                            onDeleteProjectClick = {
                                uiState.selectedProject?.let { viewModel.showDeleteProjectDialog(it) }
                            },
                            onBuildClick = { viewModel.buildProject() },
                            onInstallClick = { viewModel.installApk() },
                            onUninstallClick = { viewModel.uninstallApp() },
                            onCreateClick = { viewModel.showCreateProjectDialog() },
                            onRenameClick = { project -> viewModel.showRenameProjectDialog(project) }
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

            BuildProgressOverlay(
                isBuilding = uiState.isBuilding,
                isDownloadingApk = uiState.isDownloadingApk,
                downloadProgress = uiState.downloadProgress
            )

            BuildResultOverlay(
                buildSuccess = uiState.buildSuccess,
                canCopyLog = !uiState.buildFailureLog.isNullOrBlank(),
                onCopyLog = viewModel::copyBuildLogToClipboard,
                onDismiss = viewModel::clearBuildResult
            )

            if (showExitConfirmDialog) {
                AlertDialog(
                    onDismissRequest = { showExitConfirmDialog = false },
                    title = { Text("Salir del terminal") },
                    text = { Text("¿Quieres salir de esta pantalla SSH?") },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                showExitConfirmDialog = false
                                onNavigateBack()
                            }
                        ) {
                            Text("Salir")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showExitConfirmDialog = false }) {
                            Text("Cancelar")
                        }
                    }
                )
            }

            if (uiState.showInstallConflictDialog) {
                AlertDialog(
                    onDismissRequest = { viewModel.hideInstallConflictDialog() },
                    title = { Text("Conflicto de instalacion") },
                    text = {
                        Text("Ya hay una app instalada con ese paquete. Desinstala primero para evitar conflicto de firma.")
                    },
                    confirmButton = {
                        TextButton(onClick = { viewModel.confirmUninstallForInstallConflict() }) {
                            Text("Desinstalar")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { viewModel.hideInstallConflictDialog() }) {
                            Text("Cancelar")
                        }
                    }
                )
            }

            // Diálogo para crear proyecto
            if (uiState.showCreateProjectDialog) {
                AlertDialog(
                    onDismissRequest = { viewModel.hideCreateProjectDialog() },
                    title = { Text("Nuevo Proyecto") },
                    text = {
                        OutlinedTextField(
                            value = uiState.newProjectName,
                            onValueChange = { viewModel.updateNewProjectName(it) },
                            label = { Text("Nombre del proyecto") },
                            singleLine = true
                        )
                    },
                    confirmButton = {
                        TextButton(
                            onClick = { viewModel.createProject() },
                            enabled = !uiState.isCreatingProject && uiState.newProjectName.isNotBlank()
                        ) {
                            if (uiState.isCreatingProject) {
                                CircularProgressIndicator(Modifier.size(16.dp))
                            } else {
                                Text("Crear")
                            }
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { viewModel.hideCreateProjectDialog() }) {
                            Text("Cancelar")
                        }
                    }
                )
            }

            // Diálogo para renombrar proyecto
            if (uiState.showRenameProjectDialog) {
                AlertDialog(
                    onDismissRequest = { viewModel.hideRenameProjectDialog() },
                    title = { Text("Renombrar Proyecto") },
                    text = {
                        OutlinedTextField(
                            value = uiState.newProjectName,
                            onValueChange = { viewModel.updateNewProjectName(it) },
                            label = { Text("Nuevo nombre") },
                            singleLine = true
                        )
                    },
                    confirmButton = {
                        TextButton(
                            onClick = { viewModel.renameProject() },
                            enabled = !uiState.isRenamingProject && uiState.newProjectName.isNotBlank()
                        ) {
                            if (uiState.isRenamingProject) {
                                CircularProgressIndicator(Modifier.size(16.dp))
                            } else {
                                Text("Renombrar")
                            }
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { viewModel.hideRenameProjectDialog() }) {
                            Text("Cancelar")
                        }
                    }
                )
            }

            // Diálogo para confirmar eliminación
            if (uiState.showDeleteProjectDialog) {
                AlertDialog(
                    onDismissRequest = { viewModel.hideDeleteProjectDialog() },
                    title = { Text("Eliminar Proyecto") },
                    text = {
                        Text("¿Eliminar ${uiState.projectToManage?.substringAfterLast("/")}?\n\nEsta acción no se puede deshacer.")
                    },
                    confirmButton = {
                        TextButton(
                            onClick = { viewModel.deleteProject() },
                            enabled = !uiState.isDeletingProject
                        ) {
                            if (uiState.isDeletingProject) {
                                CircularProgressIndicator(Modifier.size(16.dp))
                            } else {
                                Text("Eliminar", color = Color.Red)
                            }
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { viewModel.hideDeleteProjectDialog() }) {
                            Text("Cancelar")
                        }
                    }
                )
            }
        }
    }
}

// Color constants for ProjectsBar
private object ProjectsBarColors {
    val Background = Color(0xFF1A1A1A)
    val Key = Color(0xFF2D2D2D)
    val Text = Color(0xFF9CDCFE)
    val Accent = Color(0xFF4EC9B0)
    val Install = Color(0xFF569CD6)
    val Warning = Color(0xFFFF9800)
    val Danger = Color(0xFFE53935)
    val Installed = Color(0xFF6A9955)
    val NotInstalled = Color(0xFFCE9178)
}

@Composable
private fun RowScope.ActionButton(
    onClick: () -> Unit,
    color: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    iconTint: Color = Color.Black,
    weight: Float = 1f
) {
    Surface(
        onClick = onClick,
        color = color,
        shape = RoundedCornerShape(4.dp),
        modifier = Modifier.weight(weight)
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = iconTint,
            modifier = Modifier.padding(12.dp).size(20.dp)
        )
    }
}

@Composable
private fun InstallStatusLabel(isAppInstalled: Boolean?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        when (isAppInstalled) {
            null -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(12.dp),
                    color = ProjectsBarColors.Text,
                    strokeWidth = 1.5.dp
                )
                Spacer(Modifier.size(6.dp))
                Text("Checking...", color = ProjectsBarColors.Text, style = MaterialTheme.typography.labelSmall)
            }
            true -> Text("● Installed", color = ProjectsBarColors.Installed, style = MaterialTheme.typography.labelSmall)
            false -> Text("○ Not installed", color = ProjectsBarColors.NotInstalled, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
fun ProjectsBar(
    projects: List<String>,
    isLoading: Boolean,
    selectedProject: String?,
    isAppInstalled: Boolean?,
    isApkAvailable: Boolean?,
    isBuilding: Boolean,
    isDownloadingApk: Boolean,
    downloadProgress: Float,
    onProjectClick: (String) -> Unit,
    onBackClick: () -> Unit,
    onDeleteProjectClick: () -> Unit,
    onBuildClick: () -> Unit,
    onInstallClick: () -> Unit,
    onUninstallClick: () -> Unit,
    onCreateClick: () -> Unit,
    onRenameClick: (String) -> Unit
) {
    if (projects.isEmpty() && !isLoading && selectedProject == null) return

    Surface(color = ProjectsBarColors.Background, modifier = Modifier.fillMaxWidth()) {
        if (selectedProject != null) {
            SelectedProjectView(
                isAppInstalled = isAppInstalled,
                isApkAvailable = isApkAvailable,
                isBuilding = isBuilding,
                isDownloadingApk = isDownloadingApk,
                downloadProgress = downloadProgress,
                onBackClick = onBackClick,
                onDeleteProjectClick = onDeleteProjectClick,
                onBuildClick = onBuildClick,
                onInstallClick = onInstallClick,
                onUninstallClick = onUninstallClick
            )
        } else {
            ProjectListView(
                projects = projects,
                isLoading = isLoading,
                onProjectClick = onProjectClick,
                onCreateClick = onCreateClick,
                onRenameClick = onRenameClick
            )
        }
    }
}

@Composable
private fun SelectedProjectView(
    isAppInstalled: Boolean?,
    isApkAvailable: Boolean?,
    isBuilding: Boolean,
    isDownloadingApk: Boolean,
    downloadProgress: Float,
    onBackClick: () -> Unit,
    onDeleteProjectClick: () -> Unit,
    onBuildClick: () -> Unit,
    onInstallClick: () -> Unit,
    onUninstallClick: () -> Unit
) {
    Column {
        InstallStatusLabel(isAppInstalled)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Back button
            Surface(
                onClick = onBackClick,
                color = ProjectsBarColors.Key,
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier.weight(0.8f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = null,
                        tint = ProjectsBarColors.Text,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // Build button
            if (isBuilding) {
                Surface(
                    color = ProjectsBarColors.Accent.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(10.dp).size(24.dp),
                        color = Color.Black,
                        strokeWidth = 2.dp
                    )
                }
            } else {
                ActionButton(
                    onClick = onBuildClick,
                    color = ProjectsBarColors.Accent,
                    icon = Icons.Default.Build,
                    contentDescription = "Build"
                )
            }

            // Install button
            if (isApkAvailable == true) {
                if (isDownloadingApk) {
                    Surface(
                        color = ProjectsBarColors.Install.copy(alpha = 0.7f),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(8.dp)) {
                            CircularProgressIndicator(
                                progress = { downloadProgress },
                                modifier = Modifier.size(28.dp),
                                color = Color.Black,
                                strokeWidth = 3.dp
                            )
                            Text(
                                text = "${(downloadProgress * 100).toInt()}%",
                                color = Color.Black,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                } else {
                    ActionButton(
                        onClick = onInstallClick,
                        color = ProjectsBarColors.Install,
                        icon = Icons.Default.Download,
                        contentDescription = "Install"
                    )
                }
            }

            if (isAppInstalled == true) {
                ActionButton(
                    onClick = onUninstallClick,
                    color = ProjectsBarColors.Warning,
                    icon = Icons.Default.Delete,
                    contentDescription = "Uninstall"
                )
            }

            ActionButton(
                onClick = onDeleteProjectClick,
                color = ProjectsBarColors.Danger,
                icon = Icons.Default.DeleteForever,
                contentDescription = "Delete project",
                iconTint = Color.White
            )
        }
    }
}

@Composable
private fun ProjectListView(
    projects: List<String>,
    isLoading: Boolean,
    onProjectClick: (String) -> Unit,
    onCreateClick: () -> Unit,
    onRenameClick: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 4.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Botón crear proyecto
        Surface(
            onClick = onCreateClick,
            color = ProjectsBarColors.Accent,
            shape = RoundedCornerShape(4.dp)
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = "Nuevo proyecto",
                modifier = Modifier.padding(10.dp),
                tint = Color.Black
            )
        }

        if (isLoading) {
            CircularProgressIndicator(Modifier.size(16.dp), ProjectsBarColors.Accent, strokeWidth = 2.dp)
            Text("Buscando proyectos...", color = Color.Gray, modifier = Modifier.padding(start = 8.dp))
        } else {
            projects.forEach { project ->
                ProjectItem(
                    project = project,
                    onProjectClick = onProjectClick,
                    onRenameClick = onRenameClick
                )
            }
        }
    }
}

@Composable
private fun ProjectItem(
    project: String,
    onProjectClick: (String) -> Unit,
    onRenameClick: (String) -> Unit
) {
    Box {
        Surface(
            color = ProjectsBarColors.Key,
            shape = RoundedCornerShape(4.dp),
            modifier = Modifier.pointerInput(project) {
                detectTapGestures(
                    onTap = { onProjectClick(project) },
                    onLongPress = { onRenameClick(project) }
                )
            }
        ) {
            Text(
                project.substringAfterLast("/"),
                color = ProjectsBarColors.Text,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            )
        }
    }
}
