package com.herezi.core.domain

import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/** 홈(S1) 가로 섹션 종류. 순서가 곧 화면 순서다(고정). */
enum class FeedSectionKind {
    ENDING_TODAY,
    WEEKEND,
    FREE,
    SEASONAL,
}

/** 목록(S1b) 시작 필터. 홈의 카테고리 타일·전체 보기가 넘긴다. */
data class ListFilter(
    val period: When = When.NOW,
    val categories: Set<Category> = emptySet(),
    val freeOnly: Boolean = false,
)

/** 히어로 1장. [startsLaterToday] 면 진행 중이 없어 "오늘 가장 가까운 곳" 으로 바뀐 경우. */
data class HeroSection(val spot: Spot, val startsLaterToday: Boolean) {
    val listFilter: ListFilter get() = ListFilter(period = if (startsLaterToday) When.TODAY else When.NOW)
}

data class FeedSection(val kind: FeedSectionKind, val items: List<Spot>) {
    val listFilter: ListFilter get() = kind.listFilter
}

val FeedSectionKind.listFilter: ListFilter
    get() = when (this) {
        FeedSectionKind.ENDING_TODAY -> ListFilter(period = When.TODAY)
        FeedSectionKind.WEEKEND -> ListFilter(period = When.WEEKEND)
        FeedSectionKind.FREE -> ListFilter(period = When.WEEK, freeOnly = true)
        FeedSectionKind.SEASONAL -> ListFilter(period = When.WEEK, categories = setOf(Category.NATURE))
    }

/**
 * 홈 피드. [ongoingCount] = 진행 중 항목 수(헤드라인), [weekCount] = 받은 항목 수,
 * [hasMore] = 서버에 더 있음(next_cursor) → 헤드라인 "N개 이상".
 */
data class HomeFeed(
    val hero: HeroSection?,
    val sections: List<FeedSection>,
    val ongoingCount: Int,
    val weekCount: Int,
    val hasMore: Boolean,
) {
    val isEmpty: Boolean get() = weekCount == 0
}

/**
 * 홈 섹션 구성 (S1-home.md "섹션 규칙"). 서버 데이터만으로 만든다.
 * [items] 는 `when=week` 응답(거리순). 모든 시각은 [zone](KST) 기준, [now] 하나로 고정한다.
 */
object HomeFeedBuilder {
    const val HOME_RADIUS_M = 3_000
    const val HOME_LIMIT = 100
    const val SECTION_MIN = 3
    const val SECTION_MAX = 10

    private const val SATURDAY_TO_MONDAY_DAYS = 2L

    fun build(items: List<Spot>, now: Instant, zone: ZoneId, hasMore: Boolean = false): HomeFeed {
        // 거리순(거리 없으면 뒤로). 서버가 이미 거리순이지만 규칙이 거리를 쓰므로 여기서 보장한다.
        val byDistance = items.sortedBy { it.distanceM ?: Int.MAX_VALUE }
        val clock = DayClock(now, now.atZone(zone).toLocalDate().plusDays(1).atStartOfDay(zone).toInstant())
        val ongoing = byDistance.filter { clock.ongoing(it) }
        val hero = ongoing.firstOrNull()?.let { HeroSection(it, startsLaterToday = false) }
            ?: byDistance.firstOrNull { clock.startsLaterToday(it) }?.let { HeroSection(it, startsLaterToday = true) }
        val sections = pickSections(candidates(byDistance, clock, weekendWindow(now, zone)), hero)
        return HomeFeed(hero, sections, ongoingCount = ongoing.size, weekCount = items.size, hasMore = hasMore)
    }

    /** 섹션별 후보(중복 처리 전). */
    private fun candidates(
        byDistance: List<Spot>,
        clock: DayClock,
        weekend: Pair<Instant, Instant>,
    ): Map<FeedSectionKind, List<Spot>> = mapOf(
        FeedSectionKind.ENDING_TODAY to byDistance.filter { clock.endsToday(it) }
            .sortedWith(compareBy<Spot> { it.end() }.thenBy { it.distanceM ?: Int.MAX_VALUE }),
        FeedSectionKind.WEEKEND to
            byDistance.filter { it.startsAt.isBefore(weekend.second) && it.end() > weekend.first },
        FeedSectionKind.FREE to byDistance.filter { it.isFree },
        FeedSectionKind.SEASONAL to byDistance.filter { it.category == Category.NATURE },
    )

    /**
     * 순서대로 채운다. 히어로는 어떤 섹션에도 넣지 않고, 앞 섹션에 나온 항목은 뒤로 미룬다.
     * 최소 개수는 미루기 전(히어로 제외) 후보 수로 판단한다.
     */
    private fun pickSections(candidates: Map<FeedSectionKind, List<Spot>>, hero: HeroSection?): List<FeedSection> {
        val shown = mutableSetOf<String>()
        hero?.let { shown += it.spot.id }
        return FeedSectionKind.entries.mapNotNull { kind ->
            val pool = candidates.getValue(kind).filterNot { it.id == hero?.spot?.id }
            if (pool.size < SECTION_MIN) return@mapNotNull null
            val (fresh, seen) = pool.partition { it.id !in shown }
            val picked = (fresh + seen).take(SECTION_MAX)
            shown += picked.map { it.id }
            FeedSection(kind, picked)
        }
    }

    private fun Spot.end(): Instant = endsAt ?: Instant.MAX

    /** 기준 시각 [now] 와 오늘 끝(내일 0시, 배타) 으로 판정한다. */
    private class DayClock(val now: Instant, val endOfToday: Instant) {
        fun ongoing(s: Spot) = !s.startsAt.isAfter(now) && now.isBefore(s.endsAt ?: Instant.MAX)

        fun startsLaterToday(s: Spot) = s.startsAt.isAfter(now) && s.startsAt.isBefore(endOfToday)

        fun endsToday(s: Spot) = ongoing(s) && (s.endsAt ?: Instant.MAX).isBefore(endOfToday)
    }

    /** 서버 `weekend` 와 같은 창: 오늘이 토·일이면 이번 주말, 아니면 다가오는 주말. 창 시작이 지났으면 now 부터. */
    fun weekendWindow(now: Instant, zone: ZoneId): Pair<Instant, Instant> {
        val today = now.atZone(zone).toLocalDate()
        val saturday = if (today.dayOfWeek == DayOfWeek.SUNDAY) {
            today.minusDays(1)
        } else {
            today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY))
        }
        val start = maxOf(now, saturday.atStartOfDay(zone).toInstant())
        return start to saturday.plusDays(SATURDAY_TO_MONDAY_DAYS).atStartOfDay(zone).toInstant()
    }
}
