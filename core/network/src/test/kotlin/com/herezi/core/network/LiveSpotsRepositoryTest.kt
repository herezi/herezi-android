package com.herezi.core.network

import com.herezi.core.domain.Category
import com.herezi.core.domain.GeoBounds
import com.herezi.core.domain.GeoPoint
import com.herezi.core.domain.MapQuery
import com.herezi.core.domain.SpotQuery
import com.herezi.core.domain.When
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

class LiveSpotsRepositoryTest {
    private val server = MockWebServer()
    private lateinit var repo: LiveSpotsRepository

    @Before
    fun setUp() {
        server.start()
        repo = LiveSpotsRepository(HereziApiFactory.create(server.url("/").toString()))
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun `필터를 계약대로 쿼리에 싣는다`() = runTest {
        server.enqueue(MockResponse().setBody("""{"items":[]}"""))

        repo.nearby(
            SpotQuery(
                center = GeoPoint(37.5, 127.0),
                categories = setOf(Category.POPUP, Category.FESTIVAL),
                period = When.WEEKEND,
                freeOnly = true,
            ),
        )

        val url = server.takeRequest().requestUrl!!
        assertEquals("/v1/spots", url.encodedPath)
        assertEquals("37.5", url.queryParameter("lat"))
        assertEquals("127.0", url.queryParameter("lng"))
        assertEquals("3000", url.queryParameter("radius_m"))
        assertEquals("festival,popup", url.queryParameter("category"))
        assertEquals("weekend", url.queryParameter("when"))
        assertEquals("true", url.queryParameter("free"))
    }

    @Test
    fun `필터가 없으면 category와 free를 보내지 않는다`() = runTest {
        server.enqueue(MockResponse().setBody("""{"items":[]}"""))

        repo.nearby(SpotQuery(center = GeoPoint.SeoulCityHall))

        val url = server.takeRequest().requestUrl!!
        assertNull(url.queryParameter("category"))
        assertNull(url.queryParameter("free"))
    }

    @Test
    fun `검색어는 앞뒤 공백을 잘라 q로 싣고, 없으면 보내지 않는다`() = runTest {
        server.enqueue(MockResponse().setBody("{\"items\":[$SPOT_JSON]}"))
        server.enqueue(MockResponse().setBody("""{"items":[]}"""))

        val page =
            repo.nearby(
                SpotQuery(center = GeoPoint.SeoulCityHall, radiusM = SpotQuery.SEARCH_RADIUS_M, keyword = " 빛초롱 "),
            )
        val url = server.takeRequest().requestUrl!!
        assertEquals("빛초롱", url.queryParameter("q"))
        assertEquals("20000", url.queryParameter("radius_m"))
        assertEquals(listOf("tourapi:1"), page.items.map { it.id })

        repo.nearby(SpotQuery(center = GeoPoint.SeoulCityHall, keyword = "  "))
        assertNull(server.takeRequest().requestUrl!!.queryParameter("q"))
    }

    @Test
    fun `상세 404는 null`() = runTest {
        server.enqueue(MockResponse().setResponseCode(404).setBody("""{"code":"not_found","message":"없음"}"""))
        assertNull(repo.spot("nope"))
    }

    @Test
    fun `지도 영역과 필터를 계약대로 쿼리에 싣고 응답을 옮긴다`() = runTest {
        server.enqueue(MockResponse().setBody("""{"items":[$SPOT_JSON],"truncated":true}"""))

        val result =
            repo.inBounds(
                MapQuery(
                    bounds = GeoBounds(sw = GeoPoint(37.5, 126.9), ne = GeoPoint(37.6, 127.1)),
                    categories = setOf(Category.POPUP, Category.FESTIVAL),
                    period = When.TODAY,
                    freeOnly = true,
                ),
            )

        val url = server.takeRequest().requestUrl!!
        assertEquals("/v1/spots/map", url.encodedPath)
        assertEquals("37.5", url.queryParameter("sw_lat"))
        assertEquals("126.9", url.queryParameter("sw_lng"))
        assertEquals("37.6", url.queryParameter("ne_lat"))
        assertEquals("127.1", url.queryParameter("ne_lng"))
        assertEquals("festival,popup", url.queryParameter("category"))
        assertEquals("today", url.queryParameter("when"))
        assertEquals("true", url.queryParameter("free"))
        assertEquals("200", url.queryParameter("limit"))
        assertTrue(result.truncated)
        assertEquals(listOf("tourapi:1"), result.items.map { it.id })
    }

    @Test
    fun `지도 필터가 없으면 category와 free를 보내지 않는다`() = runTest {
        server.enqueue(MockResponse().setBody("""{"items":[],"truncated":false}"""))

        val result = repo.inBounds(MapQuery(GeoBounds(sw = GeoPoint(37.5, 126.9), ne = GeoPoint(37.6, 127.1))))

        val url = server.takeRequest().requestUrl!!
        assertNull(url.queryParameter("category"))
        assertNull(url.queryParameter("free"))
        assertEquals("now", url.queryParameter("when"))
        assertFalse(result.truncated)
        assertTrue(result.items.isEmpty())
    }

    @Test
    fun `끝난 상세는 200으로 받아 isEnded 와 갱신 시각을 채운다`() = runTest {
        server.enqueue(MockResponse().setBody(SPOT_JSON.replace("\"is_ended\":false", "\"is_ended\":true")))

        val spot = repo.spot("tourapi:1")!!

        assertEquals("/v1/spots/tourapi:1", server.takeRequest().requestUrl!!.encodedPath)
        assertTrue(spot.isEnded)
        assertEquals(Instant.parse("2027-04-01T00:00:00Z"), spot.fetchedAt)
    }

    @Test
    fun `역지오코딩은 좌표를 계약대로 보내고 label 을 돌려준다`() = runTest {
        server.enqueue(
            MockResponse().setBody("""{"area1":"서울특별시","area2":"중구","area3":"태평로1가","label":"중구 태평로1가"}"""),
        )

        val name = repo.placeName(GeoPoint(37.5663, 126.9779))

        val url = server.takeRequest().requestUrl!!
        assertEquals("/v1/geo/reverse", url.encodedPath)
        assertEquals("37.5663", url.queryParameter("lat"))
        assertEquals("126.9779", url.queryParameter("lng"))
        assertEquals("중구 태평로1가", name)
    }

    @Test
    fun `역지오코딩 빈 label 과 501·502 는 null 이라 OS 지오코더로 넘어간다`() = runTest {
        server.enqueue(MockResponse().setBody("""{"area1":"","area2":"","area3":"","label":""}"""))
        server.enqueue(MockResponse().setResponseCode(501).setBody("""{"code":"not_configured","message":"x"}"""))
        server.enqueue(MockResponse().setResponseCode(502).setBody("""{"code":"upstream_error","message":"x"}"""))

        repeat(3) { assertNull(repo.placeName(GeoPoint.SeoulCityHall)) }
    }

    @Test
    fun `역지오코딩 400 은 오류로 던진다`() = runTest {
        server.enqueue(MockResponse().setResponseCode(400).setBody("""{"code":"bad_request","message":"x"}"""))
        val error = runCatching { repo.placeName(GeoPoint.SeoulCityHall) }.exceptionOrNull()
        assertTrue(error is retrofit2.HttpException)
    }

    private companion object {
        const val SPOT_JSON =
            """{"id":"tourapi:1","title":"t","category":"popup","lat":37.55,"lng":127.0,""" +
                """"starts_at":"2027-03-01T00:00:00Z","source":"tourapi",""" +
                """"fetched_at":"2027-04-01T00:00:00Z","is_ended":false}"""
    }
}
