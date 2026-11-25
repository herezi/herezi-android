package com.herezi.core.domain.account

import java.time.Clock
import java.time.Duration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 세션 보관·갱신. `/v1/me/…` 호출은 모두 [authorized] 를 거친다.
 * - 만료 [REFRESH_LEEWAY] 전이면 먼저 리프레시한다.
 * - 401 이면 리프레시 후 한 번 다시 시도한다. 동시에 여러 요청이 401 을 받아도 리프레시는 한 번만 한다.
 * - 리프레시가 거부되면(또는 다시 401) 세션을 지우고 [AuthException.SessionExpired] 를 던진다.
 * - 503·오프라인은 세션을 유지한 채 그대로 던진다.
 */
class AuthService(private val backend: AuthBackend, private val store: SessionStore, private val clock: Clock) {
    private val _session = MutableStateFlow(store.load())
    val session: StateFlow<AuthSession?> = _session.asStateFlow()

    private val refreshLock = Mutex()

    val isConfigured: Boolean get() = backend.isConfigured

    suspend fun signInWithIdToken(provider: AuthProvider, idToken: String, nonce: String): AuthSession {
        if (!backend.isConfigured) throw AuthException.NotConfigured()
        return backend.signInWithIdToken(provider, idToken, nonce).also(::adopt)
    }

    fun authorizeUrl(provider: AuthProvider, codeChallenge: String): String {
        if (!backend.isConfigured) throw AuthException.NotConfigured()
        return backend.authorizeUrl(provider, codeChallenge)
    }

    suspend fun exchangeCode(authCode: String, codeVerifier: String, provider: AuthProvider): AuthSession =
        backend.exchangeCode(authCode, codeVerifier, provider).also(::adopt)

    /** 서버 로그아웃은 실패해도 무시하고 기기에서는 항상 로그아웃한다. */
    suspend fun signOut() {
        val current = _session.value ?: return
        clear()
        @Suppress("TooGenericExceptionCaught", "SwallowedException")
        try {
            backend.logout(current.accessToken)
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (e: Exception) {
            // 기기 로그아웃이 우선.
        }
    }

    /** 세션을 지운다(탈퇴 후, 리프레시 실패). */
    fun clear() {
        store.save(null)
        _session.value = null
    }

    suspend fun <T> authorized(block: suspend (accessToken: String) -> T): T {
        var current = _session.value ?: throw AuthException.SessionExpired()
        if (!current.expiresAt.isAfter(clock.instant().plus(REFRESH_LEEWAY))) current = refreshed(current)
        return try {
            block(current.accessToken)
        } catch (_: AuthException.Unauthorized) {
            val fresh = refreshed(current)
            try {
                block(fresh.accessToken)
            } catch (_: AuthException.Unauthorized) {
                clear()
                throw AuthException.SessionExpired()
            }
        }
    }

    private fun adopt(session: AuthSession) {
        store.save(session)
        _session.value = session
    }

    /** [stale] 로 실패한 요청들이 몰려도 리프레시는 한 번. 이미 바뀌었으면 새 세션을 그대로 쓴다. */
    private suspend fun refreshed(stale: AuthSession): AuthSession = refreshLock.withLock {
        val current = _session.value ?: throw AuthException.SessionExpired()
        if (current.accessToken != stale.accessToken) return@withLock current
        try {
            backend.refresh(current).copy(provider = current.provider).also(::adopt)
        } catch (_: AuthException.Rejected) {
            clear()
            throw AuthException.SessionExpired()
        } catch (_: AuthException.Unauthorized) {
            clear()
            throw AuthException.SessionExpired()
        }
    }

    companion object {
        val REFRESH_LEEWAY: Duration = Duration.ofSeconds(60)
    }
}
