package com.example.runpodmanager.ui.screens.terminal

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.runpodmanager.data.ssh.SshConnectionState
import com.example.runpodmanager.data.ssh.SshForegroundService
import com.example.runpodmanager.data.ssh.SshManager
import com.example.runpodmanager.data.ssh.SshTerminalBridge
import com.termux.terminal.TerminalSession
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TerminalUiState(
    val host: String = "",
    val port: Int = 22,
    val isConnected: Boolean = false,
    val isConnecting: Boolean = false,
    val currentInput: String = "",
    val errorMessage: String? = null,
    val terminalReady: Boolean = false
)

@HiltViewModel
class TerminalViewModel @Inject constructor(
    private val sshManager: SshManager,
    private val bridge: SshTerminalBridge,
    @ApplicationContext private val context: Context,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiState = MutableStateFlow(TerminalUiState())
    val uiState: StateFlow<TerminalUiState> = _uiState.asStateFlow()

    // Terminal controller and session
    val controller = TerminalController(context)
    var session: TerminalSession? = null
        private set

    private val host: String = savedStateHandle.get<String>("host") ?: ""
    private val port: Int = savedStateHandle.get<Int>("port") ?: 22

    init {
        _uiState.value = _uiState.value.copy(host = host, port = port)

        // Observe connection state
        viewModelScope.launch {
            sshManager.connectionState.collect { state ->
                when (state) {
                    is SshConnectionState.Disconnected -> {
                        _uiState.value = _uiState.value.copy(
                            isConnected = false,
                            isConnecting = false,
                            terminalReady = false
                        )
                        bridge.stopOutputCollection()
                        stopForegroundService()
                    }
                    is SshConnectionState.Connecting -> {
                        _uiState.value = _uiState.value.copy(
                            isConnecting = true,
                            isConnected = false
                        )
                    }
                    is SshConnectionState.Connected -> {
                        _uiState.value = _uiState.value.copy(
                            isConnected = true,
                            isConnecting = false,
                            errorMessage = null
                        )
                        // Start foreground service to keep connection alive
                        startForegroundService()
                        // Initialize terminal when connected
                        initializeTerminal()
                    }
                    is SshConnectionState.Error -> {
                        _uiState.value = _uiState.value.copy(
                            isConnected = false,
                            isConnecting = false,
                            errorMessage = state.message,
                            terminalReady = false
                        )
                        bridge.stopOutputCollection()
                        stopForegroundService()
                    }
                }
            }
        }

        // Auto-connect if we have host and port
        if (host.isNotEmpty() && port > 0) {
            connect()
        }
    }

    private fun initializeTerminal() {
        // Create session through bridge
        val newSession = bridge.createSession(controller, 2000)
        session = newSession
        controller.setSession(newSession)

        // Start collecting SSH output and feeding to terminal
        bridge.startOutputCollection()

        _uiState.value = _uiState.value.copy(terminalReady = true)
    }

    fun connect() {
        viewModelScope.launch {
            sshManager.connect(
                host = _uiState.value.host,
                port = _uiState.value.port,
                username = "root"
            )
        }
    }

    fun disconnect() {
        bridge.cleanup()
        session = null
        sshManager.disconnect()
        stopForegroundService()
    }

    private fun startForegroundService() {
        val intent = Intent(context, SshForegroundService::class.java)
        ContextCompat.startForegroundService(context, intent)
    }

    private fun stopForegroundService() {
        val intent = Intent(context, SshForegroundService::class.java)
        context.stopService(intent)
    }

    fun onInputChange(input: String) {
        _uiState.value = _uiState.value.copy(currentInput = input)
    }

    fun sendCommand() {
        val command = _uiState.value.currentInput
        if (command.isNotEmpty()) {
            viewModelScope.launch {
                sshManager.sendCommand(command + "\n")
                _uiState.value = _uiState.value.copy(currentInput = "")
            }
        }
    }

    fun sendKey(key: Char) {
        viewModelScope.launch {
            sshManager.sendKey(key)
        }
    }

    fun sendEscapeSequence(bytes: ByteArray) {
        viewModelScope.launch {
            sshManager.sendRawBytes(bytes)
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    override fun onCleared() {
        super.onCleared()
        bridge.cleanup()
        sshManager.disconnect()
        stopForegroundService()
    }
}
