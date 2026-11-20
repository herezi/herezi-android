package com.herezi.feature.search

import com.herezi.core.domain.GeoPoint
import com.herezi.core.domain.MapQuery
import com.herezi.core.domain.MapSpots
import com.herezi.core.domain.Spot
import com.herezi.core.domain.SpotPage
import com.herezi.core.domain.SpotQuery
import com.herezi.core.domain.SpotsRepository
import com.herezi.core.domain.fake.FakeLocationSource
import com.herezi.core.domain.fake.FakeSpotsRepository
import com.herezi.core.domain.fake.InMemoryRecentSearchesRepository
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val clock = Clock.fixed(Instant.parse("2026-11-18T03:00:00Z"), ZoneOffset.UTC)
    private val recent = InMemoryRecentSearchesRepository()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private class Recording(private val delegate: SpotsRepository, var fail: Boolean = false) : SpotsRepository {
        val queries = mutableListOf<SpotQuery>()

        override suspend fun nearby(query: SpotQuery): SpotPage {
            queries += query
            if (fail) error("네트워크 오류")
            return delegate.nearby(query)
        }

        override suspend fun inBounds(query: MapQuery): MapSpots = delegate.inBounds(query)

        override suspend fun placeName(point: GeoPoint): String? = delegate.placeName(point)

        override suspend fun spot(id: String): Spot? = delegate.spot(id)
    }

    @Test
    fun `검색하면 q 와 20km 반경, 서울시청 기준으로 조회하고 최근 검색에 남긴다`() = runTest(dispatcher) {
        val repo = Recording(FakeSpotsRepository(clock))
        val vm = SearchViewModel(repo, recent, FakeLocationSource())
        vm.onInput("  광화문 ")
        vm.search()
        advanceUntilIdle()

        val q = repo.queries.single()
        assertEquals("광화문", q.keyword)
        assertEquals(SpotQuery.SEARCH_RADIUS_M, q.radiusM)
        assertEquals(GeoPoint.SeoulCityHall, q.center)
        val result = vm.state.value.result as SearchResult.Found
        assertEquals(listOf("fake:gwanghwamun-festival"), result.items.map { it.id })
        assertEquals(listOf("광화문"), vm.state.value.recent)
    }

    @Test
    fun `두 글자 미만이면 조회하지 않고 토스트`() = runTest(dispatcher) {
        val repo = Recording(FakeSpotsRepository(clock))
        val vm = SearchViewModel(repo, recent, FakeLocationSource())
        val messages = mutableListOf<String>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.messages.collect { messages += it } }
        vm.search(" 빛 ")
        advanceUntilIdle()

        assertTrue(repo.queries.isEmpty())
        assertEquals(listOf("두 글자 이상 입력해 주세요"), messages)
        assertEquals(SearchResult.Idle, vm.state.value.result)
    }

    @Test
    fun `결과가 없으면 Empty`() = runTest(dispatcher) {
        val vm = SearchViewModel(Recording(FakeSpotsRepository(clock)), recent, FakeLocationSource())
        vm.search("없는볼거리")
        advanceUntilIdle()
        assertEquals(SearchResult.Empty("없는볼거리"), vm.state.value.result)
    }

    @Test
    fun `실패하면 Failed 이고 다시 시도할 수 있다`() = runTest(dispatcher) {
        val repo = Recording(FakeSpotsRepository(clock), fail = true)
        val vm = SearchViewModel(repo, recent, FakeLocationSource(GeoPoint(37.55, 127.05)))
        vm.search("성수")
        advanceUntilIdle()
        assertEquals(SearchResult.Failed("성수"), vm.state.value.result)
        assertEquals(GeoPoint(37.55, 127.05), repo.queries.last().center)

        repo.fail = false
        vm.retry()
        advanceUntilIdle()
        assertTrue(vm.state.value.result is SearchResult.Found)
    }

    @Test
    fun `최근 검색 지우기와 입력 지우기`() = runTest(dispatcher) {
        val vm = SearchViewModel(Recording(FakeSpotsRepository(clock)), recent, FakeLocationSource())
        vm.search("성수")
        vm.search("광화문")
        advanceUntilIdle()
        assertEquals(listOf("광화문", "성수"), vm.state.value.recent)

        vm.removeRecent("성수")
        advanceUntilIdle()
        assertEquals(listOf("광화문"), vm.state.value.recent)

        vm.clearInput()
        assertEquals(SearchResult.Idle, vm.state.value.result)
        assertEquals("", vm.state.value.input)

        vm.clearRecent()
        advanceUntilIdle()
        assertTrue(vm.state.value.recent.isEmpty())
    }
}
