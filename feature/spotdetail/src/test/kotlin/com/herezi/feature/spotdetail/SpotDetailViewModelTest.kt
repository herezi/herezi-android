package com.herezi.feature.spotdetail

import androidx.lifecycle.SavedStateHandle
import com.herezi.core.domain.GeoPoint
import com.herezi.core.domain.MapQuery
import com.herezi.core.domain.MapSpots
import com.herezi.core.domain.SavedSpot
import com.herezi.core.domain.Spot
import com.herezi.core.domain.SpotPage
import com.herezi.core.domain.SpotQuery
import com.herezi.core.domain.SpotsRepository
import com.herezi.core.domain.fake.FakeLocationSource
import com.herezi.core.domain.fake.FakeSpotsRepository
import com.herezi.core.domain.fake.InMemorySavedSpotsRepository
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
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
class SpotDetailViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val clock = Clock.fixed(Instant.parse("2026-11-18T03:00:00Z"), ZoneOffset.UTC)
    private val fake = FakeSpotsRepository(clock)
    private val saved = InMemorySavedSpotsRepository()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(
        id: String,
        repo: SpotsRepository = fake,
        location: FakeLocationSource = FakeLocationSource(),
    ) = SpotDetailViewModel(
        SavedStateHandle(mapOf(SpotDetailViewModel.ARG_SPOT_ID to id)),
        repo,
        saved,
        location,
        clock,
    )

    private class FailingRepository : SpotsRepository {
        override suspend fun nearby(query: SpotQuery): SpotPage = error("x")

        override suspend fun inBounds(query: MapQuery): MapSpots = error("x")

        override suspend fun spot(id: String): Spot? = error("네트워크 오류")
    }

    @Test
    fun `id로 상세를 불러오고 위치가 없으면 서울시청 기준 거리를 준다`() = runTest(dispatcher) {
        val vm = viewModel("fake:yeouido-cherry")
        assertEquals(SpotDetailUiState.Loading, vm.state.value)
        advanceUntilIdle()

        val state = vm.state.value as SpotDetailUiState.Loaded
        assertEquals("여의도 봄꽃 축제", state.spot.title)
        assertFalse(state.origin!!.known)
        assertTrue(state.distanceM!! > 2_000)
    }

    @Test
    fun `내 위치를 알면 그 기준으로 거리를 준다`() = runTest(dispatcher) {
        val vm = viewModel("fake:yeouido-cherry", location = FakeLocationSource(GeoPoint(37.5285, 126.9227)))
        advanceUntilIdle()
        val state = vm.state.value as SpotDetailUiState.Loaded
        assertTrue(state.origin!!.known)
        assertEquals(0, state.distanceM)
    }

    @Test
    fun `없는 id는 NotFound`() = runTest(dispatcher) {
        val vm = viewModel("nope")
        advanceUntilIdle()
        assertEquals(SpotDetailUiState.NotFound(saved = false), vm.state.value)
    }

    @Test
    fun `끝난 행사도 상세는 열리고 isEnded 다`() = runTest(dispatcher) {
        val vm = viewModel(FakeSpotsRepository.ENDED_SPOT_ID)
        advanceUntilIdle()
        assertTrue((vm.state.value as SpotDetailUiState.Loaded).spot.isEnded)
    }

    @Test
    fun `저장 토글은 저장소에 반영하고 토스트를 보낸다`() = runTest(dispatcher) {
        val vm = viewModel("fake:yeouido-cherry")
        val messages = mutableListOf<String>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.messages.collect { messages += it } }
        advanceUntilIdle()

        vm.toggleSave()
        advanceUntilIdle()
        assertTrue(vm.saved.value)
        assertEquals(clock.instant(), saved.get("fake:yeouido-cherry")!!.savedAt)

        vm.toggleSave()
        advanceUntilIdle()
        assertFalse(vm.saved.value)
        assertEquals(listOf(SpotDetailViewModel.MSG_SAVED, SpotDetailViewModel.MSG_UNSAVED), messages)
    }

    @Test
    fun `저장된 항목은 스냅샷으로 먼저 그리고 실패하면 화면을 유지하며 토스트`() = runTest(dispatcher) {
        val snapshot = fake.spot("fake:yeouido-cherry")!!
        saved.save(snapshot, clock.instant())
        val vm = viewModel("fake:yeouido-cherry", repo = FailingRepository())
        val messages = mutableListOf<String>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.messages.collect { messages += it } }
        advanceUntilIdle()

        assertEquals(snapshot, (vm.state.value as SpotDetailUiState.Loaded).spot)
        assertEquals(listOf(SpotDetailViewModel.MSG_STALE), messages)
    }

    @Test
    fun `미리 그린 게 없으면 실패 상태`() = runTest(dispatcher) {
        val vm = viewModel("fake:yeouido-cherry", repo = FailingRepository())
        advanceUntilIdle()
        assertEquals(SpotDetailUiState.Failed, vm.state.value)
    }

    @Test
    fun `저장된 항목이 404면 종료로 표시하고 저장에서 지울 수 있다`() = runTest(dispatcher) {
        val gone = fake.spot("fake:yeouido-cherry")!!.copy(id = "gone")
        saved.restore(SavedSpot(gone, clock.instant()))
        val vm = viewModel("gone")
        advanceUntilIdle()

        assertEquals(SpotDetailUiState.NotFound(saved = true), vm.state.value)
        assertTrue(saved.get("gone")!!.spot.isEnded)

        vm.removeFromSaved()
        advanceUntilIdle()
        assertEquals(null, saved.get("gone"))
        assertEquals(SpotDetailUiState.NotFound(saved = false), vm.state.value)
    }

    @Test
    fun `상세를 받으면 저장 스냅샷을 새로 고친다`() = runTest(dispatcher) {
        val old = fake.spot("fake:yeouido-cherry")!!.copy(title = "옛 제목")
        saved.save(old, clock.instant())
        viewModel("fake:yeouido-cherry")
        advanceUntilIdle()
        assertEquals("여의도 봄꽃 축제", saved.saved.first().single().spot.title)
        assertNotNull(saved.get("fake:yeouido-cherry"))
    }
}
