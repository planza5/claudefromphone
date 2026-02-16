package com.example.runpodmanager.ui.screens.terminal

import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "TerminalViewModel"
private const val REMOTE_BUILD_LOG_PATH = "/tmp/runpod_build.log"

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
    val appPackageName: String? = null,
    val isAppInstalled: Boolean? = null, // null = checking, true/false = result
    val isApkAvailable: Boolean? = null, // null = checking, true/false = result
    val isBuilding: Boolean = false,
    val isDownloadingApk: Boolean = false,
    val downloadProgress: Float = 0f,
    val buildSuccess: Boolean? = null, // null = no result, true = success, false = error
    val buildFailureLog: String? = null,
    // Diálogos de gestión de proyectos
    val showCreateProjectDialog: Boolean = false,
    val showRenameProjectDialog: Boolean = false,
    val showDeleteProjectDialog: Boolean = false,
    val showInstallConflictDialog: Boolean = false,
    val projectToManage: String? = null,  // Proyecto a renombrar/eliminar
    val newProjectName: String = "",       // Input del usuario
    val isCreatingProject: Boolean = false,
    val isDeletingProject: Boolean = false,
    val isRenamingProject: Boolean = false
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
    private val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    private fun isPackageInstalled(packageName: String): Boolean = try {
        context.packageManager.getPackageInfo(packageName, 0)
        true
    } catch (e: Exception) {
        false
    }

    val controller = TerminalController(context)
    var session: TerminalSession? = null
        private set

    private val host: String = savedStateHandle.get<String>("host") ?: ""
    private val port: Int = savedStateHandle.get<Int>("port") ?: 22

    // BroadcastReceiver para detectar instalación/desinstalación de apps
    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            val packageName = intent?.data?.schemeSpecificPart
            val currentPackage = _uiState.value.appPackageName

            when (intent?.action) {
                Intent.ACTION_PACKAGE_ADDED, Intent.ACTION_PACKAGE_REPLACED -> {
                    Log.d(TAG, "Package installed/updated: $packageName, esperando: $currentPackage")
                    if (packageName != null && packageName == currentPackage) {
                        Log.d(TAG, "App '$packageName' instalada correctamente")
                        _uiState.update { it.copy(isAppInstalled = true) }
                    }
                }
                Intent.ACTION_PACKAGE_REMOVED -> {
                    Log.d(TAG, "Package removed: $packageName, esperando: $currentPackage")
                    if (packageName != null && packageName == currentPackage) {
                        val isStillInstalled = isPackageInstalled(packageName)
                        Log.d(TAG, "App '$packageName' todavia instalada: $isStillInstalled")
                        _uiState.update { it.copy(isAppInstalled = isStillInstalled) }
                    }
                }
            }
        }
    }

    init {
        _uiState.update { it.copy(host = host, port = port) }
        observeConnectionState()
        registerPackageReceiver()

        if (host.isNotEmpty() && port > 0) {
            connect()
        }
    }

    private fun registerPackageReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addDataScheme("package")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(packageReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(packageReceiver, filter)
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
                val projects = output.lines()
                    .filter { it.isNotBlank() }
                    .filter { !it.endsWith("/nodelete") }  // Filtrar template
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

    fun sendVoiceCommand(text: String) {
        // Convertir a minúsculas y enviar sin Enter automático
        val command = text.lowercase().replace("  ", "\t")
        viewModelScope.launch {
            sshManager.sendCommand(command)
        }
    }

    fun selectProject(project: String) {
        viewModelScope.launch {
            sshManager.sendCommand("cd $project && codex\n")
            _uiState.update { it.copy(selectedProject = project).resetProjectState() }
            checkAppInstallStatus(project)
            checkApkAvailable(project)
        }
    }

    private fun TerminalUiState.resetProjectState(): TerminalUiState = copy(
        appPackageName = null,
        isAppInstalled = null,
        isApkAvailable = null
    )

    private fun checkAppInstallStatus(project: String) {
        viewModelScope.launch {
            // Buscar applicationId o namespace en build.gradle/build.gradle.kts
            val command = """
                # Primero buscar applicationId
                APP_ID=$(grep -rh "applicationId" $project/app/build.gradle* 2>/dev/null | grep -v '//' | head -1 | sed 's/.*["'"'"']\([^"'"'"']*\)["'"'"'].*/\1/')
                if [ -n "${'$'}APP_ID" ]; then
                    echo "${'$'}APP_ID"
                    exit 0
                fi
                # Si no hay applicationId, buscar namespace
                NS=$(grep -rh "namespace" $project/app/build.gradle* 2>/dev/null | grep -v '//' | head -1 | sed 's/.*["'"'"']\([^"'"'"']*\)["'"'"'].*/\1/')
                if [ -n "${'$'}NS" ]; then
                    echo "${'$'}NS"
                    exit 0
                fi
                # Fallback: buscar en AndroidManifest.xml
                grep -h "package=" $project/app/src/main/AndroidManifest.xml 2>/dev/null | sed 's/.*package=["'"'"']\([^"'"'"']*\)["'"'"'].*/\1/'
            """.trimIndent()

            sshManager.executeCommand(command).onSuccess { result ->
                // Limpiar el package: quitar espacios, newlines, y caracteres no válidos
                val cleanPackage = result.trim()
                    .lines()
                    .lastOrNull { it.contains(".") }
                    ?.trim()
                    ?.replace(Regex("[^a-zA-Z0-9._]"), "") // Solo caracteres válidos para package
                    ?: ""

                Log.d(TAG, "Package raw: '${result.take(100)}', limpio: '$cleanPackage', bytes: ${cleanPackage.toByteArray().contentToString()}")

                if (cleanPackage.isNotBlank() && cleanPackage.contains(".")) {
                    val isInstalled = isPackageInstalled(cleanPackage)
                    Log.d(TAG, "App '$cleanPackage' instalada: $isInstalled")
                    _uiState.update { it.copy(appPackageName = cleanPackage, isAppInstalled = isInstalled) }
                } else {
                    Log.w(TAG, "No se encontró package válido para $project")
                    _uiState.update { it.copy(isAppInstalled = null) }
                }
            }.onFailure { e ->
                Log.e(TAG, "Error buscando package: ${e.message}")
                _uiState.update { it.copy(isAppInstalled = null) }
            }
        }
    }

    fun refreshInstallStatus() {
        val project = _uiState.value.selectedProject ?: return
        _uiState.update { it.copy(isAppInstalled = null) }
        checkAppInstallStatus(project)
    }

    private fun checkApkAvailable(project: String) {
        viewModelScope.launch {
            val apkPath = "$project/app/build/outputs/apk/debug/app-debug.apk"
            val command = "test -f $apkPath && echo 'EXISTS' || echo 'NOT_FOUND'"

            sshManager.executeCommand(command).onSuccess { result ->
                val exists = result.trim() == "EXISTS"
                Log.d(TAG, "APK $apkPath existe: $exists")
                _uiState.update { it.copy(isApkAvailable = exists) }
            }.onFailure { e ->
                Log.e(TAG, "Error verificando APK: ${e.message}")
                _uiState.update { it.copy(isApkAvailable = false) }
            }
        }
    }

    fun goBackFromProject() {
        _uiState.update { it.copy(selectedProject = null).resetProjectState() }
    }

    fun deleteApk() {
        val project = _uiState.value.selectedProject ?: return
        val apkPath = "$project/app/build/outputs/apk/debug/app-debug.apk"

        viewModelScope.launch {
            Log.d(TAG, "Borrando APK: $apkPath")
            sshManager.executeCommand("rm -f $apkPath").onSuccess {
                Log.d(TAG, "APK borrado correctamente")
                _uiState.update { it.copy(isApkAvailable = false) }
            }.onFailure { e ->
                Log.e(TAG, "Error borrando APK: ${e.message}", e)
                _uiState.update { it.copy(errorMessage = "Error borrando APK: ${e.message}") }
            }
        }
    }

    fun buildProject() {
        val project = _uiState.value.selectedProject ?: return
        Log.d(TAG, "buildProject() llamado para: $project")

        viewModelScope.launch {
            Log.d(TAG, "Poniendo isBuilding = true")
            _uiState.update {
                it.copy(
                    isBuilding = true,
                    isApkAvailable = null,
                    buildSuccess = null,
                    buildFailureLog = null
                )
            }
            Log.d(TAG, "Estado actual isBuilding = ${_uiState.value.isBuilding}")

            val command = "printf \"sdk.dir=/workspace/persist/android-sdk\\n\" > $project/local.properties; /workspace/compilar.sh $project > $REMOTE_BUILD_LOG_PATH 2>&1; echo \"EXIT_CODE:\$?\""
            Log.d(TAG, "Ejecutando build: $command")

            sshManager.executeCommand(command).onSuccess { output ->
                // Extraer código de salida
                val exitCode = output.lines()
                    .lastOrNull { it.startsWith("EXIT_CODE:") }
                    ?.substringAfter("EXIT_CODE:")
                    ?.trim()
                    ?.toIntOrNull() ?: -1

                Log.d(TAG, "Build exit code: $exitCode")

                if (exitCode == 0) {
                    // Build exitoso
                    _uiState.update { it.copy(isBuilding = false, buildSuccess = true) }
                    checkApkAvailable(project)
                } else {
                    viewModelScope.launch {
                        val failureLog = fetchBuildFailureLog()
                        _uiState.update { it.copy(buildFailureLog = failureLog) }
                    }
                    // Build fallido
                    _uiState.update {
                        it.copy(
                            isBuilding = false,
                            buildSuccess = false,
                            errorMessage = "Build failed (exit code: $exitCode). Log remoto: $REMOTE_BUILD_LOG_PATH"
                        )
                    }
                }
            }.onFailure { e ->
                Log.e(TAG, "Error ejecutando build: ${e.message}", e)
                _uiState.update {
                    it.copy(
                        isBuilding = false,
                        buildSuccess = false,
                        buildFailureLog = null,
                        errorMessage = "Build error: ${e.message}"
                    )
                }
            }
        }
    }

    private suspend fun fetchBuildFailureLog(): String? {
        val command = "if [ -f $REMOTE_BUILD_LOG_PATH ]; then tail -n 400 $REMOTE_BUILD_LOG_PATH; fi"
        return sshManager.executeCommand(command).getOrNull()?.takeIf { it.isNotBlank() }
    }

    fun clearBuildResult() {
        _uiState.update { it.copy(buildSuccess = null, buildFailureLog = null) }
    }

    fun copyBuildLogToClipboard() {
        val log = _uiState.value.buildFailureLog
        if (log.isNullOrBlank()) {
            _uiState.update { it.copy(errorMessage = "No build log available to copy") }
            return
        }
        clipboard.setPrimaryClip(ClipData.newPlainText("build-log", log))
        _uiState.update { it.copy(errorMessage = "Build log copied to clipboard") }
    }

    fun installApk() {
        val project = _uiState.value.selectedProject ?: return
        if (_uiState.value.isAppInstalled == true) {
            _uiState.update { it.copy(showInstallConflictDialog = true) }
            return
        }
        val apkPath = "$project/app/build/outputs/apk/debug/app-debug.apk"

        viewModelScope.launch {
            _uiState.update { it.copy(isDownloadingApk = true, downloadProgress = 0f) }

            try {
                // Crear directorio local para APKs
                val apksDir = File(context.cacheDir, "apks")
                apksDir.mkdirs()
                val localApk = File(apksDir, "app-debug.apk")

                Log.d(TAG, "Descargando APK: $apkPath -> ${localApk.absolutePath}")

                // Descargar el APK
                val result = sshManager.downloadFile(
                    remotePath = apkPath,
                    localFile = localApk,
                    onProgress = { progress ->
                        _uiState.update { it.copy(downloadProgress = progress) }
                    }
                )

                result.onSuccess {
                    Log.d(TAG, "APK descargado correctamente")
                    _uiState.update { it.copy(isDownloadingApk = false) }

                    // Instalar el APK
                    launchApkInstall(localApk)
                }.onFailure { e ->
                    Log.e(TAG, "Error descargando APK: ${e.message}", e)
                    _uiState.update {
                        it.copy(
                            isDownloadingApk = false,
                            errorMessage = "Error descargando APK: ${e.message}"
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error en installApk: ${e.message}", e)
                _uiState.update {
                    it.copy(
                        isDownloadingApk = false,
                        errorMessage = "Error: ${e.message}"
                    )
                }
            }
        }
    }

    fun hideInstallConflictDialog() {
        _uiState.update { it.copy(showInstallConflictDialog = false) }
    }

    fun confirmUninstallForInstallConflict() {
        hideInstallConflictDialog()
        uninstallApp()
    }

    private fun launchApkInstall(apkFile: File) {
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(intent)
            Log.d(TAG, "Intent de instalación lanzado")
        } catch (e: Exception) {
            Log.e(TAG, "Error lanzando instalación: ${e.message}", e)
            _uiState.update { it.copy(errorMessage = "Error abriendo instalador: ${e.message}") }
        }
    }

    fun uninstallApp() {
        Log.e(TAG, "uninstallApp() llamado")
        val packageName = _uiState.value.appPackageName
        Log.e(TAG, "appPackageName = $packageName")
        if (packageName.isNullOrBlank()) {
            Log.e(TAG, "No hay package para desinstalar")
            return
        }

        Log.e(TAG, "Abriendo Settings para desinstalar: $packageName")
        try {
            val uri = Uri.parse("package:$packageName")
            // Abrir directamente la página de la app en Settings
            val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Log.e(TAG, "Settings abierto")
        } catch (e: Exception) {
            Log.e(TAG, "Error al abrir Settings", e)
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    // === Gestión de proyectos ===

    fun showCreateProjectDialog() {
        _uiState.update { it.copy(showCreateProjectDialog = true, newProjectName = "") }
    }

    fun hideCreateProjectDialog() {
        _uiState.update { it.copy(showCreateProjectDialog = false, newProjectName = "", isCreatingProject = false) }
    }

    fun showDeleteProjectDialog(project: String) {
        _uiState.update { it.copy(showDeleteProjectDialog = true, projectToManage = project) }
    }

    fun hideDeleteProjectDialog() {
        _uiState.update { it.copy(showDeleteProjectDialog = false, projectToManage = null, isDeletingProject = false) }
    }

    fun showRenameProjectDialog(project: String) {
        val currentName = project.substringAfterLast("/")
        _uiState.update { it.copy(showRenameProjectDialog = true, projectToManage = project, newProjectName = currentName) }
    }

    fun hideRenameProjectDialog() {
        _uiState.update { it.copy(showRenameProjectDialog = false, projectToManage = null, newProjectName = "", isRenamingProject = false) }
    }

    fun updateNewProjectName(name: String) {
        _uiState.update { it.copy(newProjectName = name) }
    }

    fun createProject() {
        val name = _uiState.value.newProjectName.trim()
        if (name.isBlank()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isCreatingProject = true) }
            val command = "cp -r /workspace/projects/nodelete /workspace/projects/$name && printf \"sdk.dir=/workspace/persist/android-sdk\\n\" > /workspace/projects/$name/local.properties"
            sshManager.executeCommand(command).onSuccess {
                loadProjects()
                _uiState.update { it.copy(
                    showCreateProjectDialog = false,
                    newProjectName = "",
                    isCreatingProject = false
                )}
            }.onFailure { e ->
                Log.e(TAG, "Error creating project: ${e.message}")
                _uiState.update { it.copy(isCreatingProject = false, errorMessage = "Error creating project: ${e.message}") }
            }
        }
    }

    fun deleteProject() {
        val project = _uiState.value.projectToManage ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isDeletingProject = true) }
            val command = "rm -rf $project"
            sshManager.executeCommand(command).onSuccess {
                // Si era el proyecto seleccionado, deseleccionar
                if (_uiState.value.selectedProject == project) {
                    _uiState.update { it.copy(selectedProject = null).resetProjectState() }
                }
                loadProjects()
                hideDeleteProjectDialog()
            }.onFailure { e ->
                Log.e(TAG, "Error deleting project: ${e.message}")
                _uiState.update { it.copy(isDeletingProject = false, errorMessage = "Error deleting project: ${e.message}") }
            }
        }
    }

    fun renameProject() {
        val oldProject = _uiState.value.projectToManage ?: return
        val newName = _uiState.value.newProjectName.trim()
        if (newName.isBlank()) return

        val newProject = "/workspace/projects/$newName"

        viewModelScope.launch {
            _uiState.update { it.copy(isRenamingProject = true) }
            val command = "mv $oldProject $newProject"
            sshManager.executeCommand(command).onSuccess {
                // Si era el proyecto seleccionado, actualizar referencia
                if (_uiState.value.selectedProject == oldProject) {
                    _uiState.update { it.copy(selectedProject = newProject) }
                }
                loadProjects()
                hideRenameProjectDialog()
            }.onFailure { e ->
                Log.e(TAG, "Error renaming project: ${e.message}")
                _uiState.update { it.copy(isRenamingProject = false, errorMessage = "Error renaming project: ${e.message}") }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        try {
            context.unregisterReceiver(packageReceiver)
        } catch (e: Exception) {
            Log.w(TAG, "Error unregistering receiver: ${e.message}")
        }
        bridge.cleanup()
        sshManager.disconnect()
        stopForegroundService()
    }
}
