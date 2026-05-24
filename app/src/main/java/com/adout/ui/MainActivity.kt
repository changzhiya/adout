package com.adout.ui

import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.adout.ui.theme.*
import com.adout.vpn.AdBlockVpnService

class MainActivity : ComponentActivity() {

    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            startVpnService()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AdoutTheme {
                MainScreen()
            }
        }
    }

    private fun checkVpnPermission(): Boolean {
        val intent = VpnService.prepare(this)
        if (intent != null) {
            vpnPermissionLauncher.launch(intent)
            return false
        }
        return true
    }

    private fun startVpnService() {
        val intent = Intent(this, AdBlockVpnService::class.java)
        intent.action = "START"
        startService(intent)
    }
}

@Composable
fun MainScreen(
    viewModel: MainViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Animated gradient colors - optimized duration for responsive feel
    val backgroundColor by animateColorAsState(
        targetValue = if (uiState.isVpnRunning) GreenGradientStart else PurpleGradientStart,
        animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
        label = "backgroundColor"
    )

    val backgroundMiddle by animateColorAsState(
        targetValue = if (uiState.isVpnRunning) GreenGradientMiddle else PurpleGradientMiddle,
        animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
        label = "backgroundMiddle"
    )

    val backgroundGradientEnd by animateColorAsState(
        targetValue = if (uiState.isVpnRunning) GreenGradientEnd else PurpleGradientEnd,
        animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
        label = "backgroundGradientEnd"
    )

    // Pulse animation for active state
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (uiState.isVpnRunning) 1.05f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = if (uiState.isVpnRunning) 0.6f else 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )

    // Get system bar insets for safe area handling
    val systemBarsPadding = WindowInsets.systemBars.asPaddingValues()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(backgroundColor, backgroundMiddle, backgroundGradientEnd)
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = 32.dp,
                    end = 32.dp,
                    top = systemBarsPadding.calculateTopPadding(),
                    bottom = systemBarsPadding.calculateBottomPadding()
                )
        ) {
            // Top section - App Title
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 32.dp)
            ) {
                Text(
                    text = "Adout",
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                    color = White,
                    letterSpacing = 2.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "广告拦截 · 纯净浏览",
                    fontSize = 14.sp,
                    color = TextSecondary,
                    letterSpacing = 1.sp
                )
            }

            // Center section - Toggle Button
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Glow effect behind button
                Box(
                    modifier = Modifier
                        .size(160.dp)
                        .offset(y = (-20).dp)
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    (if (uiState.isVpnRunning) GreenGlow else PurpleGlow).copy(alpha = glowAlpha),
                                    Color.Transparent
                                )
                            )
                        )
                )

                // Main Toggle Button
                Box(
                    modifier = Modifier
                        .size(130.dp)
                        .shadow(
                            elevation = if (uiState.isVpnRunning) 24.dp else 12.dp,
                            shape = CircleShape,
                            ambientColor = Color.Black.copy(alpha = 0.4f),
                            spotColor = Color.Black.copy(alpha = 0.4f)
                        )
                        .clip(CircleShape)
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    WhiteAlpha30,
                                    WhiteAlpha10
                                )
                            )
                        )
                        .border(
                            width = 2.dp,
                            color = WhiteAlpha40,
                            shape = CircleShape
                        )
                        .clickable { viewModel.toggleVpn() },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(108.dp)
                            .scale(pulseScale)
                            .clip(CircleShape)
                            .background(
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        WhiteAlpha40,
                                        WhiteAlpha20
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PowerSettingsNew,
                            contentDescription = if (uiState.isVpnRunning)
                                "广告拦截已开启，点击关闭保护"
                            else
                                "广告拦截已关闭，点击开启保护",
                            tint = White,
                            modifier = Modifier.size(56.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Status Text with animation
                Text(
                    text = if (uiState.isVpnRunning) "保护已开启" else "保护已关闭",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    color = WhiteAlpha90,
                    letterSpacing = 1.sp,
                    modifier = Modifier.semantics {
                        contentDescription = if (uiState.isVpnRunning)
                            "广告拦截保护状态：已开启"
                        else
                            "广告拦截保护状态：已关闭"
                    }
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = if (uiState.isVpnRunning) "正在拦截广告请求" else "点击开启广告拦截",
                    fontSize = 13.sp,
                    color = TextTertiary
                )
            }

            // Bottom section - Stats Card with Glass Morphism
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(
                        elevation = 16.dp,
                        shape = RoundedCornerShape(24.dp),
                        ambientColor = Color.Black.copy(alpha = 0.2f),
                        spotColor = Color.Black.copy(alpha = 0.2f)
                    ),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.Transparent
                )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    GlassWhite,
                                    GlassWhiteLight
                                )
                            )
                        )
                        .border(
                            width = 1.dp,
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    GlassBorder,
                                    GlassBorder.copy(alpha = 0.5f)
                                )
                            ),
                            shape = RoundedCornerShape(24.dp)
                        )
                        .padding(24.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        // Blocked Count
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = formatCount(uiState.blockedCount),
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Bold,
                                color = White
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "今日拦截",
                                fontSize = 13.sp,
                                color = TextSecondary
                            )
                        }

                        // Divider
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(48.dp)
                                .background(WhiteAlpha20)
                        )

                        // Rule Count
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = formatCount(uiState.ruleCount.toLong()),
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Bold,
                                color = White
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "过滤规则",
                                fontSize = 13.sp,
                                color = TextSecondary
                            )
                        }
                    }
                }
            }

            // Version info
            Text(
                text = "v1.0.0",
                fontSize = 12.sp,
                color = TextTertiary,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }
    }
}

// Helper function to format large numbers
private fun formatCount(count: Long): String {
    return when {
        count >= 1000000 -> String.format("%.1fM", count / 1000000.0)
        count >= 1000 -> String.format("%.1fK", count / 1000.0)
        else -> count.toString()
    }
}
