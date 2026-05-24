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
    private var dnsProxy: DnsProxyWrapper? = null

    private val ruleUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "REFRESH_RULES") {
                refreshRules()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        // Register rule update receiver
        val filter = IntentFilter("REFRESH_RULES")
        registerReceiver(ruleUpdateReceiver, filter)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "START" -> startVpn()
            "STOP" -> stopVpn()
            "REFRESH_RULES" -> refreshRules()
        }
        return START_STICKY
    }

    private fun startVpn() {
        if (isRunning) return

        serviceScope.launch {
            try {
                // Load rules (including AdGuardFilters)
                loadRules()

                // Start DNS proxy
                startDnsProxy()

                // Establish VPN connection
                val vpn = establishVpn()
                vpnInterface = vpn

                if (vpn != null) {
                    // Start traffic processing
                    tunnelManager = TunnelManager(vpn, ruleEngine, dnsProxy)
                    tunnelManager?.start()

                    isRunning = true

                    // Start foreground service
                    startForeground(1, createNotification())

                    // Notify UI to update
                    withContext(Dispatchers.Main) {
                        sendBroadcast(Intent("VPN_STATUS_CHANGED"))
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start VPN", e)
                stopVpn()
            }
        }
    }

    private fun stopVpn() {
        serviceScope.launch {
            tunnelManager?.stop()
            tunnelManager = null

            dnsProxy?.stop()
            dnsProxy = null

            vpnInterface?.close()
            vpnInterface = null

            isRunning = false

            withContext(Dispatchers.Main) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                } else {
                    @Suppress("DEPRECATION")
                    stopForeground(true)
                }
                stopSelf()
                sendBroadcast(Intent("VPN_STATUS_CHANGED"))
            }
        }
    }

    private suspend fun loadRules() {
        // Load built-in rules + AdGuardFilters rules
        val allRules = ruleRepository.getAllRules(
            includeAdGuard = true,
            customRules = emptyList()
        )
        ruleEngine.loadRules(allRules)
    }

    private fun startDnsProxy() {
        dnsProxy = DnsProxyWrapper()
        dnsProxy?.start("127.0.0.1:5353", listOf("8.8.8.8", "8.8.4.4"))
    }

    private fun establishVpn(): ParcelFileDescriptor? {
        return Builder()
            .setSession("Adout")
            .addAddress("10.0.0.2", 32)
            .addRoute("0.0.0.0", 0)
            .addDnsServer("127.0.0.1") // Use local DNS proxy
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
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        notificationManager.notify(1, notification)
    }

    override fun onDestroy() {
        stopVpn()
        unregisterReceiver(ruleUpdateReceiver)
        serviceScope.cancel()
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
                    sendBroadcast(Intent("VPN_STATUS_CHANGED"))
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
