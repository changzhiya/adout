package com.adout.worker

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.adout.ui.MainActivity
import com.adout.vpn.AdBlockVpnService

class VpnWatchdogWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        const val TAG = "VpnWatchdog"
        const val WORK_NAME = "vpn_watchdog"
        private const val NOTIFICATION_ID = 100
        private const val MAX_FAILURES = 3
    }

    override suspend fun doWork(): Result {
        val prefs = applicationContext.getSharedPreferences("adout_prefs", Context.MODE_PRIVATE)
        val lastCheck = System.currentTimeMillis()
        prefs.edit().putLong("last_watchdog_check", lastCheck).apply()

        Log.d(TAG, "Watchdog check: isRunning=${AdBlockVpnService.isRunning}")

        if (AdBlockVpnService.isRunning) {
            prefs.edit().putInt("watchdog_failure_count", 0).apply()
            return Result.success()
        }

        val wasRunning = prefs.getBoolean("vpn_was_running", false)
        if (!wasRunning) {
            Log.d(TAG, "VPN was not running, no action needed")
            return Result.success()
        }

        // VPN was running but stopped - attempt restart
        val failureCount = prefs.getInt("watchdog_failure_count", 0)
        if (failureCount >= MAX_FAILURES) {
            Log.w(TAG, "Max restart failures reached, notifying user")
            showFailureNotification()
            return Result.success()
        }

        Log.i(TAG, "Attempting VPN restart (attempt ${failureCount + 1}/$MAX_FAILURES)")
        return try {
            val intent = Intent(applicationContext, AdBlockVpnService::class.java).apply {
                action = "START"
            }
            applicationContext.startService(intent)
            prefs.edit()
                .putInt("watchdog_failure_count", 0)
                .putLong("last_vpn_restart", System.currentTimeMillis())
                .apply()
            Log.i(TAG, "VPN restart successful")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "VPN restart failed", e)
            prefs.edit().putInt("watchdog_failure_count", failureCount + 1).apply()
            if (failureCount + 1 >= MAX_FAILURES) {
                showFailureNotification()
            }
            Result.success()
        }
    }

    private fun showFailureNotification() {
        val intent = Intent(applicationContext, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            applicationContext, 0, intent, PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(applicationContext, "adout_vpn_service")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("AdOut 需要您的帮助")
            .setContentText("自动恢复失败，请打开应用检查设置")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE)
            as? NotificationManager ?: return
        manager.notify(NOTIFICATION_ID, notification)
    }
}
