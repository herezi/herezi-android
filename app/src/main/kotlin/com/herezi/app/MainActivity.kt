package com.herezi.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.lifecycleScope
import com.herezi.app.auth.WebAuthFlow
import com.herezi.core.domain.SpotsRepository
import com.herezi.core.map.LocalMapConfig
import com.herezi.core.map.MapConfig
import com.herezi.core.ui.theme.HereziTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var webAuth: WebAuthFlow

    @Inject lateinit var spots: SpotsRepository

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

    /** 실행·포그라운드 복귀 때 지역 파일 확인(실행 후 첫 확인은 항상, 그 뒤로는 1시간 넘었을 때만). */
    override fun onStart() {
        super.onStart()
        lifecycleScope.launch {
            try {
                spots.refreshData(force = false)
            } catch (e: CancellationException) {
                throw e
            } catch (@Suppress("TooGenericExceptionCaught", "SwallowedException") e: Exception) {
                // 오프라인: 마지막 데이터로 동작한다.
            }
        }
    }

    override fun onResume() {
        super.onResume()
        webAuth.onResume()
    }
}
