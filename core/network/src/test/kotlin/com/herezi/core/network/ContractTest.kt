package com.herezi.core.network

import com.herezi.core.domain.Category
import com.herezi.core.domain.When
import java.io.File
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

    @Test
    fun `Category enum 이 계약과 같다`() {
        assertEquals(enumAfter("    Category:"), Category.entries.map { it.code })
    }

    @Test
    fun `When enum 이 계약과 같다`() {
        assertEquals(enumAfter("name: when"), When.entries.map { it.code })
    }

    @Test
    fun `free 는 쿼리 필터로 존재한다`() {
        assertTrue(spec.contains("name: free"))
    }
}
