package com.herezi.core.network

import com.herezi.core.domain.Category
import com.herezi.core.domain.GeoPoint
import com.herezi.core.domain.MapSpots
import com.herezi.core.domain.Spot
import com.herezi.core.domain.SpotPage
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

/**
 * DTO → 도메인. 모르는 카테고리나 깨진 날짜(starts_at·fetched_at)는 항목 하나만 버리고(null) 목록은 살린다.
 */
fun SpotDto.toDomain(): Spot? {
    val category = Category.fromCode(category)
    val starts = parseInstant(startsAt)
    val fetched = parseInstant(fetchedAt)
    if (category == null || starts == null || fetched == null) return null
    return Spot(
        id = id,
        title = title,
        category = category,
        location = GeoPoint(lat = lat, lng = lng),
        startsAt = starts,
        source = source,
        fetchedAt = fetched,
        summary = summary,
        address = address,
        placeName = placeName,
        endsAt = endsAt?.let(::parseInstant),
        isFree = isFree,
        imageUrl = imageUrl,
        sourceUrl = sourceUrl,
        distanceM = distanceM,
        isEnded = isEnded,
    )
}

fun SpotPageDto.toDomain(): SpotPage = SpotPage(items = items.mapNotNull { it.toDomain() }, nextCursor = nextCursor)

fun SpotMapDto.toDomain(): MapSpots = MapSpots(items = items.mapNotNull { it.toDomain() }, truncated = truncated)

// RFC 3339 (`2026-04-01T10:00:00+09:00`, `...Z`) 모두 받는다.
private fun parseInstant(value: String): Instant? = try {
    OffsetDateTime.parse(value).toInstant()
} catch (_: DateTimeParseException) {
    null
}
