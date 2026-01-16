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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen(
    viewModel: TerminalViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scrollState = rememberScrollState()

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    // Auto-scroll to bottom when output changes
    LaunchedEffect(uiState.terminalOutput) {
        scrollState.animateScrollTo(scrollState.maxValue)
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .imePadding()
        ) {
            // Terminal output area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color(0xFF0D0D0D))
                    .padding(8.dp)
            ) {
                if (uiState.isConnecting) {
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
                } else if (!uiState.isConnected && uiState.terminalOutput.isEmpty()) {
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
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Conectar")
                        }
                    }
                } else {
                    Text(
                        text = uiState.terminalOutput,
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                            .horizontalScroll(rememberScrollState()),
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            color = Color(0xFFD4D4D4),
                            lineHeight = 16.sp
                        )
                    )
                }
            }

            // Special keys row
            if (uiState.isConnected) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF252526))
                        .horizontalScroll(rememberScrollState())
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    SpecialKeyButton("Tab") { viewModel.sendSpecialKey(SpecialKey.TAB) }
                    SpecialKeyButton("Ctrl+C") { viewModel.sendSpecialKey(SpecialKey.CTRL_C) }
                    SpecialKeyButton("Ctrl+D") { viewModel.sendSpecialKey(SpecialKey.CTRL_D) }
                    SpecialKeyButton("Ctrl+Z") { viewModel.sendSpecialKey(SpecialKey.CTRL_Z) }
                    SpecialKeyButton("Esc") { viewModel.sendSpecialKey(SpecialKey.ESCAPE) }
                    SpecialKeyButton("↑") { viewModel.sendSpecialKey(SpecialKey.ARROW_UP) }
                    SpecialKeyButton("↓") { viewModel.sendSpecialKey(SpecialKey.ARROW_DOWN) }
                    SpecialKeyButton("←") { viewModel.sendSpecialKey(SpecialKey.ARROW_LEFT) }
                    SpecialKeyButton("→") { viewModel.sendSpecialKey(SpecialKey.ARROW_RIGHT) }
                }
            }

            // Input area
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1E1E1E))
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = uiState.currentInput,
                    onValueChange = viewModel::onInputChange,
                    modifier = Modifier.weight(1f),
                    placeholder = {
                        Text(
                            if (uiState.isConnected) "Escribe un comando..." else "Desconectado",
                            color = Color.Gray
                        )
                    },
                    enabled = uiState.isConnected,
                    singleLine = true,
                    textStyle = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp,
                        color = Color.White
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF4EC9B0),
                        unfocusedBorderColor = Color(0xFF3C3C3C),
                        cursorColor = Color(0xFF4EC9B0),
                        focusedContainerColor = Color(0xFF252526),
                        unfocusedContainerColor = Color(0xFF252526)
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { viewModel.sendCommand() })
                )

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(
                    onClick = viewModel::sendCommand,
                    enabled = uiState.isConnected && uiState.currentInput.isNotEmpty()
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Enviar",
                        tint = if (uiState.isConnected && uiState.currentInput.isNotEmpty())
                            Color(0xFF4EC9B0) else Color.Gray
                    )
                }
            }
        }
    }
}

@Composable
private fun SpecialKeyButton(
    text: String,
    onClick: () -> Unit
) {
    FilledTonalButton(
        onClick = onClick,
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = Color(0xFF3C3C3C),
            contentColor = Color(0xFFD4D4D4)
        ),
        modifier = Modifier.height(36.dp)
    ) {
        Text(text, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
    }
}
