package com.herezi.core.ui.image

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageKeysTest {
    @Test
    fun `원본이 요청보다 작거나 같으면 줄이지 않는다`() {
        assertEquals(1, ImageKeys.sampleSize(100, 100, 200, 200))
        assertEquals(1, ImageKeys.sampleSize(200, 200, 200, 200))
        assertEquals(1, ImageKeys.sampleSize(399, 399, 200, 200))
    }

    @Test
    fun `결과가 요청보다 작아지지 않는 가장 큰 2의 거듭제곱`() {
        assertEquals(2, ImageKeys.sampleSize(400, 400, 200, 200))
        assertEquals(4, ImageKeys.sampleSize(4000, 3000, 720, 540))
        // 4032x3024 → 썸네일 72dp@3x=216: 8 이면 504x378, 16 이면 252x189 (높이 189 < 216) → 8
        assertEquals(8, ImageKeys.sampleSize(4032, 3024, 216, 216))
    }

    @Test
    fun `한 변이라도 요청보다 작아지면 멈춘다`() {
        // 가로로 긴 이미지: 높이가 기준
        assertEquals(1, ImageKeys.sampleSize(4000, 300, 200, 200))
        assertEquals(2, ImageKeys.sampleSize(4000, 400, 200, 200))
    }

    @Test
    fun `크기를 모르면 1`() {
        assertEquals(1, ImageKeys.sampleSize(0, 100, 50, 50))
        assertEquals(1, ImageKeys.sampleSize(100, 100, 0, 50))
        assertEquals(1, ImageKeys.sampleSize(-1, -1, 50, 50))
    }

    @Test
    fun `요청 크기는 64 배수로 올리고 상한 2048, 모르면 상한`() {
        assertEquals(64, ImageKeys.bucket(1))
        assertEquals(64, ImageKeys.bucket(64))
        assertEquals(256, ImageKeys.bucket(216))
        assertEquals(2048, ImageKeys.bucket(5000))
        assertEquals(2048, ImageKeys.bucket(0))
    }

    @Test
    fun `메모리 키는 주소와 크기 구간으로 정해진다`() {
        val url = "https://example.com/a.jpg"
        assertEquals(ImageKeys.memoryKey(url, 200, 200), ImageKeys.memoryKey(url, 216, 250))
        assertNotEquals(ImageKeys.memoryKey(url, 216, 216), ImageKeys.memoryKey(url, 1080, 810))
        assertNotEquals(ImageKeys.memoryKey(url, 216, 216), ImageKeys.memoryKey("$url?v=2", 216, 216))
        assertEquals("256x256|$url", ImageKeys.memoryKey(url, 216, 216))
    }

    @Test
    fun `디스크 이름은 주소의 sha256 16진수 64자로 안정적이다`() {
        val name = ImageKeys.diskName("https://example.com/a.jpg?x=1&y=/2")
        assertEquals(64, name.length)
        assertTrue(name.all { it in '0'..'9' || it in 'a'..'f' })
        assertEquals(name, ImageKeys.diskName("https://example.com/a.jpg?x=1&y=/2"))
        assertNotEquals(name, ImageKeys.diskName("https://example.com/b.jpg"))
        // 알려진 값: sha256("abc")
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", ImageKeys.diskName("abc"))
    }
}
