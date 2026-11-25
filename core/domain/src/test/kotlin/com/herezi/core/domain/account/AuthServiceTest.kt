package com.herezi.core.domain.account

import com.herezi.core.domain.fake.FakeAuthBackend
import com.herezi.core.domain.fake.InMemorySessionStore
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class AuthServiceTest {
    private val now = Instant.parse("2026-11-25T03:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val backend = FakeAuthBackend(now = { now })
    private val store = InMemorySessionStore(backend.session())
    private val auth = AuthService(backend, store, clock)

    @Test
    fun `401 이면 리프레시하고 한 번 다시 시도한다`() = runTest {
        val tokens = mutableListOf<String>()
        val result = auth.authorized { token ->
            tokens += token
            if (token == "access-0") throw AuthException.Unauthorized()
            "ok"
        }
        assertEquals("ok", result)
        assertEquals(listOf("access-0", "access-1"), tokens)
        assertEquals("access-1", store.value?.accessToken)
    }

    @Test
    fun `리프레시가 거부되면 세션을 지우고 SessionExpired`() = runTest {
        backend.refreshError = AuthException.Rejected(400)
        try {
            auth.authorized { throw AuthException.Unauthorized() }
            fail()
        } catch (_: AuthException.SessionExpired) {
        }
        assertNull(auth.session.value)
        assertNull(store.value)
    }

    @Test
    fun `리프레시 후에도 401 이면 로그아웃`() = runTest {
        try {
            auth.authorized<Unit> { throw AuthException.Unauthorized() }
            fail()
        } catch (_: AuthException.SessionExpired) {
        }
        assertEquals(1, backend.refreshCount)
        assertNull(auth.session.value)
    }

    @Test
    fun `503 은 세션을 유지한다`() = runTest {
        try {
            auth.authorized<Unit> { throw AuthException.Unavailable() }
            fail()
        } catch (_: AuthException.Unavailable) {
        }
        assertNotNull(auth.session.value)
        assertEquals(0, backend.refreshCount)
    }

    @Test
    fun `리프레시가 5xx·오프라인이면 세션 유지`() = runTest {
        backend.refreshError = AuthException.Offline()
        try {
            auth.authorized<Unit> { throw AuthException.Unauthorized() }
            fail()
        } catch (_: AuthException.Offline) {
        }
        assertNotNull(auth.session.value)
    }

    @Test
    fun `만료 60초 전이면 먼저 리프레시한다`() = runTest {
        store.value = backend.session().copy(expiresAt = now.plusSeconds(30))
        val auth = AuthService(backend, store, clock)
        val token = auth.authorized { it }
        assertEquals("access-1", token)
    }

    @Test
    fun `동시에 401 이 나도 리프레시는 한 번`() = runTest {
        val results = (1..5).map {
            async {
                auth.authorized { token -> if (token == "access-0") throw AuthException.Unauthorized() else token }
            }
        }.awaitAll()
        assertEquals(1, backend.refreshCount)
        assertTrue(results.all { it == "access-1" })
    }

    @Test
    fun `로그아웃은 서버가 실패해도 기기에서 지운다`() = runTest {
        auth.signOut()
        assertNull(store.value)
        assertEquals(1, backend.logoutCalls)
    }

    @Test
    fun `설정이 없으면 로그인을 시도하지 않는다`() = runTest {
        backend.isConfigured = false
        try {
            auth.signInWithIdToken(AuthProvider.KAKAO, "id", "nonce")
            fail()
        } catch (_: AuthException.NotConfigured) {
        }
        assertTrue(backend.idTokenCalls.isEmpty())
    }

    @Test
    fun `nonce 해시`() {
        assertEquals(
            "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824",
            AuthCrypto.sha256Hex("hello"),
        )
    }
}
