package com.herezi.feature.home

import com.herezi.core.domain.fake.FakeSpotsRepository
import com.herezi.core.ui.component.PosterStyle
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 히어로 카드(DESIGN.md §6.13): 사진이 있어도 그라데이션·흰 제목·메타를 그린다(#20, iOS #23). */
class HeroContentTest {
    private val now = Instant.parse("2026-11-20T03:00:00Z")
    private val seeds = FakeSpotsRepository.seedSpots(now)

    @Test
    fun `사진이 있으면 사진 위에 그라데이션과 제목과 메타를 그린다`() {
        val spot = seeds.first { it.imageUrl != null }.copy(distanceM = 350)

        val content = heroContent(spot, now)

        assertEquals(HeroMedia.Photo(FakeSpotsRepository.FAKE_PHOTO_URL), content.media)
        assertTrue(content.showsGradient)
        assertTrue(content.showsTitle)
        assertEquals(spot.title, content.title)
        assertTrue(content.meta.startsWith("축제·행사 · 350m · "))
        assertTrue(content.meta.endsWith(" · 무료"))
        // 사진을 못 불러오면 대체 포스터는 제목 없이 (카드가 제목을 그리므로 두 번 쓰지 않는다).
        assertFalse(content.media.posterStyle.title)
    }

    @Test
    fun `사진이 없으면 포스터 바탕이고 제목과 메타는 그대로 그린다`() {
        val spot = seeds.first { it.imageUrl == null }

        val content = heroContent(spot, now)

        assertEquals(HeroMedia.Poster, content.media)
        assertEquals(PosterStyle.Backdrop, content.media.posterStyle)
        assertTrue(content.showsGradient)
        assertTrue(content.showsTitle)
        assertEquals(spot.title, content.title)
    }

    @Test
    fun `빈 사진 주소는 사진이 없는 것으로 본다`() {
        val spot = seeds.first().copy(imageUrl = " ")

        assertEquals(HeroMedia.Poster, heroContent(spot, now).media)
    }
}
