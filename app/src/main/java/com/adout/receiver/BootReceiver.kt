package com.adout.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
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
            Log.i(TAG, "VPN was running before reboot, restarting...")
            try {
                val serviceIntent = Intent(context, AdBlockVpnService::class.java).apply {
                    action = "START"
                }
                context.startService(serviceIntent)
                Log.i(TAG, "VPN restart intent sent")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to restart VPN after boot", e)
            }
        } else {
            Log.i(TAG, "VPN was not running before reboot, skipping")
        }
    }
}
