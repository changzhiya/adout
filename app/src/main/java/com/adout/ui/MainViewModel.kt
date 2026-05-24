package com.adout.ui

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.VpnService
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.adout.accessibility.AdSkipAccessibilityService
import com.adout.vpn.AdBlockVpnService
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "MainViewModel"
    }

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private val _requestVpnPermission = MutableSharedFlow<Boolean>()
    val requestVpnPermission: SharedFlow<Boolean> = _requestVpnPermission.asSharedFlow()

    private var pendingVpnToggle = false

    private val vpnStatusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "VPN_STATUS_CHANGED") {
                updateVpnStatus()
            }
        }
    }

    init {
        val filter = IntentFilter("VPN_STATUS_CHANGED")
        LocalBroadcastManager.getInstance(application).registerReceiver(vpnStatusReceiver, filter)
        updateVpnStatus()

        // Observe accessibility service skip count
        viewModelScope.launch {
            AdSkipAccessibilityService.adsSkippedCount.collect { count ->
                _uiState.value = _uiState.value.copy(adsSkippedCount = count)
            }
        }
    }

    fun toggleVpn() {
        Log.i(TAG, "toggleVpn called, isRunning=${AdBlockVpnService.isRunning}")
        val context = getApplication<Application>()

        // If VPN is already running, just stop it
        if (AdBlockVpnService.isRunning) {
            Log.i(TAG, "VPN already running, stopping")
            stopVpn()
            return
        }

        // Check VPN permission first
        val intent = VpnService.prepare(context)
        Log.i(TAG, "VpnService.prepare result: $intent")
        if (intent != null) {
            // Need permission - request it
            pendingVpnToggle = true
            Log.i(TAG, "Requesting VPN permission")
            viewModelScope.launch {
                _requestVpnPermission.emit(true)
            }
        } else {
            // Permission already granted
            Log.i(TAG, "VPN permission already granted, starting VPN")
            startVpn()
        }
    }

    fun onVpnPermissionGranted() {
        Log.i(TAG, "onVpnPermissionGranted, pendingVpnToggle=$pendingVpnToggle")
        if (pendingVpnToggle) {
            pendingVpnToggle = false
            startVpn()
        }
    }

    fun onVpnPermissionDenied() {
        Log.i(TAG, "onVpnPermissionDenied")
        pendingVpnToggle = false
        _uiState.value = _uiState.value.copy(
            showPermissionDeniedMessage = true
        )
        // Auto-hide message after 3 seconds
        viewModelScope.launch {
            kotlinx.coroutines.delay(3000)
            _uiState.value = _uiState.value.copy(
                showPermissionDeniedMessage = false
            )
        }
    }

    private fun startVpn() {
        Log.i(TAG, "startVpn: sending START to service")
        try {
            val context = getApplication<Application>()
            val intent = Intent(context, AdBlockVpnService::class.java).apply {
                action = "START"
            }
            context.startService(intent)
            Log.i(TAG, "startVpn: service started OK")
        } catch (e: Exception) {
            Log.e(TAG, "startVpn: FAILED to start service", e)
        }
    }

    private fun stopVpn() {
        Log.i(TAG, "stopVpn: sending STOP to service")
        try {
            val context = getApplication<Application>()
            val intent = Intent(context, AdBlockVpnService::class.java).apply {
                action = "STOP"
            }
            context.startService(intent)
            Log.i(TAG, "stopVpn: service stop sent OK")
        } catch (e: Exception) {
            Log.e(TAG, "stopVpn: FAILED", e)
        }
    }

    fun dismissPermissionDeniedMessage() {
        _uiState.value = _uiState.value.copy(
            showPermissionDeniedMessage = false
        )
    }

    private val _showSettings = MutableStateFlow(false)
    val showSettings: StateFlow<Boolean> = _showSettings.asStateFlow()

    fun openSettings() {
        _showSettings.value = true
    }

    fun closeSettings() {
        _showSettings.value = false
    }

    fun refreshRules() {
        val context = getApplication<Application>()
        val intent = Intent("REFRESH_RULES")
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent)
    }

    private fun updateVpnStatus() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isVpnRunning = AdBlockVpnService.isRunning,
                ruleCount = AdBlockVpnService.instance?.getRuleCount() ?: 0,
                blockedCount = AdBlockVpnService.instance?.getBlockedCount() ?: 0
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        LocalBroadcastManager.getInstance(getApplication()).unregisterReceiver(vpnStatusReceiver)
    }
}

data class MainUiState(
    val isVpnRunning: Boolean = false,
    val ruleCount: Int = 0,
    val blockedCount: Long = 0,
    val adsSkippedCount: Long = 0,
    val showPermissionDeniedMessage: Boolean = false
)
