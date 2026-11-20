package com.herezi.core.domain

import java.time.Instant

data class GeoPoint(val lat: Double, val lng: Double) {
    companion object {
        /** 위치를 모를 때 쓰는 기본 위치 (서울시청). API 계약 설명과 같은 값. */
        val SeoulCityHall = GeoPoint(lat = 37.5663, lng = 126.9779)
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
    /** 원본 소스에서 마지막으로 가져온 시각. 상세의 "갱신 …" 표시용. */
    val fetchedAt: Instant,
    val summary: String? = null,
    val address: String? = null,
    val placeName: String? = null,
    val endsAt: Instant? = null,
    val isFree: Boolean = false,
    val imageUrl: String? = null,
    val sourceUrl: String? = null,
    val distanceM: Int? = null,
    /** 끝난 항목. 목록·지도에는 나오지 않고 상세에서만 true 일 수 있다(→ '종료됨'). */
    val isEnded: Boolean = false,
)

data class SpotPage(val items: List<Spot>, val nextCursor: String? = null)

/** 근처 목록 조회 조건. 기본값은 API 기본값과 같다. */
data class SpotQuery(
    val center: GeoPoint,
    val radiusM: Int = DEFAULT_RADIUS_M,
    val categories: Set<Category> = emptySet(),
    val period: When = When.NOW,
    val freeOnly: Boolean = false,
    /** 키워드 검색(API `q`). 제목·장소명·주소 부분 일치. null 이면 검색하지 않는다. */
    val keyword: String? = null,
    val cursor: String? = null,
    val limit: Int = DEFAULT_LIMIT,
) {
    companion object {
        const val DEFAULT_RADIUS_M = 3000

        /** 검색(S5) 반경. */
        const val SEARCH_RADIUS_M = 20_000
        const val DEFAULT_LIMIT = 30
    }
}

/** 지도 영역. 남서(sw)·북동(ne) 꼭짓점. 날짜변경선을 넘는 영역은 지원하지 않는다(API 와 같음). */
data class GeoBounds(val sw: GeoPoint, val ne: GeoPoint) {
    init {
        require(sw.lat <= ne.lat && sw.lng <= ne.lng) { "sw 는 ne 보다 남서쪽이어야 한다: $sw, $ne" }
    }

    val center: GeoPoint get() = GeoPoint(lat = (sw.lat + ne.lat) / 2, lng = (sw.lng + ne.lng) / 2)

    operator fun contains(point: GeoPoint): Boolean = point.lat in sw.lat..ne.lat && point.lng in sw.lng..ne.lng
}

/** 지도 영역 조회 조건 (`GET /v1/spots/map`). 기본값은 API 기본값과 같다. */
data class MapQuery(
    val bounds: GeoBounds,
    val categories: Set<Category> = emptySet(),
    val period: When = When.NOW,
    val freeOnly: Boolean = false,
    val limit: Int = DEFAULT_LIMIT,
) {
    companion object {
        const val DEFAULT_LIMIT = 200
    }
}

/** 지도 영역 안 항목(영역 중심에서 가까운 순). [truncated] 면 영역 안에 더 있다(확대하면 보인다). */
data class MapSpots(val items: List<Spot>, val truncated: Boolean)
