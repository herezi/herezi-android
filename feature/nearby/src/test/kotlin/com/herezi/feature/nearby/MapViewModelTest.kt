package com.herezi.feature.nearby

import androidx.lifecycle.SavedStateHandle
import com.herezi.core.domain.Category
import com.herezi.core.domain.GeoBounds
import com.herezi.core.domain.GeoPoint
import com.herezi.core.domain.MapQuery
import com.herezi.core.domain.MapSpots
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MapViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val clock = Clock.fixed(Instant.parse("2026-11-18T03:00:00Z"), ZoneOffset.UTC)

    /** 도심(시청·광화문·세종문화회관·현대미술관 포함). */
    private val downtown = GeoBounds(GeoPoint(37.55, 126.96), GeoPoint(37.59, 127.00))

    /** 성수동(팝업). */
    private val seongsu = GeoBounds(GeoPoint(37.53, 127.04), GeoPoint(37.56, 127.07))

    /** 볼거리 없는 곳. */
    private val empty = GeoBounds(GeoPoint(35.0, 129.0), GeoPoint(35.01, 129.01))

    /** inBounds 쿼리를 기록. [truncated]·[fail] 로 응답을 바꾼다. */
    private class Recording(private val delegate: SpotsRepository) : SpotsRepository by delegate {
        val queries = mutableListOf<MapQuery>()
        var truncated = false
        var fail = false

        override suspend fun inBounds(query: MapQuery): MapSpots {
            queries += query
            if (fail) error("네트워크 오류")
            return delegate.inBounds(query).let { if (truncated) it.copy(truncated = true) else it }
        }
    }

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private val repo = Recording(FakeSpotsRepository(clock))

    private fun vm(
        args: Map<String, Any?> = emptyMap(),
        location: FakeLocationSource = FakeLocationSource(),
        geocoder: FakeReverseGeocoder = FakeReverseGeocoder("중구 태평로1가"),
    ) = MapViewModel(SavedStateHandle(args), repo, location, geocoder)

    @Test
    fun `위치를 모르면 서울시청에서 시작하고, 첫 카메라 멈춤에 그 영역을 찾는다`() = runTest(dispatcher) {
        val vm = vm(mapOf("when" to "week", "category" to "festival"))
        advanceUntilIdle()
        assertTrue(vm.state.value.ready)
        assertEquals(GeoPoint.SeoulCityHall, vm.state.value.origin)
        assertEquals("서울시청 주변", vm.state.value.locationName)
        assertTrue(repo.queries.isEmpty())

        vm.onCameraIdle(downtown, byUser = false)
        advanceUntilIdle()

        val q = repo.queries.single()
        assertEquals(downtown, q.bounds)
        assertEquals(When.WEEK, q.period)
        assertEquals(setOf(Category.FESTIVAL), q.categories)
        val s = vm.state.value
        assertEquals(listOf("fake:gwanghwamun-festival"), s.spots.map { it.id })
        assertEquals("이 지역 볼거리 1개", s.headline)
        assertNotNull("거리는 기준 위치에서 잰다", s.spots.single().distanceM)
    }

    @Test
    fun `위치를 알 때도 지도 준비 시 첫 영역으로 바로 찾고, 같은 영역 멈춤이 또 와도 다시 찾지 않는다`() = runTest(dispatcher) {
        val here = GeoPoint(37.5700, 126.9768)
        val vm = vm(location = FakeLocationSource(location = here))
        advanceUntilIdle()
        assertEquals(here, vm.state.value.origin)
        assertFalse(vm.state.value.locationOff)
        assertEquals("찾는 중…", vm.state.value.headline)

        // SpotMap 이 지도 준비 시 알리는 첫 영역(#40), 이어서 SDK idle 이 같은 영역으로 한 번 더 올 수 있다.
        vm.onCameraIdle(downtown, byUser = false)
        vm.onCameraIdle(downtown, byUser = false)
        advanceUntilIdle()

        assertEquals(1, repo.queries.size)
        val s = vm.state.value
        assertTrue(s.searchedOnce)
        assertFalse(s.showResearch)
        assertEquals("이 지역 볼거리 ${s.spots.size}개", s.headline)
        assertTrue(s.spots.isNotEmpty())
    }

    @Test
    fun `사용자가 옮기면 자동으로 찾지 않고 다시 찾기를 띄운다, 코드로 옮긴 건 무시`() = runTest(dispatcher) {
        val vm = vm()
        advanceUntilIdle()
        vm.onCameraIdle(downtown, byUser = false)
        advanceUntilIdle()

        vm.onCameraIdle(seongsu, byUser = false)
        assertFalse(vm.state.value.showResearch)
        vm.onCameraIdle(seongsu, byUser = true)
        advanceUntilIdle()

        assertTrue(vm.state.value.showResearch)
        assertEquals(1, repo.queries.size)
    }

    @Test
    fun `다시 찾기는 보이는 영역을 찾고 위치명과 목록 기준을 그 중심으로 바꾼다`() = runTest(dispatcher) {
        val geocoder = FakeReverseGeocoder("중구 태평로1가")
        val vm = vm(geocoder = geocoder)
        advanceUntilIdle()
        vm.onCameraIdle(downtown, byUser = false)
        advanceUntilIdle()
        assertNull(vm.state.value.listCenter)

        vm.onCameraIdle(seongsu, byUser = true)
        geocoder.name = "성동구 성수동2가"
        vm.researchHere()
        assertTrue("찾는 동안 알약을 남겨 둔다", vm.state.value.researching)
        advanceUntilIdle()

        val s = vm.state.value
        assertEquals(seongsu, repo.queries.last().bounds)
        assertFalse(s.showResearch)
        assertFalse(s.researching)
        assertEquals(seongsu.center, s.listCenter)
        assertEquals("성동구 성수동2가", s.locationName)
        assertEquals(listOf("fake:seongsu-popup"), s.spots.map { it.id })
    }

    @Test
    fun `필터를 바꾸면 지금 보이는 영역으로 바로 찾는다`() = runTest(dispatcher) {
        val vm = vm()
        advanceUntilIdle()
        vm.onCameraIdle(downtown, byUser = false)
        vm.onCameraIdle(seongsu, byUser = true)
        advanceUntilIdle()

        vm.setFreeOnly(true)
        advanceUntilIdle()

        assertEquals(2, repo.queries.size)
        assertEquals(seongsu, repo.queries.last().bounds)
        assertTrue(repo.queries.last().freeOnly)
        assertFalse(vm.state.value.showResearch)

        vm.setFreeOnly(true) // 같은 값 → 요청 없음
        advanceUntilIdle()
        assertEquals(2, repo.queries.size)
    }

    @Test
    fun `잘린 결과는 영역당 한 번만 알린다`() = runTest(dispatcher) {
        repo.truncated = true
        val vm = vm()
        advanceUntilIdle()
        vm.onCameraIdle(downtown, byUser = false)
        advanceUntilIdle()
        assertTrue(vm.state.value.truncatedNotice)
        vm.truncatedShown()

        vm.setPeriod(When.WEEK) // 같은 영역 다시 찾기
        advanceUntilIdle()
        assertFalse(vm.state.value.truncatedNotice)

        vm.onCameraIdle(seongsu, byUser = true)
        vm.researchHere()
        advanceUntilIdle()
        assertTrue(vm.state.value.truncatedNotice)
    }

    @Test
    fun `빈 영역은 이번 주로 보기, 이번 주면 버튼 없음`() = runTest(dispatcher) {
        val vm = vm()
        advanceUntilIdle()
        vm.onCameraIdle(empty, byUser = false)
        advanceUntilIdle()
        assertEquals("이 지역엔 지금 볼거리가 없어요", vm.state.value.headline)
        assertEquals(HeadlineAction.WEEK, vm.state.value.headlineAction)

        vm.setPeriod(When.WEEK)
        advanceUntilIdle()
        assertNull(vm.state.value.headlineAction)
    }

    @Test
    fun `오류면 기존 핀을 두고 다시 시도할 수 있다`() = runTest(dispatcher) {
        val vm = vm()
        advanceUntilIdle()
        vm.onCameraIdle(downtown, byUser = false)
        advanceUntilIdle()
        val before = vm.state.value.spots
        assertTrue(before.isNotEmpty())

        repo.fail = true
        vm.setPeriod(When.TODAY)
        assertEquals("찾는 중…", vm.state.value.headline)
        advanceUntilIdle()
        assertEquals(before, vm.state.value.spots)
        assertEquals("불러오지 못했어요", vm.state.value.headline)
        assertEquals(HeadlineAction.RETRY, vm.state.value.headlineAction)

        repo.fail = false
        vm.retry()
        advanceUntilIdle()
        assertFalse(vm.state.value.error)
        assertEquals(downtown, repo.queries.last().bounds)
    }

    @Test
    fun `내 위치 버튼 - 권한이 없으면 설정을 연다`() = runTest(dispatcher) {
        val vm = vm()
        advanceUntilIdle()
        vm.moveToMyLocation()
        advanceUntilIdle()
        assertTrue(vm.state.value.openLocationSettings)
        assertNull(vm.state.value.cameraRequest)
        vm.locationSettingsOpened()
        assertFalse(vm.state.value.openLocationSettings)
    }

    @Test
    fun `내 위치 버튼 - 내 위치로 옮기고 멈추면 그 지역을 찾는다`() = runTest(dispatcher) {
        val here = GeoPoint(37.5446, 127.0557)
        val vm = vm(location = FakeLocationSource(here))
        advanceUntilIdle()
        vm.onCameraIdle(downtown, byUser = false)
        advanceUntilIdle()
        vm.onCameraIdle(empty, byUser = true)
        vm.researchHere()
        advanceUntilIdle()

        vm.moveToMyLocation()
        advanceUntilIdle()
        val request = vm.state.value.cameraRequest
        assertEquals(here, request?.target)
        assertNull("내 위치로 돌아오면 목록 기준도 내 위치", vm.state.value.listCenter)

        vm.onCameraIdle(seongsu, byUser = false)
        advanceUntilIdle()
        assertEquals(seongsu, repo.queries.last().bounds)
        assertEquals(3, repo.queries.size)
    }

    @Test
    fun `고른 핀이 새 결과에 없으면 선택을 푼다`() = runTest(dispatcher) {
        val vm = vm()
        advanceUntilIdle()
        vm.onCameraIdle(downtown, byUser = false)
        advanceUntilIdle()
        vm.select("fake:sejong-concert")
        assertEquals("fake:sejong-concert", vm.state.value.selected?.id)

        vm.toggleCategory(Category.FESTIVAL)
        advanceUntilIdle()
        assertNull(vm.state.value.selectedId)

        vm.select("fake:gwanghwamun-festival")
        vm.clearSelection()
        assertNull(vm.state.value.selected)
    }
}
