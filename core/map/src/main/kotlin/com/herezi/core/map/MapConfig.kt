package com.herezi.core.map

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * 지도 사용 가능 여부. :app 이 Client ID 유무로 채운다.
 * 키가 없으면 지도 대신 안내를 보여준다.
 */
data class MapConfig(val enabled: Boolean)

val LocalMapConfig = staticCompositionLocalOf { MapConfig(enabled = false) }
