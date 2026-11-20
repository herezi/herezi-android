package com.herezi.core.domain

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeFeedBuilderTest {
    private val zone = ZoneId.of("Asia/Seoul")

    // 2026-11-18(수) 12:00 KST
    private val now = kst("2026-11-18T12:00")

    private fun kst(value: String): Instant = LocalDateTime.parse(value).atZone(zone).toInstant()

    private var seq = 0

    private fun spot(
        distance: Int,
        start: String = "2026-11-17T10:00",
        end: String? = "2026-11-30T20:00",
        category: Category = Category.FESTIVAL,
        free: Boolean = false,
    ) = Spot(
        id = "s${seq++}",
        title = "t$seq",
        category = category,
        location = GeoPoint(37.5, 127.0),
        startsAt = kst(start),
        endsAt = end?.let(::kst),
        source = "test",
        fetchedAt = now,
        isFree = free,
        distanceM = distance,
    )

    private fun build(vararg items: Spot, hasMore: Boolean = false) =
        HomeFeedBuilder.build(items.toList(), now, zone, hasMore)

    private fun HomeFeed.section(kind: FeedSectionKind) = sections.firstOrNull { it.kind == kind }

    @Test
    fun `히어로는 진행 중인 가장 가까운 항목`() {
        val later = spot(50, start = "2026-11-18T17:00", end = "2026-11-18T22:00")
        val near = spot(300)
        val far = spot(900)
        val feed = build(far, later, near)
        assertEquals(near.id, feed.hero!!.spot.id)
        assertFalse(feed.hero!!.startsLaterToday)
        assertEquals(2, feed.ongoingCount)
        assertEquals(3, feed.weekCount)
        assertEquals(ListFilter(When.NOW), feed.hero!!.listFilter)
    }

    @Test
    fun `진행 중이 없으면 오늘 시작 예정 중 가장 가까운 항목, 내일 것은 히어로가 아니다`() {
        val tomorrow = spot(10, start = "2026-11-19T10:00")
        val tonight = spot(500, start = "2026-11-18T23:59")
        val feed = build(tomorrow, tonight)
        assertEquals(tonight.id, feed.hero!!.spot.id)
        assertTrue(feed.hero!!.startsLaterToday)
        assertEquals(ListFilter(When.TODAY), feed.hero!!.listFilter)
        assertEquals(0, feed.ongoingCount)

        assertNull(build(tomorrow).hero)
    }

    @Test
    fun `오늘 끝나요는 진행 중이고 오늘 끝나는 것만, 끝 시각 빠른 순 그다음 거리`() {
        val hero = spot(1)
        val a = spot(300, end = "2026-11-18T22:00")
        val b = spot(100, end = "2026-11-18T23:59")
        val c = spot(200, end = "2026-11-18T22:00")
        val midnight = spot(50, end = "2026-11-19T00:00") // 내일 0시 = 오늘 끝이 아님
        val notStarted = spot(60, start = "2026-11-18T18:00", end = "2026-11-18T21:00")
        val feed = build(hero, a, b, c, midnight, notStarted)
        assertEquals(listOf(c.id, a.id, b.id), feed.section(FeedSectionKind.ENDING_TODAY)!!.items.map { it.id })
    }

    @Test
    fun `최소 3개보다 적으면 섹션을 숨긴다`() {
        val feed = build(spot(1), spot(2, end = "2026-11-18T20:00"), spot(3, end = "2026-11-18T21:00"))
        assertNull(feed.section(FeedSectionKind.ENDING_TODAY))
    }

    @Test
    fun `주말 창과 겹치는 항목, 평일 끝나는 것은 제외`() {
        val hero = spot(1)
        val weekday = spot(10, end = "2026-11-20T23:00") // 금요일 끝
        val sat = spot(20, start = "2026-11-21T10:00", end = "2026-11-21T18:00")
        val sun = spot(30, start = "2026-11-22T23:00", end = null)
        val nextMon = spot(40, start = "2026-11-23T00:00", end = "2026-11-23T10:00")
        val feed = build(hero, weekday, sat, sun, nextMon)
        // 히어로(진행 중, 30일까지)는 주말과 겹치지만 히어로라 빠진다 → 후보 2개라 숨김
        assertNull(feed.section(FeedSectionKind.WEEKEND))
        val withMore = build(hero, weekday, sat, sun, nextMon, spot(50))
        assertEquals(
            listOf(sat.id, sun.id, withMore.section(FeedSectionKind.WEEKEND)!!.items.last().id),
            withMore.section(FeedSectionKind.WEEKEND)!!.items.map { it.id },
        )
    }

    @Test
    fun `토요일이면 오늘부터 일요일 끝까지가 주말 창`() {
        val saturdayNoon = kst("2026-11-21T12:00")
        val (start, end) = HomeFeedBuilder.weekendWindow(saturdayNoon, zone)
        assertEquals(saturdayNoon, start)
        assertEquals(kst("2026-11-23T00:00"), end)
        val sunday = HomeFeedBuilder.weekendWindow(kst("2026-11-22T09:00"), zone)
        assertEquals(kst("2026-11-23T00:00"), sunday.second)
        val wednesday = HomeFeedBuilder.weekendWindow(now, zone)
        assertEquals(kst("2026-11-21T00:00"), wednesday.first)
    }

    @Test
    fun `무료와 계절 한정(자연) 섹션, 그리고 전체 보기 필터`() {
        val items = listOf(spot(1)) +
            (1..4).map { spot(10 * it, free = true) } +
            (1..3).map { spot(100 * it, category = Category.NATURE) }
        val feed = HomeFeedBuilder.build(items, now, zone)
        val free = feed.section(FeedSectionKind.FREE)!!
        assertEquals(4, free.items.size)
        assertTrue(free.items.all { it.isFree })
        assertEquals(ListFilter(When.WEEK, freeOnly = true), free.listFilter)
        val seasonal = feed.section(FeedSectionKind.SEASONAL)!!
        assertTrue(seasonal.items.all { it.category == Category.NATURE })
        assertEquals(ListFilter(When.WEEK, categories = setOf(Category.NATURE)), seasonal.listFilter)
    }

    @Test
    fun `히어로는 어디에도 없고, 앞 섹션에 나온 항목은 뒤로 미루되 섹션은 숨지 않는다`() {
        // 모두 무료 + 자연 + 오늘 끝: 같은 항목이 여러 섹션 후보
        val items = (1..5).map { spot(10 * it, end = "2026-11-18T20:00", category = Category.NATURE, free = true) } +
            spot(500, category = Category.NATURE, free = true)
        val feed = HomeFeedBuilder.build(items, now, zone)
        val heroId = feed.hero!!.spot.id
        assertEquals(items.first().id, heroId)
        feed.sections.forEach { s -> assertTrue(s.items.none { it.id == heroId }) }

        val ending = feed.section(FeedSectionKind.ENDING_TODAY)!!.items.map { it.id }
        val free = feed.section(FeedSectionKind.FREE)!!.items.map { it.id }
        // 무료 섹션: 아직 안 나온 항목(500m)이 먼저, 그다음 이미 나온 항목 거리순
        assertEquals(items.last().id, free.first())
        assertEquals(ending, free.drop(1))
        assertTrue(feed.section(FeedSectionKind.SEASONAL) != null)
    }

    @Test
    fun `섹션은 최대 10개, 순서는 고정`() {
        val items = (1..15).map { spot(it, end = "2026-11-18T20:00", free = true, category = Category.NATURE) } +
            (1..15).map { spot(100 + it, end = "2026-11-22T20:00") }
        val feed = HomeFeedBuilder.build(items, now, zone)
        assertEquals(FeedSectionKind.entries, feed.sections.map { it.kind })
        assertTrue(feed.sections.all { it.items.size == HomeFeedBuilder.SECTION_MAX })
    }

    @Test
    fun `빈 응답이면 isEmpty, hasMore 를 그대로 전한다`() {
        assertTrue(build().isEmpty)
        assertTrue(build(spot(1), hasMore = true).hasMore)
    }
}
