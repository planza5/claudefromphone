package com.example.runpodmanager.ui.screens.terminal

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
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
import android.util.Log
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

private const val TAG = "TerminalViewModel"

data class TerminalUiState(
    val host: String = "",
    val port: Int = 22,
    val isConnected: Boolean = false,
    val isConnecting: Boolean = false,
    val currentInput: String = "",
    val errorMessage: String? = null,
    val terminalReady: Boolean = false,
    val projects: List<String> = emptyList(),
    val isLoadingProjects: Boolean = false,
    val selectedProject: String? = null,
    val isBuilding: Boolean = false,
    // APK download states
    val apkRemotePath: String? = null,
    val showApkDialog: Boolean = false,
    val isDownloadingApk: Boolean = false,
    val downloadProgress: Float = 0f,
    val downloadedApkFile: File? = null
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

    val controller = TerminalController(context)
    var session: TerminalSession? = null
        private set

    private val host: String = savedStateHandle.get<String>("host") ?: ""
    private val port: Int = savedStateHandle.get<Int>("port") ?: 22

    init {
        _uiState.update { it.copy(host = host, port = port) }
        observeConnectionState()

        if (host.isNotEmpty() && port > 0) {
            connect()
        }
    }

    private fun observeConnectionState() {
        viewModelScope.launch {
            sshManager.connectionState.collect { state ->
                handleConnectionState(state)
            }
        }
    }

    private fun handleConnectionState(state: SshConnectionState) {
        when (state) {
            is SshConnectionState.Disconnected -> {
                _uiState.update {
                    it.copy(isConnected = false, isConnecting = false, terminalReady = false)
                }
                bridge.stopOutputCollection()
                stopForegroundService()
            }
            is SshConnectionState.Connecting -> {
                _uiState.update { it.copy(isConnecting = true, isConnected = false) }
            }
            is SshConnectionState.Connected -> {
                _uiState.update {
                    it.copy(isConnected = true, isConnecting = false, errorMessage = null)
                }
                startForegroundService()
                initializeTerminal()
            }
            is SshConnectionState.Error -> {
                _uiState.update {
                    it.copy(
                        isConnected = false,
                        isConnecting = false,
                        errorMessage = state.message,
                        terminalReady = false
                    )
                }
                bridge.stopOutputCollection()
                stopForegroundService()
            }
        }
    }

    private fun initializeTerminal() {
        val newSession = bridge.createSession(controller, 2000)
        session = newSession
        controller.setSession(newSession)
        bridge.startOutputCollection()
        _uiState.update { it.copy(terminalReady = true) }
        loadProjects()
    }

    private fun loadProjects() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingProjects = true) }
            val command = """find /workspace/projects -type f \( -name "settings.gradle" -o -name "settings.gradle.kts" \) 2>/dev/null | while read f; do dirname "${'$'}f"; done | sort -u"""
            sshManager.executeCommand(command).onSuccess { output ->
                val projects = output.lines().filter { it.isNotBlank() }
                _uiState.update { it.copy(projects = projects, isLoadingProjects = false) }
            }.onFailure {
                _uiState.update { it.copy(isLoadingProjects = false) }
            }
        }
    }

    fun connect() {
        viewModelScope.launch {
            sshManager.connect(host = _uiState.value.host, port = _uiState.value.port, username = "root")
        }
    }

    fun disconnect() {
        bridge.cleanup()
        session = null
        sshManager.disconnect()
        stopForegroundService()
    }

    private fun startForegroundService() {
        ContextCompat.startForegroundService(context, Intent(context, SshForegroundService::class.java))
    }

    private fun stopForegroundService() {
        context.stopService(Intent(context, SshForegroundService::class.java))
    }

    fun onInputChange(input: String) {
        _uiState.update { it.copy(currentInput = input) }
    }

    fun sendCommand() {
        val command = _uiState.value.currentInput
        if (command.isNotEmpty()) {
            viewModelScope.launch {
                sshManager.sendCommand(command + "\n")
                _uiState.update { it.copy(currentInput = "") }
            }
        }
    }

    fun sendKey(key: Char) {
        viewModelScope.launch { sshManager.sendKey(key) }
    }

    fun sendEscapeSequence(bytes: ByteArray) {
        viewModelScope.launch { sshManager.sendRawBytes(bytes) }
    }

    fun selectProject(project: String) {
        viewModelScope.launch {
            sshManager.sendCommand("cd $project\n")
            _uiState.update { it.copy(selectedProject = project) }
        }
    }

    fun goBackFromProject() {
        _uiState.update { it.copy(selectedProject = null) }
    }

    fun buildAndInstall() {
        val project = _uiState.value.selectedProject ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isBuilding = true) }
            sshManager.sendCommand("/workspace/compilar.sh $project\n")
            // Monitorear la salida para detectar BUILD SUCCESSFUL
            monitorBuildOutput(project)
        }
    }

    private fun monitorBuildOutput(project: String) {
        viewModelScope.launch {
            val outputBuffer = StringBuilder()
            sshManager.rawOutput.collect { bytes ->
                val text = String(bytes, Charsets.UTF_8)
                outputBuffer.append(text)

                // Detectar BUILD SUCCESSFUL
                if (outputBuffer.contains("BUILD SUCCESSFUL") && _uiState.value.isBuilding) {
                    Log.d(TAG, "Build exitoso detectado, buscando APK...")
                    _uiState.update { it.copy(isBuilding = false) }
                    findGeneratedApk(project)
                    return@collect
                }

                // Detectar BUILD FAILED
                if (outputBuffer.contains("BUILD FAILED") && _uiState.value.isBuilding) {
                    Log.d(TAG, "Build fallido detectado")
                    _uiState.update { it.copy(isBuilding = false) }
                    return@collect
                }

                // Limpiar buffer si se hace muy grande
                if (outputBuffer.length > 50000) {
                    outputBuffer.delete(0, outputBuffer.length - 10000)
                }
            }
        }
    }

    private fun findGeneratedApk(project: String) {
        viewModelScope.launch {
            val command = """find $project -name "*.apk" -path "*/build/outputs/*" -type f 2>/dev/null | head -1"""
            sshManager.executeCommand(command).onSuccess { apkPath ->
                if (apkPath.isNotBlank()) {
                    Log.d(TAG, "APK encontrado: $apkPath")
                    _uiState.update {
                        it.copy(apkRemotePath = apkPath, showApkDialog = true)
                    }
                } else {
                    Log.d(TAG, "No se encontró APK")
                }
            }.onFailure { e ->
                Log.e(TAG, "Error buscando APK: ${e.message}")
            }
        }
    }

    fun downloadApk() {
        val remotePath = _uiState.value.apkRemotePath ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isDownloadingApk = true, downloadProgress = 0f) }

            val apkDir = File(context.cacheDir, "apks")
            apkDir.mkdirs()
            val localFile = File(apkDir, "downloaded_${System.currentTimeMillis()}.apk")

            sshManager.downloadFile(
                remotePath = remotePath,
                localFile = localFile,
                onProgress = { progress ->
                    _uiState.update { it.copy(downloadProgress = progress) }
                }
            ).onSuccess {
                Log.d(TAG, "APK descargado: ${localFile.absolutePath}")
                _uiState.update {
                    it.copy(
                        isDownloadingApk = false,
                        downloadedApkFile = localFile,
                        showApkDialog = true
                    )
                }
            }.onFailure { e ->
                Log.e(TAG, "Error descargando APK: ${e.message}")
                _uiState.update {
                    it.copy(
                        isDownloadingApk = false,
                        errorMessage = "Error descargando APK: ${e.message}"
                    )
                }
            }
        }
    }

    fun installApk() {
        val apkFile = _uiState.value.downloadedApkFile ?: return
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            context.startActivity(intent)
            dismissApkDialog()
        } catch (e: Exception) {
            Log.e(TAG, "Error instalando APK: ${e.message}")
            _uiState.update { it.copy(errorMessage = "Error instalando APK: ${e.message}") }
        }
    }

    fun uninstallAndInstallApk() {
        val apkFile = _uiState.value.downloadedApkFile ?: return
        try {
            // Obtener package name del APK
            val packageInfo = context.packageManager.getPackageArchiveInfo(apkFile.absolutePath, 0)
            val packageName = packageInfo?.packageName

            if (packageName != null) {
                // Lanzar desinstalación
                val uninstallIntent = Intent(Intent.ACTION_DELETE).apply {
                    data = Uri.parse("package:$packageName")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(uninstallIntent)

                // Mantener el diálogo abierto para que pueda instalar después
                _uiState.update { it.copy(showApkDialog = true) }
            } else {
                _uiState.update { it.copy(errorMessage = "No se pudo obtener el package name del APK") }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error desinstalando: ${e.message}")
            _uiState.update { it.copy(errorMessage = "Error: ${e.message}") }
        }
    }

    fun dismissApkDialog() {
        _uiState.update {
            it.copy(showApkDialog = false, apkRemotePath = null, downloadedApkFile = null)
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    override fun onCleared() {
        super.onCleared()
        bridge.cleanup()
        sshManager.disconnect()
        stopForegroundService()
    }
}
