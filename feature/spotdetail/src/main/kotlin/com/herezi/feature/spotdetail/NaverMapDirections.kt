package com.herezi.feature.spotdetail

import com.herezi.core.domain.Spot
import java.net.URLEncoder

/**
 * 네이버지도 길찾기 링크. 앱이 있으면 [appUri], 없으면 [webUri] 를 연다.
 * 출발지는 비워 두면 네이버지도가 현재 위치를 쓴다.
 */
object NaverMapDirections {
    /** 네이버지도 앱 대중교통 길찾기 URL 스킴. */
    fun appUri(spot: Spot, appName: String): String {
        val name = encode(spot.placeName ?: spot.title)
        val (lat, lng) = spot.location
        return "nmap://route/public?dlat=$lat&dlng=$lng&dname=$name&appname=${encode(appName)}"
    }

    /** 앱이 없을 때 여는 모바일 웹 길찾기. */
    fun webUri(spot: Spot): String {
        val name = encode(spot.placeName ?: spot.title)
        val (lat, lng) = spot.location
        return "https://m.map.naver.com/route.nhn?menu=route&ex=$lng&ey=$lat&ename=$name&pathType=1"
    }

    private fun encode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")
}
