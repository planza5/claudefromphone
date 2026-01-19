package com.example.runpodmanager.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.runpodmanager.data.local.ApiKeyManager
import com.example.runpodmanager.data.repository.ApiResult
import com.example.runpodmanager.data.repository.PodRepository
import com.example.runpodmanager.data.ssh.SshKeyManager
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
    val errorMessage: String? = null,
    val hasSshKeys: Boolean = false,
    val sshPublicKey: String? = null,
    val sshPrivateKey: String? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val apiKeyManager: ApiKeyManager,
    private val podRepository: PodRepository,
    private val sshKeyManager: SshKeyManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadApiKey()
        loadSshKeys()
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

    private fun loadSshKeys() {
        _uiState.value = _uiState.value.copy(
            hasSshKeys = sshKeyManager.hasKeys(),
            sshPublicKey = sshKeyManager.getPublicKey(),
            sshPrivateKey = sshKeyManager.getPrivateKey()
        )
    }

    fun generateSshKeys() {
        val (privateKey, publicKey) = sshKeyManager.generateKeys()
        _uiState.value = _uiState.value.copy(
            hasSshKeys = true,
            sshPublicKey = publicKey,
            sshPrivateKey = privateKey
        )
    }

    fun deleteSshKeys() {
        sshKeyManager.deleteKeys()
        _uiState.value = _uiState.value.copy(
            hasSshKeys = false,
            sshPublicKey = null,
            sshPrivateKey = null
        )
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
