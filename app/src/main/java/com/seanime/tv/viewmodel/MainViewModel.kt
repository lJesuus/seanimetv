package com.seanime.tv.viewmodel

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.seanime.tv.utils.PreferencesManager

class MainViewModel(private val preferencesManager: PreferencesManager) : ViewModel() {

    private val _serverUrl = mutableStateOf(preferencesManager.getServerUrl() ?: "")
    val serverUrl: State<String> = _serverUrl

    private val _isConnected = mutableStateOf(preferencesManager.getServerUrl() != null)
    val isConnected: State<Boolean> = _isConnected

    fun updateUrl(url: String) {
        _serverUrl.value = url
    }

    fun connect() {
        val trimmedUrl = _serverUrl.value.trim()
        if (trimmedUrl.isNotEmpty()) {
            _serverUrl.value = trimmedUrl
            preferencesManager.saveServerUrl(trimmedUrl)
            _isConnected.value = true
        }
    }

    fun disconnect() {
        preferencesManager.clearServerUrl()
        _isConnected.value = false
    }

    /**
     * Factory for creating [MainViewModel] with a [PreferencesManager] dependency.
     * Use with [ViewModelProvider] to ensure proper lifecycle management.
     */
    class Factory(private val preferencesManager: PreferencesManager) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
                return MainViewModel(preferencesManager) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
