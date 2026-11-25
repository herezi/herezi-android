package com.herezi.core.domain

import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.math.abs
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 서버 `internal/domain/domain_test.go` 와 같은 경계 + 기기 조회 규칙. */
class SpotQueryEngineTest {
    private val seoul = SpotQueryEngine.Seoul

    private fun kst(y: Int, m: Int, d: Int, h: Int, min: Int = 0, s: Int = 0): Instant =
        LocalDateTime.of(y, m, d, h, min, s).atZone(seoul).toInstant()

    private fun spot(
        id: String,
        at: GeoPoint = GeoPoint.SeoulCityHall,
        starts: Instant,
        ends: Instant? = null,
        category: Category = Category.FESTIVAL,
        free: Boolean = false,
        title: String = id,
        placeName: String? = null,
        address: String? = null,
    ) = Spot(
        id = id,
        title = title,
        category = category,
        location = at,
        startsAt = starts,
        source = "test",
        fetchedAt = starts,
        endsAt = ends,
        isFree = free,
        placeName = placeName,
        address = address,
    )

    // ---- when (서버 TestWhenWindow) ----

    @Test
    fun `수요일 주말 창은 토 0시부터 일 23시59분59초`() {
        val now = kst(2026, 11, 18, 15) // 수요일
        val (from, to) = SpotQueryEngine.window(When.WEEKEND, now)
        assertEquals(DayOfWeek.SATURDAY, from.atZone(seoul).dayOfWeek)
        assertEquals(0, from.atZone(seoul).hour)
        assertEquals(kst(2026, 11, 22, 23, 59, 59), to)
    }

    @Test
    fun `오늘 창은 지금부터 오늘 23시59분59초 KST`() {
        val now = kst(2026, 11, 18, 15)
        val (from, to) = SpotQueryEngine.window(When.TODAY, now)
        assertEquals(now, from)
        assertEquals(kst(2026, 11, 18, 23, 59, 59), to)
    }

    @Test
    fun `일요일이면 주말은 지금부터 오늘 밤까지, 토요일이면 지금부터 내일 밤까지`() {
        val sun = kst(2026, 11, 22, 10)
        assertEquals(sun to kst(2026, 11, 22, 23, 59, 59), SpotQueryEngine.window(When.WEEKEND, sun))
        val sat = kst(2026, 11, 21, 10)
        assertEquals(sat to kst(2026, 11, 22, 23, 59, 59), SpotQueryEngine.window(When.WEEKEND, sat))
    }

    @Test
    fun `금요일 밤 11시 주말 창은 다음날 0시부터`() {
        val fri = kst(2026, 11, 20, 23)
        assertEquals(kst(2026, 11, 21, 0), SpotQueryEngine.window(When.WEEKEND, fri).first)
    }

    @Test
    fun `KST 와 UTC 날짜가 다른 시각에도 KST 기준 오늘`() {
        // 2026-11-18 01:30 KST = 2026-11-17 16:30 UTC
        val now = Instant.parse("2026-11-17T16:30:00Z")
        assertEquals(kst(2026, 11, 18, 23, 59, 59), SpotQueryEngine.window(When.TODAY, now).second)
        // UTC 일요일 밤 = KST 월요일 → 다가오는 토요일.
        val mon = Instant.parse("2026-11-22T16:00:00Z")
        assertEquals(kst(2026, 11, 28, 0), SpotQueryEngine.window(When.WEEKEND, mon).first)
    }

    @Test
    fun `이번 주는 지금부터 7일`() {
        val now = kst(2026, 11, 18, 15)
        assertEquals(now to now.plus(Duration.ofDays(7)), SpotQueryEngine.window(When.WEEK, now))
    }

    // ---- 겹침·끝남·무료 (서버 TestOverlaps, TestEndedAndFree) ----

