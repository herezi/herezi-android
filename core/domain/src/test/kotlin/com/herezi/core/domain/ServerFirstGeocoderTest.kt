package com.herezi.core.domain

import com.herezi.core.domain.fake.FakeReverseGeocoder
import com.herezi.core.domain.fake.FakeSpotsRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ServerFirstGeocoderTest {
    private val point = GeoPoint.SeoulCityHall

    /** 서버 응답만 바꾼 저장소. */
    private class Server(private val answer: () -> String?) : SpotsRepository by FakeSpotsRepository() {
        override suspend fun placeName(point: GeoPoint): String? = answer()
    }

    @Test
    fun `Fake 저장소는 중구 태평로1가`() = runTest {
        assertEquals("중구 태평로1가", FakeSpotsRepository().placeName(point))
    }

    @Test
    fun `서버 위치명이 있으면 그것을 쓴다`() = runTest {
        val geocoder = ServerFirstGeocoder(Server { " 중구 태평로1가 " }, FakeReverseGeocoder("OS 이름"))
        assertEquals("중구 태평로1가", geocoder.placeName(point))
    }

    @Test
    fun `서버가 비었거나 null 이면 OS 지오코더`() = runTest {
        assertEquals("OS 이름", ServerFirstGeocoder(Server { "" }, FakeReverseGeocoder("OS 이름")).placeName(point))
        assertEquals("OS 이름", ServerFirstGeocoder(Server { null }, FakeReverseGeocoder("OS 이름")).placeName(point))
    }

    @Test
    fun `서버 오류면 OS 지오코더, 둘 다 없으면 null`() = runTest {
        val failing = Server { error("네트워크 오류") }
        assertEquals("OS 이름", ServerFirstGeocoder(failing, FakeReverseGeocoder("OS 이름")).placeName(point))
        assertNull(ServerFirstGeocoder(failing, FakeReverseGeocoder(null)).placeName(point))
    }
}
