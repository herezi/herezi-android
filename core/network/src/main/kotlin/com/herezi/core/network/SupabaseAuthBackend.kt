package com.herezi.core.network

import com.herezi.core.domain.account.AuthBackend
import com.herezi.core.domain.account.AuthException
import com.herezi.core.domain.account.AuthProvider
import com.herezi.core.domain.account.AuthSession
import java.io.IOException
import java.time.Clock
import java.time.Instant
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Supabase Auth REST (supabase-kt 없이 OkHttp 로). 모든 요청에 `apikey: <publishable key>` 를 붙인다.
 * [baseUrl]·[publishableKey] 가 비어 있으면 [isConfigured] = false (CI·키 없는 빌드).
 */
class SupabaseAuthBackend(
    baseUrl: String,
    private val publishableKey: String,
    private val client: OkHttpClient = OkHttpClient(),
    private val clock: Clock = Clock.systemUTC(),
    private val redirectUrl: String = REDIRECT_URL,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : AuthBackend {
    private val root = baseUrl.trim().trimEnd('/')

    override val isConfigured: Boolean = root.toHttpUrlOrNull() != null && publishableKey.isNotBlank()

    override suspend fun signInWithIdToken(provider: AuthProvider, idToken: String, nonce: String): AuthSession = token(
        "id_token",
        buildJsonObject {
            put("provider", provider.code)
            put("id_token", idToken)
            put("nonce", nonce)
        },
        provider,
    )

    override fun authorizeUrl(provider: AuthProvider, codeChallenge: String): String =
        requireNotNull("$root/auth/v1/authorize".toHttpUrlOrNull()) { "SUPABASE_URL 이 잘못됐다" }
            .newBuilder()
            .addQueryParameter("provider", provider.code)
            .addQueryParameter("redirect_to", redirectUrl)
            .addQueryParameter("code_challenge", codeChallenge)
            .addQueryParameter("code_challenge_method", "s256")
            .build()
            .toString()

    override suspend fun exchangeCode(authCode: String, codeVerifier: String, provider: AuthProvider): AuthSession =
        token(
            "pkce",
            buildJsonObject {
                put("auth_code", authCode)
                put("code_verifier", codeVerifier)
            },
            provider,
        )

    override suspend fun refresh(session: AuthSession): AuthSession =
        token("refresh_token", buildJsonObject { put("refresh_token", session.refreshToken) }, session.provider)

    override suspend fun logout(accessToken: String) {
        val request = request("$root/auth/v1/logout")
            .header("Authorization", "Bearer $accessToken")
            .post(ByteArray(0).toRequestBody(null))
            .build()
        execute(request) { _, _ -> }
    }

    @Suppress("ThrowsCount")
    private suspend fun token(grant: String, body: JsonObject, fallbackProvider: AuthProvider): AuthSession {
        if (!isConfigured) throw AuthException.NotConfigured()
        val request = request("$root/auth/v1/token?grant_type=$grant")
            .post(body.toString().toRequestBody(JSON))
            .build()
        return execute(request) { status, text ->
            if (status !in SUCCESS) throw statusError(status)
            val dto = runCatching { HereziApiFactory.json.decodeFromString(TokenDto.serializer(), text) }
                .getOrElse { throw AuthException.Failed("token 응답 형식") }
            dto.toSession(fallbackProvider)
        }
    }

    private fun TokenDto.toSession(fallback: AuthProvider): AuthSession {
        val expires = expiresAt?.let(Instant::ofEpochSecond)
            ?: clock.instant().plusSeconds(expiresIn ?: DEFAULT_EXPIRES_IN)
        val provider = user?.appMetadata?.get("provider")?.jsonPrimitive?.content
        return AuthSession(
            accessToken = accessToken,
            refreshToken = refreshToken,
            expiresAt = expires,
            userId = user?.id ?: throw AuthException.Failed("user 없음"),
            provider = AuthProvider.fromCode(provider) ?: fallback,
        )
    }

    private fun request(url: String) = Request.Builder().url(url).header("apikey", publishableKey)

    private suspend fun <T> execute(request: Request, handle: (Int, String) -> T): T = withContext(io) {
        try {
            client.newCall(request).execute().use { response ->
                handle(response.code, response.body?.string().orEmpty())
            }
        } catch (e: IOException) {
            throw AuthException.Offline(e)
        }
    }

    private fun statusError(status: Int): AuthException = when (status) {
        in REJECTED -> AuthException.Rejected(status)
        HTTP_UNAVAILABLE -> AuthException.Unavailable()
        else -> AuthException.Failed("supabase $status")
    }

    @Serializable
    private data class TokenDto(
        @SerialName("access_token") val accessToken: String,
        @SerialName("refresh_token") val refreshToken: String,
        @SerialName("expires_in") val expiresIn: Long? = null,
        @SerialName("expires_at") val expiresAt: Long? = null,
        val user: UserDto? = null,
    )

    @Serializable
    private data class UserDto(val id: String, @SerialName("app_metadata") val appMetadata: JsonObject? = null)

    companion object {
        /** Supabase Auth 의 Redirect URLs 에 등록돼 있어야 한다. 매니페스트 딥링크와 같다. */
        const val REDIRECT_URL = "herezi://auth-callback"

        private val JSON = "application/json".toMediaType()
        private val SUCCESS = 200..299
        private val REJECTED = setOf(400, 401, 403, 404, 422)
        private const val HTTP_UNAVAILABLE = 503
        private const val DEFAULT_EXPIRES_IN = 3600L
    }
}
