package com.herezi.core.domain.fake

import com.herezi.core.domain.SavedSpot
import com.herezi.core.domain.Spot
import com.herezi.core.domain.account.AccountApi
import com.herezi.core.domain.account.AuthBackend
import com.herezi.core.domain.account.AuthException
import com.herezi.core.domain.account.AuthProvider
import com.herezi.core.domain.account.AuthSession
import com.herezi.core.domain.account.ImportResult
import com.herezi.core.domain.account.SavedCache
import com.herezi.core.domain.account.SavedPage
import com.herezi.core.domain.account.SessionStore
import com.herezi.core.domain.account.SocialLogin
import java.time.Instant

/** 메모리 계정 API. [catalog] 는 서버에 있는 볼거리(없으면 import 에서 건너뜀, PUT 은 404). */
class FakeAccountApi(
    private val catalog: Map<String, Spot> = emptyMap(),
    var now: () -> Instant = { Instant.parse("2026-11-25T03:00:00Z") },
) : AccountApi {
    val saved = linkedMapOf<String, SavedSpot>()

    /** 다음 호출에서 던질 오류(한 번). */
    var failNext: AuthException? = null

    /** 계속 던질 오류. */
    var failAlways: AuthException? = null
    var limit = 500
    val calls = mutableListOf<String>()
    val tokens = mutableListOf<String>()
    var deleted = false

    private fun check(name: String, token: String) {
        calls += name
        tokens += token
        failAlways?.let { throw it }
        failNext?.let {
            failNext = null
            throw it
        }
    }

    override suspend fun listSaved(accessToken: String, cursor: String?): SavedPage {
        check("list", accessToken)
        return SavedPage(saved.values.sortedByDescending { it.savedAt })
    }

    override suspend fun save(accessToken: String, spotId: String) {
        check("put:$spotId", accessToken)
        val spot = catalog[spotId] ?: throw AuthException.NotFound()
        if (spotId !in saved && saved.size >= limit) throw AuthException.SavedLimit()
        saved.getOrPut(spotId) { SavedSpot(spot, now()) }
    }

    override suspend fun unsave(accessToken: String, spotId: String) {
        check("delete:$spotId", accessToken)
        saved.remove(spotId)
    }

    override suspend fun importSaved(accessToken: String, spotIds: List<String>): ImportResult {
        check("import:${spotIds.size}", accessToken)
        var imported = 0
        var skipped = 0
        spotIds.distinct().forEach { id ->
            val spot = catalog[id]
            when {
                spot == null -> skipped++
                id in saved -> Unit
                else -> {
                    saved[id] = SavedSpot(spot, now())
                    imported++
                }
            }
        }
        return ImportResult(imported, skipped)
    }

    override suspend fun deleteMe(accessToken: String) {
        check("deleteMe", accessToken)
        saved.clear()
        deleted = true
    }
}

class InMemorySessionStore(var value: AuthSession? = null) : SessionStore {
    override fun load(): AuthSession? = value

    override fun save(session: AuthSession?) {
        value = session
    }
}

class InMemorySavedCache : SavedCache {
    val byUser = mutableMapOf<String, List<SavedSpot>>()

    override suspend fun load(userId: String): List<SavedSpot> = byUser[userId].orEmpty()

    override suspend fun save(userId: String, items: List<SavedSpot>) {
        byUser[userId] = items
    }

    override suspend fun clear() = byUser.clear()
}

/** 토큰 교환을 흉내 낸다. 리프레시마다 access 토큰 번호가 오른다. */
class FakeAuthBackend(override var isConfigured: Boolean = true, var now: () -> Instant = { Instant.EPOCH }) :
    AuthBackend {
    var refreshCount = 0
    var refreshError: AuthException? = null
    var signInError: AuthException? = null
    val idTokenCalls = mutableListOf<Triple<AuthProvider, String, String>>()
    var logoutCalls = 0

    fun session(provider: AuthProvider = AuthProvider.KAKAO, n: Int = 0) = AuthSession(
        accessToken = "access-$n",
        refreshToken = "refresh-$n",
        expiresAt = now().plusSeconds(3600),
        userId = "user-1",
        provider = provider,
    )

    override suspend fun signInWithIdToken(provider: AuthProvider, idToken: String, nonce: String): AuthSession {
        idTokenCalls += Triple(provider, idToken, nonce)
        signInError?.let { throw it }
        return session(provider)
    }

    override suspend fun refresh(session: AuthSession): AuthSession {
        refreshCount++
        refreshError?.let { throw it }
        return session(session.provider, refreshCount)
    }

    override suspend fun logout(accessToken: String) {
        logoutCalls++
    }
}

class FakeSocialLogin(var idToken: String = "kakao-id-token") : SocialLogin {
    var error: AuthException? = null
    val hashedNonces = mutableListOf<String>()
    var logoutCalls = 0
    var unlinkCalls = 0

    override suspend fun kakaoIdToken(hashedNonce: String): String {
        hashedNonces += hashedNonce
        error?.let { throw it }
        return idToken
    }

    override suspend fun kakaoLogout() {
        logoutCalls++
    }

    override suspend fun kakaoUnlink() {
        unlinkCalls++
    }
}

/** 테스트용 계정 묶음: [library] 와 그 뒤의 Fake 들. [signedIn] 이면 카카오 세션으로 시작한다. */
class AccountFixture(
    scope: kotlinx.coroutines.CoroutineScope,
    catalog: Collection<Spot> = emptyList(),
    signedIn: Boolean = false,
    provider: com.herezi.core.domain.account.AuthProvider = com.herezi.core.domain.account.AuthProvider.KAKAO,
    deviceSaved: List<SavedSpot> = emptyList(),
    val clock: java.time.Clock = java.time.Clock.fixed(Instant.parse("2026-11-25T03:00:00Z"), java.time.ZoneOffset.UTC),
) {
    val backend = FakeAuthBackend(now = clock::instant)
    val store = InMemorySessionStore(if (signedIn) backend.session(provider) else null)
    val auth = com.herezi.core.domain.account.AuthService(backend, store, clock)
    val api = FakeAccountApi(catalog.associateBy { it.id }, now = clock::instant)
    val cache = InMemorySavedCache()
    val device = InMemorySavedSpotsRepository(deviceSaved)
    val social = FakeSocialLogin()
    val library = com.herezi.core.domain.account.SavedLibrary(auth, api, cache, device, social, clock, scope)
}
