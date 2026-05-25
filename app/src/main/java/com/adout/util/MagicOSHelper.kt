package com.adout.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.util.Log

object MagicOSHelper {

    private const val TAG = "MagicOSHelper"

    fun isHuawei(): Boolean {
        return Build.MANUFACTURER.equals("HUAWEI", ignoreCase = true)
    }

    fun isHonor(): Boolean {
        return Build.MANUFACTURER.equals("Honor", ignoreCase = true)
    }

    fun isMagicOS(): Boolean {
        return isHuawei() || isHonor()
    }

    fun openProtectedApps(context: Context): Boolean {
        // Try Huawei system manager (HMOS devices)
        if (isHuawei()) {
            try {
                val intent = Intent().apply {
                    component = ComponentName(
                        "com.huawei.systemmanager",
                        "com.huawei.systemmanager.optimize.process.ProtectActivity"
                    )
                }
                context.startActivity(intent)
                return true
            } catch (e: Exception) {
                Log.w(TAG, "Huawei Protected Apps failed: ${e.message}")
            }
        }

        // Try generic approach for Honor MagicOS 7+ or fallback
        try {
            val intent = Intent().apply {
                component = ComponentName(
                    "com.android.settings",
                    "com.android.settings.Settings\$HighPowerApplicationsActivity"
                )
            }
            context.startActivity(intent)
            return true
        } catch (e: Exception) {
            Log.w(TAG, "Generic Protected Apps failed: ${e.message}")
        }

        // Final fallback: open app details
        return try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = android.net.Uri.parse("package:${context.packageName}")
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.w(TAG, "Fallback app settings failed: ${e.message}")
            false
        }
    }

    fun openAutoStartManager(context: Context): Boolean {
        // Try Huawei system manager (HMOS devices)
        if (isHuawei()) {
            try {
                val intent = Intent().apply {
                    component = ComponentName(
                        "com.huawei.systemmanager",
                        "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
                    )
                }
                context.startActivity(intent)
                return true
            } catch (e: Exception) {
                Log.w(TAG, "Huawei Auto-Start manager failed: ${e.message}")
            }
        }

        // Honor MagicOS 7+ may use package manager settings
        if (isHonor()) {
            try {
                val intent = Intent().apply {
                    action = "android.settings.MANAGE_DEFAULT_APPS"
                }
                context.startActivity(intent)
                return true
            } catch (e: Exception) {
                Log.w(TAG, "Honor Auto-Start manager failed: ${e.message}")
            }
        }

        // Final fallback: open app details
        return try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = android.net.Uri.parse("package:${context.packageName}")
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.w(TAG, "Fallback app settings failed: ${e.message}")
            false
        }
    }

    fun getSetupSteps(): List<SetupStep> {
        val steps = mutableListOf(
            SetupStep(
                id = "battery",
                title = "关闭电池优化",
                description = "允许 AdOut 在后台运行，防止系统关闭 VPN 保护",
                actionType = SetupActionType.BATTERY_OPTIMIZATION
            )
        )

        if (isMagicOS()) {
            steps.add(
                SetupStep(
                    id = "protected",
                    title = "设置受保护应用",
                    description = "将 AdOut 加入受保护应用列表，防止被系统清理",
                    actionType = SetupActionType.PROTECTED_APPS
                )
            )
            steps.add(
                SetupStep(
                    id = "autostart",
                    title = "开启自启动",
                    description = "允许 AdOut 开机自动启动，持续保护您的设备",
                    actionType = SetupActionType.AUTO_START
                )
            )
        }

        steps.add(
            SetupStep(
                id = "complete",
                title = "设置完成",
                description = "所有保活设置已完成，AdOut 将持续保护您的设备",
                actionType = SetupActionType.COMPLETE
            )
        )

        return steps
    }
}

data class SetupStep(
    val id: String,
    val title: String,
    val description: String,
    val actionType: SetupActionType
)

enum class SetupActionType {
    BATTERY_OPTIMIZATION,
    PROTECTED_APPS,
    AUTO_START,
    COMPLETE
}
