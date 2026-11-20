package com.herezi.core.domain

import kotlin.coroutines.cancellation.CancellationException

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

/** 좌표 → "구 동" (예: "중구 태평로1가"). 모르면 null → 화면은 "내 주변". 구현은 [ServerFirstGeocoder]. */
fun interface ReverseGeocoder {
    suspend fun placeName(point: GeoPoint): String?
}

/**
 * 위치명: 서버 역지오코딩([SpotsRepository.placeName], 네이버 경유)을 먼저 쓰고,
 * 비었거나 실패하면 [fallback](기기 OS 지오코더, :app)을 쓴다. DESIGN.md §8, herezi-api#35.
 */
class ServerFirstGeocoder(private val repository: SpotsRepository, private val fallback: ReverseGeocoder) :
    ReverseGeocoder {
    override suspend fun placeName(point: GeoPoint): String? {
        val server = try {
            repository.placeName(point)
        } catch (e: CancellationException) {
            throw e
        } catch (@Suppress("TooGenericExceptionCaught", "SwallowedException") e: Exception) {
            null
        }
        return server?.trim()?.takeIf { it.isNotEmpty() } ?: fallback.placeName(point)
    }
}
