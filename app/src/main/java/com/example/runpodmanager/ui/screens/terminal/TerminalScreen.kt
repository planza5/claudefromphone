package com.example.runpodmanager.ui.screens.terminal

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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

enum class SpeechState { Idle, Listening, Processing }

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
    speechState: SpeechState = SpeechState.Idle
) {
    val keyColor = Color(0xFF2D2D2D)
    val textColor = Color(0xFF4EC9B0)

    val sendKey: (ByteArray) -> Unit = { bytes ->
        onKey(bytes)
        onRequestFocus()
    }

    val (micColor, micTint) = when (speechState) {
        SpeechState.Listening -> Color(0xFFE53935) to Color.White
        SpeechState.Processing -> Color(0xFFFF9800) to Color.White
        else -> keyColor to textColor
    }

    Surface(color = Color(0xFF1A1A1A), modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 4.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                onClick = onMicClick,
                color = micColor,
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier.padding(2.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Icon(Icons.Default.Mic, "Voz", tint = micTint, modifier = Modifier.size(20.dp))
                    if (speechState == SpeechState.Processing) {
                        Spacer(Modifier.size(4.dp))
                        CircularProgressIndicator(Modifier.size(12.dp), Color.White, strokeWidth = 2.dp)
                    }
                }
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

    // Speech Recognition state
    var speechState by remember { mutableStateOf(SpeechState.Idle) }

    // Speech Recognizer
    val speechRecognizer = remember {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            SpeechRecognizer.createSpeechRecognizer(context)
        } else {
            null
        }
    }

    // Setup speech recognizer listener
    DisposableEffect(speechRecognizer) {
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                speechState = SpeechState.Listening
            }

            override fun onBeginningOfSpeech() {}

            override fun onRmsChanged(rmsdB: Float) {}

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                speechState = SpeechState.Processing
            }

            override fun onError(error: Int) {
                speechState = SpeechState.Idle
                val errorMessage = when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH -> "No se reconoció ninguna voz"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Tiempo de espera agotado"
                    SpeechRecognizer.ERROR_NETWORK -> "Error de red"
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Timeout de red"
                    else -> "Error de reconocimiento de voz"
                }
                scope.launch { snackbarHostState.showSnackbar(errorMessage) }
            }

            override fun onResults(results: Bundle?) {
                speechState = SpeechState.Idle
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull()
                if (!text.isNullOrBlank()) {
                    viewModel.sendVoiceCommand(text)
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {}

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        onDispose {
            speechRecognizer?.destroy()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startSpeechRecognition(speechRecognizer)
        } else {
            scope.launch { snackbarHostState.showSnackbar("Se necesita permiso de micrófono para usar voz") }
        }
    }

    fun startVoiceRecognition() {
        if (speechRecognizer == null) {
            scope.launch { snackbarHostState.showSnackbar("Reconocimiento de voz no disponible") }
            return
        }

        val hasPermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasPermission) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }

        if (speechState == SpeechState.Listening) {
            speechRecognizer.stopListening()
            speechState = SpeechState.Idle
        } else {
            startSpeechRecognition(speechRecognizer)
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
                            speechState = speechState
                        )
                        ProjectsBar(
                            projects = uiState.projects,
                            isLoading = uiState.isLoadingProjects,
                            onProjectClick = { project ->
                                // Por ahora solo imprime el proyecto seleccionado
                            }
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

private fun startSpeechRecognition(speechRecognizer: SpeechRecognizer?) {
    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-ES")
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
    }
    speechRecognizer?.startListening(intent)
}

@Composable
fun ProjectsBar(
    projects: List<String>,
    isLoading: Boolean,
    onProjectClick: (String) -> Unit
) {
    if (projects.isEmpty() && !isLoading) return

    val keyColor = Color(0xFF2D2D2D)
    val textColor = Color(0xFF9CDCFE)

    Surface(color = Color(0xFF1A1A1A), modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (isLoading) {
                Row(
                    modifier = Modifier.padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(Modifier.size(16.dp), Color(0xFF4EC9B0), strokeWidth = 2.dp)
                    Text("Buscando proyectos...", color = Color.Gray)
                }
            } else {
                projects.forEach { project ->
                    val projectName = project.substringAfterLast("/")
                    Surface(
                        onClick = { onProjectClick(project) },
                        color = keyColor,
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.fillMaxWidth()
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
