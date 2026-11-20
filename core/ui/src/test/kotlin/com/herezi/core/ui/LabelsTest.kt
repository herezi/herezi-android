package com.herezi.core.ui

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class LabelsTest {
    @Test
    fun `거리는 1km 미만이면 m, 이상이면 km`() {
        assertEquals("420m", formatDistance(420))
        assertEquals("1.5km", formatDistance(1530))
    }

    @Test
    fun `기간은 서울 시간 기준 월일로 보여준다`() {
        val start = Instant.parse("2027-03-31T16:00:00Z") // 4.1 01:00 KST
        val end = Instant.parse("2027-04-07T10:00:00Z")
        assertEquals("4.1 ~ 4.7", formatPeriod(start, end))
        assertEquals("4.1 ~", formatPeriod(start, null))
    }
}
