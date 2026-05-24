package com.adout.ui.util

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings

/**
 * HapticHelper - 触觉反馈辅助类
 *
 * 为高分辨率屏幕提供触觉反馈支持
 * 适配 2736x1264 高分辨率屏幕
 */
object HapticHelper {

    /**
     * 执行轻触反馈
     */
    fun performLightFeedback(context: Context) {
        val vibrator = getVibrator(context) ?: return
        if (!isHapticEnabled(context)) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(20, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(20)
        }
    }

    /**
     * 执行中等反馈（用于重要操作）
     */
    fun performMediumFeedback(context: Context) {
        val vibrator = getVibrator(context) ?: return
        if (!isHapticEnabled(context)) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(40)
        }
    }

    /**
     * 执行重反馈（用于状态切换）
     */
    fun performHeavyFeedback(context: Context) {
        val vibrator = getVibrator(context) ?: return
        if (!isHapticEnabled(context)) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(60, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(60)
        }
    }

    /**
     * 检查系统是否启用了触觉反馈
     */
    private fun isHapticEnabled(context: Context): Boolean {
        return Settings.System.getInt(
            context.contentResolver,
            Settings.System.HAPTIC_FEEDBACK_ENABLED,
            1
        ) == 1
    }

    /**
     * 获取 Vibrator 实例
     */
    private fun getVibrator(context: Context): Vibrator? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }
}