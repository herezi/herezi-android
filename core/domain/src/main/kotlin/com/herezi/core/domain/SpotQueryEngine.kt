package com.herezi.core.domain

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 기기에서 하는 조회(ADR herezi-api 0004: 좌표를 서버로 보내지 않는다).
 * 서버 `internal/domain/when.go`·`store.SpotFilter.Match`·memory 저장소의 의미를 그대로 옮겼다.
 * - 끝난 항목(`ends_at` < now, 기기 시각)은 목록·지도·검색에서 뺀다. 지역 파일의 `is_ended` 는 `generated_at` 기준이라 쓰지 않는다.
 * - 기간: [window] 와 겹치면(경계 포함) 통과. 무료는 모름=무료 아님. 키워드는 제목·장소명·주소 대소문자 무시 부분 일치.
 * - 정렬: 거리순, 같으면 id 순. 커서는 정수 위치(문자열).
 */
object SpotQueryEngine {
    val Seoul: ZoneId = ZoneId.of("Asia/Seoul")

    /** 서버 `earthRadiusM` 과 같은 값. */
    const val EARTH_RADIUS_M = 6_371_008.8

    /** 서버 `q` 규칙: 앞뒤 공백을 자른 뒤 최대 50자. */
    const val MAX_KEYWORD_CHARS = 50

    private const val WEEK_DAYS = 7L
    private val END_OF_DAY: LocalTime = LocalTime.of(23, 59, 59)

    /** 하버사인 거리(m, 실수). */
    fun distanceM(a: GeoPoint, b: GeoPoint): Double {
        val dLat = Math.toRadians(b.lat - a.lat)
        val dLng = Math.toRadians(b.lng - a.lng)
        val h = sin(dLat / 2).pow(2) + cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLng / 2).pow(2)
        return 2 * EARTH_RADIUS_M * asin(sqrt(h))
    }

    /** 서버 `When.Window`: 항목이 겹쳐야 하는 [from, to]. from 은 now 보다 앞서지 않는다. */
    fun window(period: When, now: Instant): Pair<Instant, Instant> {
        val local = now.atZone(Seoul)
        fun endOfDay(date: LocalDate): Instant = ZonedDateTime.of(date, END_OF_DAY, Seoul).toInstant()
        return when (period) {
            When.NOW -> now to now
            When.TODAY -> now to endOfDay(local.toLocalDate())
            When.WEEK -> now to local.plusDays(WEEK_DAYS).toInstant()
            When.WEEKEND -> {
                val today = local.toLocalDate()
                val saturday = when (today.dayOfWeek) {
                    DayOfWeek.SATURDAY -> today
                    DayOfWeek.SUNDAY -> today.minusDays(1)
                    else -> today.plusDays((DayOfWeek.SATURDAY.value - today.dayOfWeek.value).toLong())
                }
                val start = maxOf(saturday.atStartOfDay(Seoul).toInstant(), now)
                start to endOfDay(saturday.plusDays(1))
            }
        }
    }

    fun isEnded(spot: Spot, now: Instant): Boolean = spot.endsAt?.isBefore(now) == true

    /** 서버 `Spot.Overlaps`: 종료가 없으면 시작 시각을 종료로 본다. 경계 포함. */
    fun overlaps(spot: Spot, from: Instant, to: Instant): Boolean {
        val end = spot.endsAt ?: spot.startsAt
        return !spot.startsAt.isAfter(to) && !end.isBefore(from)
    }

    fun normalizeKeyword(keyword: String?): String? = keyword?.trim()?.let { k ->
        k.take(k.offsetByCodePoints(0, minOf(MAX_KEYWORD_CHARS, k.codePointCount(0, k.length))))
    }
        ?.takeIf { it.isNotEmpty() }

    fun matchesKeyword(spot: Spot, keyword: String?): Boolean {
        val kw = keyword?.lowercase() ?: return true
        return listOf(spot.title, spot.placeName.orEmpty(), spot.address.orEmpty()).any { it.lowercase().contains(kw) }
    }

    /** 서버 `SpotFilter.Match`. */
    fun matches(spot: Spot, period: When, categories: Set<Category>, freeOnly: Boolean, now: Instant): Boolean {
        val (from, to) = window(period, now)
        return !isEnded(spot, now) &&
            overlaps(spot, from, to) &&
            (categories.isEmpty() || spot.category in categories) &&
            (!freeOnly || spot.isFree)
    }

    /** 근처 목록·검색 (`/v1/spots` 의 기기판). [SpotQuery.cursor] 는 정수 위치. */
    fun nearby(spots: List<Spot>, query: SpotQuery, now: Instant): SpotPage {
        val keyword = normalizeKeyword(query.keyword)
        val hits = spots.asSequence()
            .filter { matches(it, query.period, query.categories, query.freeOnly, now) && matchesKeyword(it, keyword) }
            .map { it to distanceM(query.center, it.location) }
            .filter { (_, d) -> d <= query.radiusM }
            .sortedWith(compareBy<Pair<Spot, Double>> { it.second }.thenBy { it.first.id })
            .toList()
        val offset = query.cursor?.toIntOrNull()?.coerceAtLeast(0) ?: 0
        val page = hits.drop(offset).take(query.limit)
        val next = (offset + page.size).takeIf { page.isNotEmpty() && it < hits.size }?.toString()
        return SpotPage(page.map { (s, d) -> s.copy(distanceM = (d + 0.5).toInt(), isEnded = false) }, next)
    }

    /** 지도 영역 (`/v1/spots/map` 의 기기판): 모서리 포함, 영역 중심에서 가까운 순, [MapQuery.limit] 넘으면 truncated. */
    fun inBounds(spots: List<Spot>, query: MapQuery, now: Instant): MapSpots {
        val center = query.bounds.center
        val hits = spots.asSequence()
            .filter { it.location in query.bounds && matches(it, query.period, query.categories, query.freeOnly, now) }
            .map { it to distanceM(center, it.location) }
            .sortedWith(compareBy<Pair<Spot, Double>> { it.second }.thenBy { it.first.id })
            .toList()
        return MapSpots(
            hits.take(query.limit).map { it.first.copy(isEnded = false) },
            truncated =
            hits.size > query.limit,
        )
    }
}
