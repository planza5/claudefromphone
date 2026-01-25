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
import kotlinx.coroutines.flow.update
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
        _uiState.update {
            it.copy(hasSshKeys = sshKeyManager.hasKeys(), sshPublicKey = sshKeyManager.getPublicKey())
        }
    }

    private fun loadNetworkVolumes() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingVolumes = true) }

            when (val result = repository.getNetworkVolumes()) {
                is ApiResult.Success -> {
                    val volumes = result.data
                    val defaultVolume = volumes.find { it.name.equals("Pablo2", ignoreCase = true) }
                        ?: volumes.firstOrNull()

                    _uiState.update {
                        it.copy(
                            networkVolumes = volumes,
                            selectedNetworkVolume = defaultVolume,
                            isLoadingVolumes = false
                        )
                    }
                }
                is ApiResult.Error -> _uiState.update { it.copy(isLoadingVolumes = false) }
            }
        }
    }

    fun onNameChange(name: String) {
        _uiState.update { it.copy(name = name) }
    }

    fun onComputeTypeChange(type: ComputeType) {
        _uiState.update { it.copy(computeType = type) }
    }

    fun onGpuChange(gpu: GpuOption) {
        _uiState.update { it.copy(selectedGpu = gpu) }
    }

    fun onCpuChange(cpu: CpuOption) {
        _uiState.update { it.copy(selectedCpu = cpu) }
    }

    fun onContainerDiskChange(size: Int) {
        _uiState.update { it.copy(containerDiskGb = size) }
    }

    fun onNetworkVolumeChange(volume: NetworkVolume) {
        _uiState.update { it.copy(selectedNetworkVolume = volume) }
    }

    fun createPod() {
        val state = _uiState.value

        if (state.name.isBlank()) {
            _uiState.update { it.copy(errorMessage = "El nombre es requerido") }
            return
        }

        if (!state.hasSshKeys) {
            _uiState.update { it.copy(errorMessage = "Debes generar claves SSH en Configuracion antes de crear un pod") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            val request = buildCreatePodRequest(state)

            when (val result = repository.createPod(request)) {
                is ApiResult.Success -> _uiState.update { it.copy(isLoading = false, isCreated = true) }
                is ApiResult.Error -> _uiState.update { it.copy(isLoading = false, errorMessage = result.message) }
            }
        }
    }

    private fun buildCreatePodRequest(state: CreatePodUiState): CreatePodRequest {
        val sshSetup = state.sshPublicKey?.let {
            "mkdir -p ~/.ssh && echo '${it.trim()}' >> ~/.ssh/authorized_keys && chmod 600 ~/.ssh/authorized_keys && "
        } ?: ""

        val script = "cd /workspace && ${sshSetup}source /workspace/setup_env.sh && /workspace/start_tailscale.sh && exec /start.sh"
        val isGpu = state.computeType == ComputeType.GPU

        return CreatePodRequest(
            name = state.name,
            gpuTypeIds = if (isGpu) listOf(state.selectedGpu.id) else emptyList(),
            computeType = if (isGpu) "GPU" else "CPU",
            cpuFlavorIds = if (!isGpu) listOf(state.selectedCpu.id) else null,
            containerDiskInGb = state.containerDiskGb,
            volumeInGb = 0,
            networkVolumeId = state.selectedNetworkVolume?.id,
            dockerStartCmd = listOf("bash", "-c", script)
        )
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
