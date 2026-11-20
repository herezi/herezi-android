package com.herezi.core.network

import com.herezi.core.domain.Category
import com.herezi.core.domain.GeoPoint
import com.herezi.core.domain.Spot
import com.herezi.core.domain.SpotPage
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

/**
 * DTO → 도메인. 모르는 카테고리나 깨진 날짜는 항목 하나만 버리고(null) 목록은 살린다.
 */
fun SpotDto.toDomain(): Spot? {
    val category = Category.fromCode(category) ?: return null
    val starts = parseInstant(startsAt) ?: return null
    return Spot(
        id = id,
        title = title,
        category = category,
        location = GeoPoint(lat = lat, lng = lng),
        startsAt = starts,
        source = source,
        summary = summary,
        address = address,
        placeName = placeName,
        endsAt = endsAt?.let(::parseInstant),
        isFree = isFree,
        imageUrl = imageUrl,
        sourceUrl = sourceUrl,
        distanceM = distanceM,
    )
}

fun SpotPageDto.toDomain(): SpotPage = SpotPage(items = items.mapNotNull { it.toDomain() }, nextCursor = nextCursor)

// RFC 3339 (`2026-04-01T10:00:00+09:00`, `...Z`) 모두 받는다.
private fun parseInstant(value: String): Instant? = try {
    OffsetDateTime.parse(value).toInstant()
} catch (_: DateTimeParseException) {
    null
}
