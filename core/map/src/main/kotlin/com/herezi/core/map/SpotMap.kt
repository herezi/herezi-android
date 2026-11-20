package com.herezi.core.map

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.herezi.core.domain.GeoPoint
import com.herezi.core.domain.Spot
import com.naver.maps.geometry.LatLng
import com.naver.maps.map.CameraPosition
import com.naver.maps.map.compose.ExperimentalNaverMapApi
import com.naver.maps.map.compose.Marker
import com.naver.maps.map.compose.MarkerState
import com.naver.maps.map.compose.NaverMap
import com.naver.maps.map.compose.rememberCameraPositionState

private const val DEFAULT_ZOOM = 14.0

/**
 * 볼거리 핀 지도. 네이버 지도 SDK 를 감싼 유일한 진입점이다.
 * 클러스터링은 M1-c 에서 추가한다.
 */
@OptIn(ExperimentalNaverMapApi::class)
@Composable
fun SpotMap(spots: List<Spot>, center: GeoPoint, onSpotClick: (String) -> Unit, modifier: Modifier = Modifier) {
    if (!LocalMapConfig.current.enabled) {
        MapUnavailable(modifier)
        return
    }
    val cameraState =
        rememberCameraPositionState {
            position = CameraPosition(LatLng(center.lat, center.lng), DEFAULT_ZOOM)
        }
    NaverMap(modifier = modifier.fillMaxSize(), cameraPositionState = cameraState) {
        spots.forEach { spot ->
            Marker(
                state = MarkerState(position = LatLng(spot.location.lat, spot.location.lng)),
                captionText = spot.title,
                onClick = {
                    onSpotClick(spot.id)
                    true
                },
            )
        }
    }
}

@Composable
private fun MapUnavailable(modifier: Modifier) {
    Box(modifier = modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(
            text = "지도 키가 설정되지 않았습니다.\n" + "local.properties 에 NAVER_MAP_CLIENT_ID 를 넣어 주세요.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
