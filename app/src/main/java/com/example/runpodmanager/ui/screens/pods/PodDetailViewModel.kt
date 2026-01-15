package com.example.runpodmanager.ui.screens.pods

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.runpodmanager.data.model.Pod
import com.example.runpodmanager.data.repository.ApiResult
import com.example.runpodmanager.data.repository.PodRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PodDetailUiState(
    val pod: Pod? = null,
    val isLoading: Boolean = false,
    val isActionLoading: Boolean = false,
    val errorMessage: String? = null,
    val isDeleted: Boolean = false
)

@HiltViewModel
class PodDetailViewModel @Inject constructor(
    private val repository: PodRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val podId: String = savedStateHandle.get<String>("podId") ?: ""

    private val _uiState = MutableStateFlow(PodDetailUiState())
    val uiState: StateFlow<PodDetailUiState> = _uiState.asStateFlow()

    init {
        loadPod()
    }

    fun loadPod() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            when (val result = repository.getPod(podId)) {
                is ApiResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        pod = result.data,
                        isLoading = false
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

    fun startPod() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isActionLoading = true, errorMessage = null)
            when (val result = repository.startPod(podId)) {
                is ApiResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        pod = result.data,
                        isActionLoading = false
                    )
                }
                is ApiResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isActionLoading = false,
                        errorMessage = result.message
                    )
                }
            }
        }
    }

    fun stopPod() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isActionLoading = true, errorMessage = null)
            when (val result = repository.stopPod(podId)) {
                is ApiResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        pod = result.data,
                        isActionLoading = false
                    )
                }
                is ApiResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isActionLoading = false,
                        errorMessage = result.message
                    )
                }
            }
        }
    }

    fun restartPod() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isActionLoading = true, errorMessage = null)
            when (val result = repository.restartPod(podId)) {
                is ApiResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        pod = result.data,
                        isActionLoading = false
                    )
                }
                is ApiResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isActionLoading = false,
                        errorMessage = result.message
                    )
                }
            }
        }
    }

    fun deletePod() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isActionLoading = true, errorMessage = null)
            when (repository.deletePod(podId)) {
                is ApiResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isActionLoading = false,
                        isDeleted = true
                    )
                }
                is ApiResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isActionLoading = false,
                        errorMessage = "Error eliminando pod"
                    )
                }
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
}
