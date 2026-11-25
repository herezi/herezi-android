package com.herezi.feature.saved

import com.herezi.core.domain.GeoPoint
import com.herezi.core.domain.SavedSpot
import com.herezi.core.domain.account.AuthException
import com.herezi.core.domain.account.LoginReason
import com.herezi.core.domain.fake.AccountFixture
import com.herezi.core.domain.fake.FakeLocationSource
import com.herezi.core.domain.fake.FakeSpotsRepository
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
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
    private val t0 = Instant.parse("2026-11-25T03:00:00Z")
    private val catalog = FakeSpotsRepository.seedSpots(t0)
    private val a = catalog[0]
    private val b = catalog[1]

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun account(signedIn: Boolean = true, device: List<SavedSpot> = emptyList()) =
        AccountFixture(CoroutineScope(dispatcher), catalog, signedIn = signedIn, deviceSaved = device)

    /** 서버에 [ids] 를 저장해 둔다(앞쪽이 오래된 것). */
    private fun AccountFixture.seed(vararg spots: com.herezi.core.domain.Spot) = spots.forEachIndexed { i, s ->
        api.saved[s.id] = SavedSpot(s, t0.plus(Duration.ofMinutes(i.toLong())))
    }

    @Test
    fun `로그인 전이면 로그인 안내, 기기 저장 수를 보여주고 로그인 버튼은 시트를 연다`() = runTest(dispatcher) {
        val f = account(signedIn = false, device = listOf(SavedSpot(a, t0), SavedSpot(b, t0)))
        val vm = SavedViewModel(f.library, FakeLocationSource())
        advanceUntilIdle()
        assertEquals(SavedMode.SIGNED_OUT, vm.state.value.mode)
        assertEquals(2, vm.state.value.deviceSavedCount)
        assertFalse(vm.state.value.canEdit)

        vm.signIn()
        assertEquals(LoginReason.ACCOUNT, f.library.loginRequest.value?.reason)
    }

    @Test
    fun `로그인 후 서버 목록을 최신순으로, 끝난 항목은 따로`() = runTest(dispatcher) {
        val f = account()
        f.seed(a, b.copy(isEnded = true))
        val vm = SavedViewModel(f.library, FakeLocationSource())
        advanceUntilIdle()
        assertEquals(SavedMode.LOADING, vm.state.value.mode)
        vm.refresh()
        advanceUntilIdle()
        assertEquals(SavedMode.LIST, vm.state.value.mode)
        assertEquals(listOf(a.id), vm.state.value.active.map { it.spot.id })
        assertEquals(listOf(b.id), vm.state.value.ended.map { it.spot.id })
        assertTrue(vm.state.value.canEdit)
    }

    @Test
    fun `빈 목록과 캐시 없는 오류`() = runTest(dispatcher) {
        val f = account()
        val vm = SavedViewModel(f.library, FakeLocationSource())
        vm.refresh()
        advanceUntilIdle()
        assertEquals(SavedMode.EMPTY, vm.state.value.mode)

        val g = account()
        g.api.failAlways = AuthException.Offline()
        val vm2 = SavedViewModel(g.library, FakeLocationSource())
        vm2.refresh()
        advanceUntilIdle()
        assertEquals(SavedMode.ERROR, vm2.state.value.mode)
        g.api.failAlways = null
        vm2.retry()
        advanceUntilIdle()
        assertEquals(SavedMode.EMPTY, vm2.state.value.mode)
    }

    @Test
    fun `당겨서 새로고침 실패는 캐시가 있을 때만 토스트`() = runTest(dispatcher) {
        val f = account()
        f.seed(a)
        val vm = SavedViewModel(f.library, FakeLocationSource())
        val messages = mutableListOf<SavedMessage>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.messages.collect { messages += it } }
        vm.refresh()
        advanceUntilIdle()
        f.api.failNext = AuthException.Offline()
        vm.pullRefresh()
        advanceUntilIdle()
        assertEquals(listOf(SavedMessage(SavedViewModel.MSG_REFRESH_FAILED)), messages)
        assertEquals(SavedMode.LIST, vm.state.value.mode)
        assertFalse(vm.state.value.refreshing)
    }

    @Test
    fun `위치를 알면 origin 을 준다`() = runTest(dispatcher) {
        val vm = SavedViewModel(account().library, FakeLocationSource(GeoPoint(37.5, 127.0)))
        vm.refresh()
        advanceUntilIdle()
        assertTrue(vm.state.value.origin!!.known)
    }

    @Test
    fun `삭제하면 토스트를 보내고 되돌리기로 다시 저장한다`() = runTest(dispatcher) {
        val f = account()
        f.seed(a, b)
        val vm = SavedViewModel(f.library, FakeLocationSource())
        val messages = mutableListOf<SavedMessage>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.messages.collect { messages += it } }
        vm.refresh()
        advanceUntilIdle()

        vm.remove(a.id)
        advanceUntilIdle()
        assertEquals(listOf(b.id), vm.state.value.active.map { it.spot.id })
        assertFalse(a.id in f.api.saved)
        assertEquals(SavedMessage(SavedViewModel.MSG_REMOVED, undoable = true), messages.single())

        vm.undo()
        advanceUntilIdle()
        assertTrue(a.id in f.api.saved)
        assertEquals(setOf(a.id, b.id), vm.state.value.active.map { it.spot.id }.toSet())
    }

    @Test
    fun `삭제 실패면 카드를 되돌리고 토스트`() = runTest(dispatcher) {
        val f = account()
        f.seed(a)
        val vm = SavedViewModel(f.library, FakeLocationSource())
        val messages = mutableListOf<SavedMessage>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.messages.collect { messages += it } }
        vm.refresh()
        advanceUntilIdle()
        f.api.failNext = AuthException.Unavailable()
        vm.remove(a.id)
        advanceUntilIdle()
        assertEquals(listOf(a.id), vm.state.value.active.map { it.spot.id })
        assertEquals("지우지 못했어요. 잠시 후 다시 시도해 주세요.", messages.single().text)
    }

    @Test
    fun `편집 모드에서 고른 항목을 지우고 끝난 볼거리 모두 지우기`() = runTest(dispatcher) {
        val f = account()
        f.seed(a, b, catalog[2].copy(isEnded = true))
        val vm = SavedViewModel(f.library, FakeLocationSource())
        vm.refresh()
        advanceUntilIdle()

        vm.toggleEditing()
        vm.toggleSelected(a.id)
        vm.deleteSelected()
        advanceUntilIdle()
        assertFalse(vm.state.value.editing)
        assertEquals(listOf(b.id), vm.state.value.active.map { it.spot.id })

        vm.clearEnded()
        advanceUntilIdle()
        assertTrue(vm.state.value.ended.isEmpty())
        assertNull(f.api.saved[catalog[2].id])
    }
}
