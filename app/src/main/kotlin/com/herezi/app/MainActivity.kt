package com.herezi.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import com.herezi.core.map.LocalMapConfig
import com.herezi.core.map.MapConfig
import com.herezi.core.ui.theme.HereziTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            HereziTheme {
                CompositionLocalProvider(LocalMapConfig provides MapConfig(enabled = BuildConfig.MAP_ENABLED)) {
                    HereziApp()
                }
            }
        }
    }
}
