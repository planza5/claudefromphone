package com.example.runpodmanager.ui.screens.create

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Code
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreatePodScreen(
    viewModel: CreatePodViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onPodCreated: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    LaunchedEffect(uiState.isCreated) {
        if (uiState.isCreated) {
            onPodCreated()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Crear Pod") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Basic Info
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
                        text = "Informacion Basica",
                        style = MaterialTheme.typography.titleMedium
                    )

                    OutlinedTextField(
                        value = uiState.name,
                        onValueChange = viewModel::onNameChange,
                        label = { Text("Nombre del Pod") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = !uiState.isLoading
                    )

                    // Compute Type Selection
                    Text(
                        text = "Tipo de Compute",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = uiState.computeType == ComputeType.GPU,
                            onClick = { viewModel.onComputeTypeChange(ComputeType.GPU) },
                            label = { Text("GPU") },
                            enabled = !uiState.isLoading
                        )
                        FilterChip(
                            selected = uiState.computeType == ComputeType.CPU,
                            onClick = { viewModel.onComputeTypeChange(ComputeType.CPU) },
                            label = { Text("CPU") },
                            enabled = !uiState.isLoading
                        )
                    }

                    // GPU or CPU selection based on compute type
                    if (uiState.computeType == ComputeType.GPU) {
                        var gpuExpanded by remember { mutableStateOf(false) }
                        ExposedDropdownMenuBox(
                            expanded = gpuExpanded,
                            onExpandedChange = { gpuExpanded = !gpuExpanded }
                        ) {
                            OutlinedTextField(
                                value = "${uiState.selectedGpu.name} - $${String.format("%.2f", uiState.selectedGpu.costPerHour)}/hr",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Tipo de GPU") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = gpuExpanded) },
                                modifier = Modifier
                                    .menuAnchor()
                                    .fillMaxWidth(),
                                enabled = !uiState.isLoading
                            )
                            ExposedDropdownMenu(
                                expanded = gpuExpanded,
                                onDismissRequest = { gpuExpanded = false }
                            ) {
                                viewModel.availableGpus.forEach { gpu ->
                                    DropdownMenuItem(
                                        text = {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(gpu.name)
                                                Text(
                                                    "$${String.format("%.2f", gpu.costPerHour)}/hr",
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        },
                                        onClick = {
                                            viewModel.onGpuChange(gpu)
                                            gpuExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    } else {
                        var cpuExpanded by remember { mutableStateOf(false) }
                        ExposedDropdownMenuBox(
                            expanded = cpuExpanded,
                            onExpandedChange = { cpuExpanded = !cpuExpanded }
                        ) {
                            OutlinedTextField(
                                value = "${uiState.selectedCpu.name} - $${String.format("%.2f", uiState.selectedCpu.costPerHour)}/hr",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Tipo de CPU") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = cpuExpanded) },
                                modifier = Modifier
                                    .menuAnchor()
                                    .fillMaxWidth(),
                                enabled = !uiState.isLoading
                            )
                            ExposedDropdownMenu(
                                expanded = cpuExpanded,
                                onDismissRequest = { cpuExpanded = false }
                            ) {
                                viewModel.availableCpus.forEach { cpu ->
                                    DropdownMenuItem(
                                        text = {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(cpu.name)
                                                Text(
                                                    "$${String.format("%.2f", cpu.costPerHour)}/hr",
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        },
                                        onClick = {
                                            viewModel.onCpuChange(cpu)
                                            cpuExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Network Volume
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.Cloud,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Network Volume",
                            style = MaterialTheme.typography.titleMedium
                        )
                        if (uiState.isLoadingVolumes) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                        }
                    }

                    if (uiState.networkVolumes.isEmpty() && !uiState.isLoadingVolumes) {
                        Text(
                            text = "No tienes Network Volumes disponibles",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else if (uiState.networkVolumes.isNotEmpty()) {
                        var volumeExpanded by remember { mutableStateOf(false) }
                        ExposedDropdownMenuBox(
                            expanded = volumeExpanded,
                            onExpandedChange = { volumeExpanded = !volumeExpanded }
                        ) {
                            OutlinedTextField(
                                value = uiState.selectedNetworkVolume?.let {
                                    "${it.name} (${it.size ?: "?"}GB)"
                                } ?: "Seleccionar...",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Network Volume") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = volumeExpanded) },
                                modifier = Modifier
                                    .menuAnchor()
                                    .fillMaxWidth(),
                                enabled = !uiState.isLoading
                            )
                            ExposedDropdownMenu(
                                expanded = volumeExpanded,
                                onDismissRequest = { volumeExpanded = false }
                            ) {
                                uiState.networkVolumes.forEach { volume ->
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(volume.name)
                                                Text(
                                                    text = "${volume.size ?: "?"}GB - ${volume.dataCenter?.name ?: volume.dataCenterId ?: ""}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        },
                                        onClick = {
                                            viewModel.onNetworkVolumeChange(volume)
                                            volumeExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Storage
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
                        text = "Disco Contenedor",
                        style = MaterialTheme.typography.titleMedium
                    )

                    Text("${uiState.containerDiskGb} GB")
                    Slider(
                        value = uiState.containerDiskGb.toFloat(),
                        onValueChange = { viewModel.onContainerDiskChange(it.toInt()) },
                        valueRange = 5f..100f,
                        steps = 18,
                        enabled = !uiState.isLoading
                    )
                }
            }

            // Start Script
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.Code,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Script de Inicio",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }

                    OutlinedTextField(
                        value = uiState.startScript,
                        onValueChange = viewModel::onStartScriptChange,
                        label = { Text("Comando o script") },
                                                modifier = Modifier.fillMaxWidth(),
                        minLines = 4,
                        maxLines = 8,
                        enabled = !uiState.isLoading
                    )

                    Text(
                        text = "Opcional: se ejecuta al iniciar el pod",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Create Button
            Button(
                onClick = viewModel::createPod,
                modifier = Modifier.fillMaxWidth(),
                enabled = uiState.name.isNotBlank() && !uiState.isLoading
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text("Crear Pod")
                }
            }
        }
    }
}
