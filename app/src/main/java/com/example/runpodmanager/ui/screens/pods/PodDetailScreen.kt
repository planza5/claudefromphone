package com.example.runpodmanager.ui.screens.pods

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.launch
import com.example.runpodmanager.ui.components.LoadingOverlay
import com.example.runpodmanager.ui.components.StatusChip

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PodDetailScreen(
    viewModel: PodDetailViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onPodDeleted: () -> Unit = onNavigateBack
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showDeleteDialog by remember { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    LaunchedEffect(uiState.isDeleted) {
        if (uiState.isDeleted) {
            onPodDeleted()
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Eliminar Pod") },
            text = { Text("Estas seguro de que quieres eliminar este pod? Esta accion no se puede deshacer.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        viewModel.deletePod()
                    }
                ) {
                    Text("Eliminar", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(uiState.pod?.name ?: "Detalle del Pod") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                actions = {
                    IconButton(onClick = viewModel::loadPod) {
                        Icon(Icons.Default.Refresh, contentDescription = "Actualizar")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        LoadingOverlay(
            isLoading = uiState.isActionLoading,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                uiState.isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                uiState.pod != null -> {
                    val pod = uiState.pod!!
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Status Card
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Estado",
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    StatusChip(status = pod.desiredStatus ?: "UNKNOWN")
                                }
                            }
                        }

                        // Info Card
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = "Informacion",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                InfoRow("ID", pod.id)
                                InfoRow("Nombre", pod.name)
                                pod.gpuTypeId?.let { InfoRow("GPU", it) }
                                pod.machine?.gpuDisplayName?.let { InfoRow("GPU Display", it) }
                                pod.gpuCount?.let { InfoRow("GPUs", it.toString()) }
                                pod.imageName?.let { InfoRow("Imagen", it) }
                                pod.containerDiskInGb?.let { InfoRow("Disco", "${it} GB") }
                                pod.volumeInGb?.let { InfoRow("Volumen", "${it} GB") }
                                pod.volumeMountPath?.let { InfoRow("Mount Path", it) }
                                pod.costPerHr?.let { InfoRow("Costo", "$${String.format("%.3f", it)}/hr") }
                                pod.machine?.location?.let { InfoRow("Ubicacion", it) }
                            }
                        }

                        // SSH Card
                        pod.runtime?.ports?.let { ports ->
                            val sshPort = ports.find { it.privatePort == 22 }
                            if (sshPort != null && sshPort.ip != null && sshPort.publicPort != null) {
                                val sshCommand = "ssh root@${sshPort.ip} -p ${sshPort.publicPort}"
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer
                                    )
                                ) {
                                    Column(
                                        modifier = Modifier.padding(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Terminal,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                            Text(
                                                text = "Conexion SSH",
                                                style = MaterialTheme.typography.titleMedium,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        }

                                        Text(
                                            text = sshCommand,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )

                                        Button(
                                            onClick = {
                                                clipboardManager.setText(AnnotatedString(sshCommand))
                                                scope.launch {
                                                    snackbarHostState.showSnackbar("Comando SSH copiado")
                                                }
                                            },
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Icon(Icons.Default.ContentCopy, contentDescription = null)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Copiar comando SSH")
                                        }
                                    }
                                }
                            }
                        }

                        // Jupyter Lab Card
                        pod.runtime?.ports?.let { ports ->
                            val jupyterPort = ports.find { it.privatePort == 8888 }
                            if (jupyterPort != null && jupyterPort.ip != null && jupyterPort.publicPort != null) {
                                val jupyterUrl = "http://${jupyterPort.ip}:${jupyterPort.publicPort}"
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                                    )
                                ) {
                                    Column(
                                        modifier = Modifier.padding(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(
                                                text = "📓",
                                                fontSize = 20.sp
                                            )
                                            Text(
                                                text = "Jupyter Lab",
                                                style = MaterialTheme.typography.titleMedium,
                                                color = MaterialTheme.colorScheme.onTertiaryContainer
                                            )
                                        }

                                        Text(
                                            text = jupyterUrl,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onTertiaryContainer
                                        )

                                        Button(
                                            onClick = {
                                                clipboardManager.setText(AnnotatedString(jupyterUrl))
                                                scope.launch {
                                                    snackbarHostState.showSnackbar("URL de Jupyter copiada")
                                                }
                                            },
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Icon(Icons.Default.ContentCopy, contentDescription = null)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Copiar URL Jupyter")
                                        }
                                    }
                                }
                            }
                        }

                        // Ports Card
                        pod.runtime?.ports?.let { ports ->
                            if (ports.isNotEmpty()) {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                                    )
                                ) {
                                    Column(
                                        modifier = Modifier.padding(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = "Puertos",
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                        ports.forEach { port ->
                                            Text(
                                                text = "${port.privatePort} -> ${port.ip}:${port.publicPort} (${port.type})",
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Actions
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = "Acciones",
                                    style = MaterialTheme.typography.titleMedium
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    val isRunning = pod.desiredStatus?.uppercase() == "RUNNING"

                                    if (isRunning) {
                                        OutlinedButton(
                                            onClick = viewModel::stopPod,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(Icons.Default.Stop, contentDescription = null)
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text("Detener")
                                        }
                                    } else {
                                        Button(
                                            onClick = viewModel::startPod,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text("Iniciar")
                                        }
                                    }

                                    OutlinedButton(
                                        onClick = viewModel::restartPod,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Default.Refresh, contentDescription = null)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text("Reiniciar")
                                    }
                                }

                                Button(
                                    onClick = { showDeleteDialog = true },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.error
                                    )
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = null)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("Eliminar Pod")
                                }
                            }
                        }
                    }
                }
                else -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No se pudo cargar el pod")
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f, fill = false),
            maxLines = 2
        )
    }
}
