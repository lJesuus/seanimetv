package com.seanime.tv

import android.os.Bundle
import android.util.Log
import android.webkit.WebStorage
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import com.seanime.tv.components.ServerUrlInputScreen
import com.seanime.tv.components.WebViewScreen
import com.seanime.tv.utils.PreferencesManager
import com.seanime.tv.viewmodel.MainViewModel

private const val TAG = "SeanimeTV"

class MainActivity : ComponentActivity() {

    private lateinit var viewModel: MainViewModel

    @OptIn(ExperimentalTvMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val preferencesManager = PreferencesManager(this)
        viewModel = ViewModelProvider(
            this,
            MainViewModel.Factory(preferencesManager)
        )[MainViewModel::class.java]

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                ) {
                    val isConnected by viewModel.isConnected
                    val url by viewModel.serverUrl

                    if (isConnected) {
                        WebViewScreen(
                            url = url,
                            onDisconnect = { viewModel.disconnect() }
                        )
                    } else {
                        ServerUrlInputScreen(
                            url = url,
                            onUrlChange = { viewModel.updateUrl(it) },
                            onConnect = { viewModel.connect() }
                        )
                    }
                }
            }
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        when {
            level >= TRIM_MEMORY_COMPLETE -> {
                // Critical memory pressure — clear everything we can
                Log.w(TAG, "Critical memory pressure (level=$level), clearing WebView data")
                clearWebViewCache(includeStorage = true)
            }
            level >= TRIM_MEMORY_MODERATE -> {
                // Moderate pressure — clear cache but preserve session storage
                Log.w(TAG, "Moderate memory pressure (level=$level), clearing WebView cache")
                clearWebViewCache(includeStorage = false)
            }
        }
    }

    private fun clearWebViewCache(includeStorage: Boolean) {
        try {
            if (includeStorage) {
                WebStorage.getInstance().deleteAllData()
            }
            WebView.clearClientCertPreferences(null)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear WebView cache", e)
        }
    }
}
