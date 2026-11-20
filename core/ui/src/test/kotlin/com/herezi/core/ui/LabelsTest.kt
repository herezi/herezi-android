package com.herezi.core.ui

import java.time.Instant
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LabelsTest {
    // 2026-11-20(금) 12:00 KST
    private val now = Instant.parse("2026-11-20T03:00:00Z")

    private fun kst(value: String): Instant = LocalDateTime.parse(value).atZone(Seoul).toInstant()

    @Test
    fun `거리는 10m 단위 m, 10km 미만 소수 한 자리 km, 그 이상 정수 km`() {
        assertEquals("350m", formatDistance(347))
        assertEquals("1.0km", formatDistance(996))
        assertEquals("1.5km", formatDistance(1530))
        assertEquals("12km", formatDistance(12_400))
        assertEquals("350미터", spokenDistance(352))
        assertEquals("1.2킬로미터", spokenDistance(1200))
    }

    @Test
    fun `카드 기간은 여러 날, 끝 없음, 해 넘김을 구분한다`() {
        val start = kst("2026-11-20T00:00:00")
        assertEquals(PeriodText("11.20(금) – 12.14(월)"), formatCardPeriod(start, kst("2026-12-14T00:00:00"), now))
        assertEquals(PeriodText("11.20(금)부터"), formatCardPeriod(start, null, now))
        assertEquals(
            PeriodText("2026.12.20 – 2027.01.04"),
            formatCardPeriod(kst("2026-12-20T10:00:00"), kst("2027-01-04T10:00:00"), now),
        )
    }

    @Test
    fun `카드 기간은 오늘 하루면 시각, 진행 중 오늘 끝나면 오늘까지`() {
        assertEquals(
            PeriodText("오늘 18:00 – 22:00"),
            formatCardPeriod(kst("2026-11-20T18:00:00"), kst("2026-11-20T22:00:00"), now),
        )
        assertEquals(
            PeriodText("오늘까지", emphasized = true),
            formatCardPeriod(kst("2026-11-10T10:00:00"), kst("2026-11-20T22:00:00"), now),
        )
    }

    @Test
    fun `상세 기간과 시간`() {
        val start = kst("2026-11-20T17:00:00")
        val end = kst("2026-12-14T22:00:00")
        assertEquals("11월 20일(금) – 12월 14일(월)", formatDetailPeriod(start, end))
        assertEquals("11월 20일(금)부터", formatDetailPeriod(start, null))
        assertEquals("매일 17:00 – 22:00", formatDetailTime(start, end))
        assertEquals("18:00 – 22:00", formatDetailTime(kst("2026-11-20T18:00:00"), kst("2026-11-20T22:00:00")))
        assertNull(formatDetailTime(kst("2026-11-20T00:00:00"), end))
        assertEquals("오늘까지", formatDetailPeriodNote(kst("2026-11-20T22:00:00"), now))
        assertNull(formatDetailPeriodNote(end, now))
        assertEquals("11월 20일부터 12월 14일까지", spokenPeriod(start, end))
    }

    @Test
    fun `갱신 시각은 분, 시간, 날짜로 보여준다`() {
        assertEquals("방금 갱신", formatFetchedAt(now.minusSeconds(30), now))
        assertEquals("5분 전 갱신", formatFetchedAt(now.minusSeconds(5 * 60), now))
        assertEquals("2시간 전 갱신", formatFetchedAt(now.minusSeconds(2 * 3600 + 59 * 60), now))
        assertEquals("11월 18일 갱신", formatFetchedAt(kst("2026-11-18T09:12:00"), now))
        assertEquals("11월 18일 오전 9시 12분", spokenDateTime(kst("2026-11-18T09:12:00")))
    }

    @Test
    fun `종료 안내와 출처 이름`() {
        assertEquals("11월 17일에 끝난 볼거리예요.", formatEndedNotice(kst("2026-11-17T21:00:00")))
        assertEquals("끝난 볼거리예요.", formatEndedNotice(null))
        assertEquals("서울 열린데이터광장", sourceLabel("seoul_culture"))
        assertEquals("한국관광공사", sourceLabel("tourapi"))
        assertEquals("fake", sourceLabel("fake"))
    }

    @Test
    fun `히어로 배지와 기간`() {
        assertEquals("오늘 17:00 시작", formatHeroBadge(kst("2026-11-20T17:00:00"), kst("2026-11-20T22:00:00"), now))
        assertEquals("오늘 22:00까지", formatHeroBadge(kst("2026-11-19T10:00:00"), kst("2026-11-20T22:00:00"), now))
        assertEquals("오늘까지", formatHeroBadge(kst("2026-11-19T10:00:00"), kst("2026-11-20T23:59:00"), now))
        assertEquals("지금 진행 중", formatHeroBadge(kst("2026-11-19T10:00:00"), kst("2026-12-14T22:00:00"), now))
        assertEquals("지금 진행 중", formatHeroBadge(kst("2026-11-19T10:00:00"), null, now))
        assertNull(formatHeroBadge(kst("2026-11-21T10:00:00"), null, now))
        assertEquals("12.14(월)까지", formatHeroPeriod(kst("2026-11-19T10:00:00"), kst("2026-12-14T22:00:00"), now))
        assertEquals("오늘 17:00 – 22:00", formatHeroPeriod(kst("2026-11-20T17:00:00"), kst("2026-11-20T22:00:00"), now))
    }
}
