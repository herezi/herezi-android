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
    fun `저장 옮기기 DTO 가 계약 SavedImport 스키마와 같다`() {
        assertEquals(requiredOf("SavedImportRequest"), requiredOf(SavedImportRequestDto.serializer().descriptor))
        assertEquals(requiredOf("SavedImportResult"), requiredOf(SavedImportResultDto.serializer().descriptor))
    }

    @Test
    fun `계정 경로가 계약에 있다`() {
        listOf("  /v1/me/saved:", "  /v1/me/saved:import:", "  /v1/me/saved/{spot_id}:", "  /v1/me:").forEach {
            assertTrue("$it 경로가 없다", spec.contains("\n$it\n"))
        }
    }

    @Test
    fun `지역 파일 DTO 가 계약 RegionSpot·RegionSpots 와 같다`() {
        assertEquals(requiredOf("RegionSpot"), requiredOf(RegionSpotDto.serializer().descriptor))
        assertEquals(requiredOf("RegionSpots"), requiredOf(RegionSpotsDto.serializer().descriptor))
        assertTrue(spec.contains("\n  /v1/regions/{region}/spots:\n"))
    }

    @Test
    fun `좌표를 받는 경로는 계약에 없다 (서버에서 제거, 410)`() {
        listOf("  /v1/spots:", "  /v1/spots/map:", "  /v1/geo/reverse:").forEach { path ->
            assertTrue("$path 가 아직 계약에 있다", !spec.contains("\n$path\n"))
        }
    }

    @Test
    fun `Category enum 이 계약과 같다`() {
        assertEquals(enumAfter("    Category:"), Category.entries.map { it.code })
    }

    @Test
    fun `When enum 이 계약과 같다`() {
        assertEquals(enumAfter("    When:"), When.entries.map { it.code })
    }

    @Test
    fun `무료 여부는 지역 파일 항목에 있다 (기기에서 거른다)`() {
        val start = spec.indexOf("    RegionSpot:\n")
        assertTrue("RegionSpot 스키마가 없다", start >= 0)
        assertTrue(spec.indexOf("is_free:", start) > start)
    }
}
