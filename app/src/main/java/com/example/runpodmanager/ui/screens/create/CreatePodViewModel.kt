package com.example.runpodmanager.ui.screens.create

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.runpodmanager.data.model.ComputeTypes
import com.example.runpodmanager.data.model.CpuOption
import com.example.runpodmanager.data.model.CreatePodRequest
import com.example.runpodmanager.data.model.GpuOption
import com.example.runpodmanager.data.model.NetworkVolume
import com.example.runpodmanager.data.repository.ApiResult
import com.example.runpodmanager.data.repository.PodRepository
import com.example.runpodmanager.data.ssh.SshKeyManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class ComputeType {
    GPU, CPU
}

data class CreatePodUiState(
    val name: String = "pexito",
    val computeType: ComputeType = ComputeType.CPU,
    val selectedGpu: GpuOption = ComputeTypes.gpuTypes.first(),
    val selectedCpu: CpuOption = ComputeTypes.cpuTypes.first(),
    val containerDiskGb: Int = 5,
    val networkVolumes: List<NetworkVolume> = emptyList(),
    val selectedNetworkVolume: NetworkVolume? = null,
    val startScript: String = "",
    val isLoadingVolumes: Boolean = false,
    val isLoading: Boolean = false,
    val isCreated: Boolean = false,
    val errorMessage: String? = null,
    val hasSshKeys: Boolean = false,
    val sshPublicKey: String? = null
)

@HiltViewModel
class CreatePodViewModel @Inject constructor(
    private val repository: PodRepository,
    private val sshKeyManager: SshKeyManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(CreatePodUiState())
    val uiState: StateFlow<CreatePodUiState> = _uiState.asStateFlow()

    val availableGpus = ComputeTypes.gpuTypes
    val availableCpus = ComputeTypes.cpuTypes

    init {
        loadNetworkVolumes()
        loadSshKeys()
    }

    private fun loadSshKeys() {
        val hasKeys = sshKeyManager.hasKeys()
        val publicKey = sshKeyManager.getPublicKey()

        _uiState.value = _uiState.value.copy(
            hasSshKeys = hasKeys,
            sshPublicKey = publicKey
        )
    }

    private fun loadNetworkVolumes() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingVolumes = true)
            when (val result = repository.getNetworkVolumes()) {
                is ApiResult.Success -> {
                    val volumes = result.data
                    // Seleccionar "Pablo2" por defecto si existe
                    val defaultVolume = volumes.find { it.name.equals("Pablo2", ignoreCase = true) }
                        ?: volumes.firstOrNull()

                    _uiState.value = _uiState.value.copy(
                        networkVolumes = volumes,
                        selectedNetworkVolume = defaultVolume,
                        isLoadingVolumes = false
                    )
                }
                is ApiResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isLoadingVolumes = false
                    )
                }
            }
        }
    }

    fun onNameChange(name: String) {
        _uiState.value = _uiState.value.copy(name = name)
    }

    fun onComputeTypeChange(type: ComputeType) {
        _uiState.value = _uiState.value.copy(computeType = type)
    }

    fun onGpuChange(gpu: GpuOption) {
        _uiState.value = _uiState.value.copy(selectedGpu = gpu)
    }

    fun onCpuChange(cpu: CpuOption) {
        _uiState.value = _uiState.value.copy(selectedCpu = cpu)
    }

    fun onContainerDiskChange(size: Int) {
        _uiState.value = _uiState.value.copy(containerDiskGb = size)
    }

    fun onNetworkVolumeChange(volume: NetworkVolume) {
        _uiState.value = _uiState.value.copy(selectedNetworkVolume = volume)
    }

    fun onStartScriptChange(script: String) {
        _uiState.value = _uiState.value.copy(startScript = script)
    }

    fun createPod() {
        val state = _uiState.value

        if (state.name.isBlank()) {
            _uiState.value = state.copy(errorMessage = "El nombre es requerido")
            return
        }

        if (!state.hasSshKeys) {
            _uiState.value = state.copy(errorMessage = "Debes generar claves SSH en Configuracion antes de crear un pod")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

            val networkVolumeId = state.selectedNetworkVolume?.id

            // Configurar clave SSH y luego ejecutar el entrypoint original de Runpod
            val sshSetup = state.sshPublicKey?.let { key ->
                "mkdir -p ~/.ssh && echo '${key.trim()}' >> ~/.ssh/authorized_keys && chmod 600 ~/.ssh/authorized_keys"
            } ?: ""

            // Combinar setup SSH + script usuario + entrypoint original
            val fullScript = buildString {
                if (sshSetup.isNotBlank()) append("$sshSetup && ")
                if (state.startScript.isNotBlank()) append("${state.startScript} && ")
                append("exec /start.sh")  // Ejecutar entrypoint original de Runpod
            }

            val dockerStartCmd = listOf("bash", "-c", fullScript)
            val envVars: Map<String, String>? = null

            val request = if (state.computeType == ComputeType.GPU) {
                CreatePodRequest(
                    name = state.name,
                    gpuTypeIds = listOf(state.selectedGpu.id),
                    computeType = "GPU",
                    containerDiskInGb = state.containerDiskGb,
                    volumeInGb = 0,
                    networkVolumeId = networkVolumeId,
                    dockerStartCmd = dockerStartCmd,
                    env = envVars
                )
            } else {
                CreatePodRequest(
                    name = state.name,
                    gpuTypeIds = emptyList(),
                    computeType = "CPU",
                    cpuFlavorIds = listOf(state.selectedCpu.id),
                    containerDiskInGb = state.containerDiskGb,
                    volumeInGb = 0,
                    networkVolumeId = networkVolumeId,
                    dockerStartCmd = dockerStartCmd,
                    env = envVars
                )
            }

            when (val result = repository.createPod(request)) {
                is ApiResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isCreated = true
                    )
                }
                is ApiResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = result.message
                    )
                }
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
}
