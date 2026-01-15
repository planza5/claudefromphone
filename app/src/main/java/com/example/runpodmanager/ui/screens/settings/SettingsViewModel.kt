package com.example.runpodmanager.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.runpodmanager.data.local.ApiKeyManager
import com.example.runpodmanager.data.repository.ApiResult
import com.example.runpodmanager.data.repository.PodRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val apiKey: String = "",
    val isLoading: Boolean = false,
    val isSaved: Boolean = false,
    val isValidated: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val apiKeyManager: ApiKeyManager,
    private val podRepository: PodRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadApiKey()
    }

    private fun loadApiKey() {
        viewModelScope.launch {
            val savedKey = apiKeyManager.apiKey.first()
            _uiState.value = _uiState.value.copy(
                apiKey = savedKey,
                isSaved = savedKey.isNotBlank()
            )
        }
    }

    fun onApiKeyChange(newKey: String) {
        _uiState.value = _uiState.value.copy(
            apiKey = newKey,
            errorMessage = null,
            isValidated = false
        )
    }

    fun saveApiKey() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

            apiKeyManager.saveApiKey(_uiState.value.apiKey)

            // Validate by trying to fetch pods
            when (val result = podRepository.getPods()) {
                is ApiResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isSaved = true,
                        isValidated = true,
                        errorMessage = null
                    )
                }
                is ApiResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isSaved = true,
                        isValidated = false,
                        errorMessage = if (result.code == 401) {
                            "API key invalida"
                        } else {
                            "Error: ${result.message}"
                        }
                    )
                }
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
}
