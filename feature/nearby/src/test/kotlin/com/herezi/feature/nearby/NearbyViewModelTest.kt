package com.herezi.feature.nearby

import com.herezi.core.domain.Category
import com.herezi.core.domain.GeoPoint
import com.herezi.core.domain.Spot
import com.herezi.core.domain.SpotPage
import com.herezi.core.domain.SpotQuery
import com.herezi.core.domain.SpotsRepository
import com.herezi.core.domain.When
import com.herezi.core.domain.fake.FakeSpotsRepository
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NearbyViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val clock = Clock.fixed(Instant.parse("2026-11-18T03:00:00Z"), ZoneOffset.UTC)

    /** 받은 쿼리를 기록하는 Fake 래퍼. */
    private class RecordingRepository(private val delegate: SpotsRepository) : SpotsRepository {
        val queries = mutableListOf<SpotQuery>()

        override suspend fun nearby(query: SpotQuery): SpotPage {
            queries += query
            return delegate.nearby(query)
        }

        override suspend fun spot(id: String): Spot? = delegate.spot(id)
    }

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `처음에는 지금 필터로 서울시청 근처를 불러온다`() = runTest(dispatcher) {
        val repo = RecordingRepository(FakeSpotsRepository(clock))
        val vm = NearbyViewModel(repo)
        advanceUntilIdle()

        val state = vm.state.value
        assertFalse(state.loading)
        assertNull(state.error)
        assertTrue(state.spots.isNotEmpty())
        assertEquals(GeoPoint.SeoulCityHall, repo.queries.single().center)
        assertEquals(When.NOW, repo.queries.single().period)
    }

    @Test
    fun `카테고리 토글과 무료 필터가 쿼리에 반영된다`() = runTest(dispatcher) {
        val repo = RecordingRepository(FakeSpotsRepository(clock))
        val vm = NearbyViewModel(repo)

        vm.toggleCategory(Category.FESTIVAL)
        vm.setFreeOnly(true)
        advanceUntilIdle()

        val last = repo.queries.last()
        assertEquals(setOf(Category.FESTIVAL), last.categories)
        assertTrue(last.freeOnly)
        assertTrue(vm.state.value.spots.all { it.category == Category.FESTIVAL && it.isFree })

        vm.toggleCategory(Category.FESTIVAL)
        advanceUntilIdle()
        assertEquals(emptySet<Category>(), repo.queries.last().categories)
    }

    @Test
    fun `기간을 바꾸면 다시 불러오고 같은 값이면 요청하지 않는다`() = runTest(dispatcher) {
        val repo = RecordingRepository(FakeSpotsRepository(clock))
        val vm = NearbyViewModel(repo)
        advanceUntilIdle()

        vm.setPeriod(When.NOW)
        advanceUntilIdle()
        assertEquals(1, repo.queries.size)

        vm.setPeriod(When.WEEK)
        advanceUntilIdle()
        assertEquals(When.WEEK, repo.queries.last().period)
        assertEquals(When.WEEK, vm.state.value.filter.period)
    }

    @Test
    fun `실패하면 에러를 보여주고 다시 시도할 수 있다`() = runTest(dispatcher) {
        var fail = true
        val repo =
            object : SpotsRepository {
                override suspend fun nearby(query: SpotQuery): SpotPage {
                    if (fail) error("네트워크 오류")
                    return SpotPage(emptyList())
                }

                override suspend fun spot(id: String): Spot? = null
            }
        val vm = NearbyViewModel(repo)
        advanceUntilIdle()
        assertEquals("네트워크 오류", vm.state.value.error)

        fail = false
        vm.refresh()
        advanceUntilIdle()
        assertNull(vm.state.value.error)
    }
}
