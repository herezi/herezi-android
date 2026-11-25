package com.herezi.feature.settings

import com.herezi.core.domain.LocationAccess
import com.herezi.core.domain.account.AuthException
import com.herezi.core.domain.account.AuthProvider
import com.herezi.core.domain.account.LoginReason
import com.herezi.core.domain.fake.AccountFixture
import com.herezi.core.domain.fake.FakeLocationSource
import com.herezi.core.domain.fake.FakeSpotsRepository
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
class SettingsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val catalog = FakeSpotsRepository.seedSpots(Instant.parse("2026-11-25T03:00:00Z"))

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun account(signedIn: Boolean, provider: AuthProvider = AuthProvider.KAKAO) =
        AccountFixture(CoroutineScope(dispatcher), catalog, signedIn = signedIn, provider = provider)

    @Test
    fun `위치 권한 상태를 문구로 보여주고 돌아오면 갱신한다`() = runTest(dispatcher) {
        val location = FakeLocationSource()
        val vm = SettingsViewModel(location, account(signedIn = false).library)
        assertEquals("허용 안 됨", vm.state.value.accessLabel)

        location.accessValue = LocationAccess.APPROXIMATE
        vm.refresh()
        assertEquals("대략적 위치만", vm.state.value.accessLabel)

        location.accessValue = LocationAccess.PRECISE
        vm.refresh()
        assertEquals("허용됨", vm.state.value.accessLabel)
    }

    @Test
    fun `계정 그룹은 로그인 전이면 로그인 행, 로그인 행은 시트를 연다`() = runTest(dispatcher) {
        val f = account(signedIn = false)
        val vm = SettingsViewModel(FakeLocationSource(), f.library)
        advanceUntilIdle()
        assertNull(vm.state.value.provider)
        vm.signIn()
        assertEquals(LoginReason.ACCOUNT, f.library.loginRequest.value?.reason)
        assertNull(f.library.loginRequest.value?.pendingSave)
    }

    @Test
    fun `로그인 후 제공자 표시, 로그아웃하면 로그인 전으로 돌아오고 토스트`() = runTest(dispatcher) {
        val f = account(signedIn = true, provider = AuthProvider.APPLE)
        val toasts = mutableListOf<String>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { f.library.messages.collect { toasts += it } }
        val vm = SettingsViewModel(FakeLocationSource(), f.library)
        advanceUntilIdle()
        assertEquals("Apple로 로그인됨", vm.state.value.provider!!.signedInLabel)

        vm.signOut()
        advanceUntilIdle()
        assertNull(vm.state.value.provider)
        assertEquals(listOf("로그아웃했어요"), toasts)
        assertEquals(0, f.social.logoutCalls) // Apple 은 카카오 SDK 로그아웃을 부르지 않는다
    }

    @Test
    fun `탈퇴 확인은 체크해야 진행되고 성공하면 완료 화면`() = runTest(dispatcher) {
        val f = account(signedIn = true)
        f.library.save(catalog[0])
        val vm = DeleteAccountViewModel(f.library)
        advanceUntilIdle()
        assertEquals("저장한 볼거리 1개가 모두 지워져요", vm.state.value.savedLine)
        assertEquals("카카오 계정 연결이 끊어져요", vm.state.value.unlinkLine)

        vm.delete()
        advanceUntilIdle()
        assertFalse(f.api.deleted)

        vm.toggleConfirmed()
        vm.delete()
        advanceUntilIdle()
        assertTrue(vm.state.value.done)
        assertTrue(f.api.deleted)
        assertEquals(1, f.social.unlinkCalls)
        assertNull(f.auth.session.value)
    }

    @Test
    fun `탈퇴 실패면 화면과 체크를 유지하고 토스트, 오프라인은 연결 문구`() = runTest(dispatcher) {
        val f = account(signedIn = true)
        val vm = DeleteAccountViewModel(f.library)
        val toasts = mutableListOf<String>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.messages.collect { toasts += it } }
        vm.toggleConfirmed()

        f.api.failNext = AuthException.Failed("502")
        vm.delete()
        advanceUntilIdle()
        f.api.failNext = AuthException.Offline()
        vm.delete()
        advanceUntilIdle()

        assertFalse(vm.state.value.done)
        assertTrue(vm.state.value.confirmed)
        assertEquals(listOf(DeleteAccountViewModel.MSG_FAILED, DeleteAccountViewModel.MSG_OFFLINE), toasts)
        assertEquals("저장한 볼거리가 모두 지워져요", vm.state.value.savedLine)
    }

    @Test
    fun `세션이 만료돼 탈퇴하지 못하면 로그인 시트`() = runTest(dispatcher) {
        val f = account(signedIn = true)
        f.backend.refreshError = AuthException.Rejected(400)
        f.api.failAlways = AuthException.Unauthorized()
        val vm = DeleteAccountViewModel(f.library)
        vm.toggleConfirmed()
        vm.delete()
        advanceUntilIdle()
        assertEquals(LoginReason.ACCOUNT, f.library.loginRequest.value?.reason)
    }

    @Test
    fun `출처 목록과 제보 본문`() {
        assertEquals(6, DataSources.size)
        assertTrue(DataSources.all { it.url.startsWith("https://") })
        assertTrue(reportBody("1.0.0 (12)", "15").contains("앱 버전: 1.0.0 (12)\nOS: Android 15"))
    }
}
