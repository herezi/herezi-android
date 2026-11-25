package com.herezi.core.network

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class RegionSpotsRepositoryTest {
    private val server = MockWebServer()
    private lateinit var dir: File
    private val clock = MutableClock(Instant.parse("2026-11-25T03:00:00Z"))

    class MutableClock(var now: Instant) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC

        override fun withZone(zone: ZoneId?): Clock = this

        override fun instant(): Instant = now
    }

    @Before
    fun setUp() {
        server.start()
        dir = Files.createTempDirectory("region").toFile()
    }

    @After
    fun tearDown() {
        server.shutdown()
        dir.deleteRecursively()
    }

    private fun repo(base: String = server.url("/").toString()) =
        RegionSpotsRepository(base, dir, clock = clock, io = Dispatchers.Unconfined)

    private fun ok(body: String, etag: String = "\"v1\"") =
        MockResponse().setHeader("ETag", etag).setHeader("Content-Type", "application/json").setBody(body)

    @Test
    fun `첫 실행은 받아서 디스크에 두고, 1시간 안에는 다시 묻지 않으며, force 면 If-None-Match 로 묻는다`() = runTest {
        server.enqueue(ok(regionJson(RegionItem("a"), RegionItem("b"))))
        val repo = repo()
        assertEquals(listOf("a", "b"), repo.spots().map { it.id })
        val first = server.takeRequest()
        assertEquals("/v1/regions/seoul/spots", first.path)
        assertNull(first.getHeader("If-None-Match"))
        assertEquals("gzip", first.getHeader("Accept-Encoding"))
        assertTrue(File(dir, "seoul.json").exists() && File(dir, "seoul.meta.json").exists())

        assertFalse(repo.refresh(force = false))
        assertEquals(1, server.requestCount)

        server.enqueue(MockResponse().setResponseCode(304))
        assertFalse(repo.refresh(force = true))
        assertEquals("\"v1\"", server.takeRequest().getHeader("If-None-Match"))

        clock.now = clock.now.plus(Duration.ofMinutes(61))
        server.enqueue(ok(regionJson(RegionItem("c")), etag = "\"v2\""))
        assertTrue(repo.refresh(force = false))
        assertEquals(listOf("c"), repo.spots().map { it.id })
    }

    @Test
    fun `다시 실행하면 디스크로 먼저 그리고 첫 확인은 304 로 끝난다`() = runTest {
        server.enqueue(ok(regionJson(RegionItem("a"))))
        repo().spots()
        server.takeRequest()

        val again = repo()
        assertEquals(listOf("a"), again.spots().map { it.id })
        assertEquals(1, server.requestCount) // 디스크에서 읽음
        server.enqueue(MockResponse().setResponseCode(304))
        assertFalse(again.refresh(force = false)) // 실행 후 첫 확인은 항상
        assertEquals("\"v1\"", server.takeRequest().getHeader("If-None-Match"))
    }

    @Test
    fun `오프라인이면 마지막 데이터로 동작하고 refresh 는 던진다`() = runTest {
        server.enqueue(ok(regionJson(RegionItem("a"))))
        repo().spots()
        server.shutdown()

        val offline = repo("http://127.0.0.1:9/")
        assertEquals(listOf("a"), offline.spots().map { it.id })
        try {
            offline.refresh(force = true)
            fail("던져야 한다")
        } catch (_: IOException) {
        }
        assertEquals(listOf("a"), offline.spots().map { it.id })
    }

    @Test
    fun `데이터가 없고 받지도 못하면 던진다`() = runTest {
        try {
            repo("http://127.0.0.1:9/").spots()
            fail("던져야 한다")
        } catch (_: IOException) {
        }
    }

    @Test
    fun `깨진 디스크 파일은 지우고 다시 받는다`() = runTest {
        File(dir, "seoul.json").writeText("{깨짐")
        File(dir, "seoul.meta.json").writeText("""{"etag":"\"old\""}""")
        server.enqueue(ok(regionJson(RegionItem("a"))))
        assertEquals(listOf("a"), repo().spots().map { it.id })
        assertNull(server.takeRequest().getHeader("If-None-Match"))
    }

    @Test
    fun `gzip 응답을 풀고 나노초 날짜를 읽는다`() = runTest {
        server.enqueue(
            MockResponse().setHeader("ETag", "\"g\"").setHeader("Content-Encoding", "gzip")
                .setBody(gzip(regionJson(RegionItem("a", startsAt = "2026-11-01T09:00:00.987654321+09:00")))),
        )
        val spot = repo().spots().single()
        assertEquals(Instant.parse("2026-11-01T00:00:00.987654321Z"), spot.startsAt)
    }

    @Test
    fun `동시 요청은 한 번으로 합친다`() = runTest {
        server.enqueue(ok(regionJson(RegionItem("a"))))
        val repo = repo()
        (1..5).map { async { repo.spots() } }.awaitAll()
        assertEquals(1, server.requestCount)
    }
}
