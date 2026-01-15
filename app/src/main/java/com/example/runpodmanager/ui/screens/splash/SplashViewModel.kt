package com.example.runpodmanager.ui.screens.splash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.runpodmanager.data.local.ApiKeyManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SplashViewModel @Inject constructor(
    private val apiKeyManager: ApiKeyManager
) : ViewModel() {

    private val _hasApiKey = MutableStateFlow(false)
    val hasApiKey: StateFlow<Boolean> = _hasApiKey.asStateFlow()

    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    init {
        checkApiKey()
    }

    private fun checkApiKey() {
        viewModelScope.launch {
            val apiKey = apiKeyManager.apiKey.first()
            _hasApiKey.value = apiKey.isNotBlank()
            _isReady.value = true
        }
    }
}
