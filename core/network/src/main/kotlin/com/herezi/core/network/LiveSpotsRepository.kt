package com.herezi.core.network

import com.herezi.core.domain.Category
import com.herezi.core.domain.GeoPoint
import com.herezi.core.domain.MapQuery
import com.herezi.core.domain.MapSpots
import com.herezi.core.domain.Spot
import com.herezi.core.domain.SpotPage
import com.herezi.core.domain.SpotQuery
import com.herezi.core.domain.SpotsRepository
import retrofit2.HttpException

/** herezi-api 를 호출하는 실제 저장소. */
class LiveSpotsRepository(private val api: HereziApi) : SpotsRepository {
    override suspend fun nearby(query: SpotQuery): SpotPage = api
        .listSpots(
            lat = query.center.lat,
            lng = query.center.lng,
            radiusM = query.radiusM,
            category = query.categories.toCategoryParam(),
            period = query.period.code,
            free = if (query.freeOnly) true else null,
            keyword = query.keyword?.trim()?.takeIf { it.isNotEmpty() },
            cursor = query.cursor,
            limit = query.limit,
        ).toDomain()

    override suspend fun inBounds(query: MapQuery): MapSpots = api
        .listSpotsInBBox(
            swLat = query.bounds.sw.lat,
            swLng = query.bounds.sw.lng,
            neLat = query.bounds.ne.lat,
            neLng = query.bounds.ne.lng,
            category = query.categories.toCategoryParam(),
            period = query.period.code,
            free = if (query.freeOnly) true else null,
            limit = query.limit,
        ).toDomain()

    override suspend fun spot(id: String): Spot? = try {
        api.getSpot(id).toDomain()
    } catch (e: HttpException) {
        if (e.code() == HTTP_NOT_FOUND) null else throw e
    }

    /** 빈 label·501(키 없음)·502(네이버 실패)는 null → OS 지오코더로 대체. 그 밖의 오류는 던진다. */
    override suspend fun placeName(point: GeoPoint): String? = try {
        api.reverseGeocode(lat = point.lat, lng = point.lng).label.trim().ifEmpty { null }
    } catch (e: HttpException) {
        if (e.code() in FALLBACK_CODES) null else throw e
    }

    private fun Set<Category>.toCategoryParam(): String? =
        if (isEmpty()) null else sortedBy { it.ordinal }.joinToString(",") { it.code }

    private companion object {
        const val HTTP_NOT_FOUND = 404
        val FALLBACK_CODES = setOf(501, 502)
    }
}
