package com.adout.service

import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import com.adout.vpn.AdBlockVpnService

object ForegroundServiceManager {

    fun updateNotification(context: Context) {
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        val isRunning = AdBlockVpnService.isRunning
        val blockedCount = AdBlockVpnService.instance?.getBlockedCount() ?: 0

        val notification = NotificationHelper.createVpnServiceNotification(
            context,
            isRunning,
            blockedCount
        )

        notificationManager.notify(1, notification)
    }

    fun checkBatteryOptimization(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            val isIgnoringBatteryOptimizations = powerManager.isIgnoringBatteryOptimizations(
                context.packageName
            )

            if (!isIgnoringBatteryOptimizations) {
                NotificationHelper.showBatteryOptimizationNotification(context)
            }
        }
    }

    fun isBatteryOptimizationDisabled(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            return powerManager.isIgnoringBatteryOptimizations(context.packageName)
        }
        return true
    }

    fun requestIgnoreBatteryOptimization(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = android.content.Intent(
                android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                android.net.Uri.parse("package:${context.packageName}")
            )
            context.startActivity(intent)
        }
    }
}
