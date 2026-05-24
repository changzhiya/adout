package com.adout.ui.util

import android.content.Context
import android.provider.Settings

/**
 * AnimationHelper - 动画设置检测
 *
 * 检测用户是否启用了减少动画选项
 * 支持 reduced-motion 无障碍功能
 */
object AnimationHelper {

    /**
     * 检查用户是否启用了减少动画
     * @return true 表示应该减少或禁用动画
     */
    fun isReducedMotionEnabled(context: Context): Boolean {
        return try {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1.0f
            ) == 0f
        } catch (e: Settings.SettingNotFoundException) {
            false
        }
    }

    /**
     * 获取动画时长倍数
     * @return 0.0f 到 1.0f 之间的值，1.0f 表示正常速度
     */
    fun getAnimationScale(context: Context): Float {
        return try {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1.0f
            )
        } catch (e: Settings.SettingNotFoundException) {
            1.0f
        }
    }

    /**
     * 根据动画设置计算实际动画时长
     * @param baseDuration 基础时长（毫秒）
     * @return 调整后的时长
     */
    fun getAdjustedDuration(context: Context, baseDuration: Int): Int {
        val scale = getAnimationScale(context)
        return (baseDuration * scale).toInt()
    }
}