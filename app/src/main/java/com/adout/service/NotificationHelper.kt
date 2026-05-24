package com.adout.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.adout.AdoutApplication
import com.adout.ui.MainActivity

object NotificationHelper {

    fun createVpnServiceNotification(
        context: Context,
        isRunning: Boolean,
        blockedCount: Long = 0
    ): Notification {
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (isRunning) "Adout 保护中" else "Adout 已停止"
        val text = if (isRunning) {
            "广告拦截已启用，已拦截 $blockedCount 个广告"
        } else {
            "点击开启保护"
        }

        return Notification.Builder(context, AdoutApplication.NOTIFICATION_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setOngoing(isRunning)
            .build()
    }

    fun createBatteryOptimizationNotification(context: Context): Notification {
        val pendingIntent = PendingIntent.getActivity(
            context,
            1,
            Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
            PendingIntent.FLAG_IMMUTABLE
        )

        return Notification.Builder(context, AdoutApplication.NOTIFICATION_CHANNEL_ID)
            .setContentTitle("需要关闭电池优化")
            .setContentText("Adout 需要在后台运行，请关闭电池优化")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
    }

    fun showBatteryOptimizationNotification(context: Context) {
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        notificationManager.notify(2, createBatteryOptimizationNotification(context))
    }
}
