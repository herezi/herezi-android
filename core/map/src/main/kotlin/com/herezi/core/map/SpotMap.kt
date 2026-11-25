package com.herezi.core.map

import android.graphics.PointF
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.herezi.core.domain.Category
import com.herezi.core.domain.GeoBounds
import com.herezi.core.domain.GeoPoint
import com.herezi.core.domain.Spot
import com.naver.maps.geometry.LatLng
import com.naver.maps.map.CameraAnimation
import com.naver.maps.map.CameraPosition
import com.naver.maps.map.CameraUpdate
import com.naver.maps.map.NaverMap as SdkMap
import com.naver.maps.map.clustering.ClusterMarkerUpdater
import com.naver.maps.map.clustering.Clusterer
import com.naver.maps.map.clustering.ClusteringKey
import com.naver.maps.map.clustering.LeafMarkerUpdater
import com.naver.maps.map.compose.DisposableMapEffect
import com.naver.maps.map.compose.ExperimentalNaverMapApi
import com.naver.maps.map.compose.LocationOverlay
import com.naver.maps.map.compose.MapUiSettings
import com.naver.maps.map.compose.Marker
import com.naver.maps.map.compose.MarkerState
import com.naver.maps.map.compose.NaverMap
import com.naver.maps.map.compose.rememberCameraPositionState
import com.naver.maps.map.overlay.Overlay

/** 첫 진입 줌: 반경 3km 가 보이는 정도 (S2). */
const val DEFAULT_MAP_ZOOM = 14.0

/** 화면이 지도를 옮기라고 할 때. [id] 가 바뀔 때마다 한 번 움직인다. */
data class MapCameraRequest(val target: GeoPoint, val id: Long)

/**
 * 볼거리 지도 (S2). 네이버 지도 SDK 를 쓰는 유일한 진입점이다.
 *
 * - 핀: 카테고리 색 원(꼬리 없음), 묶음은 SDK [Clusterer] 로 잉크 원 + 숫자. 클러스터를 누르면 그 자리로 확대.
 * - [selectedId] 핀은 묶음에서 빼서 크게(44, 잉크 테두리, 제목 라벨) 맨 위에 그린다.
 * - 카메라가 멈출 때마다 [onCameraIdle] 에 보이는 영역(콘텐츠 패딩 제외)과 사용자 제스처였는지를 알린다.
 *   지도가 준비되면 첫 영역도 한 번 알린다(byUser=false).
 * - 키가 없으면 회색 바탕 "지도를 불러오지 못했어요".
 */
@OptIn(ExperimentalNaverMapApi::class)
@Composable
fun SpotMap(
    spots: List<Spot>,
    selectedId: String?,
    initialCenter: GeoPoint,
    myLocation: GeoPoint?,
    cameraRequest: MapCameraRequest?,
    pinStyle: PinStyle,
    onCameraIdle: (bounds: GeoBounds, byUser: Boolean) -> Unit,
    onSpotClick: (String) -> Unit,
    onMapClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    if (!LocalMapConfig.current.enabled) {
        MapUnavailable(modifier)
        return
    }
    val density = LocalDensity.current
    val painters = Category.entries.associateWith { rememberVectorPainter(pinStyle.icon(it)) }
    val images = remember(density, pinStyle) { MarkerImages(density, pinStyle, painters) }
    val cameraState = rememberCameraPositionState {
        position = CameraPosition(LatLng(initialCenter.lat, initialCenter.lng), DEFAULT_MAP_ZOOM)
    }
    val currentOnIdle by rememberUpdatedState(onCameraIdle)
    val currentOnSpotClick by rememberUpdatedState(onSpotClick)
    val clusters = remember(images) { SpotClusters(images) { currentOnSpotClick(it) } }
    DisposableEffect(clusters) { onDispose { clusters.clusterer.map = null } }

    LaunchedEffect(cameraRequest?.id) {
        val request = cameraRequest ?: return@LaunchedEffect
        cameraState.animate(CameraUpdate.scrollTo(LatLng(request.target.lat, request.target.lng)))
    }
    // 묶음에는 선택 안 된 핀만.
    LaunchedEffect(clusters, spots, selectedId) { clusters.sync(spots.filter { it.id != selectedId }) }

    NaverMap(
        modifier = modifier.fillMaxSize(),
        cameraPositionState = cameraState,
        uiSettings = MapUiSettings(
            isLocationButtonEnabled = false,
            isZoomControlEnabled = false,
            isScaleBarEnabled = false,
            isCompassEnabled = false,
            isTiltGesturesEnabled = false,
        ),
        onMapClick = { _, _ -> onMapClick() },
        contentPadding = contentPadding,
    ) {
        DisposableMapEffect(clusters) { map ->
            clusters.clusterer.map = map
            var byGesture = false
            val change = SdkMap.OnCameraChangeListener { reason, _ ->
                if (reason == CameraUpdate.REASON_GESTURE) byGesture = true
            }
            val idle = SdkMap.OnCameraIdleListener {
                map.contentBounds.toGeoBounds()?.let { currentOnIdle(it, byGesture) }
                byGesture = false
            }
            map.addOnCameraChangeListener(change)
            map.addOnCameraIdleListener(idle)
            // 첫 카메라(초기 위치)는 리스너를 붙이기 전에 이미 멈춰 있어 idle 이 다시 오지 않는다(#40).
            // 지도가 준비된 지금 보이는 영역을 한 번 알려 첫 계산을 시작한다. 이후 idle 과 겹쳐도
            // 같은 영역이면 화면 쪽에서 무시한다.
            map.contentBounds.toGeoBounds()?.let { currentOnIdle(it, false) }
            onDispose {
                map.removeOnCameraChangeListener(change)
                map.removeOnCameraIdleListener(idle)
            }
        }
        myLocation?.let {
            LocationOverlay(
                position = LatLng(it.lat, it.lng),
                icon = images.myLocation,
                circleRadius = MY_LOCATION_CIRCLE_DP.dp,
                circleColor = pinStyle.ink.copy(alpha = MY_LOCATION_CIRCLE_ALPHA),
            )
        }
        spots.firstOrNull { it.id == selectedId }?.let { spot ->
            val picked = images.selected(spot.category, spot.title)
            Marker(
                state = remember(spot.id) { MarkerState(position = LatLng(spot.location.lat, spot.location.lng)) },
                icon = picked.image,
                anchor = picked.anchor,
                globalZIndex = SELECTED_Z,
                onClick = { true },
            )
        }
    }
}

