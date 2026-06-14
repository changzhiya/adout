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
     * Ad SDK package/class name patterns - HIGH CONFIDENCE.
     * These are specific to known ad SDKs and rarely appear in normal apps.
     * Used by isAdClassName() and isConfidentAdActivity().
     */
    val AD_SDK_PATTERNS = listOf(
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
    )

    /**
     * Generic ad Activity name patterns - LOWER CONFIDENCE.
     * These are common ad Activity names but could appear in normal apps.
     * Only used for initial filtering (isLikelyAdActivity), NOT for confident detection.
     * Should be combined with other signals (package name, etc.) before taking action.
     */
    val GENERIC_AD_ACTIVITY_PATTERNS = listOf(
        // 通用广告 Activity 名称 (注意: SplashActivity 太通用，已移除)
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
     * Combined patterns for backward compatibility.
     * @deprecated Use AD_SDK_PATTERNS or GENERIC_AD_ACTIVITY_PATTERNS instead.
     */
    @Deprecated(
        "Use AD_SDK_PATTERNS for confident detection, GENERIC_AD_ACTIVITY_PATTERNS for initial filtering",
        ReplaceWith("AD_SDK_PATTERNS + GENERIC_AD_ACTIVITY_PATTERNS")
    )
    val AD_CLASS_PATTERNS = AD_SDK_PATTERNS + GENERIC_AD_ACTIVITY_PATTERNS

    /**
     * Skip button text patterns.
     * When a button or view with this text is found, click it.
     */
    val SKIP_TEXT_PATTERNS = listOf(
        // Basic skip patterns (must contain "跳过" or "广告")
        "跳过",
        "跳过广告",
        "跳过 ",
        "广告跳过",
        "点击跳过",
        "关闭广告",
        "广告关闭",
        "skip",
        "Skip",
        "SKIP",
        "Skip Ad",
        "SkipAds",

        // Explicit dismiss patterns
        "摇一摇跳过",
        "滑动跳过",
        "滑动关闭",
        "上滑跳过",
        "右滑跳过",
        "向左滑动跳过",
        "向上滑动跳过",
        "滑动以跳过",
        "摇动跳过",

        // Countdown skip variations (must have "跳过")
        "跳过 |",
        "跳过 | ",
        "| 跳过",
        "| 跳過",
        "跳過",
        "略过",
        "略過",

        // Ad close variants (must have "广告" or "视频")
        "关闭广告",
        "点击关闭广告",
        "关闭视频",

        // Countdown with skip (e.g., "跳过 3", "3s跳过", "跳过(3)")
        "跳过 \\d".toRegex(),
        "\\d+s?跳过".toRegex(),
        "跳过\\(\\d+\\)".toRegex(),
        "跳过 \\d+s".toRegex(),
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
     * Check if a class name matches known ad SDK patterns (HIGH CONFIDENCE).
     * This should only match patterns that are specific to ad SDKs.
     */
    fun isAdClassName(className: String): Boolean {
        val lower = className.lowercase()
        return AD_SDK_PATTERNS.any { pattern ->
            lower.contains(pattern.lowercase())
        }
    }

    /**
     * Check if a class name matches any ad-related pattern (LOWER CONFIDENCE).
     * Includes both SDK patterns and generic Activity names.
     * Use for initial filtering, not for confident detection.
     */
    fun isLikelyAdClassName(className: String): Boolean {
        val lower = className.lowercase()
        return (AD_SDK_PATTERNS + GENERIC_AD_ACTIVITY_PATTERNS).any { pattern ->
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
            when (pattern) {
                is Regex -> pattern.containsMatchIn(trimmed)
                is String -> trimmed.equals(pattern, ignoreCase = true) ||
                        trimmed.startsWith(pattern, ignoreCase = true)
                else -> false
            }
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
