package com.herezi.core.data

import com.herezi.core.domain.Category
import com.herezi.core.domain.GeoPoint
import com.herezi.core.domain.SavedSpot
import com.herezi.core.domain.Spot
import java.time.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** 저장 스냅샷의 디스크 형식. 필드를 바꾸면 기본값으로 이전 데이터를 읽을 수 있게 한다. */
@Serializable
internal data class SavedSpotRecord(
    val id: String,
    val title: String,
    val category: String,
    val lat: Double,
    val lng: Double,
    val startsAt: Long,
    val source: String,
    val fetchedAt: Long,
    val savedAt: Long,
    val summary: String? = null,
    val address: String? = null,
    val placeName: String? = null,
    val endsAt: Long? = null,
    val isFree: Boolean = false,
    val imageUrl: String? = null,
    val sourceUrl: String? = null,
    val isEnded: Boolean = false,
)

internal fun SavedSpot.toRecord() = SavedSpotRecord(
    id = spot.id,
    title = spot.title,
    category = spot.category.code,
    lat = spot.location.lat,
    lng = spot.location.lng,
    startsAt = spot.startsAt.toEpochMilli(),
    source = spot.source,
    fetchedAt = spot.fetchedAt.toEpochMilli(),
    savedAt = savedAt.toEpochMilli(),
    summary = spot.summary,
    address = spot.address,
    placeName = spot.placeName,
    endsAt = spot.endsAt?.toEpochMilli(),
    isFree = spot.isFree,
    imageUrl = spot.imageUrl,
    sourceUrl = spot.sourceUrl,
    isEnded = spot.isEnded,
)

/** 모르는 카테고리(앱 버전이 낮아진 경우 등)는 버린다. 거리는 저장하지 않는다(위치마다 다름). */
internal fun SavedSpotRecord.toDomain(): SavedSpot? {
    val category = Category.fromCode(category) ?: return null
    return SavedSpot(
        spot = Spot(
            id = id,
            title = title,
            category = category,
            location = GeoPoint(lat, lng),
            startsAt = Instant.ofEpochMilli(startsAt),
            source = source,
            fetchedAt = Instant.ofEpochMilli(fetchedAt),
            summary = summary,
            address = address,
            placeName = placeName,
            endsAt = endsAt?.let(Instant::ofEpochMilli),
            isFree = isFree,
            imageUrl = imageUrl,
            sourceUrl = sourceUrl,
            isEnded = isEnded,
        ),
        savedAt = Instant.ofEpochMilli(savedAt),
    )
}

internal val StoreJson = Json { ignoreUnknownKeys = true }

internal object SavedCodec {
    fun decode(raw: String?): List<SavedSpot> = if (raw.isNullOrEmpty()) {
        emptyList()
    } else {
        runCatching { StoreJson.decodeFromString<List<SavedSpotRecord>>(raw) }
            .getOrDefault(emptyList())
            .mapNotNull { it.toDomain() }
    }

    fun encode(items: List<SavedSpot>): String = StoreJson.encodeToString(items.map { it.toRecord() })
}

internal object StringListCodec {
    fun decode(raw: String?): List<String> = if (raw.isNullOrEmpty()) {
        emptyList()
    } else {
        runCatching { StoreJson.decodeFromString<List<String>>(raw) }.getOrDefault(emptyList())
    }

    fun encode(items: List<String>): String = StoreJson.encodeToString(items)
}
