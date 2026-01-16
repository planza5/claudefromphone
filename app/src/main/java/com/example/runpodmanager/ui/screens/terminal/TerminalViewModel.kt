package com.example.runpodmanager.ui.screens.terminal

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.runpodmanager.data.ssh.SshConnectionState
import com.example.runpodmanager.data.ssh.SshManager
import dagger.hilt.android.lifecycle.HiltViewModel
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
    val terminalOutput: String = "",
    val currentInput: String = "",
    val errorMessage: String? = null
)

@HiltViewModel
class TerminalViewModel @Inject constructor(
    private val sshManager: SshManager,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiState = MutableStateFlow(TerminalUiState())
    val uiState: StateFlow<TerminalUiState> = _uiState.asStateFlow()

    private val host: String = savedStateHandle.get<String>("host") ?: ""
    private val port: Int = savedStateHandle.get<Int>("port") ?: 22

    init {
        _uiState.value = _uiState.value.copy(host = host, port = port)

        // Observe connection state
        viewModelScope.launch {
            sshManager.connectionState.collect { state ->
                _uiState.value = when (state) {
                    is SshConnectionState.Disconnected -> _uiState.value.copy(
                        isConnected = false,
                        isConnecting = false
                    )
                    is SshConnectionState.Connecting -> _uiState.value.copy(
                        isConnecting = true,
                        isConnected = false
                    )
                    is SshConnectionState.Connected -> _uiState.value.copy(
                        isConnected = true,
                        isConnecting = false,
                        errorMessage = null
                    )
                    is SshConnectionState.Error -> _uiState.value.copy(
                        isConnected = false,
                        isConnecting = false,
                        errorMessage = state.message
                    )
                }
            }
        }

        // Observe terminal output
        viewModelScope.launch {
            sshManager.terminalOutput.collect { output ->
                _uiState.value = _uiState.value.copy(terminalOutput = output)
            }
        }

        // Auto-connect if we have host and port
        if (host.isNotEmpty() && port > 0) {
            connect()
        }
    }

    fun connect() {
        viewModelScope.launch {
            sshManager.clearOutput()
            sshManager.connect(
                host = _uiState.value.host,
                port = _uiState.value.port,
                username = "root"
            )
        }
    }

    fun disconnect() {
        sshManager.disconnect()
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

    fun sendSpecialKey(key: SpecialKey) {
        viewModelScope.launch {
            when (key) {
                SpecialKey.ENTER -> sshManager.sendCommand("\n")
                SpecialKey.TAB -> sshManager.sendCommand("\t")
                SpecialKey.CTRL_C -> sshManager.sendKey('\u0003')
                SpecialKey.CTRL_D -> sshManager.sendKey('\u0004')
                SpecialKey.CTRL_Z -> sshManager.sendKey('\u001a')
                SpecialKey.ESCAPE -> sshManager.sendKey('\u001b')
                SpecialKey.ARROW_UP -> sshManager.sendCommand("\u001b[A")
                SpecialKey.ARROW_DOWN -> sshManager.sendCommand("\u001b[B")
                SpecialKey.ARROW_LEFT -> sshManager.sendCommand("\u001b[D")
                SpecialKey.ARROW_RIGHT -> sshManager.sendCommand("\u001b[C")
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    override fun onCleared() {
        super.onCleared()
        sshManager.disconnect()
    }
}

enum class SpecialKey {
    ENTER, TAB, CTRL_C, CTRL_D, CTRL_Z, ESCAPE,
    ARROW_UP, ARROW_DOWN, ARROW_LEFT, ARROW_RIGHT
}