    @Test
    fun `끝난 항목은 now 와 겹치지 않고 진행 중은 겹친다, 종료가 없으면 시작을 종료로 본다, 경계 포함`() {
        val now = kst(2026, 11, 18, 15)
        val hour = Duration.ofHours(1)
        val (f, t) = SpotQueryEngine.window(When.NOW, now)
        assertFalse(SpotQueryEngine.overlaps(spot("a", starts = now - hour.multipliedBy(2), ends = now - hour), f, t))
        assertTrue(SpotQueryEngine.overlaps(spot("b", starts = now - hour, ends = now + hour), f, t))
        assertFalse(SpotQueryEngine.overlaps(spot("c", starts = now - hour), f, t))
        assertTrue(SpotQueryEngine.overlaps(spot("d", starts = now), f, t))
        assertTrue(SpotQueryEngine.overlaps(spot("e", starts = now - hour, ends = now), f, t))
    }

    @Test
    fun `끝남은 ends_at 이 now 보다 앞설 때만, 종료 없음은 끝나지 않음`() {
        val now = kst(2026, 11, 18, 15)
        val m = Duration.ofMinutes(1)
        assertTrue(SpotQueryEngine.isEnded(spot("a", starts = now - m.multipliedBy(10), ends = now - m), now))
        assertFalse(SpotQueryEngine.isEnded(spot("b", starts = now - m.multipliedBy(10), ends = now + m), now))
        assertFalse(SpotQueryEngine.isEnded(spot("c", starts = now - m.multipliedBy(10)), now))
    }

    // ---- 거리 (서버 TestDistanceM) ----

    @Test
    fun `서울시청에서 광화문까지 약 1_07km`() {
        val d = SpotQueryEngine.distanceM(GeoPoint(37.5663, 126.9779), GeoPoint(37.5759, 126.9768))
        assertTrue("$d", abs(d - 1070) < 200)
    }

    // ---- 키워드 (서버 TestMatchesKeyword) ----

    @Test
    fun `키워드는 제목·장소명·주소 대소문자 무시 부분 일치, 와일드카드는 글자 그대로`() {
        val sp =
            spot("k", starts = Instant.EPOCH, title = "Seoul Lantern 빛초롱", placeName = "청계천", address = "서울 중구 50%_로")
        mapOf(
            "" to true, "lantern" to true, "LANTERN" to true, "빛초롱" to true, "청계" to true,
            "중구" to true, "%_" to true, "성수" to false, "lanterns" to false,
        ).forEach { (kw, want) ->
            assertEquals(kw, want, SpotQueryEngine.matchesKeyword(sp, SpotQueryEngine.normalizeKeyword(kw)))
        }
    }

    @Test
    fun `키워드는 앞뒤 공백을 자르고 50자까지`() {
        assertNull(SpotQueryEngine.normalizeKeyword("   "))
        assertEquals("빛초롱", SpotQueryEngine.normalizeKeyword("  빛초롱 "))
        assertEquals(50, SpotQueryEngine.normalizeKeyword("가".repeat(60))!!.length)
    }

    // ---- 목록·지도 ----

    private val now = kst(2026, 11, 18, 15)
    private val cityHall = GeoPoint.SeoulCityHall
    private fun near(dLat: Double) = GeoPoint(cityHall.lat + dLat, cityHall.lng)

    @Test
    fun `근처는 반경 안·끝나지 않은 것만 거리순, 같으면 id 순, 거리를 채운다`() {
        val day = Duration.ofDays(1)
        val spots = listOf(
            spot("b", near(0.001), now - day, now + day),
            spot("a", near(0.001), now - day, now + day),
            spot("far", near(0.1), now - day, now + day),
            spot("ended", near(0.0), now - day.multipliedBy(2), now - day),
            spot("close", near(0.0005), now - day, now + day),
        )
        val page = SpotQueryEngine.nearby(spots, SpotQuery(center = cityHall, radiusM = 3000), now)
        assertEquals(listOf("close", "a", "b"), page.items.map { it.id })
        assertEquals(111, page.items[1].distanceM)
        assertNull(page.nextCursor)
    }

