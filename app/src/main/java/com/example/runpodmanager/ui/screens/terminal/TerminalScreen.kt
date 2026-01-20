package com.example.runpodmanager.ui.screens.terminal

import android.Manifest
import android.content.pm.PackageManager
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.runpodmanager.data.whisper.WhisperManager
import com.example.runpodmanager.data.whisper.WhisperManagerListener
import com.example.runpodmanager.data.whisper.WhisperState
import com.termux.view.TerminalView
import kotlinx.coroutines.launch

@Composable
fun ExtraKeysBar(
    onKey: (ByteArray) -> Unit,
    onRequestFocus: () -> Unit,
    onMicClick: () -> Unit,
    whisperState: WhisperState = WhisperState.Idle
) {
    val keyColor = Color(0xFF2D2D2D)
    val textColor = Color(0xFF4EC9B0)
    val micActiveColor = Color(0xFFE53935)
    val micProcessingColor = Color(0xFFFF9800)
    val micLoadingColor = Color(0xFF9E9E9E)

    val sendKey: (ByteArray) -> Unit = { bytes ->
        onKey(bytes)
        onRequestFocus()
    }

    val micColor = when (whisperState) {
        is WhisperState.Listening -> micActiveColor
        is WhisperState.Processing -> micProcessingColor
        is WhisperState.Loading -> micLoadingColor
        else -> keyColor
    }

    val micTint = when (whisperState) {
        is WhisperState.Listening, is WhisperState.Processing -> Color.White
        else -> textColor
    }

    Surface(
        color = Color(0xFF1A1A1A),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 4.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Botón de micrófono con Whisper
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
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "Voz (Whisper)",
                        tint = micTint,
                        modifier = Modifier.size(20.dp)
                    )
                    if (whisperState is WhisperState.Processing) {
                        Spacer(modifier = Modifier.size(4.dp))
                        CircularProgressIndicator(
                            modifier = Modifier.size(12.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    }
                }
            }

            ExtraKey("↵", keyColor, textColor) { sendKey(byteArrayOf(0x0D)) } // Enter
            ExtraKey("TAB", keyColor, textColor) { sendKey(byteArrayOf(0x09)) }
            ExtraKey("\u2191", keyColor, textColor) { sendKey(byteArrayOf(0x1B, 0x5B, 0x41)) }
            ExtraKey("\u2193", keyColor, textColor) { sendKey(byteArrayOf(0x1B, 0x5B, 0x42)) }
            ExtraKey("\u2190", keyColor, textColor) { sendKey(byteArrayOf(0x1B, 0x5B, 0x44)) }
            ExtraKey("\u2192", keyColor, textColor) { sendKey(byteArrayOf(0x1B, 0x5B, 0x43)) }
            ExtraKey("C-c", keyColor, textColor) { sendKey(byteArrayOf(0x03)) }
            ExtraKey("~", keyColor, textColor) { sendKey("~".toByteArray()) }
            ExtraKey("ESC", keyColor, textColor) { sendKey(byteArrayOf(0x1B)) }
        }
    }
}

@Composable
fun ExtraKey(
    label: String,
    backgroundColor: Color,
    textColor: Color,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = backgroundColor,
        shape = RoundedCornerShape(4.dp),
        modifier = Modifier.padding(2.dp)
    ) {
        Text(
            text = label,
            color = textColor,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            style = MaterialTheme.typography.bodyMedium
        )
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

    // Whisper state
    var whisperState by remember { mutableStateOf<WhisperState>(WhisperState.Idle) }
    var amplitude by remember { mutableFloatStateOf(0f) }

    // Initialize WhisperManager
    val whisperManager = remember {
        WhisperManager(context).apply {
            setListener(object : WhisperManagerListener {
                override fun onStateChanged(state: WhisperState) {
                    whisperState = state
                    Log.d("Whisper", "State: $state")
                }

                override fun onTranscriptionResult(text: String) {
                    Log.d("Whisper", "Resultado: $text")
                    if (text.isNotBlank()) {
                        viewModel.sendVoiceCommand(text)
                    }
                }

                override fun onError(message: String) {
                    scope.launch { snackbarHostState.showSnackbar(message) }
                }

                override fun onAmplitudeUpdate(amp: Float) {
                    amplitude = amp
                }
            })
        }
    }

    // Initialize Whisper model on first composition
    LaunchedEffect(Unit) {
        whisperManager.initialize()
    }

    // Cleanup on dispose
    DisposableEffect(Unit) {
        onDispose {
            whisperManager.release()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            whisperManager.startListening()
        } else {
            scope.launch { snackbarHostState.showSnackbar("Se necesita permiso de microfono para usar voz") }
        }
    }

    fun startVoiceRecognition() {
        val hasPermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasPermission) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }

        if (whisperManager.isListening()) {
            whisperManager.stopListening()
        } else {
            whisperManager.startListening()
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
                            whisperState = whisperState
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
