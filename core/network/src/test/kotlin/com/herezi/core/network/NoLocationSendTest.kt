package com.herezi.core.network

import com.herezi.core.domain.Category
import com.herezi.core.domain.GeoBounds
import com.herezi.core.domain.GeoPoint
import com.herezi.core.domain.LocalSpotsRepository
import com.herezi.core.domain.MapQuery
import com.herezi.core.domain.SpotQuery
import com.herezi.core.domain.When
import java.nio.file.Files
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 가드: 앱이 쓰는 모든 서버 호출을 실제 HTTP 어댑터로 돌리고, 나간 요청 어디에도 기기 좌표가 없음을 확인한다
 * (herezi-api ADR 0004). 좌표를 받는 폐기 예고 경로(`/v1/spots?lat…`, `/v1/spots/map`, `/v1/geo/reverse`)는 부르지 않는다.
 */
class NoLocationSendTest {
    private val server = MockWebServer()
    private val dir = Files.createTempDirectory("nosend").toFile()
    private val now = Instant.parse("2026-11-25T03:00:00Z")

    /** 일부러 흔하지 않은 기기 좌표 — 요청에 이 숫자가 나오면 실패. */
    private val device = GeoPoint(37.54321, 127.05678)

    private val regionBody = regionJson(
        RegionItem("tourapi:1", lat = 37.5433, lng = 127.0568, title = "성수 팝업"),
        RegionItem("tourapi:2", lat = 37.5663, lng = 126.9779, title = "시청 축제", category = "festival"),
        RegionItem("tourapi:3", lat = 37.5440, lng = 127.0570, title = "성수 전시", category = "exhibition"),
    )

    private val requests = mutableListOf<RecordedRequest>()

    init {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                synchronized(requests) { requests += request }
                val path = request.requestUrl!!.encodedPath
                return when {
                    path == "/v1/regions/seoul/spots" ->
                        if (request.getHeader("If-None-Match") == "\"r1\"") {
                            MockResponse().setResponseCode(304)
                        } else {
                            MockResponse().setHeader("ETag", "\"r1\"").setBody(regionBody)
                        }
                    path.startsWith("/v1/spots/") && path != "/v1/spots/map" -> MockResponse().setBody(
                        RegionItem(
                            "old:1",
                            lat = 37.5,
                            lng = 127.0,
                        ).json().replace("\"is_ended\":false", "\"is_ended\":true"),
                    )
                    path == "/v1/me/saved" && request.method == "GET" -> MockResponse().setBody("""{"items":[]}""")
                    path == "/v1/me/saved:import" -> MockResponse().setBody("""{"imported":1,"skipped_unknown":0}""")
                    path.startsWith("/v1/me") -> MockResponse().setResponseCode(204)
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
        dir.deleteRecursively()
    }

    @Test
    fun `홈·목록·지도·검색·상세·저장 어디에서도 좌표를 보내지 않는다`() = runTest {
        val base = server.url("/").toString()
        val region =
            RegionSpotsRepository(base, dir, clock = Clock.fixed(now, ZoneOffset.UTC), io = Dispatchers.Unconfined)
        val api = HereziApiFactory.create(base)
        val repo = LocalSpotsRepository(region, SpotDetailApi(api)::spot, Clock.fixed(now, ZoneOffset.UTC))
        val account = LiveAccountApi(api)

        // 홈(3km·이번 주), 전체 목록(필터·페이지), 새로고침
        val home = repo.nearby(SpotQuery(center = device, radiusM = 3000, period = When.WEEK, limit = 100))
        assertEquals(listOf("tourapi:1", "tourapi:3"), home.items.map { it.id })
        repo.nearby(SpotQuery(center = device, categories = setOf(Category.EXHIBITION), freeOnly = false, limit = 1))
        repo.nearby(SpotQuery(center = device, limit = 1, cursor = "1"))
        repo.refreshData(force = true)
        repo.refreshData(force = false)
        // 지도(영역·다시 찾기·내 위치)
        repo.inBounds(MapQuery(GeoBounds(GeoPoint(37.53, 127.04), GeoPoint(37.56, 127.07))))
        repo.inBounds(
            MapQuery(
                GeoBounds(
                    GeoPoint(device.lat - 0.01, device.lng - 0.01),
                    GeoPoint(
                        device.lat + 0.01,
                        device.lng + 0.01,
                    ),
                ),
            ),
        )
        // 검색(20km, q)
        val found = repo.nearby(SpotQuery(center = device, radiusM = SpotQuery.SEARCH_RADIUS_M, keyword = " 성수 "))
        assertEquals(2, found.items.size)
        // 상세: 기기 데이터 / 원격
        assertFalse(repo.spot("tourapi:1")!!.isEnded)
        assertTrue(repo.spot("old:1")!!.isEnded)
        // 저장(목록·저장·취소·옮기기·탈퇴)
        account.listSaved("token", null)
        account.save("token", "tourapi:1")
        account.unsave("token", "tourapi:1")
        account.importSaved("token", listOf("tourapi:1"))
        account.deleteMe("token")

        val banned =
            listOf("lat", "lng", "sw_lat", "sw_lng", "ne_lat", "ne_lng", "radius_m", "coords", "latitude", "longitude")
        val coordinateText = listOf("37.5432", "127.0567", "37.54321", "127.05678")
        assertTrue(requests.isNotEmpty())
        requests.forEach { r ->
            val url = r.requestUrl!!
            val names = url.queryParameterNames
            assertTrue("좌표 쿼리: ${r.path}", names.none { it in banned || it.startsWith("sw_") || it.startsWith("ne_") })
            val path = url.encodedPath
            assertFalse(
                "폐기 예고 경로: ${r.path}",
                path == "/v1/spots" || path == "/v1/spots/map" || path == "/v1/geo/reverse",
            )
            val everything = r.requestLine + "\n" + r.headers.toString() + "\n" + r.body.clone().readUtf8()
            coordinateText.forEach { assertFalse("좌표 숫자 $it: $everything", everything.contains(it)) }
            val body = r.body.clone().readUtf8()
            banned.forEach { assertFalse("본문 키 $it: $body", body.contains("\"$it\"")) }
        }
        // 지역 파일은 두 번째부터 If-None-Match 로만 묻는다.
        val regionCalls = requests.filter { it.requestUrl!!.encodedPath == "/v1/regions/seoul/spots" }
        assertTrue(regionCalls.size >= 2)
        assertTrue(regionCalls.drop(1).all { it.getHeader("If-None-Match") == "\"r1\"" })
    }

    @Test
    fun `API 인터페이스에 좌표를 받는 메서드가 없다`() {
        val params = HereziApi::class.java.declaredMethods.flatMap { m ->
            m.parameterAnnotations.flatMap { it.toList() }.filterIsInstance<retrofit2.http.Query>().map { it.value }
        }
        assertTrue(
            params.toString(),
            params.none {
                it in setOf("lat", "lng", "radius_m") || it.startsWith("sw_") ||
                    it.startsWith("ne_")
            },
        )
        val paths = HereziApi::class.java.declaredMethods.mapNotNull { m ->
            m.getAnnotation(retrofit2.http.GET::class.java)?.value
        }
        assertFalse(paths.toString(), paths.any { it == "v1/spots" || it == "v1/spots/map" || it == "v1/geo/reverse" })
    }
}
