package com.herezi.core.network

import java.time.Instant
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SpotDetailApiTest {
    private val server = MockWebServer()
    private lateinit var repo: SpotDetailApi

    @Before
    fun setUp() {
        server.start()
        repo = SpotDetailApi(HereziApiFactory.create(server.url("/").toString()))
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun `상세 404는 null`() = runTest {
        server.enqueue(MockResponse().setResponseCode(404).setBody("""{"code":"not_found","message":"없음"}"""))
        assertNull(repo.spot("nope"))
    }

    @Test
    fun `상세 경로의 스팟 ID는 한 번만 인코딩한다`() = runTest {
        server.enqueue(MockResponse().setBody(SPOT_JSON))
        server.enqueue(MockResponse().setResponseCode(404))

        repo.spot("seoul_culture:159334")
        val path = server.takeRequest().path!!
        assertTrue(path, path == "/v1/spots/seoul_culture:159334" || path == "/v1/spots/seoul_culture%3A159334")
        assertFalse(path, path.contains("%25"))

        // 경로를 가를 수 있는 문자는 세그먼트 안으로 한 번만 인코딩한다.
        repo.spot("a/b%c")
        assertEquals("/v1/spots/a%2Fb%25c", server.takeRequest().path)
    }

    @Test
    fun `끝난 상세는 200으로 받아 isEnded 와 갱신 시각을 채운다`() = runTest {
        server.enqueue(MockResponse().setBody(SPOT_JSON.replace("\"is_ended\":false", "\"is_ended\":true")))

        val spot = repo.spot("tourapi:1")!!

        assertEquals("/v1/spots/tourapi:1", server.takeRequest().requestUrl!!.encodedPath)
        assertTrue(spot.isEnded)
        assertEquals(Instant.parse("2027-04-01T00:00:00Z"), spot.fetchedAt)
    }

    private companion object {
        const val SPOT_JSON =
            """{"id":"tourapi:1","title":"t","category":"popup","lat":37.55,"lng":127.0,""" +
                """"starts_at":"2027-03-01T00:00:00Z","source":"tourapi",""" +
                """"fetched_at":"2027-04-01T00:00:00Z","is_ended":false}"""
    }
}
