package com.adout.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.adout.vpn.AdBlockVpnService

class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        Log.i(TAG, "Boot completed received")

        val prefs = context.getSharedPreferences("adout_prefs", Context.MODE_PRIVATE)
        val wasRunning = prefs.getBoolean("vpn_was_running", false)

        if (wasRunning) {
            Log.i(TAG, "VPN was running before reboot, enqueueing restart...")
            // Use WorkManager for reliable execution across Android 12+ restrictions
            val workRequest = OneTimeWorkRequestBuilder<BootVpnWorker>()
                .addTag("boot_vpn_restart")
                .build()
            WorkManager.getInstance(context).enqueue(workRequest)
        } else {
            Log.i(TAG, "VPN was not running before reboot, skipping")
        }
    }
}

class BootVpnWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {

    companion object {
        private const val TAG = "BootVpnWorker"
    }

    override fun doWork(): Result {
        Log.i(TAG, "BootVpnWorker starting VPN...")
        return try {
            val intent = Intent(applicationContext, AdBlockVpnService::class.java).apply {
                action = "START"
            }
            applicationContext.startService(intent)
            Log.i(TAG, "BootVpnWorker VPN start intent sent")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "BootVpnWorker failed to start VPN", e)
            Result.retry()
        }
    }
}
