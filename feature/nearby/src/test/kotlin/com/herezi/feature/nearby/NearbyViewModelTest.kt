package com.herezi.feature.nearby

import androidx.lifecycle.SavedStateHandle
import com.herezi.core.domain.Category
import com.herezi.core.domain.GeoPoint
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

    /** 받은 쿼리를 기록하는 Fake 래퍼. [pages] 가 있으면 커서별로 돌려준다. */
    private class RecordingRepository(
        private val delegate: SpotsRepository,
        private val pages: Map<String?, SpotPage>? = null,
    ) : SpotsRepository {
        val queries = mutableListOf<SpotQuery>()
        var fail = false

        override suspend fun nearby(query: SpotQuery): SpotPage {
            queries += query
            if (fail) error("네트워크 오류")
            return pages?.getValue(query.cursor) ?: delegate.nearby(query)
        }

        override suspend fun inBounds(query: MapQuery): MapSpots = delegate.inBounds(query)

        override suspend fun spot(id: String): Spot? = delegate.spot(id)
    }

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun vm(
        repo: SpotsRepository,
        args: Map<String, Any?> = emptyMap(),
        location: FakeLocationSource = FakeLocationSource(),
        geocoder: FakeReverseGeocoder = FakeReverseGeocoder(),
    ) = NearbyViewModel(SavedStateHandle(args), repo, location, geocoder)

    @Test
    fun `처음에는 지금 필터로 서울시청 근처를 불러오고 위치명은 서울시청 주변`() = runTest(dispatcher) {
        val repo = RecordingRepository(FakeSpotsRepository(clock))
        val vm = vm(repo)
        advanceUntilIdle()

        val state = vm.state.value
        assertFalse(state.loading)
        assertNull(state.error)
        assertTrue(state.spots.isNotEmpty())
        assertEquals(GeoPoint.SeoulCityHall, repo.queries.single().center)
        assertEquals(When.NOW, repo.queries.single().period)
        assertEquals("서울시청 주변", state.locationName)
        assertTrue(state.locationOff)
    }

    @Test
    fun `내비게이션 인자로 시작 필터를 받는다`() = runTest(dispatcher) {
        val repo = RecordingRepository(FakeSpotsRepository(clock))
        val vm = vm(repo, mapOf("when" to "week", "category" to "nature,pet", "free" to true))
        advanceUntilIdle()
        val q = repo.queries.single()
        assertEquals(When.WEEK, q.period)
        assertEquals(setOf(Category.NATURE, Category.PET), q.categories)
        assertTrue(q.freeOnly)
        assertTrue(vm.state.value.filtered)
    }

    @Test
    fun `지도에서 넘겨받은 중심이 있으면 그 기준으로 부르고 그 동네 이름을 쓴다`() = runTest(dispatcher) {
        val repo = RecordingRepository(FakeSpotsRepository(clock))
        val vm = vm(
            repo,
            mapOf("lat" to "37.5446", "lng" to "127.0557"),
            location = FakeLocationSource(),
            geocoder = FakeReverseGeocoder("성동구 성수동2가"),
        )
        advanceUntilIdle()
        assertEquals(GeoPoint(37.5446, 127.0557), repo.queries.single().center)
        assertEquals("성동구 성수동2가", vm.state.value.locationName)
        // 위치 권한은 여전히 없으니 배너는 그대로.
        assertTrue(vm.state.value.locationOff)
    }

    @Test
    fun `위치를 알면 역지오코딩 이름, 모르면 내 주변`() = runTest(dispatcher) {
        val here = FakeLocationSource(GeoPoint(37.5663, 126.9779))
        val named =
            vm(
                RecordingRepository(FakeSpotsRepository(clock)),
                location = here,
                geocoder = FakeReverseGeocoder("중구 태평로1가"),
            )
        val unnamed = vm(RecordingRepository(FakeSpotsRepository(clock)), location = here)
        advanceUntilIdle()
        assertEquals("중구 태평로1가", named.state.value.locationName)
        assertFalse(named.state.value.locationOff)
        assertEquals("내 주변", unnamed.state.value.locationName)
    }

    @Test
    fun `카테고리 토글과 전체, 무료 필터가 쿼리에 반영된다`() = runTest(dispatcher) {
        val repo = RecordingRepository(FakeSpotsRepository(clock))
        val vm = vm(repo)

        vm.toggleCategory(Category.FESTIVAL)
        vm.setFreeOnly(true)
        advanceUntilIdle()

        val last = repo.queries.last()
        assertEquals(setOf(Category.FESTIVAL), last.categories)
        assertTrue(last.freeOnly)
        assertTrue(vm.state.value.spots.all { it.category == Category.FESTIVAL && it.isFree })

        vm.clearCategories()
        advanceUntilIdle()
        assertEquals(emptySet<Category>(), repo.queries.last().categories)
    }

    @Test
    fun `기간을 바꾸면 다시 불러오고 같은 값이면 요청하지 않는다`() = runTest(dispatcher) {
        val repo = RecordingRepository(FakeSpotsRepository(clock))
        val vm = vm(repo)
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
    fun `위치를 알면 그 기준으로 부르고, 돌아왔을 때 같으면 다시 부르지 않는다`() = runTest(dispatcher) {
        val repo = RecordingRepository(FakeSpotsRepository(clock))
        val location = FakeLocationSource()
        val vm = vm(repo, location = location)
        advanceUntilIdle()

        vm.refreshLocation()
        advanceUntilIdle()
        assertEquals(1, repo.queries.size)

        location.location = GeoPoint(37.57, 126.98)
        vm.refreshLocation()
        advanceUntilIdle()
        assertEquals(2, repo.queries.size)
        assertEquals(GeoPoint(37.57, 126.98), repo.queries.last().center)
        assertFalse(vm.state.value.locationOff)
    }

    @Test
    fun `필터 초기화는 기본 필터로 돌리고 다시 불러온다`() = runTest(dispatcher) {
        val repo = RecordingRepository(FakeSpotsRepository(clock))
        val vm = vm(repo)
        vm.toggleCategory(Category.PET)
        vm.setPeriod(When.WEEK)
        advanceUntilIdle()

        vm.resetFilters()
        advanceUntilIdle()
        assertEquals(NearbyFilter(), vm.state.value.filter)
        assertFalse(vm.state.value.filtered)
    }

    @Test
    fun `다음 페이지를 이어 붙이고, 실패하면 표시 후 다시 시도한다`() = runTest(dispatcher) {
        val fake = FakeSpotsRepository(clock)
        val all = fake.nearby(SpotQuery(GeoPoint.SeoulCityHall, radiusM = 20_000, period = When.WEEK)).items
        val repo = RecordingRepository(
            fake,
            pages = mapOf(null to SpotPage(all.take(2), "c2"), "c2" to SpotPage(all.drop(2).take(1), null)),
        )
        val vm = vm(repo)
        advanceUntilIdle()
        assertTrue(vm.state.value.hasMore)

        repo.fail = true
        vm.loadMore()
        advanceUntilIdle()
        assertTrue(vm.state.value.moreFailed)
        assertEquals(2, vm.state.value.spots.size)

        repo.fail = false
        vm.loadMore()
        advanceUntilIdle()
        assertEquals("c2", repo.queries.last().cursor)
        assertEquals(3, vm.state.value.spots.size)
        assertFalse(vm.state.value.hasMore)

        vm.loadMore()
        advanceUntilIdle()
        assertEquals(3, repo.queries.size) // 더 없으면 요청하지 않는다
    }

    @Test
    fun `실패하면 에러를 보여주고 다시 시도할 수 있다`() = runTest(dispatcher) {
        val repo = RecordingRepository(FakeSpotsRepository(clock))
        repo.fail = true
        val vm = vm(repo)
        advanceUntilIdle()
        assertEquals("네트워크 오류", vm.state.value.error)

        repo.fail = false
        vm.refresh()
        advanceUntilIdle()
        assertNull(vm.state.value.error)
    }
}
