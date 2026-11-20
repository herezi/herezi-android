package com.herezi.core.network

import com.herezi.core.domain.Category
import com.herezi.core.domain.When
import java.io.File
import kotlinx.serialization.descriptors.SerialDescriptor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 복사해 둔 계약(api/openapi.yaml)과 도메인 enum 이 어긋나면 실패한다.
 * 실패하면: `make sync-api` 로 최신 계약을 받고 Category/When 과 DTO 를 맞춘다.
 */
class ContractTest {
    private val spec: String = File(System.getProperty("herezi.openapi")).readText()

    private fun enumAfter(anchor: String): List<String> {
        val start = spec.indexOf(anchor)
        check(start >= 0) { "openapi.yaml 에서 '$anchor' 를 찾지 못했다" }
        val line = Regex("""enum: \[([^\]]*)]""").find(spec, start) ?: error("enum 없음: $anchor")
        return line.groupValues[1].split(",").map { it.trim() }
    }

    /** 스키마 [name] 의 `required: [...]` 목록. */
    private fun requiredOf(name: String): Set<String> {
        val start = spec.indexOf("    $name:\n")
        check(start >= 0) { "openapi.yaml 에서 스키마 '$name' 를 찾지 못했다" }
        val line = Regex("""required: \[([^\]]*)]""").find(spec, start) ?: error("required 없음: $name")
        return line.groupValues[1].split(",").map { it.trim() }.toSet()
    }

    /** DTO 에서 기본값이 없는(=JSON 에 꼭 있어야 하는) 필드의 직렬화 이름. */
    private fun requiredOf(descriptor: SerialDescriptor): Set<String> = (0 until descriptor.elementsCount)
        .filterNot { descriptor.isElementOptional(it) }
        .map { descriptor.getElementName(it) }
        .toSet()

    @Test
    fun `SpotDto 필수 필드가 계약 Spot 의 required 와 같다`() {
        assertEquals(requiredOf("Spot"), requiredOf(SpotDto.serializer().descriptor))
    }

    @Test
    fun `SpotMapDto 필수 필드가 계약 SpotMap 의 required 와 같다`() {
        assertEquals(requiredOf("SpotMap"), requiredOf(SpotMapDto.serializer().descriptor))
    }

    @Test
    fun `GeoReverseDto 필수 필드가 계약 GeoReverse 의 required 와 같다`() {
        assertEquals(requiredOf("GeoReverse"), requiredOf(GeoReverseDto.serializer().descriptor))
    }

    @Test
    fun `역지오코딩 경로와 lat lng 파라미터, 폴백 응답 코드가 계약에 있다`() {
        val start = spec.indexOf("  /v1/geo/reverse:")
        assertTrue("/v1/geo/reverse 경로가 없다", start >= 0)
        val end = spec.indexOf("\ncomponents:", start)
        val section = spec.substring(start, if (end < 0) spec.length else end)
        listOf("name: lat", "name: lng", "GeoReverse", "\"501\"", "\"502\"").forEach {
            assertTrue("$it 가 없다", section.contains(it))
        }
    }

    @Test
    fun `지도 bbox 경로와 파라미터가 계약에 있다`() {
        val start = spec.indexOf("  /v1/spots/map:")
        assertTrue("/v1/spots/map 경로가 없다", start >= 0)
        val end = spec.indexOf("\n  /v1/", start + 1)
        val section = spec.substring(start, if (end < 0) spec.length else end)
        listOf("sw_lat", "sw_lng", "ne_lat", "ne_lng", "category", "when", "free", "limit").forEach {
            assertTrue("$it 파라미터가 없다", section.contains("name: $it"))
        }
        assertTrue(section.contains("SpotMap"))
    }

    @Test
    fun `Category enum 이 계약과 같다`() {
        assertEquals(enumAfter("    Category:"), Category.entries.map { it.code })
    }

    @Test
    fun `When enum 이 계약과 같다`() {
        assertEquals(enumAfter("name: when"), When.entries.map { it.code })
    }

    @Test
    fun `목록 경로에 키워드 검색 q 가 있다`() {
        val start = spec.indexOf("  /v1/spots:")
        assertTrue("/v1/spots 경로가 없다", start >= 0)
        val end = spec.indexOf("\n  /v1/", start + 1)
        assertTrue(spec.substring(start, if (end < 0) spec.length else end).contains("name: q"))
    }

    @Test
    fun `free 는 쿼리 필터로 존재한다`() {
        assertTrue(spec.contains("name: free"))
    }
}
