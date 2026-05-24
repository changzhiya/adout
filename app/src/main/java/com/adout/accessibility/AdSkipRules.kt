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
    )

    /**
     * Skip button text patterns.
     * When a button or view with this text is found, click it.
     */
    val SKIP_TEXT_PATTERNS = listOf(
        "跳过",
        "跳过广告",
        "跳过 ",
        "关闭",
        "关闭广告",
        "skip",
        "Skip",
        "SKIP",
        "Skip Ad",
        "关闭",
        "稍后",
        "x",
        "X",
        "✕",
        "×",
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
