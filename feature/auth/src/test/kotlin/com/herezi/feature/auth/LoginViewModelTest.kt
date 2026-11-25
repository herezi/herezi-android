package com.herezi.feature.auth

import com.herezi.core.domain.account.AuthCrypto
import com.herezi.core.domain.account.AuthException
import com.herezi.core.domain.account.AuthProvider
import com.herezi.core.domain.account.LoginReason
import com.herezi.core.domain.account.LoginRequest
import com.herezi.core.domain.fake.AccountFixture
import com.herezi.core.domain.fake.FakeSpotsRepository
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val catalog = FakeSpotsRepository.seedSpots(Instant.parse("2026-11-25T03:00:00Z"))
    private lateinit var f: AccountFixture
    private lateinit var vm: LoginViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        f = AccountFixture(CoroutineScope(dispatcher), catalog)
        vm = LoginViewModel(f.auth, f.social, f.library)
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `카카오는 해시 nonce 를 카카오에, 원문을 Supabase 에 보내고 대기 저장을 마친다`() = runTest(dispatcher) {
        val done = mutableListOf<Unit>()
        val toasts = mutableListOf<String>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.done.collect { done += it } }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { f.library.messages.collect { toasts += it } }
        val request = LoginRequest(LoginReason.SAVE, catalog[0])

        vm.signInWithKakao(request)
        assertEquals(AuthProvider.KAKAO, vm.state.value.inProgress)
        advanceUntilIdle()

        val (provider, idToken, raw) = f.backend.idTokenCalls.single()
        assertEquals(AuthProvider.KAKAO, provider)
        assertEquals("kakao-id-token", idToken)
        assertEquals(AuthCrypto.sha256Hex(raw), f.social.hashedNonces.single())
        assertNotNull(f.auth.session.value)
        assertEquals(1, done.size)
        assertNull(vm.state.value.inProgress)
        assertTrue(catalog[0].id in f.api.saved)
        assertEquals(listOf("저장했어요"), toasts)
    }

    @Test
    fun `취소는 문구 없이 기본 상태`() = runTest(dispatcher) {
        f.social.error = AuthException.Cancelled()
        vm.signInWithKakao(null)
        advanceUntilIdle()
        assertEquals(LoginUiState(), vm.state.value)
        assertNull(f.auth.session.value)
    }

    @Test
    fun `오류는 일반 문구, 오프라인은 연결 문구`() = runTest(dispatcher) {
        f.backend.signInError = AuthException.Rejected(400)
        vm.signInWithKakao(null)
        advanceUntilIdle()
        assertEquals(LoginViewModel.MSG_FAILED, vm.state.value.error)

        f.backend.signInError = AuthException.Offline()
        vm.signInWithKakao(null)
        advanceUntilIdle()
        assertEquals(LoginViewModel.MSG_OFFLINE, vm.state.value.error)
    }

    @Test
    fun `진행 중 연타는 무시한다`() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        val social = object : com.herezi.core.domain.account.SocialLogin by f.social {
            override suspend fun kakaoIdToken(hashedNonce: String): String {
                gate.await()
                return f.social.kakaoIdToken(hashedNonce)
            }
        }
        val vm = LoginViewModel(f.auth, social, f.library)
        vm.signInWithKakao(null)
        advanceUntilIdle()
        vm.signInWithKakao(null)
        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(1, f.backend.idTokenCalls.size)
    }

    @Test
    fun `설정이 없으면 로그인 실패 문구`() = runTest(dispatcher) {
        f.backend.isConfigured = false
        vm.signInWithKakao(null)
        advanceUntilIdle()
        assertEquals(LoginViewModel.MSG_FAILED, vm.state.value.error)
        assertTrue(f.social.hashedNonces.isEmpty())
    }

    @Test
    fun `시트 제목은 연 곳에 따라 다르다`() {
        assertEquals("로그인하면 저장할 수 있어요", LoginRequest(LoginReason.SAVE).sheetTitle())
        assertEquals("로그인하고 저장한 볼거리를 모아 보세요", LoginRequest(LoginReason.ACCOUNT).sheetTitle())
    }
}
