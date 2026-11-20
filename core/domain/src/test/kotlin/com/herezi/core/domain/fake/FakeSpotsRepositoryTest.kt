package com.herezi.core.domain.fake

import com.herezi.core.domain.Category
import com.herezi.core.domain.GeoPoint
import com.herezi.core.domain.SpotQuery
import com.herezi.core.domain.When
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeSpotsRepositoryTest {
    // 2026-11-18(수) 12:00 KST
    private val clock = Clock.fixed(Instant.parse("2026-11-18T03:00:00Z"), ZoneOffset.UTC)
    private val repo = FakeSpotsRepository(clock)
    private val wide = SpotQuery(center = GeoPoint.SeoulCityHall, radiusM = 20_000)

    @Test
    fun `거리순으로 정렬하고 거리를 채운다`() = runTest {
        val items = repo.nearby(wide).items
        assertTrue(items.isNotEmpty())
        assertEquals(items.map { it.distanceM }, items.map { it.distanceM }.sortedBy { it })
    }

    @Test
    fun `반경 밖 항목은 제외한다`() = runTest {
        val items = repo.nearby(SpotQuery(center = GeoPoint.SeoulCityHall, radiusM = 3_000)).items
        assertTrue(items.all { it.distanceM!! <= 3_000 })
        assertTrue(items.none { it.category == Category.POPUP }) // 성수는 약 8km
    }

    @Test
    fun `카테고리와 무료 필터를 적용한다`() = runTest {
        val query = wide.copy(categories = setOf(Category.NATURE, Category.POPUP), freeOnly = true)
        val items = repo.nearby(query).items
        assertEquals(setOf(Category.NATURE, Category.POPUP), items.map { it.category }.toSet())
        assertTrue(items.all { it.isFree })
    }

    @Test
    fun `지금 필터는 아직 시작하지 않은 항목을 숨기고 이번 주는 보여준다`() = runTest {
        val now = repo.nearby(wide).items.map { it.id }
        val week = repo.nearby(wide.copy(period = When.WEEK)).items.map { it.id }
        assertTrue("fake:pet-festival" !in now)
        assertTrue("fake:pet-festival" in week)
    }

    @Test
    fun `상세는 id로 찾고 없으면 null`() = runTest {
        assertEquals("여의도 봄꽃 축제", repo.spot("fake:yeouido-cherry")?.title)
        assertNull(repo.spot("nope"))
    }
}
