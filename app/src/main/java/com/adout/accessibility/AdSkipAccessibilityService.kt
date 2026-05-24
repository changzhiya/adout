package com.adout.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.Intent
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

        var instance: AdSkipAccessibilityService? = null
            private set

        fun isRunning(): Boolean = instance != null

        // Observable skip count for UI
        private val _adsSkippedCount = kotlinx.coroutines.flow.MutableStateFlow(0L)
        val adsSkippedCount: kotlinx.coroutines.flow.StateFlow<Long> = _adsSkippedCount
    }

    private var lastPackageName: String? = null
    private var lastClassName: String? = null
    private var lastSkipTime: Long = 0
    private val SKIP_COOLDOWN_MS = 2000L

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        // Load persisted skip count
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
        }
    }

    private fun handleWindowStateChanged(event: AccessibilityEvent) {
        val packageName = event.packageName?.toString() ?: return
        val className = event.className?.toString() ?: return

        if (packageName == "com.adout") return

        if (packageName == lastPackageName && className == lastClassName) return
        lastPackageName = packageName
        lastClassName = className

        val now = System.currentTimeMillis()
        if (now - lastSkipTime < SKIP_COOLDOWN_MS) return

        Log.d(TAG, "Window changed: $packageName / $className")

        if (!isLikelyAdActivity(packageName, className)) return

        Log.i(TAG, "Detected potential ad: $packageName / $className")

        val rootNode = rootInActiveWindow ?: return

        if (tryClickSkipButton(rootNode)) {
            Log.i(TAG, "Clicked skip button for $packageName")
            lastSkipTime = now
            sendSkipBroadcast(packageName)
        } else if (isConfidentAdActivity(className)) {
            Log.i(TAG, "No skip button found, pressing back for $packageName")
            performGlobalAction(GLOBAL_ACTION_BACK)
            lastSkipTime = now
            sendSkipBroadcast(packageName)
        }

        rootNode.recycle()
    }

    private fun isLikelyAdActivity(packageName: String, className: String): Boolean {
        // Strategy 1: Check if class name matches ad SDK patterns (most reliable)
        if (AdSkipRules.isAdClassName(className)) return true

        // Strategy 2: Check if package belongs to known ad SDK
        if (AdSkipRules.isAdSdkPackage(packageName)) {
            Log.d(TAG, "Ad SDK package detected: $packageName")
            return true
        }

        // Strategy 3: Check for common ad Activity naming patterns
        val lower = className.lowercase()
        if (lower.contains("splash") && lower.contains("ad")) return true
        if (lower.contains("adsplash") || lower.contains("splashad")) return true
        if (lower.contains("openscreen") || lower.contains("fullscreenad")) return true
        if (lower.contains("interstitial") || lower.contains("rewardvideo")) return true

        return false
    }

    private fun isConfidentAdActivity(className: String): Boolean {
        val lower = className.lowercase()
        return lower.contains("adsplashactivity") ||
               lower.contains("splashadactivity") ||
               lower.contains("openscreenadactivity") ||
               lower.contains("fullscreenadactivity") ||
               lower.contains("interstitialadactivity") ||
               lower.contains("rewardvideoactivity") ||
               AdSkipRules.isAdClassName(className)
    }

    private fun tryClickSkipButton(root: AccessibilityNodeInfo): Boolean {
        // Strategy 1: Find by text using isSkipText helper
        for (text in AdSkipRules.SKIP_TEXT_PATTERNS) {
            val nodes = root.findAccessibilityNodeInfosByText(text)
            for (node in nodes) {
                // Validate the text actually matches skip patterns
                val nodeText = node.text?.toString() ?: ""
                if (!AdSkipRules.isSkipText(nodeText)) continue

                if (node.isClickable && node.isEnabled) {
                    node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    Log.d(TAG, "Clicked skip by text: '$nodeText'")
                    return true
                }
                val parent = node.parent
                if (parent != null && parent.isClickable && parent.isEnabled) {
                    parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    Log.d(TAG, "Clicked skip parent by text: '$nodeText'")
                    return true
                }
            }
        }

        // Strategy 2: Find by resource ID using isSkipResourceId helper
        val skipNodes = findNodesByResourceId(root, AdSkipRules.SKIP_RESOURCE_ID_PATTERNS)
        for (node in skipNodes) {
            // Validate the resource ID actually matches skip patterns
            val resourceId = node.viewIdResourceName ?: ""
            if (!AdSkipRules.isSkipResourceId(resourceId)) continue

            if (node.isClickable && node.isEnabled) {
                node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                Log.d(TAG, "Clicked skip by resource ID: $resourceId")
                return true
            }
            val parent = node.parent
            if (parent != null && parent.isClickable && parent.isEnabled) {
                parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                Log.d(TAG, "Clicked skip parent by resource ID: $resourceId")
                return true
            }
        }

        // Strategy 3: Find countdown text and look for nearby clickable
        if (hasCountdownText(root)) {
            Log.d(TAG, "Found countdown text, looking for skip button nearby")
            val clickableNodes = findClickableNodesInCorner(root)
            for (node in clickableNodes) {
                node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                Log.d(TAG, "Clicked corner clickable node")
                return true
            }
        }

        return false
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

    private fun hasCountdownText(root: AccessibilityNodeInfo): Boolean {
        return hasCountdownTextRecursive(root, 0)
    }

    private fun hasCountdownTextRecursive(node: AccessibilityNodeInfo, depth: Int): Boolean {
        if (depth > 15) return false

        val text = node.text?.toString() ?: ""
        if (text.isNotEmpty() && AdSkipRules.isCountdownText(text)) {
            return true
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            if (hasCountdownTextRecursive(child, depth + 1)) return true
        }
        return false
    }

    private fun findClickableNodesInCorner(root: AccessibilityNodeInfo): List<AccessibilityNodeInfo> {
        val results = mutableListOf<AccessibilityNodeInfo>()
        findClickableInCornerRecursive(root, results, 0)
        return results
    }

    private fun findClickableInCornerRecursive(
        node: AccessibilityNodeInfo,
        results: MutableList<AccessibilityNodeInfo>,
        depth: Int
    ) {
        if (depth > 10) return

        if (node.isClickable && node.isEnabled) {
            val rect = android.graphics.Rect()
            node.getBoundsInScreen(rect)
            val screenWidth = resources.displayMetrics.widthPixels
            val screenHeight = resources.displayMetrics.heightPixels
            if (rect.centerX() > screenWidth * 0.7 && rect.centerY() < screenHeight * 0.3) {
                val width = rect.width()
                val height = rect.height()
                if (width < 300 && height < 200) {
                    results.add(node)
                }
            }
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            findClickableInCornerRecursive(child, results, depth + 1)
        }
    }

    private fun sendSkipBroadcast(packageName: String) {
        // Increment skip count
        val newCount = _adsSkippedCount.value + 1
        _adsSkippedCount.value = newCount

        // Persist count
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        prefs.edit().putLong(KEY_ADS_SKIPPED_COUNT, newCount).apply()

        Log.i(TAG, "Ad skipped for $packageName, total: $newCount")
    }

    override fun onInterrupt() {
        Log.w(TAG, "AdSkipAccessibilityService interrupted")
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
        Log.i(TAG, "AdSkipAccessibilityService destroyed")
    }
}
