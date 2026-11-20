package com.herezi.core.network

import com.herezi.core.domain.Category
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MappingTest {
    private val sample =
        """
        {
          "items": [
            {
              "id": "tourapi:123", "title": "여의도 봄꽃 축제", "category": "nature",
              "lat": 37.5285, "lng": 126.9227,
              "starts_at": "2027-04-01T10:00:00+09:00", "ends_at": "2027-04-07T22:00:00Z",
              "is_free": true, "place_name": "여의서로", "source": "tourapi",
              "distance_m": 420, "fetched_at": "2027-03-31T18:30:00Z", "is_ended": false,
              "unknown_future_field": 1
            },
            {
              "id": "x:1", "title": "모르는 카테고리", "category": "unknown",
              "lat": 0, "lng": 0, "starts_at": "2027-04-01T10:00:00Z", "source": "x",
              "fetched_at": "2027-03-31T18:30:00Z", "is_ended": false
            }
          ],
          "next_cursor": "abc"
        }
        """.trimIndent()

    private fun dto(startsAt: String) = SpotDto(
        id = "a",
        title = "t",
        category = "popup",
        lat = 1.0,
        lng = 2.0,
        startsAt = startsAt,
        source = "s",
        fetchedAt = "2027-01-01T00:00:00Z",
        isEnded = false,
    )

    @Test
    fun `JSON을 도메인으로 바꾸고 모르는 카테고리 항목은 버린다`() {
        val page = HereziApiFactory.json.decodeFromString(SpotPageDto.serializer(), sample).toDomain()

        assertEquals(1, page.items.size)
        assertEquals("abc", page.nextCursor)
        val spot = page.items.single()
        assertEquals(Category.NATURE, spot.category)
        assertEquals(Instant.parse("2027-04-01T01:00:00Z"), spot.startsAt)
        assertEquals(Instant.parse("2027-04-07T22:00:00Z"), spot.endsAt)
        assertEquals(true, spot.isFree)
        assertEquals("여의서로", spot.placeName)
        assertEquals(420, spot.distanceM)
        assertEquals(Instant.parse("2027-03-31T18:30:00Z"), spot.fetchedAt)
        assertFalse(spot.isEnded)
    }

    @Test
    fun `끝난 상세는 isEnded 를 옮긴다`() {
        val spot = dto(startsAt = "2027-01-01T00:00:00Z").copy(isEnded = true).toDomain()!!
        assertTrue(spot.isEnded)
    }

    @Test
    fun `깨진 갱신 시각은 항목을 버린다`() {
        assertNull(dto(startsAt = "2027-01-01T00:00:00Z").copy(fetchedAt = "모름").toDomain())
    }

    @Test
    fun `지도 응답을 도메인으로 바꾼다`() {
        val json =
            """
            {"items":[{"id":"a","title":"t","category":"popup","lat":1,"lng":2,
              "starts_at":"2027-01-01T00:00:00Z","source":"s","fetched_at":"2027-01-01T00:00:00Z",
              "is_ended":false}],"truncated":true}
            """.trimIndent()
        val map = HereziApiFactory.json.decodeFromString(SpotMapDto.serializer(), json).toDomain()
        assertEquals(listOf("a"), map.items.map { it.id })
        assertTrue(map.truncated)
    }

    @Test
    fun `선택 필드가 없으면 기본값을 쓴다`() {
        val dto = dto(startsAt = "2027-01-01T00:00:00Z")
        val spot = dto.toDomain()!!
        assertFalse(spot.isFree)
        assertNull(spot.endsAt)
        assertNull(spot.distanceM)
    }

    @Test
    fun `깨진 시작 시각은 항목을 버린다`() {
        val dto = dto(startsAt = "어제")
        assertNull(dto.toDomain())
    }
}
