package com.herezi.core.domain

import java.time.Instant

data class GeoPoint(val lat: Double, val lng: Double) {
    companion object {
        /** 위치를 모를 때 쓰는 기본 위치 (서울시청). */
        val SeoulCityHall = GeoPoint(lat = 37.5665, lng = 126.9780)
    }
}

/** 지금 주변에서 볼 만한 것 하나. API `Spot` 스키마의 도메인 표현. */
data class Spot(
    val id: String,
    val title: String,
    val category: Category,
    val location: GeoPoint,
    val startsAt: Instant,
    val source: String,
    val summary: String? = null,
    val address: String? = null,
    val placeName: String? = null,
    val endsAt: Instant? = null,
    val isFree: Boolean = false,
    val imageUrl: String? = null,
    val sourceUrl: String? = null,
    val distanceM: Int? = null,
)

data class SpotPage(val items: List<Spot>, val nextCursor: String? = null)

/** 근처 목록 조회 조건. 기본값은 API 기본값과 같다. */
data class SpotQuery(
    val center: GeoPoint,
    val radiusM: Int = DEFAULT_RADIUS_M,
    val categories: Set<Category> = emptySet(),
    val period: When = When.NOW,
    val freeOnly: Boolean = false,
    val cursor: String? = null,
    val limit: Int = DEFAULT_LIMIT,
) {
    companion object {
        const val DEFAULT_RADIUS_M = 3000
        const val DEFAULT_LIMIT = 30
    }
}
