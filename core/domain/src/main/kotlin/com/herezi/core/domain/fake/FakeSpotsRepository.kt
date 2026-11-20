package com.herezi.core.domain.fake

import com.herezi.core.domain.Category
import com.herezi.core.domain.GeoPoint
import com.herezi.core.domain.Spot
import com.herezi.core.domain.SpotPage
import com.herezi.core.domain.SpotQuery
import com.herezi.core.domain.SpotsRepository
import com.herezi.core.domain.When
import com.herezi.core.domain.distanceTo
import java.time.Clock
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/**
 * 서버 없이 화면을 개발·테스트하기 위한 가짜 저장소.
 * 서울 시드 데이터를 [clock] 기준 상대 시간으로 만들어 언제 실행해도 "지금" 항목이 있다.
 * 필터 규칙은 서버(herezi-api)의 조회 규칙을 단순화해 따른다.
 */
class FakeSpotsRepository(
    private val clock: Clock = Clock.systemUTC(),
    private val zone: ZoneId = SEOUL,
    spots: List<Spot>? = null,
) : SpotsRepository {
    private val all: List<Spot> = spots ?: seedSpots(clock.instant())

    override suspend fun nearby(query: SpotQuery): SpotPage {
        val now = clock.instant()
        val (windowStart, windowEnd) = window(query.period, now)
        val items =
            all
                .asSequence()
                .filter { it.endsAt == null || it.endsAt >= now }
                .filter { query.categories.isEmpty() || it.category in query.categories }
                .filter { !query.freeOnly || it.isFree }
                .filter { it.startsAt <= windowEnd && (it.endsAt ?: Instant.MAX) >= windowStart }
                .map { it to query.center.distanceTo(it.location) }
                .filter { (_, distance) -> distance <= query.radiusM }
                .sortedBy { (_, distance) -> distance }
                .map { (spot, distance) -> spot.copy(distanceM = distance) }
                .take(query.limit)
                .toList()
        return SpotPage(items = items)
    }

    override suspend fun spot(id: String): Spot? = all.firstOrNull { it.id == id }

    /** 기간 필터를 [시작, 끝] 시간 창으로 바꾼다. 항목 기간이 이 창과 겹치면 포함. */
    private fun window(period: When, now: Instant): Pair<Instant, Instant> {
        val today = now.atZone(zone).toLocalDate()
        val endOfToday = today.plusDays(1).atStartOfDay(zone).toInstant()
        return when (period) {
            When.NOW -> now to now
            When.TODAY -> now to endOfToday
            When.WEEKEND -> {
                val saturday = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY))
                val start = maxOf(now, saturday.atStartOfDay(zone).toInstant())
                start to saturday.plusDays(2).atStartOfDay(zone).toInstant()
            }
            When.WEEK -> now to now.plus(Duration.ofDays(7))
        }
    }

    companion object {
        val SEOUL: ZoneId = ZoneId.of("Asia/Seoul")

        @Suppress("LongMethod")
        fun seedSpots(now: Instant): List<Spot> {
            fun daysAgo(d: Long) = now.minus(Duration.ofDays(d))
            fun daysLater(d: Long) = now.plus(Duration.ofDays(d))
            return listOf(
                Spot(
                    id = "fake:yeouido-cherry",
                    title = "여의도 봄꽃 축제",
                    category = Category.NATURE,
                    location = GeoPoint(37.5285, 126.9227),
                    startsAt = daysAgo(2),
                    endsAt = daysLater(5),
                    isFree = true,
                    placeName = "여의서로",
                    address = "서울 영등포구 여의서로",
                    summary = "윤중로 벚꽃길을 따라 걷는 봄꽃 축제. 차 없는 거리로 운영된다.",
                    source = "fake",
                ),
                Spot(
                    id = "fake:gwanghwamun-festival",
                    title = "광화문 야간 축제",
                    category = Category.FESTIVAL,
                    location = GeoPoint(37.5720, 126.9769),
                    startsAt = daysAgo(1),
                    endsAt = daysLater(3),
                    isFree = true,
                    placeName = "광화문광장",
                    summary = "광장 미디어파사드와 야시장이 함께 열린다.",
                    source = "fake",
                ),
                Spot(
                    id = "fake:sejong-concert",
                    title = "세종 체임버 콘서트",
                    category = Category.PERFORMANCE,
                    location = GeoPoint(37.5725, 126.9760),
                    startsAt = daysAgo(0),
                    endsAt = daysLater(1),
                    placeName = "세종문화회관",
                    summary = "실내악 저녁 공연. 현장 예매 가능.",
                    source = "fake",
                ),
                Spot(
                    id = "fake:mmca-exhibition",
                    title = "국립현대미술관 기획전",
                    category = Category.EXHIBITION,
                    location = GeoPoint(37.5786, 126.9800),
                    startsAt = daysAgo(20),
                    endsAt = daysLater(40),
                    placeName = "국립현대미술관 서울",
                    source = "fake",
                ),
                Spot(
                    id = "fake:seongsu-popup",
                    title = "성수 브랜드 팝업스토어",
                    category = Category.POPUP,
                    location = GeoPoint(37.5446, 127.0557),
                    startsAt = daysAgo(3),
                    endsAt = daysLater(10),
                    isFree = true,
                    placeName = "성수동 연무장길",
                    source = "fake",
                ),
                Spot(
                    id = "fake:banpo-nightmarket",
                    title = "반포 한강 야시장",
                    category = Category.FOOD,
                    location = GeoPoint(37.5110, 126.9960),
                    startsAt = daysLater(1),
                    endsAt = daysLater(2),
                    placeName = "반포한강공원",
                    source = "fake",
                ),
                Spot(
                    id = "fake:childrens-park",
                    title = "어린이대공원 숲 체험",
                    category = Category.FAMILY,
                    location = GeoPoint(37.5481, 127.0747),
                    startsAt = daysAgo(5),
                    endsAt = daysLater(25),
                    isFree = true,
                    placeName = "서울어린이대공원",
                    source = "fake",
                ),
                Spot(
                    id = "fake:pet-festival",
                    title = "한강 펫 페스티벌",
                    category = Category.PET,
                    location = GeoPoint(37.5283, 126.9340),
                    startsAt = daysLater(4),
                    endsAt = daysLater(5),
                    isFree = true,
                    placeName = "여의도한강공원",
                    source = "fake",
                ),
                Spot(
                    id = "fake:jamsil-baseball",
                    title = "잠실 야구 경기",
                    category = Category.SPORTS,
                    location = GeoPoint(37.5122, 127.0719),
                    startsAt = daysAgo(0),
                    endsAt = daysLater(0).plus(Duration.ofHours(4)),
                    placeName = "잠실야구장",
                    source = "fake",
                ),
            )
        }
    }
}
