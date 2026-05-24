package com.adout.ui.setup

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.adout.ui.theme.AdoutTheme

class SetupGuideActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AdoutTheme {
                SetupGuideScreen(
                    onComplete = {
                        getSharedPreferences("adout_prefs", MODE_PRIVATE)
                            .edit()
                            .putBoolean("setup_completed", true)
                            .apply()
                        finish()
                    }
                )
            }
        }
    }
}
