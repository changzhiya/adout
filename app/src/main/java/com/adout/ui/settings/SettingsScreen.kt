package com.adout.ui.settings

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adout.ui.theme.*
import com.adout.ui.setup.SetupGuideActivity
import com.adout.util.BatteryOptimizationHelper
import com.adout.util.MagicOSHelper
import com.adout.vpn.AdBlockVpnService

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember {
        context.getSharedPreferences("adout_prefs", android.content.Context.MODE_PRIVATE)
    }

    // Refresh state when screen is shown
    var isBatteryExempt by remember {
        mutableStateOf(BatteryOptimizationHelper.isIgnoringBatteryOptimizations(context))
    }
    val isVpnRunning = remember { mutableStateOf(AdBlockVpnService.isRunning) }
    val lastCheck = remember { mutableStateOf(prefs.getLong("last_watchdog_check", 0)) }
    val lastRestart = remember { mutableStateOf(prefs.getLong("last_vpn_restart", 0)) }

    LaunchedEffect(Unit) {
        isBatteryExempt = BatteryOptimizationHelper.isIgnoringBatteryOptimizations(context)
        isVpnRunning.value = AdBlockVpnService.isRunning
        lastCheck.value = prefs.getLong("last_watchdog_check", 0)
        lastRestart.value = prefs.getLong("last_vpn_restart", 0)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(PurpleGradientStart, PurpleGradientMiddle, PurpleGradientEnd)
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
        ) {
            // Top bar
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 32.dp, bottom = 24.dp)
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.Default.ArrowBack,
                        contentDescription = "返回",
                        tint = White
                    )
                }
                Text(
                    text = "保活设置",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = White
                )
            }

            // Keep-alive status section
            Text(
                text = "保活状态",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = TextTertiary,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            StatusCard {
                StatusItem(
                    icon = Icons.Default.BatteryStd,
                    title = "电池优化",
                    status = if (isBatteryExempt) "已豁免" else "未豁免",
                    isGood = isBatteryExempt,
                    actionText = if (!isBatteryExempt) "修复" else null,
                    onAction = {
                        BatteryOptimizationHelper.requestIgnoreBatteryOptimizations(context)
                    }
                )

                if (MagicOSHelper.isMagicOS()) {
                    StatusItem(
                        icon = Icons.Default.Shield,
                        title = "受保护应用",
                        status = "需手动检查",
                        isGood = null,
                        actionText = "设置",
                        onAction = { MagicOSHelper.openProtectedApps(context) }
                    )

                    StatusItem(
                        icon = Icons.Default.PlayArrow,
                        title = "自启动",
                        status = "需手动检查",
                        isGood = null,
                        actionText = "设置",
                        onAction = { MagicOSHelper.openAutoStartManager(context) }
                    )
                }

                StatusItem(
                    icon = Icons.Default.VpnKey,
                    title = "Always-On VPN",
                    status = "需在系统设置中开启",
                    isGood = null,
                    actionText = "引导",
                    onAction = {
                        try {
                            val intent = Intent("android.settings.VPN_SETTINGS")
                            context.startActivity(intent)
                        } catch (_: Exception) {}
                    }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // VPN status section
            Text(
                text = "VPN 状态",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = TextTertiary,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            StatusCard {
                StatusItem(
                    icon = Icons.Default.VpnKey,
                    title = "当前状态",
                    status = if (isVpnRunning.value) "运行中" else "已停止",
                    isGood = isVpnRunning.value
                )

                StatusItem(
                    icon = Icons.Default.Update,
                    title = "上次检查",
                    status = if (lastCheck.value > 0) formatTime(lastCheck.value) else "尚未检查",
                    isGood = null
                )

                StatusItem(
                    icon = Icons.Default.RestartAlt,
                    title = "上次自动重启",
                    status = if (lastRestart.value > 0) formatTime(lastRestart.value) else "未发生",
                    isGood = null
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Actions
            Text(
                text = "操作",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = TextTertiary,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            StatusCard {
                ActionItem(
                    icon = Icons.Default.Assignment,
                    title = "重新运行设置引导",
                    onClick = {
                        context.startActivity(Intent(context, SetupGuideActivity::class.java))
                    }
                )
            }
        }
    }
}

@Composable
private fun StatusCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = GlassWhite)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            content()
        }
    }
}

@Composable
private fun StatusItem(
    icon: ImageVector,
    title: String,
    status: String,
    isGood: Boolean?,
    actionText: String? = null,
    onAction: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(WhiteAlpha20),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = White, modifier = Modifier.size(20.dp))
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, color = White, fontWeight = FontWeight.Medium)
            Text(
                status,
                fontSize = 13.sp,
                color = when (isGood) {
                    true -> GreenGradientEnd
                    false -> Color(0xFFFF6B6B)
                    null -> TextTertiary
                }
            )
        }

        if (actionText != null && onAction != null) {
            TextButton(onClick = onAction) {
                Text(actionText, color = White, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun ActionItem(icon: ImageVector, title: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(WhiteAlpha20),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = White, modifier = Modifier.size(20.dp))
        }

        Spacer(modifier = Modifier.width(12.dp))

        Text(title, fontSize = 15.sp, color = White, fontWeight = FontWeight.Medium)

        Spacer(modifier = Modifier.weight(1f))

        Icon(
            Icons.Default.ChevronRight,
            contentDescription = null,
            tint = TextTertiary
        )
    }
}

private fun formatTime(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    return when {
        diff < 60_000 -> "刚刚"
        diff < 3_600_000 -> "${diff / 60_000} 分钟前"
        diff < 86_400_000 -> "${diff / 3_600_000} 小时前"
        else -> "${diff / 86_400_000} 天前"
    }
}
