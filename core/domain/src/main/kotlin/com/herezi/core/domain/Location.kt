package com.herezi.core.domain

/** 위치 권한 상태 (S6 "허용됨 · 대략적 위치만 · 허용 안 됨"). */
enum class LocationAccess { PRECISE, APPROXIMATE, DENIED }

/** 기기 위치 포트. 구현은 :app(Android LocationManager). */
interface LocationSource {
    fun access(): LocationAccess

    /** 현재 위치. 권한이 없거나 모르면 null → 화면은 서울시청 기준. */
    suspend fun current(): GeoPoint?
}

/** 기준 위치. [known] 이 false 면 서울시청 대체 위치다. */
data class Origin(val point: GeoPoint, val known: Boolean)

suspend fun LocationSource.origin(): Origin =
    current()?.let { Origin(it, known = true) } ?: Origin(GeoPoint.SeoulCityHall, known = false)

/**
 * 좌표 → "구 동" (예: "중구 태평로1가"). 모르면 null → 화면은 "내 주변".
 * 기기 지오코더(Android `Geocoder`)만 쓴다 — 좌표를 서버로 보내지 않는다(herezi-api ADR 0004).
 */
fun interface ReverseGeocoder {
    suspend fun placeName(point: GeoPoint): String?
}