/** 클러스터링 키. 같은 id 면 같은 핀. */
private class SpotKey(val spot: Spot) : ClusteringKey {
    override fun getPosition(): LatLng = LatLng(spot.location.lat, spot.location.lng)

    override fun equals(other: Any?): Boolean = other is SpotKey && other.spot.id == spot.id

    override fun hashCode(): Int = spot.id.hashCode()
}

private fun buildClusterer(images: MarkerImages, onSpotClick: (String) -> Unit): Clusterer<SpotKey> =
    Clusterer.Builder<SpotKey>()
        .screenDistance(MapMarkerRules.CLUSTER_SCREEN_DISTANCE_DP)
        .maxZoom(MapMarkerRules.CLUSTER_MAX_ZOOM)
        .clusterMarkerUpdater(
            ClusterMarkerUpdater { info, marker ->
                marker.icon = images.cluster(info.size)
                marker.anchor = Center
                marker.captionText = ""
                marker.onClickListener = Overlay.OnClickListener {
                    // 묶인 핀이 풀리는 줌까지 그 자리로 확대 (motion.slow).
                    marker.map?.moveCamera(
                        CameraUpdate.scrollAndZoomTo(info.position, (info.maxZoom + 1).toDouble())
                            .animate(CameraAnimation.Easing, CLUSTER_ZOOM_MS),
                    )
                    true
                }
            },
        ).leafMarkerUpdater(
            LeafMarkerUpdater { info, marker ->
                val spot = (info.key as SpotKey).spot
                marker.icon = images.pin(spot.category)
                marker.anchor = Center
                marker.captionText = ""
                marker.onClickListener = Overlay.OnClickListener {
                    onSpotClick(spot.id)
                    true
                }
            },
        ).build()

/** SDK 클러스터러 + 지금 넣어 둔 핀 목록(SDK 가 돌려주지 않는다). */
private class SpotClusters(images: MarkerImages, onSpotClick: (String) -> Unit) {
    val clusterer: Clusterer<SpotKey> = buildClusterer(images, onSpotClick)
    private val current = mutableSetOf<SpotKey>()

    /** 빠진 핀은 빼고 새 핀만 넣는다(깜빡임 방지). */
    fun sync(spots: List<Spot>) {
        val next = spots.map(::SpotKey).toSet()
        val removed = current - next
        val added = next - current
        if (removed.isNotEmpty()) clusterer.removeAll(removed)
        if (added.isNotEmpty()) clusterer.addAll(added.associateWith { null })
        current.removeAll(removed)
        current.addAll(added)
    }
}

private fun com.naver.maps.geometry.LatLngBounds?.toGeoBounds(): GeoBounds? {
    val sw = this?.southWest ?: return null
    val ne = northEast
    if (sw.latitude > ne.latitude || sw.longitude > ne.longitude) return null
    return GeoBounds(GeoPoint(sw.latitude, sw.longitude), GeoPoint(ne.latitude, ne.longitude))
}

private val Center = PointF(0.5f, 0.5f)
private const val CLUSTER_ZOOM_MS = 350L
private const val SELECTED_Z = 300_000
private const val MY_LOCATION_CIRCLE_DP = 24
private const val MY_LOCATION_CIRCLE_ALPHA = 0.12f

@Composable
private fun MapUnavailable(modifier: Modifier) {
    Box(
        modifier = modifier.fillMaxSize().background(UnavailableBg).padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = "지도를 불러오지 못했어요", color = UnavailableText)
    }
}

private val UnavailableBg = Color(0xFFF2F4F6)
private val UnavailableText = Color(0xFF4E5968)
