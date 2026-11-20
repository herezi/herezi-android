package com.herezi.feature.spotdetail

import com.herezi.core.domain.Category
import com.herezi.core.domain.GeoPoint
import com.herezi.core.domain.Spot
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class NaverMapDirectionsTest {
    private val spot =
        Spot(
            id = "a",
            title = "여의도 봄꽃 축제",
            category = Category.NATURE,
            location = GeoPoint(37.5285, 126.9227),
            startsAt = Instant.EPOCH,
            source = "fake",
            fetchedAt = Instant.EPOCH,
            placeName = "여의서로 벚꽃길",
        )

    @Test
    fun `앱 링크는 도착지 좌표와 장소명, 앱 이름을 담는다`() {
        assertEquals(
            "nmap://route/public?dlat=37.5285&dlng=126.9227" +
                "&dname=%EC%97%AC%EC%9D%98%EC%84%9C%EB%A1%9C%20%EB%B2%9A%EA%BD%83%EA%B8%B8&appname=com.herezi.app",
            NaverMapDirections.appUri(spot, "com.herezi.app"),
        )
    }

    @Test
    fun `장소명이 없으면 제목을 쓴다`() {
        val uri = NaverMapDirections.webUri(spot.copy(placeName = null))
        assertEquals(true, uri.contains("ename=%EC%97%AC%EC%9D%98%EB%8F%84"))
        assertEquals(true, uri.startsWith("https://m.map.naver.com/route.nhn?menu=route&ex=126.9227&ey=37.5285"))
    }
}
