package com.example.runpodmanager.ui.screens.terminal

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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.termux.view.TerminalView

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
    onRequestFocus: () -> Unit
) {
    val keyColor = Color(0xFF2D2D2D)
    val textColor = Color(0xFF4EC9B0)

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

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    // APK Download Dialog
    if (uiState.showApkDialog) {
        ApkDownloadDialog(
            apkPath = uiState.apkRemotePath,
            isDownloading = uiState.isDownloadingApk,
            downloadProgress = uiState.downloadProgress,
            isDownloaded = uiState.downloadedApkFile != null,
            onDownload = { viewModel.downloadApk() },
            onInstall = { viewModel.installApk() },
            onUninstallAndInstall = { viewModel.uninstallAndInstallApk() },
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
                            onRequestFocus = { controller.showKeyboard() }
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
    onDownload: () -> Unit,
    onInstall: () -> Unit,
    onUninstallAndInstall: () -> Unit,
    onDismiss: () -> Unit
) {
    val accentColor = Color(0xFF4EC9B0)
    val warningColor = Color(0xFFFF9800)

    AlertDialog(
        onDismissRequest = { if (!isDownloading) onDismiss() },
        containerColor = Color(0xFF1E1E1E),
        titleContentColor = Color.White,
        textContentColor = Color.LightGray,
        title = {
            Text(
                if (isDownloaded) "APK Descargado" else "APK Generado",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column {
                if (!isDownloaded) {
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
                }

                if (isDownloading) {
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
                }

                if (isDownloaded) {
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
            if (isDownloaded) {
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
