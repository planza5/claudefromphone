package com.example.runpodmanager.ui.screens.auto

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.runpodmanager.data.model.ComputeTypes
import com.example.runpodmanager.data.model.CreatePodRequest
import com.example.runpodmanager.data.model.NetworkVolume
import com.example.runpodmanager.data.model.Pod
import com.example.runpodmanager.data.repository.ApiResult
import com.example.runpodmanager.data.repository.PodRepository
import com.example.runpodmanager.data.ssh.SshKeyManager
import com.example.runpodmanager.data.ssh.SshManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

private const val SSH_POLL_DELAY_MS = 5000L
private const val SSH_POLL_MAX_ATTEMPTS = 60

enum class AutoPodStep {
    CheckingPods,
    CreatingPod,
    StartingPod,
    WaitingForSsh,
    Ready,
    NeedSshKeys,
    Error
}

data class AutoPodUiState(
    val step: AutoPodStep = AutoPodStep.CheckingPods,
    val statusMessage: String? = null,
    val statusDetail: String? = null,
    val errorMessage: String? = null,
    val host: String? = null,
    val port: Int? = null,
    val progress: Float? = null
)

@HiltViewModel
class AutoPodViewModel @Inject constructor(
    private val repository: PodRepository,
    private val sshKeyManager: SshKeyManager,
    private val sshManager: SshManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(AutoPodUiState())
    val uiState: StateFlow<AutoPodUiState> = _uiState.asStateFlow()

    init {
        start()
    }

    fun retry() {
        start()
    }

    private fun start() {
        viewModelScope.launch {
            ensureSshKeys()

            _uiState.update {
                it.copy(
                    step = AutoPodStep.CheckingPods,
                    statusMessage = "Buscando pods...",
                    statusDetail = null,
                    errorMessage = null,
                    progress = null
                )
            }

            when (val podsResult = repository.getPods()) {
                is ApiResult.Success -> {
                    val pod = pickPod(podsResult.data)
                    if (pod == null) {
                        createDefaultPod()
                    } else {
                        ensurePodReady(pod)
                    }
                }
                is ApiResult.Error -> {
                    _uiState.update {
                        it.copy(
                            step = AutoPodStep.Error,
                            errorMessage = podsResult.message,
                            statusMessage = null
                        )
                    }
                }
            }
        }
    }

    private fun ensureSshKeys() {
        if (sshKeyManager.hasKeys()) return
        _uiState.update {
            it.copy(
                step = AutoPodStep.CheckingPods,
                statusMessage = "Generando claves SSH...",
                statusDetail = null,
                errorMessage = null,
                progress = null
            )
        }
        sshKeyManager.generateKeys()
    }

    private fun pickPod(pods: List<Pod>): Pod? {
        if (pods.isEmpty()) return null
        return pods.firstOrNull { it.desiredStatus?.uppercase() == "RUNNING" } ?: pods.first()
    }

    private suspend fun createDefaultPod() {
        _uiState.update {
            it.copy(
                step = AutoPodStep.CreatingPod,
                statusMessage = "Creando pod por defecto...",
                statusDetail = null,
                errorMessage = null,
                progress = null
            )
        }

        val defaultVolume = loadDefaultNetworkVolume()
        var publicKey = sshKeyManager.getPublicKey()
        if (publicKey.isNullOrBlank()) {
            sshKeyManager.generateKeys()
            publicKey = sshKeyManager.getPublicKey()
        }
        if (publicKey.isNullOrBlank()) {
            _uiState.update {
                it.copy(
                    step = AutoPodStep.Error,
                    errorMessage = "No se pudieron generar claves SSH",
                    statusMessage = null,
                    statusDetail = null,
                    progress = null
                )
            }
            return
        }

        val request = buildDefaultCreatePodRequest(publicKey, defaultVolume)
        when (val result = repository.createPod(request)) {
            is ApiResult.Success -> ensurePodReady(result.data)
            is ApiResult.Error -> _uiState.update {
                it.copy(
                    step = AutoPodStep.Error,
                    errorMessage = result.message,
                    statusMessage = null
                )
            }
        }
    }

    private suspend fun loadDefaultNetworkVolume(): NetworkVolume? {
        return when (val result = repository.getNetworkVolumes()) {
            is ApiResult.Success -> {
                val volumes = result.data
                volumes.find { it.name.equals("Pablo2", ignoreCase = true) } ?: volumes.firstOrNull()
            }
            is ApiResult.Error -> null
        }
    }

    private suspend fun ensurePodReady(pod: Pod) {
        val shouldStart = pod.desiredStatus?.uppercase() != "RUNNING"
        if (shouldStart) {
            _uiState.update {
                it.copy(
                    step = AutoPodStep.StartingPod,
                    statusMessage = "Iniciando pod...",
                    statusDetail = null,
                    errorMessage = null,
                    progress = null
                )
            }
            when (val result = repository.startPod(pod.id)) {
                is ApiResult.Success -> waitForSsh(pod.id)
                is ApiResult.Error -> {
                    _uiState.update {
                        it.copy(
                            step = AutoPodStep.Error,
                            errorMessage = result.message,
                            statusMessage = null
                        )
                    }
                }
            }
        } else {
            waitForSsh(pod.id)
        }
    }

    private suspend fun waitForSsh(podId: String, allowRecovery: Boolean = true) {
        _uiState.update {
            it.copy(
                step = AutoPodStep.WaitingForSsh,
                statusMessage = "Esperando conexion en la nube",
                statusDetail = "(esto puede tardar)",
                errorMessage = null,
                progress = 0f
            )
        }

        repeat(SSH_POLL_MAX_ATTEMPTS) { attempt ->
            val pct = (attempt + 1).toFloat() / SSH_POLL_MAX_ATTEMPTS.toFloat()
            _uiState.update { it.copy(progress = pct) }
            when (val result = repository.getPod(podId)) {
                is ApiResult.Success -> {
                    val pod = result.data
                    val host = pod.publicIp
                    val port = getSshPort(pod)

                    if (host != null && port != null) {
                        val result = tryConnectSsh(host, port)
                        if (result.success) {
                            _uiState.update {
                                it.copy(
                                    step = AutoPodStep.Ready,
                                    statusMessage = "SSH listo",
                                    statusDetail = null,
                                    errorMessage = null,
                                    host = host,
                                    port = port,
                                    progress = 1f
                                )
                            }
                            return
                        } else if (result.authError && allowRecovery) {
                            val recovered = recoverFromInvalidKeys(podId)
                            if (recovered != null) {
                                waitForSsh(recovered.id, allowRecovery = false)
                                return
                            } else {
                                _uiState.update {
                                    it.copy(
                                        step = AutoPodStep.Error,
                                        errorMessage = "Error recuperando claves SSH",
                                        statusMessage = null,
                                        statusDetail = null,
                                        progress = null
                                    )
                                }
                                return
                            }
                        }
                    }
                }
                is ApiResult.Error -> {
                    if (attempt == SSH_POLL_MAX_ATTEMPTS - 1) {
                        _uiState.update {
                            it.copy(
                                step = AutoPodStep.Error,
                                errorMessage = result.message,
                                statusMessage = null
                            )
                        }
                        return
                    }
                }
            }
            delay(SSH_POLL_DELAY_MS)
        }

        _uiState.update {
            it.copy(
                step = AutoPodStep.Error,
                errorMessage = "Timeout esperando SSH",
                statusMessage = null,
                statusDetail = null,
                progress = null
            )
        }
    }

    private fun getSshPort(pod: Pod): Int? {
        pod.portMappings?.get("22")?.let { return it }
        val runtimePort = pod.runtime?.ports?.firstOrNull {
            it.privatePort == 22 && it.publicPort != null
        }?.publicPort
        if (runtimePort != null) return runtimePort
        return null
    }

    private data class SshConnectResult(
        val success: Boolean,
        val authError: Boolean
    )

    private suspend fun tryConnectSsh(host: String, port: Int): SshConnectResult = withContext(Dispatchers.IO) {
        try {
            // Validate SSH by attempting real auth
            val result = sshManager.connect(host = host, port = port, username = "root")
            if (result.isSuccess) {
                SshConnectResult(success = true, authError = false)
            } else {
                val msg = result.exceptionOrNull()?.message ?: ""
                SshConnectResult(success = false, authError = isAuthError(msg))
            }
        } catch (e: Exception) {
            val msg = e.message ?: ""
            SshConnectResult(success = false, authError = isAuthError(msg))
        }
    }

    private fun isAuthError(message: String): Boolean {
        val msg = message.lowercase()
        return msg.contains("autentic") ||
            msg.contains("auth") ||
            msg.contains("clave privada") ||
            msg.contains("publickey")
    }

    private suspend fun recoverFromInvalidKeys(podId: String): Pod? {
        _uiState.update {
            it.copy(
                step = AutoPodStep.WaitingForSsh,
                statusMessage = "Claves SSH inválidas",
                statusDetail = "Regenerando y recreando pod...",
                errorMessage = null,
                progress = 0f
            )
        }

        sshManager.disconnect()
        repository.deletePod(podId)
        sshKeyManager.deleteKeys()
        sshKeyManager.generateKeys()

        val request = buildDefaultCreatePodRequest(
            sshKeyManager.getPublicKey() ?: return null,
            loadDefaultNetworkVolume()
        )

        return when (val result = repository.createPod(request)) {
            is ApiResult.Success -> result.data
            is ApiResult.Error -> null
        }
    }

    private fun buildDefaultCreatePodRequest(
        sshPublicKey: String,
        networkVolume: NetworkVolume?
    ): CreatePodRequest {
        val sshSetup =
            "mkdir -p ~/.ssh && echo '${sshPublicKey.trim()}' >> ~/.ssh/authorized_keys && chmod 600 ~/.ssh/authorized_keys && "
        val script = "cd /workspace && ${sshSetup}source /workspace/setup_env.sh && /workspace/start_tailscale.sh && exec /start.sh"

        val defaultCpu = ComputeTypes.cpuTypes.first()

        return CreatePodRequest(
            name = "pexito",
            gpuTypeIds = emptyList(),
            computeType = "CPU",
            cpuFlavorIds = listOf(defaultCpu.id),
            containerDiskInGb = 5,
            volumeInGb = 0,
            networkVolumeId = networkVolume?.id,
            dockerStartCmd = listOf("bash", "-c", script)
        )
    }
}
