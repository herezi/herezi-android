package com.herezi.core.network

import com.herezi.core.domain.Category
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

    private fun Set<Category>.toCategoryParam(): String? =
        if (isEmpty()) null else sortedBy { it.ordinal }.joinToString(",") { it.code }

    private companion object {
        const val HTTP_NOT_FOUND = 404
    }
}
