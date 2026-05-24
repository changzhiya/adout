package com.adout.vpn

import android.app.Notification
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.adout.AdoutApplication
import com.adout.rule.RuleEngine
import com.adout.data.RuleRepository
import com.adout.service.NotificationHelper
import com.adout.ui.MainActivity
import kotlinx.coroutines.*

class AdBlockVpnService : VpnService() {

    companion object {
        private const val TAG = "AdBlockVpnService"

        var isRunning = false
            private set

        var instance: AdBlockVpnService? = null
            private set
    }

    private var vpnInterface: ParcelFileDescriptor? = null
    private val ruleEngine = RuleEngine()
    private val ruleRepository = RuleRepository(this)
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var tunnelManager: TunnelManager? = null

    private val ruleUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "REFRESH_RULES") {
                refreshRules()
            }
        }
    }

    override fun onCreate() {
        Log.i(TAG, "=== onCreate START ===")
        super.onCreate()
        instance = this
        Log.i(TAG, "=== onCreate END ===")

        // Register rule update receiver (local broadcast only)
        val filter = IntentFilter("REFRESH_RULES")
        LocalBroadcastManager.getInstance(this).registerReceiver(ruleUpdateReceiver, filter)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "=== onStartCommand action=${intent?.action} ===")
        when (intent?.action) {
            "START" -> startVpn()
            "STOP" -> stopVpn()
            "REFRESH_RULES" -> refreshRules()
        }
        return START_STICKY
    }

    private fun startVpn() {
        Log.i(TAG, "=== startVpn BEGIN isRunning=$isRunning ===")
        if (isRunning) {
            Log.i(TAG, "=== startVpn SKIPPED (already running) ===")
            return
        }

        // Start foreground IMMEDIATELY to avoid ForegroundServiceStartNotAllowedException on Android 14+
        try {
            Log.i(TAG, "Step 1: startForeground...")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    1,
                    createNotification(),
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(1, createNotification())
            }
            Log.i(TAG, "Step 1: startForeground OK")
        } catch (e: Exception) {
            Log.e(TAG, "Step 1: startForeground FAILED", e)
            return
        }

        serviceScope.launch {
            try {
                Log.i(TAG, "Step 2: loadRules START")
                loadRules()
                Log.i(TAG, "Step 2: loadRules OK, ruleCount=${ruleEngine.getRuleCount()}")

                Log.i(TAG, "Step 3: establishVpn START (thread=${Thread.currentThread().name})")
                val vpn = withContext(Dispatchers.Main) {
                    Log.i(TAG, "Step 3: establishVpn on MAIN thread")
                    establishVpn()
                }
                vpnInterface = vpn
                Log.i(TAG, "Step 3: establishVpn OK vpn=$vpn")

                if (vpn != null) {
                    Log.i(TAG, "Step 4: TunnelManager START")
                    tunnelManager = TunnelManager(vpn, ruleEngine, this@AdBlockVpnService)
                    tunnelManager?.start()

                    isRunning = true
                    saveVpnState(true)
                    Log.i(TAG, "Step 4: TunnelManager OK")

                    Log.i(TAG, "Step 5: updateNotification")
                    updateNotification()

                    Log.i(TAG, "Step 6: sendBroadcast")
                    withContext(Dispatchers.Main) {
                        LocalBroadcastManager.getInstance(this@AdBlockVpnService)
                            .sendBroadcast(Intent("VPN_STATUS_CHANGED"))
                    }
                    Log.i(TAG, "=== startVpn COMPLETE ===")
                } else {
                    Log.e(TAG, "Step 3: establishVpn returned NULL")
                }
            } catch (e: Exception) {
                Log.e(TAG, "=== startVpn EXCEPTION at step ===", e)
                stopVpn()
            }
        }
    }

    private fun stopVpn() {
        serviceScope.launch {
            cleanupVpn()

            withContext(Dispatchers.Main) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                } else {
                    @Suppress("DEPRECATION")
                    stopForeground(true)
                }
                stopSelf()
                LocalBroadcastManager.getInstance(this@AdBlockVpnService)
                    .sendBroadcast(Intent("VPN_STATUS_CHANGED"))
            }
        }
    }

    /**
     * Synchronous cleanup - safe to call from onDestroy without coroutine
     */
    private fun cleanupVpn() {
        tunnelManager?.stop()
        tunnelManager = null

        try {
            vpnInterface?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to close vpnInterface", e)
        }
        vpnInterface = null

        isRunning = false
        saveVpnState(false)
    }

    private suspend fun loadRules() {
        // Only load local rules (built-in + assets) for fast startup
        val allRules = ruleRepository.getAllRules(
            includeAdGuard = false,
            customRules = emptyList()
        )
        ruleEngine.loadRules(allRules)
        Log.i(TAG, "Loaded ${allRules.size} local rules")
    }

    private fun establishVpn(): ParcelFileDescriptor? {
        // DNS-only mode: only route DNS traffic through VPN
        // Non-DNS traffic (HTTP, etc.) bypasses VPN completely
        return Builder()
            .setSession("Adout")
            .addAddress("10.0.0.2", 32)
            .addDnsServer("10.0.0.2")  // Route DNS to our VPN address
            .setMtu(1500)
            .setBlocking(true)
            .establish()
    }

    private fun createNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        val ruleCount = ruleEngine.getRuleCount()
        val blockedCount = tunnelManager?.getBlockedCount() ?: 0

        return Notification.Builder(this, AdoutApplication.NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Adout 保护中")
            .setContentText("已加载 $ruleCount 条规则，拦截 $blockedCount 个广告")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification() {
        val notification = createNotification()
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager ?: return
        notificationManager.notify(1, notification)
    }

    override fun onDestroy() {
        cleanupVpn()
        serviceScope.cancel()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(ruleUpdateReceiver)
        instance = null
        super.onDestroy()
    }

    override fun onRevoke() {
        stopVpn()
        super.onRevoke()
    }

    fun getRuleCount(): Int {
        return ruleEngine.getRuleCount()
    }

    fun getBlockedCount(): Long {
        return tunnelManager?.getBlockedCount() ?: 0
    }

    private fun saveVpnState(running: Boolean) {
        val prefs = getSharedPreferences("adout_prefs", MODE_PRIVATE)
        prefs.edit().putBoolean("vpn_was_running", running).apply()
    }

    /**
     * Hot update rules
     */
    fun updateRules(newRules: List<String>) {
        serviceScope.launch {
            ruleEngine.reloadRules(newRules)
            Log.i(TAG, "Rules updated: ${newRules.size} rules")
            updateNotification()
        }
    }

    /**
     * Refresh rules from all sources
     */
    fun refreshRules() {
        serviceScope.launch {
            try {
                val allRules = ruleRepository.getAllRules()
                ruleEngine.reloadRules(allRules)
                Log.i(TAG, "Rules refreshed: ${allRules.size} rules")
                updateNotification()

                withContext(Dispatchers.Main) {
                    LocalBroadcastManager.getInstance(this@AdBlockVpnService)
                        .sendBroadcast(Intent("VPN_STATUS_CHANGED"))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to refresh rules", e)
            }
        }
    }

    /**
     * Add custom rule
     */
    fun addCustomRule(ruleText: String) {
        serviceScope.launch {
            ruleEngine.addRule(ruleText)
            Log.i(TAG, "Custom rule added: $ruleText")
            updateNotification()
        }
    }
}
