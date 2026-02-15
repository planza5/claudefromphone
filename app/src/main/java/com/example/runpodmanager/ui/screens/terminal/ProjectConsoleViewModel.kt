package com.example.runpodmanager.ui.screens.terminal

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.runpodmanager.data.ssh.SshConnectionState
import com.example.runpodmanager.data.ssh.SshManager
import dagger.hilt.android.lifecycle.HiltViewModel
import java.net.URLDecoder
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProjectConsoleUiState(
    val projectPath: String = "",
    val isConnected: Boolean = false,
    val isConnecting: Boolean = false,
    val currentInput: String = "",
    val terminalOutput: String = "",
    val errorMessage: String? = null
)

@HiltViewModel
class ProjectConsoleViewModel @Inject constructor(
    private val sshManager: SshManager,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProjectConsoleUiState())
    val uiState: StateFlow<ProjectConsoleUiState> = _uiState.asStateFlow()

    private val projectPath: String = savedStateHandle.get<String>("path")
        ?.let { URLDecoder.decode(it, Charsets.UTF_8.name()) }
        ?: ""

    init {
        _uiState.update { it.copy(projectPath = projectPath) }
        observeConnectionState()
        observeSshOutput()
        if (sshManager.isConnected()) {
            enterProjectDir()
        }
    }

    private fun observeConnectionState() {
        viewModelScope.launch {
            sshManager.connectionState.collect { state ->
                when (state) {
                    is SshConnectionState.Disconnected -> _uiState.update {
                        it.copy(isConnected = false, isConnecting = false)
                    }
                    is SshConnectionState.Connecting -> _uiState.update {
                        it.copy(isConnected = false, isConnecting = true)
                    }
                    is SshConnectionState.Connected -> {
                        _uiState.update { it.copy(isConnected = true, isConnecting = false) }
                        enterProjectDir()
                    }
                    is SshConnectionState.Error -> _uiState.update {
                        it.copy(isConnected = false, isConnecting = false, errorMessage = state.message)
                    }
                }
            }
        }
    }

    private fun observeSshOutput() {
        viewModelScope.launch {
            sshManager.rawOutput.collect { bytes ->
                val raw = String(bytes, Charsets.UTF_8)
                val clean = sanitizeOutput(raw)
                appendOutput(clean)
            }
        }
    }

    private fun enterProjectDir() {
        if (projectPath.isBlank()) return
        viewModelScope.launch {
            sshManager.sendCommand("cd \"$projectPath\" && pwd\n")
        }
    }

    fun onInputChange(input: String) {
        _uiState.update { it.copy(currentInput = input) }
    }

    fun sendCommand() {
        val command = _uiState.value.currentInput
        if (command.isBlank()) return
        viewModelScope.launch {
            appendOutput("> " + command + "\n")
            sshManager.sendCommand(command + "\n")
            _uiState.update { it.copy(currentInput = "") }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    private fun sanitizeOutput(raw: String): String {
        val ansiRegex = Regex("\\u001B\\[[;\\d]*[ -/]*[@-~]")
        return raw.replace(ansiRegex, "")
    }

    private fun appendOutput(text: String) {
        if (text.isEmpty()) return
        _uiState.update { state ->
            val builder = StringBuilder(state.terminalOutput)
            for (ch in text) {
                when (ch) {
                    '\r' -> {
                        val lastNewline = builder.lastIndexOf("\n")
                        if (lastNewline >= 0) builder.setLength(lastNewline + 1) else builder.setLength(0)
                    }
                    '\b' -> if (builder.isNotEmpty()) builder.deleteCharAt(builder.length - 1)
                    '\t' -> builder.append("    ")
                    else -> builder.append(ch)
                }
            }
            val maxChars = 20000
            if (builder.length > maxChars) {
                state.copy(terminalOutput = builder.substring(builder.length - maxChars))
            } else {
                state.copy(terminalOutput = builder.toString())
            }
        }
    }
}
