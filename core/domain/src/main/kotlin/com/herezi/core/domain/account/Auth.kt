package com.herezi.core.domain.account

import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.util.Base64

/** 로그인 제공자. [code] 는 Supabase `provider` 값. */
enum class AuthProvider(val code: String) {
    KAKAO("kakao"),
    APPLE("apple"),
    ;

    companion object {
        fun fromCode(code: String?): AuthProvider? = entries.firstOrNull { it.code == code }
    }
}

/** Supabase Auth 세션. [userId] 는 JWT `sub`. */
data class AuthSession(
    val accessToken: String,
    val refreshToken: String,
    val expiresAt: Instant,
    val userId: String,
    val provider: AuthProvider,
) {
    /** 로그·toString 에 토큰이 찍히지 않게 한다. */
    override fun toString(): String = "AuthSession(userId=$userId, provider=$provider, expiresAt=$expiresAt)"
}

/**
 * 인증·계정 API 오류. 네트워크 계층이 HTTP 상태를 이 타입으로 바꾼다.
 * 화면은 원인을 보여주지 않고 [com.herezi.core.domain.account.AccountMessages] 문구만 쓴다.
 */
sealed class AuthException(message: String) : Exception(message) {
    /** 사용자가 카카오·Apple 화면을 닫음. 조용히 기본 상태로. */
    class Cancelled : AuthException("cancelled")

    /** 인터넷 연결 없음. */
    class Offline(cause: Throwable? = null) : AuthException("offline") {
        init {
            cause?.let(::initCause)
        }
    }

    /** 키(Supabase·카카오)가 설정되지 않은 빌드. */
    class NotConfigured : AuthException("not configured")

    /** Supabase 가 토큰 교환·리프레시를 거부(400·401·403·422). */
    class Rejected(val status: Int) : AuthException("rejected $status")

    /** API 401: 토큰이 만료·무효. [AuthService.authorized] 가 리프레시 후 한 번 다시 시도한다. */
    class Unauthorized : AuthException("unauthorized")

    /** 리프레시까지 실패 → 로그아웃 상태가 됐다. */
    class SessionExpired : AuthException("session expired")

    /** 503 auth_unavailable·auth_not_configured: 일시적. 세션은 유지한다. */
    class Unavailable : AuthException("auth unavailable")

    /** 409 saved_limit_exceeded (500개). */
    class SavedLimit : AuthException("saved limit")

    /** 404. */
    class NotFound : AuthException("not found")

    /** 그 밖의 서버·제공자 오류. */
    class Failed(detail: String) : AuthException(detail)
}

/** Supabase Auth 포트. 구현: :core:network `SupabaseAuthBackend`. */
interface AuthBackend {
    /** SUPABASE_URL·키가 있는지. 없으면 로그인을 시도하지 않는다. */
    val isConfigured: Boolean

    /** `POST /auth/v1/token?grant_type=id_token` — [nonce] 는 원문(제공자에는 SHA-256 값을 보냈다). */
    suspend fun signInWithIdToken(provider: AuthProvider, idToken: String, nonce: String): AuthSession

    /** 웹 OAuth(PKCE) 시작 주소. `redirect_to` 는 앱 딥링크. */
    fun authorizeUrl(provider: AuthProvider, codeChallenge: String): String

    /** `POST /auth/v1/token?grant_type=pkce` — 딥링크로 받은 `code` 교환. */
    suspend fun exchangeCode(authCode: String, codeVerifier: String, provider: AuthProvider): AuthSession

    /** `POST /auth/v1/token?grant_type=refresh_token`. */
    suspend fun refresh(session: AuthSession): AuthSession

    /** `POST /auth/v1/logout`. 실패해도 기기에서는 로그아웃한다. */
    suspend fun logout(accessToken: String)
}

/** 세션 보관 포트. 구현: :core:data `EncryptedSessionStore`(EncryptedSharedPreferences). */
interface SessionStore {
    fun load(): AuthSession?

    fun save(session: AuthSession?)
}

/** 플랫폼 로그인 화면 포트. 구현: :app (카카오 SDK, Custom Tabs). */
interface SocialLogin {
    /** 카카오톡(없으면 카카오 계정 웹)으로 로그인해 OIDC id_token 을 돌려준다. [hashedNonce] 는 SHA-256 hex. */
    suspend fun kakaoIdToken(hashedNonce: String): String

    /** 브라우저(Custom Tabs)로 [authorizeUrl] 을 열고, 앱 딥링크로 돌아온 `code` 를 돌려준다. 탭을 닫으면 [AuthException.Cancelled]. */
    suspend fun webAuthCode(authorizeUrl: String): String

    /** 카카오 SDK 로그아웃(토큰 삭제). 실패는 무시한다. */
    suspend fun kakaoLogout()

    /** 카카오 연결 끊기(탈퇴). 실패해도 서버 탈퇴는 진행한다. */
    suspend fun kakaoUnlink()
}

/** nonce·PKCE 값. 원문은 Supabase 에, SHA-256 값은 제공자에 보낸다(iOS 와 같은 방식). */
object AuthCrypto {
    private const val RAW_BYTES = 32
    private val random = SecureRandom()

    fun randomToken(): String {
        val bytes = ByteArray(RAW_BYTES).also(random::nextBytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    fun sha256Hex(value: String): String = sha256(value).joinToString("") { "%02x".format(it) }

    /** PKCE S256 challenge = base64url(sha256(verifier)), 패딩 없음. */
    fun codeChallenge(verifier: String): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(sha256(verifier))

    private fun sha256(value: String): ByteArray = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
}
