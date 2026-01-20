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
import kotlinx.coroutines.flow.update
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
        fetchPods(isRefresh = false)
    }

    fun refresh() {
        fetchPods(isRefresh = true)
    }

    private fun fetchPods(isRefresh: Boolean) {
        viewModelScope.launch {
            _uiState.update {
                if (isRefresh) it.copy(isRefreshing = true, errorMessage = null)
                else it.copy(isLoading = true, errorMessage = null)
            }

            when (val result = repository.getPods()) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(pods = result.data, isLoading = false, isRefreshing = false)
                }
                is ApiResult.Error -> _uiState.update {
                    it.copy(isLoading = false, isRefreshing = false, errorMessage = result.message)
                }
            }
        }
    }

    fun deletePod(podId: String) {
        executePodAction { repository.deletePod(podId) }
    }

    fun stopPod(podId: String) {
        executePodAction { repository.stopPod(podId) }
    }

    fun startPod(podId: String) {
        executePodAction { repository.startPod(podId) }
    }

    private fun <T> executePodAction(action: suspend () -> ApiResult<T>) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            when (val result = action()) {
                is ApiResult.Success -> loadPods()
                is ApiResult.Error -> _uiState.update {
                    it.copy(isLoading = false, errorMessage = result.message)
                }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
