package com.adout.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

object MagicOSHelper {

    private const val TAG = "MagicOSHelper"

    fun isMagicOS(): Boolean {
        return Build.MANUFACTURER.equals("Honor", ignoreCase = true) ||
               Build.MANUFACTURER.equals("HUAWEI", ignoreCase = true)
    }

    fun openProtectedApps(context: Context): Boolean {
        return try {
            val intent = Intent().apply {
                component = ComponentName(
                    "com.huawei.systemmanager",
                    "com.huawei.systemmanager.optimize.process.ProtectActivity"
                )
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.w(TAG, "Failed to open Protected Apps: ${e.message}")
            false
        }
    }

    fun openAutoStartManager(context: Context): Boolean {
        return try {
            val intent = Intent().apply {
                component = ComponentName(
                    "com.huawei.systemmanager",
                    "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
                )
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.w(TAG, "Failed to open Auto-Start manager: ${e.message}")
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