    @Test
    fun `카테고리·무료·기간 필터와 페이지 커서`() {
        val day = Duration.ofDays(1)
        val spots = (1..5).map { spot("s$it", near(it * 0.001), now - day, now + day, free = it % 2 == 1) } +
            spot("perf", near(0.0), now - day, now + day, category = Category.PERFORMANCE) +
            spot("later", near(0.0), now + day.multipliedBy(3), now + day.multipliedBy(4))
        val free = SpotQueryEngine.nearby(spots, SpotQuery(center = cityHall, freeOnly = true), now)
        assertEquals(listOf("s1", "s3", "s5"), free.items.map { it.id })
        val perf = SpotQueryEngine.nearby(
            spots,
            SpotQuery(center = cityHall, categories = setOf(Category.PERFORMANCE)),
            now,
        )
        assertEquals(listOf("perf"), perf.items.map { it.id })
        assertFalse(SpotQueryEngine.nearby(spots, SpotQuery(center = cityHall), now).items.any { it.id == "later" })
        assertTrue(
            SpotQueryEngine.nearby(spots, SpotQuery(center = cityHall, period = When.WEEK), now).items.any {
                it.id ==
                    "later"
            },
        )

        val first = SpotQueryEngine.nearby(spots, SpotQuery(center = cityHall, limit = 2), now)
        assertEquals("2", first.nextCursor)
        val second = SpotQueryEngine.nearby(
            spots,
            SpotQuery(center = cityHall, limit = 2, cursor = first.nextCursor),
            now,
        )
        assertEquals(listOf("s2", "s3"), second.items.map { it.id })
        val last = SpotQueryEngine.nearby(spots, SpotQuery(center = cityHall, limit = 2, cursor = "4"), now)
        assertEquals(listOf("s4", "s5"), last.items.map { it.id })
        assertNull(last.nextCursor)
    }

    @Test
    fun `지도는 모서리 포함·영역 중심에서 가까운 순·상한을 넘으면 truncated`() {
        val day = Duration.ofDays(1)
        val bounds = GeoBounds(GeoPoint(37.0, 127.0), GeoPoint(37.2, 127.2))
        val spots = listOf(
            spot("edge", GeoPoint(37.0, 127.0), now - day, now + day),
            spot("center", GeoPoint(37.1, 127.1), now - day, now + day),
            spot("out", GeoPoint(37.3, 127.1), now - day, now + day),
            spot("ended", GeoPoint(37.1, 127.1), now - day.multipliedBy(2), now - day),
        )
        val all = SpotQueryEngine.inBounds(spots, MapQuery(bounds), now)
        assertEquals(listOf("center", "edge"), all.items.map { it.id })
        assertFalse(all.truncated)
        val one = SpotQueryEngine.inBounds(spots, MapQuery(bounds, limit = 1), now)
        assertEquals(listOf("center"), one.items.map { it.id })
        assertTrue(one.truncated)
    }

    @Test
    fun `상세는 지역 데이터에서 끝남을 기기 시각으로 계산하고, 없으면 원격`() = runTest {
        val ended = spot("ended", starts = now - Duration.ofDays(3), ends = now - Duration.ofDays(1))
        val region = object : RegionData {
            override suspend fun spots() = listOf(ended)
            override suspend fun refresh(force: Boolean) = false
        }
        val remote = mutableListOf<String>()
        val repo =
            LocalSpotsRepository(region, { id ->
                remote += id
                null
            }, java.time.Clock.fixed(now, ZoneOffset.UTC))
        assertTrue(repo.spot("ended")!!.isEnded)
        assertNull(repo.spot("other"))
        assertEquals(listOf("other"), remote)
        assertTrue(repo.nearby(SpotQuery(center = cityHall, period = When.WEEK)).items.isEmpty())
    }
}
