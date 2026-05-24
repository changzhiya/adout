package com.adout.ui.setup

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adout.ui.theme.*
import com.adout.util.MagicOSHelper
import com.adout.util.SetupActionType
import com.adout.util.SetupStep

@Composable
fun SetupGuideScreen(onComplete: () -> Unit) {
    val context = LocalContext.current
    val steps = remember { MagicOSHelper.getSetupSteps() }
    var currentStep by remember { mutableIntStateOf(0) }

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
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 48.dp)
            ) {
                Text(
                    text = "AdOut 初始设置",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = White
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "完成以下步骤，确保广告拦截持续运行",
                    fontSize = 14.sp,
                    color = TextSecondary
                )
            }

            // Step content
            AnimatedContent(
                targetState = currentStep,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "stepContent"
            ) { stepIndex ->
                val s = steps[stepIndex]
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(vertical = 32.dp)
                ) {
                    // Step icon
                    Box(
                        modifier = Modifier
                            .size(96.dp)
                            .clip(CircleShape)
                            .background(WhiteAlpha20),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = getStepIcon(s.actionType),
                            contentDescription = null,
                            tint = White,
                            modifier = Modifier.size(48.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        text = s.title,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = White
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = s.description,
                        fontSize = 15.sp,
                        color = TextSecondary,
                        textAlign = TextAlign.Center,
                        lineHeight = 22.sp
                    )

                    Spacer(modifier = Modifier.height(32.dp))

                    // Action button
                    if (s.actionType != SetupActionType.COMPLETE) {
                        Button(
                            onClick = { executeStep(context, s) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = White
                            ),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                        ) {
                            Text(
                                text = getStepButtonText(s.actionType),
                                color = PurpleGradientMiddle,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                    }
                }
            }

            // Bottom navigation
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Step indicators
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    steps.forEachIndexed { index, _ ->
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(
                                    if (index == currentStep) White
                                    else WhiteAlpha40
                                )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Next/Done button
                Button(
                    onClick = {
                        if (currentStep < steps.size - 1) {
                            currentStep++
                        } else {
                            onComplete()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = WhiteAlpha30
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    Text(
                        text = if (currentStep < steps.size - 1) "下一步" else "开始使用",
                        color = White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                TextButton(onClick = onComplete) {
                    Text("跳过设置", color = TextTertiary, fontSize = 13.sp)
                }
            }
        }
    }
}

private fun getStepIcon(type: SetupActionType): ImageVector {
    return when (type) {
        SetupActionType.BATTERY_OPTIMIZATION -> Icons.Default.BatteryStd
        SetupActionType.PROTECTED_APPS -> Icons.Default.Shield
        SetupActionType.AUTO_START -> Icons.Default.PlayArrow
        SetupActionType.COMPLETE -> Icons.Default.CheckCircle
    }
}

private fun getStepButtonText(type: SetupActionType): String {
    return when (type) {
        SetupActionType.BATTERY_OPTIMIZATION -> "去关闭电池优化"
        SetupActionType.PROTECTED_APPS -> "去设置受保护应用"
        SetupActionType.AUTO_START -> "去开启自启动"
        SetupActionType.COMPLETE -> "完成"
    }
}

private fun executeStep(context: android.content.Context, step: SetupStep) {
    when (step.actionType) {
        SetupActionType.BATTERY_OPTIMIZATION -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                try {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                    context.startActivity(intent)
                } catch (e: Exception) {
                    val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                    context.startActivity(intent)
                }
            }
        }
        SetupActionType.PROTECTED_APPS -> {
            if (!MagicOSHelper.openProtectedApps(context)) {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
                context.startActivity(intent)
            }
        }
        SetupActionType.AUTO_START -> {
            if (!MagicOSHelper.openAutoStartManager(context)) {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
                context.startActivity(intent)
            }
        }
        SetupActionType.COMPLETE -> { /* no-op */ }
    }
}
