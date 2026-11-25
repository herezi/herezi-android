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
    @SerialName("saved_at") val savedAt: String? = null,
)

/** openapi `SpotPage`. */
@Serializable
data class SpotPageDto(val items: List<SpotDto>, @SerialName("next_cursor") val nextCursor: String? = null)

/** openapi `RegionSpot` — `Spot` 에서 `distance_m`·`saved_at` 만 없다. `is_ended` 는 `generated_at` 기준이라 앱이 다시 계산한다. */
@Serializable
data class RegionSpotDto(
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
)

/** openapi `RegionSpots` (`GET /v1/regions/{region}/spots`). */
@Serializable
data class RegionSpotsDto(
    val region: String,
    @SerialName("generated_at") val generatedAt: String,
    val count: Int,
    val items: List<RegionSpotDto>,
)

/** openapi `SavedImportRequest` (`POST /v1/me/saved:import`). */
@Serializable
data class SavedImportRequestDto(@SerialName("spot_ids") val spotIds: List<String>)

/** openapi `SavedImportResult`. */
@Serializable
data class SavedImportResultDto(val imported: Int, @SerialName("skipped_unknown") val skippedUnknown: Int)

/** openapi `Error`. */
@Serializable
data class ErrorDto(val code: String, val message: String)
