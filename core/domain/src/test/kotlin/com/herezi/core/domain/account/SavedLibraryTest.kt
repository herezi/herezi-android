package com.herezi.core.domain.account

import com.herezi.core.domain.SavedSpot
import com.herezi.core.domain.Spot
import com.herezi.core.domain.fake.AccountFixture
import com.herezi.core.domain.fake.FakeSpotsRepository
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class SavedLibraryTest {
    private val catalog: List<Spot> = FakeSpotsRepository.seedSpots(Instant.parse("2026-11-25T03:00:00Z"))
    private val a = catalog[0]
    private val b = catalog[1]
    private val c = catalog[2]

    private fun TestScope.fixture(signedIn: Boolean = true, device: List<SavedSpot> = emptyList()) =
        AccountFixture(this, catalog, signedIn = signedIn, deviceSaved = device)

    private fun TestScope.messages(f: AccountFixture): List<String> {
        val out = mutableListOf<String>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { f.library.messages.toList(out) }
        return out
    }

    @Test
    fun `로그아웃 상태에서 저장하면 시트를 띄우고 아무것도 저장하지 않는다`() = runTest {
        val f = fixture(signedIn = false)
        val outcome = f.library.toggle(a)
        assertEquals(SaveOutcome.NeedsLogin, outcome)
        assertEquals(LoginRequest(LoginReason.SAVE, a), f.library.loginRequest.value)
        assertTrue(f.library.items.value.isEmpty())
        assertTrue(f.api.calls.isEmpty())
    }

    @Test
    fun `로그인 후 import 한 번, 목록, 대기 저장, 토스트 차례`() = runTest {
        val old = Instant.parse("2026-01-01T00:00:00Z")
        val f = fixture(signedIn = false, device = listOf(SavedSpot(b, old), SavedSpot(c, old)))
        val out = messages(f)
        f.library.toggle(a)
        val request = f.library.loginRequest.value
        f.auth.signInWithIdToken(AuthProvider.KAKAO, "id", "nonce")
        f.library.onSignedIn(request)
        advanceUntilIdle()

        assertEquals(listOf("import:2", "list", "put:${a.id}"), f.api.calls)
        assertEquals(setOf(a.id, b.id, c.id), f.library.items.value.map { it.spot.id }.toSet())
        assertTrue(f.device.saved.first().isEmpty())
        assertEquals(listOf("저장했어요", "기기에 저장했던 2개를 계정으로 옮겼어요"), out)
        assertNull(f.library.loginRequest.value)
        assertEquals(SavedSync.LOADED, f.library.sync.value)
    }

    @Test
    fun `이미 계정에 있던 항목은 옮긴 수에서 뺀다`() = runTest {
        val f = fixture(signedIn = false, device = listOf(SavedSpot(a, Instant.EPOCH), SavedSpot(b, Instant.EPOCH)))
        f.api.saved[a.id] = SavedSpot(a, Instant.EPOCH)
        val out = messages(f)
        f.auth.signInWithIdToken(AuthProvider.KAKAO, "id", "nonce")
        f.library.onSignedIn(LoginRequest(LoginReason.ACCOUNT))
        advanceUntilIdle()
        assertEquals(listOf("로그인했어요", "기기에 저장했던 1개를 계정으로 옮겼어요"), out)
    }

    @Test
    fun `옮기기에 실패하면 로컬을 남기고 다음 실행 때 조용히 다시 한다`() = runTest {
        val f = fixture(signedIn = false, device = listOf(SavedSpot(a, Instant.EPOCH)))
        val out = messages(f)
        f.auth.signInWithIdToken(AuthProvider.KAKAO, "id", "nonce")
        f.api.failNext = AuthException.Offline()
        f.library.onSignedIn(LoginRequest(LoginReason.ACCOUNT))
        advanceUntilIdle()
        assertEquals(1, f.device.saved.first().size)
        assertEquals(listOf("로그인했어요"), out)

        f.library.start()
        advanceUntilIdle()
        assertTrue(f.device.saved.first().isEmpty())
        assertEquals(listOf("로그인했어요"), out) // 다시 할 때는 토스트 없음
        assertTrue(f.api.saved.containsKey(a.id))
    }

    @Test
    fun `200개 넘으면 나눠서 옮긴다`() = runTest {
        val many = (1..250).map { i -> SavedSpot(a.copy(id = "x$i"), Instant.EPOCH) }
        val f = fixture(signedIn = true, device = many)
        f.library.start()
        advanceUntilIdle()
        assertEquals(listOf("import:200", "import:50"), f.api.calls.filter { it.startsWith("import") })
    }

    @Test
    fun `저장 실패면 되돌리고 409 는 한도 문구`() = runTest {
        val f = fixture()
        f.api.limit = 0
        val outcome = f.library.save(a)
        assertEquals(SaveOutcome.Failed(SavedLibrary.MSG_LIMIT), outcome)
        assertTrue(f.library.items.value.isEmpty())

        f.api.limit = 500
        f.api.failNext = AuthException.Unavailable()
        assertEquals(SaveOutcome.Failed(SavedLibrary.MSG_SAVE_FAILED), f.library.save(a))
        assertNotNull(f.auth.session.value) // 503 은 로그아웃시키지 않음
    }

    @Test
    fun `삭제 실패면 카드를 되돌린다`() = runTest {
        val f = fixture()
        f.library.save(a)
        f.api.failNext = AuthException.Offline()
        assertFalse(f.library.remove(listOf(a.id)))
        assertEquals(listOf(a.id), f.library.items.value.map { it.spot.id })
        assertTrue(f.library.remove(listOf(a.id)))
        assertTrue(f.library.items.value.isEmpty())
    }

    @Test
    fun `목록은 계정별로 캐시하고 로그아웃하면 지운다`() = runTest {
        val f = fixture()
        val out = messages(f)
        f.library.save(a)
        f.library.refresh()
        assertEquals(1, f.cache.byUser["user-1"]?.size)
        f.library.signOut()
        assertTrue(f.cache.byUser.isEmpty())
        assertTrue(f.library.items.value.isEmpty())
        assertNull(f.auth.session.value)
        assertEquals(1, f.social.logoutCalls)
        assertEquals(listOf("로그아웃했어요"), out)
    }

    @Test
    fun `세션이 만료되면 로그아웃 상태로 바꾸고 다시 로그인 안내`() = runTest {
        val f = fixture()
        val out = messages(f)
        f.backend.refreshError = AuthException.Rejected(400)
        f.api.failAlways = AuthException.Unauthorized()
        assertFalse(f.library.refresh())
        assertNull(f.auth.session.value)
        assertEquals(listOf(SavedLibrary.MSG_SESSION_EXPIRED), out)
    }

    @Test
    fun `탈퇴 성공이면 카카오 연결 끊고 세션·캐시·기기 저장을 모두 지운다`() = runTest {
        val f = fixture(device = listOf(SavedSpot(b, Instant.EPOCH)))
        f.library.save(a)
        f.library.deleteAccount()
        assertTrue(f.api.deleted)
        assertEquals(1, f.social.unlinkCalls)
        assertNull(f.auth.session.value)
        assertTrue(f.cache.byUser.isEmpty())
        assertTrue(f.device.saved.first().isEmpty())
        assertTrue(f.library.items.value.isEmpty())
    }

    @Test
    fun `탈퇴 실패면 세션을 유지하고 오류를 던진다`() = runTest {
        val f = fixture()
        f.api.failNext = AuthException.Failed("502")
        try {
            f.library.deleteAccount()
            error("던져야 한다")
        } catch (_: AuthException.Failed) {
        }
        assertNotNull(f.auth.session.value)
    }
}
