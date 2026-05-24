package com.adout

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.adout.worker.VpnWatchdogWorker
import java.util.concurrent.TimeUnit

class AdoutApplication : Application() {

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "adout_vpn_service"
        const val NOTIFICATION_CHANNEL_NAME = "VPN Service"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        scheduleWatchdog()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                NOTIFICATION_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Adout VPN Service Notification"
                setShowBadge(false)
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun scheduleWatchdog() {
        val request = PeriodicWorkRequestBuilder<VpnWatchdogWorker>(
            15, TimeUnit.MINUTES
        ).build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            VpnWatchdogWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }
}
