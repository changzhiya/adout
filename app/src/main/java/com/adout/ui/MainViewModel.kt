package com.adout.ui

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.VpnService
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.adout.vpn.AdBlockVpnService
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

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
        application.registerReceiver(vpnStatusReceiver, filter)
        updateVpnStatus()
    }

    fun toggleVpn() {
        val context = getApplication<Application>()

        // If VPN is already running, just stop it
        if (AdBlockVpnService.isRunning) {
            stopVpn()
            return
        }

        // Check VPN permission first
        val intent = VpnService.prepare(context)
        if (intent != null) {
            // Need permission - request it
            pendingVpnToggle = true
            viewModelScope.launch {
                _requestVpnPermission.emit(true)
            }
        } else {
            // Permission already granted
            startVpn()
        }
    }

    fun onVpnPermissionGranted() {
        if (pendingVpnToggle) {
            pendingVpnToggle = false
            startVpn()
        }
    }

    fun onVpnPermissionDenied() {
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
        val context = getApplication<Application>()
        val intent = Intent(context, AdBlockVpnService::class.java).apply {
            action = "START"
        }
        context.startService(intent)
    }

    private fun stopVpn() {
        val context = getApplication<Application>()
        val intent = Intent(context, AdBlockVpnService::class.java).apply {
            action = "STOP"
        }
        context.startService(intent)
    }

    fun dismissPermissionDeniedMessage() {
        _uiState.value = _uiState.value.copy(
            showPermissionDeniedMessage = false
        )
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
        getApplication<Application>().unregisterReceiver(vpnStatusReceiver)
    }
}

data class MainUiState(
    val isVpnRunning: Boolean = false,
    val ruleCount: Int = 0,
    val blockedCount: Long = 0,
    val showPermissionDeniedMessage: Boolean = false
)
