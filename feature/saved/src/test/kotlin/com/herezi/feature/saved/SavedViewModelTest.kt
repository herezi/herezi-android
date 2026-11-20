package com.herezi.feature.saved

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
import java.time.Duration
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SavedViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val clock = Clock.fixed(Instant.parse("2026-11-18T03:00:00Z"), ZoneOffset.UTC)
    private val fake = FakeSpotsRepository(clock)
    private val t0 = clock.instant()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private suspend fun saved(vararg ids: String): InMemorySavedSpotsRepository {
        val repo = InMemorySavedSpotsRepository()
        ids.forEachIndexed { i, id ->
            val spot = fake.spot(id) ?: fake.spot("fake:yeouido-cherry")!!.copy(id = id)
            repo.restore(SavedSpot(spot.copy(isEnded = false), t0.plus(Duration.ofMinutes(i.toLong()))))
        }
        return repo
    }

    private class CountingRepository(private val delegate: SpotsRepository, private val failId: String? = null) :
        SpotsRepository {
        var calls = 0

        override suspend fun nearby(query: SpotQuery): SpotPage = delegate.nearby(query)

        override suspend fun inBounds(query: MapQuery): MapSpots = delegate.inBounds(query)

        override suspend fun placeName(point: GeoPoint): String? = delegate.placeName(point)

        override suspend fun spot(id: String): Spot? {
            calls++
            if (id == failId) error("네트워크 오류")
            return delegate.spot(id)
        }
    }

    @Test
    fun `비어 있으면 빈 상태`() = runTest(dispatcher) {
        val vm = SavedViewModel(InMemorySavedSpotsRepository(), fake, FakeLocationSource())
        advanceUntilIdle()
        assertTrue(vm.state.value.isEmpty)
    }

    @Test
    fun `재조회로 끝난 항목과 404를 끝난 섹션으로 옮기고 실패는 조용히 둔다`() = runTest(dispatcher) {
        val store = saved("fake:yeouido-cherry", FakeSpotsRepository.ENDED_SPOT_ID, "gone", "flaky")
        val repo = CountingRepository(fake, failId = "flaky")
        val vm = SavedViewModel(store, repo, FakeLocationSource())
        advanceUntilIdle()
        assertEquals(4, vm.state.value.active.size)

        vm.refresh()
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals(4, repo.calls)
        assertEquals(listOf("flaky", "fake:yeouido-cherry"), state.active.map { it.spot.id })
        assertEquals(setOf(FakeSpotsRepository.ENDED_SPOT_ID, "gone"), state.ended.map { it.spot.id }.toSet())
        assertNull(state.origin)
    }

    @Test
    fun `위치를 알면 origin 을 준다`() = runTest(dispatcher) {
        val vm = SavedViewModel(saved("fake:yeouido-cherry"), fake, FakeLocationSource(GeoPoint(37.5, 127.0)))
        vm.refresh()
        advanceUntilIdle()
        assertEquals(GeoPoint(37.5, 127.0), vm.state.value.origin!!.point)
    }

    @Test
    fun `삭제하면 토스트를 보내고 되돌리기로 저장 시각까지 되살린다`() = runTest(dispatcher) {
        val store = saved("fake:yeouido-cherry", "fake:sejong-concert")
        val vm = SavedViewModel(store, fake, FakeLocationSource())
        val messages = mutableListOf<SavedMessage>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.messages.collect { messages += it } }
        advanceUntilIdle()

        vm.remove("fake:yeouido-cherry")
        advanceUntilIdle()
        assertEquals(listOf("fake:sejong-concert"), vm.state.value.active.map { it.spot.id })
        assertEquals(listOf(SavedMessage("저장을 취소했어요", undoable = true)), messages)

        vm.undo()
        advanceUntilIdle()
        assertEquals(listOf("fake:sejong-concert", "fake:yeouido-cherry"), vm.state.value.active.map { it.spot.id })
        assertEquals(t0, store.get("fake:yeouido-cherry")!!.savedAt)
    }

    @Test
    fun `편집 모드에서 고른 항목을 지운다`() = runTest(dispatcher) {
        val store = saved("a", "b", "c")
        val vm = SavedViewModel(store, fake, FakeLocationSource())
        advanceUntilIdle()

        vm.toggleEditing()
        vm.toggleSelected("a")
        vm.toggleSelected("c")
        vm.toggleSelected("c")
        vm.toggleSelected("b")
        assertEquals(setOf("a", "b"), vm.state.value.selected)

        vm.deleteSelected()
        advanceUntilIdle()
        assertFalse(vm.state.value.editing)
        assertEquals(listOf("c"), vm.state.value.active.map { it.spot.id })
    }

    @Test
    fun `끝난 볼거리 모두 지우기`() = runTest(dispatcher) {
        val store = saved("fake:yeouido-cherry", FakeSpotsRepository.ENDED_SPOT_ID)
        val vm = SavedViewModel(store, fake, FakeLocationSource())
        vm.refresh()
        advanceUntilIdle()
        assertEquals(1, vm.state.value.ended.size)

        vm.clearEnded()
        advanceUntilIdle()
        assertTrue(vm.state.value.ended.isEmpty())
        assertEquals(1, vm.state.value.active.size)
    }
}
