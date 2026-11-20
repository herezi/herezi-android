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
            startsAt = Instant.parse("2026-11-20T00:00:00Z"),
            endsAt = Instant.parse("2026-12-14T00:00:00Z"),
            source = "fake",
            fetchedAt = Instant.EPOCH,
            placeName = "여의서로 벚꽃길",
        )
    private val name = "%EC%97%AC%EC%9D%98%EC%84%9C%EB%A1%9C%20%EB%B2%9A%EA%BD%83%EA%B8%B8"

    @Test
    fun `가까우면 걷기, 2km 이상이면 대중교통 앱 링크`() {
        assertEquals(
            "nmap://route/walk?dlat=37.5285&dlng=126.9227&dname=$name&appname=com.herezi.app",
            NaverMapDirections.appUri(spot, "com.herezi.app", distanceM = 800),
        )
        assertEquals(
            "nmap://route/public?dlat=37.5285&dlng=126.9227&dname=$name&appname=com.herezi.app",
            NaverMapDirections.appUri(spot, "com.herezi.app", distanceM = 2_000),
        )
    }

    @Test
    fun `웹 길찾기는 경도,위도,이름 순서이고 장소명이 없으면 제목을 쓴다`() {
        assertEquals(
            "https://map.naver.com/p/directions/-/126.9227,37.5285,$name/-/walk",
            NaverMapDirections.webUri(spot, 100),
        )
        val uri = NaverMapDirections.webUri(spot.copy(placeName = null), 5_000)
        assertEquals(true, uri.contains("37.5285,%EC%97%AC%EC%9D%98%EB%8F%84"))
        assertEquals(true, uri.endsWith("/transit"))
    }

    @Test
    fun `끝난 볼거리는 장소 보기 링크`() {
        assertEquals(
            "nmap://place?lat=37.5285&lng=126.9227&name=$name&appname=com.herezi.app",
            NaverMapDirections.placeAppUri(spot, "com.herezi.app"),
        )
    }

    @Test
    fun `공유 문구와 제보 제목`() {
        assertEquals(
            "여의도 봄꽃 축제\n11월 20일(금) – 12월 14일(월) · 여의서로 벚꽃길\nhttps://herezi.com/s/a",
            shareText(spot),
        )
        assertEquals("[정보 오류] a 여의도 봄꽃 축제", reportSubject(spot))
    }
}
