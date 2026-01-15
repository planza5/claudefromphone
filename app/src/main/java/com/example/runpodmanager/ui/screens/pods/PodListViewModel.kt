package com.example.runpodmanager.ui.screens.pods

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

data class PodListUiState(
    val pods: List<Pod> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class PodListViewModel @Inject constructor(
    private val repository: PodRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PodListUiState())
    val uiState: StateFlow<PodListUiState> = _uiState.asStateFlow()

    init {
        loadPods()
    }

    fun loadPods() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            when (val result = repository.getPods()) {
                is ApiResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        pods = result.data,
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

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRefreshing = true, errorMessage = null)
            when (val result = repository.getPods()) {
                is ApiResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        pods = result.data,
                        isRefreshing = false
                    )
                }
                is ApiResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isRefreshing = false,
                        errorMessage = result.message
                    )
                }
            }
        }
    }

    fun deletePod(podId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            when (val result = repository.deletePod(podId)) {
                is ApiResult.Success -> {
                    loadPods()
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

    fun stopPod(podId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            when (val result = repository.stopPod(podId)) {
                is ApiResult.Success -> {
                    loadPods()
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

    fun startPod(podId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            when (val result = repository.startPod(podId)) {
                is ApiResult.Success -> {
                    loadPods()
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
