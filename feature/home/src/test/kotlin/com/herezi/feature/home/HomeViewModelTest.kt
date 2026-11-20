package com.herezi.feature.home

import com.herezi.core.domain.FeedSectionKind
import com.herezi.core.domain.GeoPoint
import com.herezi.core.domain.HeroSection
import com.herezi.core.domain.HomeFeed
import com.herezi.core.domain.HomeFeedBuilder
import com.herezi.core.domain.MapQuery
import com.herezi.core.domain.MapSpots
import com.herezi.core.domain.Spot
import com.herezi.core.domain.SpotPage
import com.herezi.core.domain.SpotQuery
import com.herezi.core.domain.SpotsRepository
import com.herezi.core.domain.When
import com.herezi.core.domain.fake.FakeLocationSource
import com.herezi.core.domain.fake.FakeReverseGeocoder
import com.herezi.core.domain.fake.FakeSpotsRepository
import com.herezi.core.domain.fake.InMemorySavedSpotsRepository
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val start = Instant.parse("2026-11-18T03:00:00Z")

    private class MutableClock(var now: Instant) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC

        override fun withZone(zone: ZoneId?): Clock = this

        override fun instant(): Instant = now
    }

    private val clock = MutableClock(start)
    private val saved = InMemorySavedSpotsRepository()

    private class Recording(private val delegate: SpotsRepository) : SpotsRepository {
        val queries = mutableListOf<SpotQuery>()
        var fail = false

        override suspend fun nearby(query: SpotQuery): SpotPage {
            queries += query
            if (fail) error("네트워크 오류")
            return delegate.nearby(query)
        }

        override suspend fun inBounds(query: MapQuery): MapSpots = delegate.inBounds(query)

        override suspend fun placeName(point: GeoPoint): String? = delegate.placeName(point)

        override suspend fun spot(id: String): Spot? = delegate.spot(id)
    }

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun vm(repo: SpotsRepository, location: FakeLocationSource = FakeLocationSource(), name: String? = null) =
        HomeViewModel(repo, location, FakeReverseGeocoder(name), saved, clock)

    @Test
    fun `한 번 호출(3km, 이번 주, 100개)로 피드를 만들고 위치 거부면 서울시청 주변`() = runTest(dispatcher) {
        val repo = Recording(FakeSpotsRepository(clock))
        val vm = vm(repo)
        advanceUntilIdle()

        val q = repo.queries.single()
        assertEquals(HomeFeedBuilder.HOME_RADIUS_M, q.radiusM)
        assertEquals(When.WEEK, q.period)
        assertEquals(HomeFeedBuilder.HOME_LIMIT, q.limit)
        assertEquals(GeoPoint.SeoulCityHall, q.center)
        val state = vm.state.value
        assertNotNull(state.feed?.hero)
        assertFalse(state.loading)
        assertFalse(state.locationKnown)
        assertEquals("서울시청 주변", state.locationName)
        assertEquals(start, state.now)
    }

    @Test
    fun `위치를 알면 역지오코딩 이름`() = runTest(dispatcher) {
        val vm = vm(Recording(FakeSpotsRepository(clock)), FakeLocationSource(GeoPoint(37.57, 126.98)), "중구 태평로1가")
        advanceUntilIdle()
        assertEquals("중구 태평로1가", vm.state.value.locationName)
        assertTrue(vm.state.value.locationKnown)
    }

    @Test
    fun `처음 실패는 오류, 데이터가 있을 때 실패는 화면 유지와 토스트`() = runTest(dispatcher) {
        val repo = Recording(FakeSpotsRepository(clock)).apply { fail = true }
        val vm = vm(repo)
        val messages = mutableListOf<String>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.messages.collect { messages += it } }
        advanceUntilIdle()
        assertTrue(vm.state.value.error)

        repo.fail = false
        vm.refresh()
        advanceUntilIdle()
        assertFalse(vm.state.value.error)
        val feed = vm.state.value.feed

        repo.fail = true
        vm.refresh()
        advanceUntilIdle()
        assertFalse(vm.state.value.error)
        assertEquals(feed, vm.state.value.feed)
        assertEquals(listOf(HomeViewModel.MSG_STALE), messages)
    }

    @Test
    fun `15분 넘게 지나 돌아오면 다시 부른다`() = runTest(dispatcher) {
        val repo = Recording(FakeSpotsRepository(clock))
        val vm = vm(repo)
        advanceUntilIdle()

        clock.now = start.plus(Duration.ofMinutes(10))
        vm.onResume()
        advanceUntilIdle()
        assertEquals(1, repo.queries.size)

        clock.now = start.plus(Duration.ofMinutes(16))
        vm.onResume()
        advanceUntilIdle()
        assertEquals(2, repo.queries.size)
    }

    @Test
    fun `히어로 저장 토글`() = runTest(dispatcher) {
        val vm = vm(Recording(FakeSpotsRepository(clock)))
        val messages = mutableListOf<String>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.messages.collect { messages += it } }
        advanceUntilIdle()
        val hero = vm.state.value.feed!!.hero!!.spot

        vm.toggleSave(hero)
        advanceUntilIdle()
        assertTrue(hero.id in vm.state.value.savedIds)
        vm.toggleSave(hero)
        advanceUntilIdle()
        assertFalse(hero.id in vm.state.value.savedIds)
        assertEquals(listOf("저장했어요", "저장을 취소했어요"), messages)
    }

    @Test
    fun `헤드라인과 섹션 제목`() {
        val spot = FakeSpotsRepository.seedSpots(start).first()
        val feed = HomeFeed(HeroSection(spot, false), emptyList(), ongoingCount = 24, weekCount = 40, hasMore = false)
        assertEquals("지금 근처에" to "볼거리 24개 있어요", homeHeadline(feed, locationKnown = true))
        assertEquals("지금 근처에" to "볼거리 24개 이상 있어요", homeHeadline(feed.copy(hasMore = true), true))
        assertEquals("이번 주 근처에" to "볼거리 40개 있어요", homeHeadline(feed.copy(ongoingCount = 0), true))
        assertEquals("서울시청 주변에" to "볼거리 24개 있어요", homeHeadline(feed, locationKnown = false))
        assertEquals("오늘 가장 가까운 곳", HeroSection(spot, startsLaterToday = true).title)
        assertEquals("이번 주말 놓치면 아쉬운", FeedSectionKind.WEEKEND.title)
    }
}
