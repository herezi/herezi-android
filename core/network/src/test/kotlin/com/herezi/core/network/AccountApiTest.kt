package com.herezi.core.network

import com.herezi.core.domain.account.AuthException
import com.herezi.core.domain.account.AuthProvider
import com.herezi.core.domain.account.AuthService
import com.herezi.core.domain.fake.InMemorySessionStore
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/** Supabase Auth(토큰 교환·리프레시) + `/v1/me/…` 를 MockWebServer 하나로 확인한다. */
class AccountApiTest {
    private val server = MockWebServer()
    private val now = Instant.parse("2026-11-25T03:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private lateinit var backend: SupabaseAuthBackend
    private lateinit var api: LiveAccountApi
    private val store = InMemorySessionStore()

    @Before
    fun setUp() {
        server.start()
        val base = server.url("/").toString()
        backend = SupabaseAuthBackend(base, "pk-test", clock = clock, io = Dispatchers.Unconfined)
        api = LiveAccountApi(HereziApiFactory.create(base))
    }

    @After
    fun tearDown() = server.shutdown()

    private fun tokenJson(n: Int, provider: String = "kakao") = """
        {"access_token":"access-$n","token_type":"bearer","expires_in":3600,"expires_at":${now.epochSecond + 3600},
         "refresh_token":"refresh-$n","user":{"id":"user-1","app_metadata":{"provider":"$provider"}}}
    """.trimIndent()

    private fun json(code: Int, body: String) = MockResponse().setResponseCode(code)
        .setHeader("Content-Type", "application/json").setBody(body)

    @Test
    fun `id_token 교환 요청은 apikey 헤더와 provider·id_token·nonce 를 보낸다`() = runTest {
        server.enqueue(json(200, tokenJson(0)))
        val session = backend.signInWithIdToken(AuthProvider.KAKAO, "kakao-id", "raw-nonce")

        val req = server.takeRequest()
        assertEquals("/auth/v1/token?grant_type=id_token", req.path)
        assertEquals("POST", req.method)
        assertEquals("pk-test", req.getHeader("apikey"))
        val body = req.body.readUtf8()
        listOf("\"provider\":\"kakao\"", "\"id_token\":\"kakao-id\"", "\"nonce\":\"raw-nonce\"").forEach {
            assertTrue("$it in $body", body.contains(it))
        }
        assertEquals("access-0", session.accessToken)
        assertEquals("user-1", session.userId)
        assertEquals(now.plusSeconds(3600), session.expiresAt)
    }

    @Test
    fun `거부되면 Rejected, 5xx 는 Failed, 연결 실패는 Offline`() = runTest {
        server.enqueue(json(400, """{"error":"invalid_grant"}"""))
        expect<AuthException.Rejected> { backend.signInWithIdToken(AuthProvider.KAKAO, "x", "n") }
        server.enqueue(json(500, "{}"))
        expect<AuthException.Failed> { backend.signInWithIdToken(AuthProvider.KAKAO, "x", "n") }
        server.shutdown()
        expect<AuthException.Offline> { backend.signInWithIdToken(AuthProvider.KAKAO, "x", "n") }
    }

    @Test
    fun `Apple 웹 흐름 주소와 PKCE 교환`() = runTest {
        val url = backend.authorizeUrl(AuthProvider.APPLE, "challenge-1")
        assertTrue(url, url.contains("/auth/v1/authorize?provider=apple"))
        assertTrue(url, url.contains("redirect_to=herezi%3A%2F%2Fauth-callback"))
        assertTrue(url, url.contains("code_challenge=challenge-1&code_challenge_method=s256"))

        server.enqueue(json(200, tokenJson(0, provider = "apple")))
        val session = backend.exchangeCode("code-1", "verifier-1", AuthProvider.APPLE)
        val req = server.takeRequest()
        assertEquals("/auth/v1/token?grant_type=pkce", req.path)
        val body = req.body.readUtf8()
        assertTrue(body.contains("\"auth_code\":\"code-1\"") && body.contains("\"code_verifier\":\"verifier-1\""))
        assertEquals(AuthProvider.APPLE, session.provider)
    }

    @Test
    fun `me 요청은 Bearer 를 붙이고 401 이면 리프레시 후 한 번 다시 시도한다`() = runTest {
        server.enqueue(json(200, tokenJson(0)))
        val auth = AuthService(backend, store, clock)
        auth.signInWithIdToken(AuthProvider.KAKAO, "id", "n")
        server.takeRequest()

        server.enqueue(json(401, """{"code":"unauthorized","message":"expired"}"""))
        server.enqueue(json(200, tokenJson(1)))
        server.enqueue(MockResponse().setResponseCode(204))
        auth.authorized { api.save(it, "tourapi:1") }

        val first = server.takeRequest()
        assertEquals("PUT", first.method)
        assertEquals("/v1/me/saved/tourapi:1", first.path)
        assertEquals("Bearer access-0", first.getHeader("Authorization"))
        val refresh = server.takeRequest()
        assertEquals("/auth/v1/token?grant_type=refresh_token", refresh.path)
        assertTrue(refresh.body.readUtf8().contains("\"refresh_token\":\"refresh-0\""))
        assertEquals("Bearer access-1", server.takeRequest().getHeader("Authorization"))
        assertEquals("access-1", store.value?.accessToken)
    }

    @Test
    fun `리프레시가 거부되면 로그아웃 상태`() = runTest {
        server.enqueue(json(200, tokenJson(0)))
        val auth = AuthService(backend, store, clock)
        auth.signInWithIdToken(AuthProvider.KAKAO, "id", "n")
        server.enqueue(json(401, """{"code":"unauthorized","message":"x"}"""))
        server.enqueue(json(400, """{"error":"invalid_grant"}"""))
        expect<AuthException.SessionExpired> { auth.authorized { api.listSaved(it, null) } }
        assertNull(store.value)
    }

    @Test
    fun `503 은 Unavailable, 409 는 SavedLimit, 404 는 NotFound`() = runTest {
        server.enqueue(json(503, """{"code":"auth_unavailable","message":"jwks"}"""))
        expect<AuthException.Unavailable> { api.listSaved("t", null) }
        server.enqueue(json(409, """{"code":"saved_limit_exceeded","message":"500"}"""))
        expect<AuthException.SavedLimit> { api.save("t", "a") }
        server.enqueue(json(404, """{"code":"not_found","message":"x"}"""))
        expect<AuthException.NotFound> { api.save("t", "a") }
    }

    @Test
    fun `저장 목록은 saved_at 과 is_ended 를 읽고 import 는 spot_ids 를 보낸다`() = runTest {
        server.enqueue(
            json(
                200,
                """
                {"items":[{"id":"a","title":"A","category":"festival","lat":37.5,"lng":127.0,
                  "starts_at":"2026-11-01T00:00:00Z","source":"tourapi","fetched_at":"2026-11-20T00:00:00Z",
                  "is_ended":true,"saved_at":"2026-11-24T10:00:00+09:00"}],"next_cursor":"c2"}
                """.trimIndent(),
            ),
        )
        val page = api.listSaved("t", null)
        assertEquals("/v1/me/saved?limit=100", server.takeRequest().path)
        assertEquals(Instant.parse("2026-11-24T01:00:00Z"), page.items.single().savedAt)
        assertTrue(page.items.single().spot.isEnded)
        assertEquals("c2", page.nextCursor)

        server.enqueue(json(200, """{"imported":2,"skipped_unknown":1}"""))
        val result = api.importSaved("t", listOf("a", "b", "c"))
        val req = server.takeRequest()
        assertEquals("/v1/me/saved:import", req.path)
        assertEquals("""{"spot_ids":["a","b","c"]}""", req.body.readUtf8())
        assertEquals(2, result.imported)
        assertEquals(1, result.skippedUnknown)

        server.enqueue(MockResponse().setResponseCode(204))
        api.deleteMe("t")
        val del = server.takeRequest()
        assertEquals("DELETE", del.method)
        assertEquals("/v1/me", del.path)
    }

    private suspend inline fun <reified T : AuthException> expect(crossinline block: suspend () -> Unit) {
        try {
            block()
            fail("${T::class.simpleName} 를 던져야 한다")
        } catch (e: AuthException) {
            assertTrue("got $e", e is T)
        }
    }
}
