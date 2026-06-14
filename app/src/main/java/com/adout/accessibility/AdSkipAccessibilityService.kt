package com.adout.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.localbroadcastmanager.content.LocalBroadcastManager

class AdSkipAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "AdSkipService"
        private const val PREFS_NAME = "adout_prefs"
        private const val KEY_AD_SKIP_ENABLED = "ad_skip_enabled"
        private const val KEY_ADS_SKIPPED_COUNT = "ads_skipped_count"

        // System packages — ignore entirely to avoid false positives
        private val SYSTEM_PACKAGES = listOf(
            "com.android.systemui",
            "com.android.launcher",
            "com.huawei.android.launcher",
            "com.huawei.systemui",
            "com.hihonor.android.launcher",
            "com.hihonor.systemui",
            "com.android.settings",
        )

        // Known ad-showing apps — any new window from these is suspicious
        private val KNOWN_AD_APP_PACKAGES = listOf(
            "com.zhixing.che",              // 智行
            "com.baidu.netdisk",             // 百度网盘
            "com.sankuai.meituan",           // 美团
            "com.MobileTicket",              // 铁路12306
            "com.xuexiang",                  // 学习通
            "com.ctrip",                     // 携程
            "com.achievo.vipshop",           // 唯品会
            "com.taobao.taobao",             // 淘宝
            "com.eg.android.AlipayGphone",   // 支付宝
            "com.jingdong.app.mall",         // 京东
            "com.sina.weibo",                // 微博
            "com.tencent.mm",                // 微信
        )

        // Safe apps — never scan or interfere, even if ad SDK Activity detected.
        // User is actively using these; accidental clicks cause poor UX.
        private val SAFE_APP_PACKAGES = listOf(
            "com.tencent.mobileqq",          // QQ
            "com.tencent.mm",                // 微信 (duplicate but explicit)
        )

        var instance: AdSkipAccessibilityService? = null
            private set

        fun isRunning(): Boolean = instance != null

        private val _adsSkippedCount = kotlinx.coroutines.flow.MutableStateFlow(0L)
        val adsSkippedCount: kotlinx.coroutines.flow.StateFlow<Long> = _adsSkippedCount
    }

    private var lastPackageName: String? = null
    private var lastClassName: String? = null
    private var lastSkipTime: Long = 0
    private val SKIP_COOLDOWN_MS = 2000L

    // Standard pass delays (unknown apps)
    private val SCAN_PASS_DELAYS = listOf(200L, 700L, 1600L)
    // Aggressive pass delays (known ad apps — need more coverage)
    private val AGGRESSIVE_SCAN_DELAYS = listOf(200L, 700L, 1600L, 3000L)

    // Monitored ad window: keep checking for delayed skip button
    private var monitoredAdPackage: String? = null
    private var monitoredAdClassName: String? = null
    private var monitoredAdTime: Long = 0
    private val MONITOR_TIMEOUT_MS = 8000L
    private val MONITOR_INTERVAL_MS = 400L
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        _adsSkippedCount.value = prefs.getLong(KEY_ADS_SKIPPED_COUNT, 0)
        Log.i(TAG, "AdSkipAccessibilityService connected, skipCount=${_adsSkippedCount.value}")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_AD_SKIP_ENABLED, true)) return

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                handleWindowStateChanged(event)
            }
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                handleWindowContentChanged(event)
            }
        }
    }

    private fun handleWindowStateChanged(event: AccessibilityEvent) {
        val packageName = event.packageName?.toString() ?: return
        val className = event.className?.toString() ?: return

        if (packageName == "com.adout") return
        if (SYSTEM_PACKAGES.any { packageName.startsWith(it) }) return
        if (SAFE_APP_PACKAGES.any { packageName.startsWith(it) }) return

        if (packageName == lastPackageName && className == lastClassName) return
        lastPackageName = packageName
        lastClassName = className

        val now = System.currentTimeMillis()
        if (now - lastSkipTime < SKIP_COOLDOWN_MS) return

        Log.d(TAG, "Window changed: $packageName / $className")

        if (!isLikelyAdActivity(packageName, className)) return

        Log.i(TAG, "Detected potential ad: $packageName / $className")
        startAdMonitoring(packageName, className)

        // Multi-pass scan: each pass is independent; once one succeeds,
        // cooldown blocks subsequent passes.
        val isKnownAdApp = KNOWN_AD_APP_PACKAGES.any { packageName.startsWith(it) }
        scheduleScanPasses(packageName, className, isKnownAdApp)
    }

    private fun scheduleScanPasses(
        packageName: String,
        className: String,
        isKnownAdApp: Boolean
    ) {
        val delays = if (isKnownAdApp) AGGRESSIVE_SCAN_DELAYS else SCAN_PASS_DELAYS
        val total = delays.size
        for ((index, delay) in delays.withIndex()) {
            mainHandler.postDelayed({
                executeScanPass(packageName, className, isKnownAdApp, index + 1, total)
            }, delay)
        }
    }

    /**
     * Each pass: accessibility tree search → coordinate swipe → back/gesture.
     */
    private fun executeScanPass(
        packageName: String,
        className: String,
        isKnownAdApp: Boolean,
        passNumber: Int,
        totalPasses: Int
    ) {
        if (System.currentTimeMillis() - lastSkipTime < SKIP_COOLDOWN_MS) return
        if (monitoredAdPackage != packageName) return

        // Check if this is an interactive/shake ad — avoid triggering it
        val isInteractiveAd = isInteractiveAd(className)

        val rootNode = rootInActiveWindow
        if (rootNode != null) {
            val currentPkg = rootNode.packageName?.toString()
            if (currentPkg != null && currentPkg != packageName) {
                rootNode.recycle()
                return
            }
            if (tryClickSkipButton(rootNode)) {
                rootNode.recycle()
                markSkipped(packageName)
                return
            }
            rootNode.recycle()
        }

        // Only swipe if not an interactive ad (swipe would trigger it)
        if (!isInteractiveAd) {
            swipeTopRightCorner()
        }

        // Back key: for confident ads, always try.
        // For known ad apps: try on later passes (overlay may intercept back)
        // For interactive ads: always try back to dismiss
        val shouldBack = isConfidentAdActivity(className) ||
                isInteractiveAd ||
                (isKnownAdApp && passNumber >= 3)
        if (shouldBack) {
            performGlobalAction(GLOBAL_ACTION_BACK)
            markSkipped(packageName)
        }
    }

    /**
     * Check if the activity is an interactive/shake ad.
     * These ads trigger on swipe/shake gestures, so we should NOT perform gestures.
     */
    private fun isInteractiveAd(className: String): Boolean {
        val lower = className.lowercase()
        return lower.contains("interactivead") ||
                lower.contains("interactionad") ||
                lower.contains("motionad") ||
                lower.contains("shakead") ||
                lower.contains("sensorad") ||
                lower.contains("gravityad") ||
                lower.contains("gyroad") ||
                lower.contains("dynamicad") ||
                lower.contains("interactivesplash") ||
                lower.contains("interactionsplash") ||
                lower.contains("dynamicsplash")
    }

    private fun markSkipped(packageName: String) {
        lastSkipTime = System.currentTimeMillis()
        sendSkipBroadcast(packageName)
        stopAdMonitoring()
    }

    /**
     * Handle content changes within an ad window.
     * This catches skip buttons that appear with a delay.
     */
    private fun handleWindowContentChanged(event: AccessibilityEvent) {
        val packageName = event.packageName?.toString() ?: return
        if (packageName == "com.adout") return
        if (packageName != monitoredAdPackage) return

        val now = System.currentTimeMillis()
        if (now - monitoredAdTime > MONITOR_TIMEOUT_MS) {
            stopAdMonitoring()
            return
        }
        if (now - lastSkipTime < SKIP_COOLDOWN_MS) return

        val rootNode = rootInActiveWindow ?: return
        if (tryClickSkipButton(rootNode)) {
            Log.i(TAG, "Clicked skip button from content change for $packageName")
            markSkipped(packageName)
        }
        rootNode.recycle()
    }

    /**
     * Start monitoring an ad window for delayed skip buttons.
     */
    private fun startAdMonitoring(packageName: String, className: String) {
        monitoredAdPackage = packageName
        monitoredAdClassName = className
        monitoredAdTime = System.currentTimeMillis()
        Log.d(TAG, "Started ad monitoring: $packageName")
    }

    private fun stopAdMonitoring() {
        monitoredAdPackage = null
        monitoredAdClassName = null
        monitoredAdTime = 0
    }

    private fun isLikelyAdActivity(packageName: String, className: String): Boolean {
        // Use isLikelyAdClassName for initial filtering (includes generic patterns)
        if (AdSkipRules.isLikelyAdClassName(className)) return true
        if (AdSkipRules.isAdSdkPackage(packageName)) {
            Log.d(TAG, "Ad SDK package detected: $packageName")
            return true
        }
        // Known ad-showing apps — their new windows are splash ads
        if (KNOWN_AD_APP_PACKAGES.any { packageName.startsWith(it) }) {
            Log.d(TAG, "Known ad app window: $packageName")
            return true
        }
        // Additional pattern: "splash" and "ad" co-occurrence (catches variants not in patterns)
        val lower = className.lowercase()
        if (lower.contains("splash") && lower.contains("ad")) return true
        return false
    }

    private fun isConfidentAdActivity(className: String): Boolean {
        return AdSkipRules.isAdClassName(className)
    }

    private fun tryClickSkipButton(root: AccessibilityNodeInfo): Boolean {
        // Strategy 1: Search by text + contentDescription via framework
        if (clickByTextOrDescription(root)) return true

        // Strategy 2: Search by resource ID
        if (clickByResourceId(root)) return true

        // Strategy 3: Aggressive full-tree scan for any skip-related content
        if (clickByFullTreeScan(root)) return true

        // Note: Removed aggressive strategies (corner nodes, small clickable nodes)
        // to avoid false positives on normal UI elements.
        // Only use coordinate swipe as last resort for WebView ads.

        return false
    }

    /**
     * Search by text + contentDescription. Check both text and description
     * since ad SDKs often set only contentDescription (ImageView).
     */
    private fun clickByTextOrDescription(root: AccessibilityNodeInfo): Boolean {
        // Search for specific skip keywords (must contain "跳过" or "广告")
        val searchKeywords = listOf("跳过", "广告")
        for (keyword in searchKeywords) {
            val nodes = root.findAccessibilityNodeInfosByText(keyword)
            for (node in nodes) {
                val nodeText = node.text?.toString() ?: ""
                val nodeDesc = node.contentDescription?.toString() ?: ""
                if (AdSkipRules.isSkipText(nodeText) || AdSkipRules.isSkipText(nodeDesc)) {
                    if (clickNodeOrParent(node)) {
                        Log.i(TAG, "Clicked by text/desc: ${nodeText.take(20)}")
                        return true
                    }
                }
            }
        }

        return false
    }

    /**
     * Search by resource ID patterns.
     */
    private fun clickByResourceId(root: AccessibilityNodeInfo): Boolean {
        val skipNodes = findNodesByResourceId(root, AdSkipRules.SKIP_RESOURCE_ID_PATTERNS)
        for (node in skipNodes) {
            val resourceId = node.viewIdResourceName ?: ""
            if (!AdSkipRules.isSkipResourceId(resourceId)) continue
            if (clickNodeOrParent(node)) return true
        }
        return false
    }

    /**
     * Aggressive full-tree recursive scan. Checks every node's text,
     * contentDescription, and viewId for skip-related content.
     * Catches cases where framework search misses due to view hierarchy depth.
     */
    private fun clickByFullTreeScan(root: AccessibilityNodeInfo): Boolean {
        return clickByFullTreeScanRecursive(root, 0)
    }

    private fun clickByFullTreeScanRecursive(node: AccessibilityNodeInfo, depth: Int): Boolean {
        if (depth > 20) return false

        val text = node.text?.toString() ?: ""
        val desc = node.contentDescription?.toString() ?: ""
        val viewId = node.viewIdResourceName ?: ""

        // Check if this node matches any skip pattern
        val matchesText = text.isNotEmpty() && AdSkipRules.isSkipText(text)
        val matchesDesc = desc.isNotEmpty() && AdSkipRules.isSkipText(desc)
        val matchesId = viewId.isNotEmpty() && AdSkipRules.isSkipResourceId(viewId)

        if (matchesText || matchesDesc || matchesId) {
            if (clickNodeOrParent(node)) return true
        }

        // Recurse children
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            if (clickByFullTreeScanRecursive(child, depth + 1)) return true
        }
        return false
    }

    private fun clickNodeOrParent(node: AccessibilityNodeInfo): Boolean {
        if (node.isClickable && node.isEnabled) {
            node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            val info = (node.text ?: node.contentDescription ?: node.viewIdResourceName ?: "unknown")
            Log.d(TAG, "Clicked node: $info")
            return true
        }
        val parent = node.parent
        if (parent != null && parent.isClickable && parent.isEnabled) {
            parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            val info = (parent.text ?: parent.contentDescription ?: parent.viewIdResourceName ?: "unknown")
            Log.d(TAG, "Clicked parent: $info")
            return true
        }
        return false
    }

    /**
     * Swipe diagonally across top-right corner.
     * WebView-rendered ads hide "跳过" text from accessibility tree;
     * coordinate gesture bypasses this by hitting the general area.
     *
     * The swipe covers a diagonal strip from (90%,3%) to (80%,20%),
     * catching most skip button placements across ad SDKs.
     */
    private fun swipeTopRightCorner() {
        try {
            val w = resources.displayMetrics.widthPixels.toFloat()
            val h = resources.displayMetrics.heightPixels.toFloat()

            // Diagonal swipe: from very corner inwards
            val path = Path().apply {
                moveTo(w * 0.92f, h * 0.03f)
                lineTo(w * 0.85f, h * 0.10f)
                lineTo(w * 0.80f, h * 0.20f)
            }
            val gesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, 150))
                .build()
            dispatchGesture(gesture, null, null)
            Log.i(TAG, "Corner swipe (92%-80%, 3%-20%)")
        } catch (e: Exception) {
            Log.w(TAG, "Corner swipe failed", e)
        }
    }

    private fun findNodesByResourceId(
        root: AccessibilityNodeInfo,
        patterns: List<String>
    ): List<AccessibilityNodeInfo> {
        val results = mutableListOf<AccessibilityNodeInfo>()
        findNodesByResourceIdRecursive(root, patterns, results, 0)
        return results
    }

    private fun findNodesByResourceIdRecursive(
        node: AccessibilityNodeInfo,
        patterns: List<String>,
        results: MutableList<AccessibilityNodeInfo>,
        depth: Int
    ) {
        if (depth > 15) return

        val viewId = node.viewIdResourceName ?: ""
        if (viewId.isNotEmpty()) {
            for (pattern in patterns) {
                if (viewId.lowercase().contains(pattern.lowercase())) {
                    results.add(node)
                    break
                }
            }
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            findNodesByResourceIdRecursive(child, patterns, results, depth + 1)
        }
    }

    private fun sendSkipBroadcast(packageName: String) {
        val newCount = _adsSkippedCount.value + 1
        _adsSkippedCount.value = newCount

        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        prefs.edit().putLong(KEY_ADS_SKIPPED_COUNT, newCount).apply()

        Log.i(TAG, "Ad skipped for $packageName, total: $newCount")
    }

    override fun onInterrupt() {
        Log.w(TAG, "AdSkipAccessibilityService interrupted")
    }

    override fun onDestroy() {
        stopAdMonitoring()
        instance = null
        super.onDestroy()
        Log.i(TAG, "AdSkipAccessibilityService destroyed")
    }
}
