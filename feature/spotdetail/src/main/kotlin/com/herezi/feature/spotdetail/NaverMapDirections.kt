package com.herezi.feature.spotdetail

import com.herezi.core.domain.Spot
import java.net.URLEncoder

/**
 * 네이버지도 링크 (S3). 앱이 있으면 앱 링크, 없으면 웹을 연다.
 * 길찾기는 걷기, 거리 2km 이상이면 대중교통. 출발지는 비워 두면 네이버지도가 현재 위치를 쓴다.
 */
object NaverMapDirections {
    /** 이 거리(m) 이상이면 대중교통 길찾기. */
    const val PUBLIC_TRANSIT_FROM_M = 2_000

    private fun transit(distanceM: Int?) = distanceM != null && distanceM >= PUBLIC_TRANSIT_FROM_M

    private fun Spot.name() = encode(placeName ?: title)

    fun appUri(spot: Spot, appName: String, distanceM: Int? = null): String {
        val mode = if (transit(distanceM)) "public" else "walk"
        val (lat, lng) = spot.location
        return "nmap://route/$mode?dlat=$lat&dlng=$lng&dname=${spot.name()}&appname=${encode(appName)}"
    }

    fun webUri(spot: Spot, distanceM: Int? = null): String {
        val mode = if (transit(distanceM)) "transit" else "walk"
        val (lat, lng) = spot.location
        return "https://map.naver.com/p/directions/-/$lng,$lat,${spot.name()}/-/$mode"
    }

    /** 끝난 볼거리: 길찾기 대신 장소만 보여준다. */
    fun placeAppUri(spot: Spot, appName: String): String {
        val (lat, lng) = spot.location
        return "nmap://place?lat=$lat&lng=$lng&name=${spot.name()}&appname=${encode(appName)}"
    }

    fun placeWebUri(spot: Spot): String = "https://map.naver.com/p/search/${spot.name()}"

    private fun encode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")
}
