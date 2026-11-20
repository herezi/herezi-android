package com.herezi.core.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** openapi `Spot`. 필드 이름은 계약 그대로(snake_case)이고, 필수 필드만 non-null. */
@Serializable
data class SpotDto(
    val id: String,
    val title: String,
    val category: String,
    val lat: Double,
    val lng: Double,
    @SerialName("starts_at") val startsAt: String,
    val source: String,
    @SerialName("fetched_at") val fetchedAt: String,
    @SerialName("is_ended") val isEnded: Boolean,
    val summary: String? = null,
    val address: String? = null,
    @SerialName("place_name") val placeName: String? = null,
    @SerialName("ends_at") val endsAt: String? = null,
    @SerialName("is_free") val isFree: Boolean = false,
    @SerialName("image_url") val imageUrl: String? = null,
    @SerialName("source_url") val sourceUrl: String? = null,
    @SerialName("distance_m") val distanceM: Int? = null,
)

/** openapi `SpotPage`. */
@Serializable
data class SpotPageDto(val items: List<SpotDto>, @SerialName("next_cursor") val nextCursor: String? = null)

/** openapi `SpotMap` (`GET /v1/spots/map`). */
@Serializable
data class SpotMapDto(val items: List<SpotDto>, val truncated: Boolean)
