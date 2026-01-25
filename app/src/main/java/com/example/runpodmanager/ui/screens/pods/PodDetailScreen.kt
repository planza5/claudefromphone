package com.example.runpodmanager.ui.screens.pods

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Button
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
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.launch
import com.example.runpodmanager.ui.components.LoadingOverlay
import com.example.runpodmanager.ui.components.StatusChip

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PodDetailScreen(
    viewModel: PodDetailViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onPodDeleted: () -> Unit = onNavigateBack,
    onNavigateToTerminal: (host: String, port: Int) -> Unit = { _, _ -> }
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
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
                        DetailCard {
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

                        // SSH Card
                        val sshPublicPort = pod.portMappings?.get("22")
                        val sshPublicIp = pod.publicIp
                        if (sshPublicPort != null && sshPublicIp != null) {
                            val sshCommand = "ssh root@$sshPublicIp -p $sshPublicPort"
                            DetailCard(containerColor = MaterialTheme.colorScheme.primaryContainer) {
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

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            clipboardManager.setText(AnnotatedString(sshCommand))
                                            scope.launch { snackbarHostState.showSnackbar("Comando SSH copiado") }
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Default.ContentCopy, contentDescription = null)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Copiar")
                                    }

                                    Button(
                                        onClick = { onNavigateToTerminal(sshPublicIp, sshPublicPort) },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Default.Terminal, contentDescription = null)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Terminal")
                                    }
                                }
                            }
                        }

                        // Jupyter Lab Card
                        val jupyterPublicPort = pod.portMappings?.get("8888")
                        if (jupyterPublicPort != null && sshPublicIp != null) {
                            val jupyterUrl = "http://$sshPublicIp:$jupyterPublicPort"
                            DetailCard(containerColor = MaterialTheme.colorScheme.tertiaryContainer) {
                                Text(
                                    text = "Jupyter Lab",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )

                                Text(
                                    text = jupyterUrl,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )

                                Button(
                                    onClick = {
                                        clipboardManager.setText(AnnotatedString(jupyterUrl))
                                        scope.launch { snackbarHostState.showSnackbar("URL de Jupyter copiada") }
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
private fun DetailCard(
    modifier: Modifier = Modifier,
    containerColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.surfaceVariant,
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            content()
        }
    }
}
