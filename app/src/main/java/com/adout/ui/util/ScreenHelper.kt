package com.adout.ui.util

import android.content.Context
import android.util.DisplayMetrics
import android.view.WindowManager

/**
 * ScreenHelper - 屏幕适配工具类
 *
 * 针对高分辨率屏幕（如 2736x1264, ~458dpi）进行适配
 * 提供 dp/sp 计算和触控目标尺寸建议
 */
object ScreenHelper {

    /**
     * 屏幕密度等级
     */
    enum class DensityCategory {
        LDPI,      // ~120dpi
        MDPI,      // ~160dpi
        HDPI,      // ~240dpi
        XHDPI,     // ~320dpi
        XXHDPI,    // ~480dpi (2736x1264 屏幕约在此级别)
        XXXHDPI    // ~640dpi
    }

    /**
     * 获取屏幕信息
     */
    data class ScreenInfo(
        val widthPixels: Int,
        val heightPixels: Int,
        val densityDpi: Int,
        val density: Float,
        val densityCategory: DensityCategory,
        val screenSizeInches: Float
    )

    /**
     * 获取当前屏幕信息
     */
    fun getScreenInfo(context: Context): ScreenInfo {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(metrics)

        val widthPixels = metrics.widthPixels
        val heightPixels = metrics.heightPixels
        val densityDpi = metrics.densityDpi
        val density = metrics.density

        val screenSizeInches = calculateScreenSize(widthPixels, heightPixels, densityDpi)
        val densityCategory = getDensityCategory(densityDpi)

        return ScreenInfo(
            widthPixels = widthPixels,
            heightPixels = heightPixels,
            densityDpi = densityDpi,
            density = density,
            densityCategory = densityCategory,
            screenSizeInches = screenSizeInches
        )
    }

    /**
     * 计算屏幕尺寸（英寸）
     */
    private fun calculateScreenSize(widthPixels: Int, heightPixels: Int, densityDpi: Int): Float {
        val widthInches = widthPixels.toFloat() / densityDpi
        val heightInches = heightPixels.toFloat() / densityDpi
        return Math.sqrt((widthInches * widthInches + heightInches * heightInches).toDouble()).toFloat()
    }

    /**
     * 获取密度等级
     */
    private fun getDensityCategory(densityDpi: Int): DensityCategory {
        return when {
            densityDpi <= 120 -> DensityCategory.LDPI
            densityDpi <= 160 -> DensityCategory.MDPI
            densityDpi <= 240 -> DensityCategory.HDPI
            densityDpi <= 320 -> DensityCategory.XHDPI
            densityDpi <= 480 -> DensityCategory.XXHDPI
            else -> DensityCategory.XXXHDPI
        }
    }

    /**
     * 获取推荐的最小触控目标尺寸（dp）
     * 高分辨率屏幕需要更大的触控区域
     */
    fun getRecommendedTouchTargetSize(context: Context): Int {
        val screenInfo = getScreenInfo(context)
        return when (screenInfo.densityCategory) {
            DensityCategory.XXHDPI, DensityCategory.XXXHDPI -> 56  // 高分辨率屏幕推荐 56dp
            DensityCategory.XHDPI -> 52
            else -> 48  // Material Design 最小标准
        }
    }

    /**
     * 获取推荐的图标尺寸（dp）
     */
    fun getRecommendedIconSize(context: Context): Int {
        val screenInfo = getScreenInfo(context)
        return when (screenInfo.densityCategory) {
            DensityCategory.XXHDPI, DensityCategory.XXXHDPI -> 28
            DensityCategory.XHDPI -> 24
            else -> 24
        }
    }

    /**
     * 是否为高分辨率屏幕
     */
    fun isHighDensityScreen(context: Context): Boolean {
        val screenInfo = getScreenInfo(context)
        return screenInfo.densityCategory >= DensityCategory.XXHDPI
    }

    /**
     * 获取高分辨率屏幕的缩放因子
     * 用于调整文字和间距
     */
    fun getHighDensityScaleFactor(context: Context): Float {
        val screenInfo = getScreenInfo(context)
        return when (screenInfo.densityCategory) {
            DensityCategory.XXXHDPI -> 1.1f
            DensityCategory.XXHDPI -> 1.05f
            else -> 1.0f
        }
    }
}