package com.herezi.core.data

import com.herezi.core.domain.Category
import com.herezi.core.domain.GeoPoint
import com.herezi.core.domain.RecentSearchesRepository
import com.herezi.core.domain.SavedSpot
import com.herezi.core.domain.Spot
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CodecTest {
    private val spot = Spot(
        id = "tourapi:1",
        title = "서울빛초롱축제",
        category = Category.FESTIVAL,
        location = GeoPoint(37.569, 126.978),
        startsAt = Instant.parse("2026-11-20T09:00:00Z"),
        source = "tourapi",
        fetchedAt = Instant.parse("2026-11-20T00:00:00Z"),
        endsAt = Instant.parse("2026-12-14T13:00:00Z"),
        placeName = "청계광장",
        isFree = true,
        isEnded = true,
    )

    @Test
    fun `저장 스냅샷은 그대로 왕복한다`() {
        val items = listOf(SavedSpot(spot, Instant.parse("2026-11-20T05:00:00Z")))
        assertEquals(items, SavedCodec.decode(SavedCodec.encode(items)))
    }

    @Test
    fun `깨진 값이나 모르는 카테고리는 버린다`() {
        assertTrue(SavedCodec.decode("not json").isEmpty())
        val unknown = SavedCodec.encode(listOf(SavedSpot(spot, Instant.EPOCH))).replace("festival", "space")
        assertTrue(SavedCodec.decode(unknown).isEmpty())
    }

    @Test
    fun `최근 검색은 중복을 맨 위로 올리고 10개까지`() {
        var list = emptyList<String>()
        (1..12).forEach { list = RecentSearchesRepository.push(list, "q$it") }
        list = RecentSearchesRepository.push(list, "q5")
        assertEquals(10, list.size)
        assertEquals("q5", list.first())
        assertEquals(list, StringListCodec.decode(StringListCodec.encode(list)))
    }
}
