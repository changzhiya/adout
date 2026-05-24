package com.adout.ui

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.adout.vpn.AdBlockVpnService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

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
        val intent = Intent(context, AdBlockVpnService::class.java)

        if (AdBlockVpnService.isRunning) {
            intent.action = "STOP"
        } else {
            intent.action = "START"
        }

        context.startService(intent)
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
    val blockedCount: Long = 0
)
