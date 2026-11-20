package com.herezi.core.network

import com.herezi.core.domain.Category
import com.herezi.core.domain.GeoPoint
import com.herezi.core.domain.SpotQuery
import com.herezi.core.domain.When
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
    fun `상세 404는 null`() = runTest {
        server.enqueue(MockResponse().setResponseCode(404).setBody("""{"code":"not_found","message":"없음"}"""))
        assertNull(repo.spot("nope"))
    }
}
