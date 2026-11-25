package com.herezi.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import com.herezi.app.auth.WebAuthFlow
import com.herezi.core.map.LocalMapConfig
import com.herezi.core.map.MapConfig
import com.herezi.core.ui.theme.HereziTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var webAuth: WebAuthFlow

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        intent?.data?.let(webAuth::deliver)
        setContent {
            HereziTheme {
                CompositionLocalProvider(LocalMapConfig provides MapConfig(enabled = BuildConfig.MAP_ENABLED)) {
                    HereziApp()
                }
            }
        }
    }

    /** Apple 웹 로그인 복귀(herezi://auth-callback). singleTask 라 여기로 온다. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.data?.let(webAuth::deliver)
    }

    override fun onResume() {
        super.onResume()
        webAuth.onResume()
    }
}
