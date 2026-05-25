package com.adout.accessibility

/**
 * Rules for detecting ad Activities and skip buttons.
 * Uses multiple detection strategies:
 * 1. Ad SDK class name patterns (most reliable)
 * 2. Skip button text patterns
 * 3. Package name patterns
 */
object AdSkipRules {

    /**
     * Ad SDK class name patterns.
     * These are the most reliable indicators of ad Activities.
     * Checked against the root view's class name.
     */
    val AD_CLASS_PATTERNS = listOf(
        // 穿山甲/CSJ (ByteDance)
        "com.bytedance.sdk.openadsdk",
        "com.ss.android.download",
        "com.bytedance.frameworks.core.download",
        "com.pangolin.sdk",

        // 广点通/GDT (Tencent)
        "com.qq.e.ads",
        "com.qq.e.comm",
        "com.tencent.gdt",

        // 百度广告
        "com.baidu.mobads",
        "com.baidu.ad",

        // 快手广告
        "com.kwad.sdk",
        "com.yxcorp.gifshow.ad",

        // 京东广告
        "com.jd.ad",

        // 通用广告 Activity 名称
        "SplashActivity",
        "AdActivity",
        "AdSplashActivity",
        "SplashAdActivity",
        "LaunchAdActivity",
        "OpenScreenAdActivity",
        "FullScreenAdActivity",
        "InterstitialAdActivity",
        "RewardVideoActivity",
        "NativeExpressActivity",

        // 互动/摇一摇广告 Activity 名称
        "InteractiveAdActivity",
        "InteractionAdActivity",
        "MotionAdActivity",
        "ShakeAdActivity",
        "SensorAdActivity",
        "GravityAdActivity",
        "GyroAdActivity",
        "InteractiveFloatActivity",
        "InteractiveSplashActivity",
        "InteractionSplashActivity",
        "DynamicAdActivity",
        "DynamicSplashActivity",
    )

    /**
     * Skip button text patterns.
     * When a button or view with this text is found, click it.
     */
    val SKIP_TEXT_PATTERNS = listOf(
        "跳过",
        "跳过广告",
        "跳过 ",
        "广告跳过",
        "点击跳过",
        "关闭",
        "关闭广告",
        "skip",
        "Skip",
        "SKIP",
        "Skip Ad",
        "SkipAds",
        "稍后",
        "知道了",
        "忽略",
        "不再关注",
        "✕",
        "×",

        // Shake/Interactive ad dismiss patterns
        "摇一摇跳过",
        "点击跳过",
        "点击跳转",
        "点击查看",
        "点击打开",
        "滑动跳过",
        "滑动关闭",
        "上滑跳过",
        "右滑跳过",
        "向左滑动跳过",
        "向上滑动跳过",
        "滑动以跳过",
        "摇动跳过",
        "摇一摇",
        "摇一摇进入",
        "摇一摇查看",
        "转动跳过",
        "倾斜跳过",
        "翻转跳过",

        // Countdown skip variations
        "跳过 |",
        "跳过 | ",
        "| 跳过",
        "| 跳過",
        "跳過",
        "略过",
        "略過",

        // Ad close variants
        "关闭广告",
        "X 关闭",
        "点击关闭",
        "点击关闭广告",
        "关闭视频",
        "关闭详情",
    )

    /**
     * Skip button resource ID patterns.
     * Some ad SDKs use consistent resource IDs for skip buttons.
     */
    val SKIP_RESOURCE_ID_PATTERNS = listOf(
        "skip_btn",
        "skip_button",
        "close_btn",
        "close_button",
        "btn_skip",
        "btn_close",
        "iv_close",
        "iv_skip",
        "countdown",
        "skip_countdown",
        "tt_splash_skip_btn",
        "tt_skip_btn",
        "gdt_splash_skip",
        "btn_skip_ad",

        // Interactive/shake ad resource IDs
        "interactive_skip",
        "interaction_skip",
        "motion_skip",
        "shake_skip",
        "sensor_skip",
        "gyro_skip",
        "dynamic_close",
        "iv_interaction_skip",
        "btn_interactive_close",
        "splash_interactive_close",
        "floating_close",
        "float_close",
        "activity_close",
        "interact_close",
        "ad_close",
        "ad_close_btn",
        "splash_close",
        "splash_ad_close",
        "dialog_close",
        "dialog_close_btn",
        "iv_ad_close",
        "img_close",
        "img_skip",
        "skip_tv",
        "tv_skip",
    )

    /**
     * Countdown text patterns.
     * Ad countdowns usually contain numbers and "秒" or "s".
     * Used to confirm this is an ad page.
     */
    val COUNTDOWN_PATTERNS = listOf(
        "秒后跳过",
        "秒跳过",
        "s后跳过",
        "s跳过",
        "秒后关闭",
        "s后关闭",
        "跳过 ",
        "跳过\t",
        "跳過 ",
        // Numeric countdown like "3" "2" "1" near skip context
    )

    /**
     * Package names of known ad SDKs.
     * Used as a secondary check.
     */
    val AD_PACKAGE_PREFIXES = listOf(
        "com.bytedance.sdk",
        "com.pangolin",
        "com.ss.android.download",
        "com.qq.e",
        "com.tencent.gdt",
        "com.baidu.mobads",
        "com.kwad.sdk",
        "com.jd.ad",
        "com.unity3d.ads",
        "com.applovin",
        "com.ironsource",
        "com.mintegral",
        "com.vungle",
        "com.chartboost",
        "com.tapjoy",
        "com.adcolony",
        "com.inmobi",
        "com.moat",
        "com.crittercism",
        "com.flurry",
        "com.millennialmedia",
        "com.supersonicads",
        "com.facebook.ads",
        "com.google.ads",
        "com.google.android.gms.ads",
    )

    /**
     * Check if a class name matches known ad patterns.
     */
    fun isAdClassName(className: String): Boolean {
        val lower = className.lowercase()
        return AD_CLASS_PATTERNS.any { pattern ->
            lower.contains(pattern.lowercase())
        }
    }

    /**
     * Check if text matches a skip button pattern.
     */
    fun isSkipText(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return false
        return SKIP_TEXT_PATTERNS.any { pattern ->
            trimmed.equals(pattern, ignoreCase = true) ||
            trimmed.startsWith(pattern, ignoreCase = true)
        }
    }

    /**
     * Check if a resource ID matches skip button patterns.
     */
    fun isSkipResourceId(resourceId: String): Boolean {
        val lower = resourceId.lowercase()
        return SKIP_RESOURCE_ID_PATTERNS.any { pattern ->
            lower.contains(pattern.lowercase())
        }
    }

    /**
     * Check if text looks like an ad countdown.
     */
    fun isCountdownText(text: String): Boolean {
        return COUNTDOWN_PATTERNS.any { pattern ->
            text.contains(pattern, ignoreCase = true)
        }
    }

    /**
     * Check if a package belongs to a known ad SDK.
     */
    fun isAdSdkPackage(packageName: String): Boolean {
        return AD_PACKAGE_PREFIXES.any { prefix ->
            packageName.startsWith(prefix, ignoreCase = true)
        }
    }
}
