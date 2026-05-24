package com.adout.ui

import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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

    val backgroundColor by animateColorAsState(
        targetValue = if (uiState.isVpnRunning) {
            GreenGradientStart
        } else {
            PurpleGradientStart
        },
        animationSpec = tween(durationMillis = 500),
        label = "backgroundColor"
    )

    val backgroundGradientEnd by animateColorAsState(
        targetValue = if (uiState.isVpnRunning) {
            GreenGradientEnd
        } else {
            PurpleGradientEnd
        },
        animationSpec = tween(durationMillis = 500),
        label = "backgroundGradientEnd"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = listOf(backgroundColor, backgroundGradientEnd)
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(32.dp)
        ) {
            // App Name
            Text(
                text = "Adout",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = White,
                modifier = Modifier.padding(bottom = 40.dp)
            )

            // Main Toggle Button
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .shadow(
                        elevation = if (uiState.isVpnRunning) 20.dp else 8.dp,
                        shape = CircleShape,
                        ambientColor = Color.Black.copy(alpha = 0.3f),
                        spotColor = Color.Black.copy(alpha = 0.3f)
                    )
                    .clip(CircleShape)
                    .background(WhiteAlpha20)
                    .clickable { viewModel.toggleVpn() },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .clip(CircleShape)
                        .background(WhiteAlpha30),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PowerSettingsNew,
                        contentDescription = if (uiState.isVpnRunning) "关闭保护" else "开启保护",
                        tint = White,
                        modifier = Modifier.size(48.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(30.dp))

            // Status Text
            Text(
                text = if (uiState.isVpnRunning) "保护已开启" else "保护已关闭",
                fontSize = 16.sp,
                color = WhiteAlpha80,
                modifier = Modifier.padding(bottom = 20.dp)
            )

            // Stats Row
            Row(
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Blocked Count
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "${uiState.blockedCount}",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = White
                    )
                    Text(
                        text = "今日拦截",
                        fontSize = 12.sp,
                        color = WhiteAlpha60
                    )
                }

                // Rule Count
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "${uiState.ruleCount}",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = White
                    )
                    Text(
                        text = "规则数量",
                        fontSize = 12.sp,
                        color = WhiteAlpha60
                    )
                }
            }
        }
    }
}
